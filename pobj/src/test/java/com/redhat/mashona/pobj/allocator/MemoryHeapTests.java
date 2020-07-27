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

import com.redhat.mashona.pobj.generated.MBOTestEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for the MemoryHeap.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-06
 */
public class MemoryHeapTests {

    private static File TEST_DIR = new File("/mnt/pmem/test"); // TODO  System.getenv("PMEM_TEST_DIR"));

    File heapFile = new File(TEST_DIR, "test.heap");
    private MemoryHeap memoryHeap;

    @BeforeEach
    public void setUp() throws IOException {

        CompositeAllocator compositeAllocator = new CompositeAllocator(0, CompositeAllocatorTests.PAGE_SIZE);

        if (heapFile.exists()) {
            heapFile.delete();
        }

        memoryHeap = new MemoryHeap(heapFile, compositeAllocator.getBackingSize(), compositeAllocator);
    }

    @AfterEach
    public void tearDown() throws IOException {
        memoryHeap.close();
        if (heapFile.exists()) {
            heapFile.delete();
        }
    }

    @Test
    public void testBasicOperations() {

        MBOTestEntity mboTestEntity = memoryHeap.newInstance(MBOTestEntity.class);

        assertEquals(0, mboTestEntity.getMyByte());
        mboTestEntity.setMyByte((byte) 1);
        assertEquals(1, mboTestEntity.getMyByte());

        MBOTestEntity duplicateTestEntity = memoryHeap.attachInstance(MBOTestEntity.class, mboTestEntity.getMemory().getHeapOffset());
        assertEquals(1, duplicateTestEntity.getMyByte());

        memoryHeap.delete(mboTestEntity);
        memoryHeap.delete(duplicateTestEntity);

        assertThrows(IllegalArgumentException.class, () -> memoryHeap.attachInstance(MBOTestEntity.class, mboTestEntity.getMemory().getHeapOffset()));

        assertThrows(IllegalStateException.class, () -> mboTestEntity.setMyByte((byte) 1));
    }

    @Test
    public void testOperateOnClosed() throws IOException {
        memoryHeap.close();
        assertThrows(IllegalStateException.class, () -> memoryHeap.newInstance(MBOTestEntity.class));
        assertThrows(IllegalStateException.class, () -> memoryHeap.delete(null));
    }

    @Test
    public void testBadObjectType() {
        assertThrows(RuntimeException.class, () -> memoryHeap.newInstance(BogusMemoryBackedObject.class));
    }

    @Test
    public void testNotEnoughSpace() {
        assertThrows(RuntimeException.class, () -> memoryHeap.newInstance(LargeMemoryBackedObject.class));
    }
}
