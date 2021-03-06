/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.util.resource;

import java.util.Arrays;
import java.util.Collection;

import org.apache.hadoop.yarn.api.records.Resource;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class TestResourceCalculator {
  private ResourceCalculator resourceCalculator;

  @Parameterized.Parameters
  public static Collection<ResourceCalculator[]> getParameters() {
    return Arrays.asList(new ResourceCalculator[][] {
        { new DefaultResourceCalculator() },
        { new DominantResourceCalculator() } });
  }

  public TestResourceCalculator(ResourceCalculator rs) {
    this.resourceCalculator = rs;
  }
  
  @Test(timeout = 10000)
  public void testFitsIn() {
    Resource cluster = Resource.newInstance(1024, 1);

    if (resourceCalculator instanceof DefaultResourceCalculator) {
      Assert.assertTrue(resourceCalculator.fitsIn(cluster,
          Resource.newInstance(1, 2), Resource.newInstance(2, 1)));
      Assert.assertTrue(resourceCalculator.fitsIn(cluster,
          Resource.newInstance(1, 2), Resource.newInstance(2, 2)));
      Assert.assertTrue(resourceCalculator.fitsIn(cluster,
          Resource.newInstance(1, 2), Resource.newInstance(1, 2)));
      Assert.assertTrue(resourceCalculator.fitsIn(cluster,
          Resource.newInstance(1, 2), Resource.newInstance(1, 1)));
      Assert.assertFalse(resourceCalculator.fitsIn(cluster,
          Resource.newInstance(2, 1), Resource.newInstance(1, 2)));
    } else if (resourceCalculator instanceof DominantResourceCalculator) {
      Assert.assertFalse(resourceCalculator.fitsIn(cluster,
          Resource.newInstance(1, 2), Resource.newInstance(2, 1)));
      Assert.assertTrue(resourceCalculator.fitsIn(cluster,
          Resource.newInstance(1, 2), Resource.newInstance(2, 2)));
      Assert.assertTrue(resourceCalculator.fitsIn(cluster,
          Resource.newInstance(1, 2), Resource.newInstance(1, 2)));
      Assert.assertFalse(resourceCalculator.fitsIn(cluster,
          Resource.newInstance(1, 2), Resource.newInstance(1, 1)));
      Assert.assertFalse(resourceCalculator.fitsIn(cluster,
          Resource.newInstance(2, 1), Resource.newInstance(1, 2)));
    }
  }

  @Test(timeout = 10000)
  public void testResourceCalculatorCompareMethod() {
    Resource clusterResource = Resource.newInstance(0, 0);

    // For lhs == rhs
    Resource lhs = Resource.newInstance(0, 0);
    Resource rhs = Resource.newInstance(0, 0);
    assertResourcesOperations(clusterResource, lhs, rhs, false, true, false,
        true, lhs, lhs);

    // lhs > rhs
    lhs = Resource.newInstance(1, 1);
    rhs = Resource.newInstance(0, 0);
    assertResourcesOperations(clusterResource, lhs, rhs, false, false, true,
        true, lhs, rhs);

    // For lhs < rhs
    lhs = Resource.newInstance(0, 0);
    rhs = Resource.newInstance(1, 1);
    assertResourcesOperations(clusterResource, lhs, rhs, true, true, false,
        false, rhs, lhs);

    if (!(resourceCalculator instanceof DominantResourceCalculator)) {
      return;
    }

    // verify for 2 dimensional resources i.e memory and cpu
    // dominant resource types
    lhs = Resource.newInstance(1, 0);
    rhs = Resource.newInstance(0, 1);
    assertResourcesOperations(clusterResource, lhs, rhs, false, true, false,
        true, lhs, lhs);

    lhs = Resource.newInstance(0, 1);
    rhs = Resource.newInstance(1, 0);
    assertResourcesOperations(clusterResource, lhs, rhs, false, true, false,
        true, lhs, lhs);

    lhs = Resource.newInstance(1, 1);
    rhs = Resource.newInstance(1, 0);
    assertResourcesOperations(clusterResource, lhs, rhs, false, false, true,
        true, lhs, rhs);

    lhs = Resource.newInstance(0, 1);
    rhs = Resource.newInstance(1, 1);
    assertResourcesOperations(clusterResource, lhs, rhs, true, true, false,
        false, rhs, lhs);

  }

  private void assertResourcesOperations(Resource clusterResource,
      Resource lhs, Resource rhs, boolean lessThan, boolean lessThanOrEqual,
      boolean greaterThan, boolean greaterThanOrEqual, Resource max,
      Resource min) {

    Assert.assertEquals("Less Than operation is wrongly calculated.", lessThan,
        Resources.lessThan(resourceCalculator, clusterResource, lhs, rhs));

    Assert.assertEquals(
        "Less Than Or Equal To operation is wrongly calculated.",
        lessThanOrEqual, Resources.lessThanOrEqual(resourceCalculator,
            clusterResource, lhs, rhs));

    Assert.assertEquals("Greater Than operation is wrongly calculated.",
        greaterThan,
        Resources.greaterThan(resourceCalculator, clusterResource, lhs, rhs));

    Assert.assertEquals(
        "Greater Than Or Equal To operation is wrongly calculated.",
        greaterThanOrEqual, Resources.greaterThanOrEqual(resourceCalculator,
            clusterResource, lhs, rhs));

    Assert.assertEquals("Max(value) Operation wrongly calculated.", max,
        Resources.max(resourceCalculator, clusterResource, lhs, rhs));

    Assert.assertEquals("Min(value) operation is wrongly calculated.", min,
        Resources.min(resourceCalculator, clusterResource, lhs, rhs));
  }

  /**
   * Test resource normalization.
   */
  @Test(timeout = 10000)
  public void testNormalize() {
    // requested resources value cannot be an arbitrary number.
    Resource ask = Resource.newInstance(1111, 2);
    Resource min = Resource.newInstance(1024, 1);
    Resource max = Resource.newInstance(8 * 1024, 8);
    Resource increment = Resource.newInstance(1024, 4);
    if (resourceCalculator instanceof DefaultResourceCalculator) {
      Resource result = Resources.normalize(resourceCalculator,
          ask, min, max, increment);

      Assert.assertEquals(2 * 1024, result.getMemorySize());
    } else if (resourceCalculator instanceof DominantResourceCalculator) {
      Resource result = Resources.normalize(resourceCalculator,
          ask, min, max, increment);

      Assert.assertEquals(2 * 1024, result.getMemorySize());
      Assert.assertEquals(4, result.getVirtualCores());
    }

    // if resources asked are less than minimum resource, then normalize it to
    // minimum resource.
    ask = Resource.newInstance(512, 0);
    min = Resource.newInstance(2 * 1024, 2);
    max = Resource.newInstance(8 * 1024, 8);
    increment = Resource.newInstance(1024, 1);
    if (resourceCalculator instanceof DefaultResourceCalculator) {
      Resource result = Resources.normalize(resourceCalculator,
          ask, min, max, increment);

      Assert.assertEquals(2 * 1024, result.getMemorySize());
    } else if (resourceCalculator instanceof DominantResourceCalculator) {
      Resource result = Resources.normalize(resourceCalculator,
          ask, min, max, increment);

      Assert.assertEquals(2 * 1024, result.getMemorySize());
      Assert.assertEquals(2, result.getVirtualCores());
    }

    // if resources asked are larger than maximum resource, then normalize it to
    // maximum resources.
    ask = Resource.newInstance(9 * 1024, 9);
    min = Resource.newInstance(2 * 1024, 2);
    max = Resource.newInstance(8 * 1024, 8);
    increment = Resource.newInstance(1024, 1);
    if (resourceCalculator instanceof DefaultResourceCalculator) {
      Resource result = Resources.normalize(resourceCalculator,
          ask, min, max, increment);

      Assert.assertEquals(8 * 1024, result.getMemorySize());
    } else if (resourceCalculator instanceof DominantResourceCalculator) {
      Resource result = Resources.normalize(resourceCalculator,
          ask, min, max, increment);

      Assert.assertEquals(8 * 1024, result.getMemorySize());
      Assert.assertEquals(8, result.getVirtualCores());
    }

    // if increment is 0, use minimum resource as the increment resource.
    ask = Resource.newInstance(1111, 2);
    min = Resource.newInstance(2 * 1024, 2);
    max = Resource.newInstance(8 * 1024, 8);
    increment = Resource.newInstance(0, 0);
    if (resourceCalculator instanceof DefaultResourceCalculator) {
      Resource result = Resources.normalize(resourceCalculator,
          ask, min, max, increment);

      Assert.assertEquals(2 * 1024, result.getMemorySize());
    } else if (resourceCalculator instanceof DominantResourceCalculator) {
      Resource result = Resources.normalize(resourceCalculator,
          ask, min, max, increment);

      Assert.assertEquals(2 * 1024, result.getMemorySize());
      Assert.assertEquals(2, result.getVirtualCores());
    }
  }
}