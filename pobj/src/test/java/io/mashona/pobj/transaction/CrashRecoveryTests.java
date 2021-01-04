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
package io.mashona.pobj.transaction;

import io.mashona.pobj.generated.PointImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for crash recovery of persistent transactions
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-07
 */
public class CrashRecoveryTests {

    private static File TEST_DIR = new File("/mnt/pmem/test"); // TODO  System.getenv("PMEM_TEST_DIR"));

    private PersistentTransactionManager persistentTransactionManager;
    private TransactionalCompositeAllocator compositeAllocator;
    private TransactionalMemoryHeap transactionalMemoryHeap;

    private static File storeFile = new File(TEST_DIR, "transaction_store");
    private static File heapFile = new File(TEST_DIR, "heap");
    private TransactionStore transactionStore;

    protected PersistentTransactionManager getTransactionManager() {
        return new PersistentTransactionManager(transactionStore);
    }

    @BeforeEach
    public void setUp() throws IOException {
        heapFile.delete();
        storeFile.delete();
        init();
    }

    @AfterEach
    public void tearDown() throws IOException {
        transactionalMemoryHeap.close();
        heapFile.delete();
        transactionStore.close();
        storeFile.delete();
    }

    private void init() throws IOException {
        transactionStore = new TransactionStore(storeFile, 1024*1024);
        persistentTransactionManager = getTransactionManager();
        compositeAllocator = new TransactionalCompositeAllocator(0, 1024 * 1024 * 4, persistentTransactionManager);
        transactionalMemoryHeap = new TransactionalMemoryHeap(
                heapFile, compositeAllocator.getBackingSize(), compositeAllocator, persistentTransactionManager);
    }

    private void simulateCrash() throws IOException {
        transactionStore.close();
        init();
    }

    @Test
    public void testRecoverCommittedTransaction() throws IOException {

        persistentTransactionManager.begin();

        PointImpl pointA = transactionalMemoryHeap.newInstance(PointImpl.class);
        pointA.setX(100);
        pointA.setY(200);

        persistentTransactionManager.commit();

        long addr = pointA.getMemory().getHeapOffset();

        simulateCrash();

        persistentTransactionManager.begin();
        assertThrows(IllegalArgumentException.class, () -> transactionalMemoryHeap.attachInstance(PointImpl.class, addr));
        persistentTransactionManager.rollback();

        persistentTransactionManager.recover(transactionalMemoryHeap);

        persistentTransactionManager.begin();
        PointImpl pointB = transactionalMemoryHeap.attachInstance(PointImpl.class, addr);

        assertEquals(100, pointB.getX());
        assertEquals(200, pointB.getY());

        pointA.getMemory().delete();
        pointB.getMemory().delete();
        persistentTransactionManager.commit();
    }

    @Test
    public void testRecoverRolledbackTransaction() throws IOException {

        persistentTransactionManager.begin();
        PointImpl pointA = transactionalMemoryHeap.newInstance(PointImpl.class);
        pointA.setX(100);
        persistentTransactionManager.commit();

        persistentTransactionManager.begin();
        pointA.setX(101);
        persistentTransactionManager.rollback();

        long addr = pointA.getMemory().getHeapOffset();

        simulateCrash();
        persistentTransactionManager.recover(transactionalMemoryHeap);

        persistentTransactionManager.begin();
        PointImpl pointB = transactionalMemoryHeap.attachInstance(PointImpl.class, addr);

        assertEquals(100, pointB.getX());

        pointA.getMemory().delete();
        pointB.getMemory().delete();
        persistentTransactionManager.commit();
    }
}
