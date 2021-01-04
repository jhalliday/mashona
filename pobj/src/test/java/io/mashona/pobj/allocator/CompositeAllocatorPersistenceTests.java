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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for persistence of CompositeAllocators.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-06
 */
public class CompositeAllocatorPersistenceTests {

    @Test
    public void testPersistable() {

        CompositeAllocator compositeAllocator = new CompositeAllocator(0, 1024 * 1024 * 8);
        CompositeAllocatorPersistence compositeAllocatorPersistence = new CompositeAllocatorPersistence();

        for (int i = 0; i < (1024 * 1024 * 4) / 8; i++) {
            assertNotEquals(-1, compositeAllocator.allocate(8));
        }

        for (int i = 0; i < (1024 * 1024 * 4) / 32; i++) {
            assertNotEquals(-1, compositeAllocator.allocate(32));
        }

        assertEquals(-1, compositeAllocator.allocate(8));
        assertEquals(-1, compositeAllocator.allocate(32));

        ByteBuffer byteBuffer = ByteBuffer.allocate(1024 * 128);
        compositeAllocatorPersistence.writeInto(compositeAllocator, byteBuffer);
        byteBuffer.flip();

        CompositeAllocator recoveredCompositeAllocator = compositeAllocatorPersistence.readFrom(byteBuffer);

        assertEquals(1024 * 1024 * 8L, recoveredCompositeAllocator.getBackingSize());

        assertEquals(-1, recoveredCompositeAllocator.allocate(8));
        assertEquals(-1, recoveredCompositeAllocator.allocate(32));
    }
}
