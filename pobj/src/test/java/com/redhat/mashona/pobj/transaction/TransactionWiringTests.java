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
package com.redhat.mashona.pobj.transaction;

import com.redhat.mashona.pobj.generated.PointImpl;
import com.redhat.mashona.pobj.transaction.events.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for integrated behaviour of the transaction system classes.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-07
 */
public class TransactionWiringTests {

    protected TransactionManager transactionManager;
    protected TransactionalCompositeAllocator compositeAllocator;
    protected TransactionalMemoryHeap transactionalMemoryHeap;

    protected TransactionManager getTransactionManager() {
        return new TransactionManager();
    }

    protected void checkTransactionLog() {
        // null-op here in the volatile version - extension point for PersistentTransactionWiringTests
    }

    @BeforeEach
    public void setUp() throws IOException {
        transactionManager = getTransactionManager();
        compositeAllocator = new TransactionalCompositeAllocator(0, 1024 * 1024 * 4, transactionManager);
        transactionalMemoryHeap = new TransactionalMemoryHeap(
                new File("/mnt/pmem/test/heap"), compositeAllocator.getBackingSize(), compositeAllocator, transactionManager);
    }

    @AfterEach
    public void tearDown() throws IOException {
        transactionalMemoryHeap.close();
    }

    @Test
    public void testAllocatingAndMutating() throws IOException {

        transactionManager.begin();

        PointImpl pointA = transactionalMemoryHeap.newInstance(PointImpl.class);
        pointA.setX(100);
        pointA.setY(200);

        PointImpl pointB = transactionalMemoryHeap.newInstance(PointImpl.class);
        transactionalMemoryHeap.delete(pointB);

        assertThrows(IllegalStateException.class, () -> pointB.getX());
        assertThrows(IllegalStateException.class, () -> pointB.setX(300));

        VolatileTransaction volatileTransaction = transactionManager.getCurrent();

        transactionManager.commit();

        assertEquals(100, pointA.getX());
        assertEquals(200, pointA.getY());

        assertThrows(IllegalStateException.class, () -> pointB.getX());

        List<TransactionEvent> entries = volatileTransaction.events;
        assertEquals(9, entries.size());
        assertEquals(MallocEvent.class, entries.get(0).getClass()); // alloc pointA
        assertEquals(CreateEvent.class, entries.get(1).getClass()); // create point A
        assertEquals(BeforeWriteEvent.class, entries.get(2).getClass()); // pointA.setX
        assertEquals(BeforeWriteEvent.class, entries.get(3).getClass()); // pointA.setY
        assertEquals(MallocEvent.class, entries.get(4).getClass()); // alloc pointB
        assertEquals(CreateEvent.class, entries.get(5).getClass()); // create pointB
        assertEquals(DeallocateEvent.class, entries.get(6).getClass()); // deallocate pointB
        assertEquals(DeleteEvent.class, entries.get(7).getClass()); // delete pointB
        assertEquals(OutcomeEvent.class, entries.get(8).getClass()); // commit

        checkTransactionLog();

        transactionManager.begin();
        pointA.getMemory().delete();
        transactionManager.commit();
    }

    @Test
    public void testRollback() throws IOException {

        transactionManager.begin();

        PointImpl pointA = transactionalMemoryHeap.newInstance(PointImpl.class);
        pointA.setX(100);

        PointImpl pointB = transactionalMemoryHeap.newInstance(PointImpl.class);
        pointB.setX(200);

        transactionManager.commit();

        assertEquals(100, pointA.getX());
        assertEquals(200, pointB.getX());

        transactionManager.begin();

        pointA.setX(101);

        transactionalMemoryHeap.delete(pointB);

        PointImpl pointC = transactionalMemoryHeap.newInstance(PointImpl.class);
        pointC.setX(300);

        VolatileTransaction volatileTransaction = transactionManager.getCurrent();

        transactionManager.rollback();

        assertEquals(100, pointA.getX());
        assertEquals(200, pointB.getX());
        assertThrows(IllegalStateException.class, () -> pointC.getX());

        List<TransactionEvent> entries = volatileTransaction.events;
        assertEquals(7, entries.size());
        assertEquals(BeforeWriteEvent.class, entries.get(0).getClass()); // pointA.setX
        assertEquals(DeallocateEvent.class, entries.get(1).getClass()); // deallocate pointB
        assertEquals(DeleteEvent.class, entries.get(2).getClass()); // delete pointB
        assertEquals(MallocEvent.class, entries.get(3).getClass()); // alloc pointC
        assertEquals(CreateEvent.class, entries.get(4).getClass()); // create pointC
        assertEquals(BeforeWriteEvent.class, entries.get(5).getClass()); // pointC.setX
        assertEquals(OutcomeEvent.class, entries.get(6).getClass()); // rollback

        checkTransactionLog();

        transactionManager.begin();
        pointA.getMemory().delete();
        pointB.getMemory().delete();
        pointC.getMemory().delete();
        transactionManager.commit();
    }
}
