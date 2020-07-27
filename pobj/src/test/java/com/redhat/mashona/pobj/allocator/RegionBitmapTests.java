/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.mashona.pobj.allocator;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the RegionBitmap.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-06
 */
public class RegionBitmapTests {

    @Test
    public void testBasicOperations() {

        RegionBitmap regionBitmap = new RegionBitmap(0, new RegionConfig(8, 1024));

        assertEquals(1024, regionBitmap.getBackingSize());
        assertEquals(8, regionBitmap.getElementSize());
        assertEquals(1024 / 8, regionBitmap.getMaxElements());
        assertEquals(regionBitmap.getMaxElements(), regionBitmap.getNumAvail());

        Set<Long> allocations = new HashSet<>();
        for (int i = 0; i < regionBitmap.getMaxElements(); i++) {
            long allocation = regionBitmap.allocate();
            assertTrue(allocations.add(allocation));
            assertFalse(regionBitmap.isFree(allocation));
        }

        assertEquals(regionBitmap.getMaxElements(), allocations.size());
        assertEquals(0, regionBitmap.getNumAvail());

        assertEquals(-1, regionBitmap.allocate());

        for (Long l : allocations) {
            regionBitmap.free(l);
            assertTrue(regionBitmap.isFree(l));
        }

        assertEquals(regionBitmap.getMaxElements(), regionBitmap.getNumAvail());
    }

    @Test
    public void testNonMultipleSize() {
        RegionBitmap regionBitmap = new RegionBitmap(0, new RegionConfig(8, 1024 - 1));
        assertEquals((1024 / 8) - 1, regionBitmap.getMaxElements());
    }

    @Test
    public void testIntegerOverflow() {
        assertThrows(IllegalArgumentException.class,
                () -> new RegionBitmap(0, new RegionConfig(1, 1L + Integer.MAX_VALUE)));
    }

    @Test
    public void testInvalidFrees() {

        RegionBitmap regionBitmap = new RegionBitmap(0, new RegionConfig(8, 1024));

        long addr = regionBitmap.allocate();
        regionBitmap.free(addr);

        assertThrows(IllegalArgumentException.class,
                () -> regionBitmap.free(addr));

        assertThrows(IllegalArgumentException.class,
                () -> regionBitmap.free(-1));

        assertThrows(IllegalArgumentException.class,
                () -> regionBitmap.free(1024 + 8));
    }

    @Test
    public void testComparator() {
        RegionBitmap regionBitmapA = new RegionBitmap(0, new RegionConfig(8, 1024));
        RegionBitmap regionBitmapB = new RegionBitmap(1024, new RegionConfig(8, 1024));
        RegionBitmap regionBitmapC = new RegionBitmap(2048, new RegionConfig(8, 1024));

        assertEquals(-1, regionBitmapA.compareTo(regionBitmapB.baseAddress));
        assertEquals(0, regionBitmapB.compareTo(regionBitmapB.baseAddress));
        assertEquals(1, regionBitmapC.compareTo(regionBitmapB.baseAddress));

        List<RegionBitmap> sortedList = List.of(regionBitmapA, regionBitmapB, regionBitmapC);

        List<RegionBitmap> list = new ArrayList<>(List.of(regionBitmapB, regionBitmapC, regionBitmapA));

        assertNotEquals(sortedList, list);
        Collections.sort(list, RegionBitmap.COMPARATOR);
        assertEquals(sortedList, list);
    }
}
