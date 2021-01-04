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
package io.mashona.pobj.allocator;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for persistence of RegionBitmaps.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-06
 */
public class RegionBitmapPersistenceTests {

    @Test
    public void testPersistable() {

        RegionBitmap regionBitmap = new RegionBitmap(1024, new RegionConfig(8, 552));
        RegionBitmapPersistence regionBitmapPersistence = new RegionBitmapPersistence();

        Set<Long> allocations = new HashSet<>();
        for (int i = 0; i < regionBitmap.getMaxElements(); i++) {
            long allocation = regionBitmap.allocate();
            assertFalse(allocations.contains(allocation));
            allocations.add(allocation);
        }

        assertEquals(regionBitmap.getMaxElements(), allocations.size());

        assertEquals(-1, regionBitmap.allocate());

        ByteBuffer byteBuffer = ByteBuffer.allocate(48);
        regionBitmapPersistence.writeInto(regionBitmap, byteBuffer);
        byteBuffer.flip();

        RegionBitmap recoveredRegionBitmap = regionBitmapPersistence.readFrom(byteBuffer);

        assertEquals(regionBitmap.getBackingSize(), recoveredRegionBitmap.getBackingSize());
        assertEquals(regionBitmap.getElementSize(), recoveredRegionBitmap.getElementSize());
        assertEquals(regionBitmap.getMaxElements(), recoveredRegionBitmap.getMaxElements());
        assertEquals(regionBitmap.getNumAvail(), recoveredRegionBitmap.getNumAvail());

        assertEquals(-1, recoveredRegionBitmap.allocate());
    }
}
