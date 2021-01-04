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

import io.mashona.pobj.transaction.events.CreateEvent;
import io.mashona.pobj.transaction.events.DeleteEvent;
import io.mashona.pobj.transaction.events.TransactionEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit tests for integrated behaviour of the transaction system classes.
 * This extends the volatile (in-memory) tests with coverage of the persistence (logging) mechanism.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-07
 */
public class PersistentTransactionWiringTests extends TransactionWiringTests {

    private static File TEST_DIR = new File("/mnt/pmem/test"); // TODO  System.getenv("PMEM_TEST_DIR"));

    private static File storeFile = new File(TEST_DIR, "transaction_store");
    private TransactionStore transactionStore;

    @BeforeEach
    public void setUp() throws IOException {
        storeFile.delete();
        transactionStore = new TransactionStore(storeFile, 1024 * 1024);
        super.setUp();
    }

    @AfterEach
    public void tearDown() throws IOException {
        super.tearDown();
        transactionStore.close();
        storeFile.delete();
    }

    @Override
    protected TransactionManager getTransactionManager() {
        return new PersistentTransactionManager(transactionStore);
    }

    @Override
    protected void checkTransactionLog() {

        VolatileTransaction volatileTransaction = transactionManager.getCurrent();
        List<TransactionEvent> volatileTransactionEventsList = volatileTransaction.events;

        List<PersistentTransaction> transactionList = transactionStore.read();
        PersistentTransaction latestLoggedTransaction = transactionList.get(transactionList.size() - 1);

        Iterator<TransactionEvent> loggedTransactionEventsIterator = latestLoggedTransaction.events.iterator();

        for (TransactionEvent expectedEvent : volatileTransactionEventsList) {
            if (expectedEvent instanceof CreateEvent || expectedEvent instanceof DeleteEvent) {
                continue; // these are volatile and not persisted
            }
            TransactionEvent actualEvent = loggedTransactionEventsIterator.next();
            assertEquals(expectedEvent, actualEvent);
        }

        assertFalse(loggedTransactionEventsIterator.hasNext());
    }
}
