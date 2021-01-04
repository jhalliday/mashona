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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the CompositeAllocator.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-06
 */
public class CompositeAllocatorTests {

    public static int PAGE_SIZE = 1024 * 1024 * 4;

    @Test
    public void testBasicOperations() {

        CompositeAllocator compositeAllocator = new CompositeAllocator(0, PAGE_SIZE);

        List<Long> addresses = fill(compositeAllocator, 8);
        assertEquals(PAGE_SIZE / 8, addresses.size());

        Set<Long> uniqAddresses = new HashSet<>(addresses);
        assertEquals(PAGE_SIZE / 8, uniqAddresses.size());
        assertFalse(uniqAddresses.contains(-1L));

        assertEquals(-1, compositeAllocator.allocate(8));

        empty(compositeAllocator, 8, addresses);
    }

    @Test
    public void testBackingTooSmall() {
        assertThrows(IllegalArgumentException.class, () -> new CompositeAllocator(0, PAGE_SIZE - 1));
    }

    @Test
    public void testRequestTooBig() {
        CompositeAllocator compositeAllocator = new CompositeAllocator(0, PAGE_SIZE);
        assertEquals(-1, compositeAllocator.allocate(PAGE_SIZE + 1));

        assertThrows(IllegalArgumentException.class, () -> compositeAllocator.free(0, PAGE_SIZE + 1));
    }

    @Test
    public void testOddSize() {
        CompositeAllocator compositeAllocator = new CompositeAllocator(0, 1024 * 1024 * 4);
        long addr = compositeAllocator.allocate(5);
        assertNotEquals(-1, addr);
        compositeAllocator.free(addr, 5);
    }

    private void empty(CompositeAllocator compositeAllocator, int elementSize, List<Long> addresses) {
        for (Long l : addresses) {
            compositeAllocator.free(l, elementSize);
            assertTrue(compositeAllocator.isFree(l, elementSize));
        }
    }

    private List<Long> fill(CompositeAllocator compositeAllocator, int elementSize) {
        int n = (int) compositeAllocator.getBackingSize() / elementSize;
        List<Long> allocatedAddresses = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Long addr = compositeAllocator.allocate(elementSize);
            allocatedAddresses.add(addr);
            assertFalse(compositeAllocator.isFree(addr, elementSize));
        }
        return allocatedAddresses;
    }
}
