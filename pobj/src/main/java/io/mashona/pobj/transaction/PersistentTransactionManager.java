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

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.List;

/**
 * A factory for logged (i.e. fault-tolerant) transactions.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-07
 */
public class PersistentTransactionManager extends TransactionManager {

    private static final XLogger logger = XLoggerFactory.getXLogger(PersistentTransactionManager.class);

    private final TransactionStore transactionStore;

    /**
     * Create a new factory instance that will subsequently create transaction
     * instances that use the provided store for persistence.
     *
     * @param transactionStore the backing storage used to persist the transaction state.
     */
    public PersistentTransactionManager(TransactionStore transactionStore) {
        this.transactionStore = transactionStore;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void begin() {
        logger.entry();

        currentTransaction = new PersistentTransaction(transactionStore);

        logger.exit();
    }

    /**
     * Recover the transactions from the store, bringing the state of the heap back to a consistent point.
     *
     * @param transactionalMemoryHeap The heap against which to apply the transactional changes.
     */
    public void recover(TransactionalMemoryHeap transactionalMemoryHeap) {
        logger.entry(transactionalMemoryHeap);

        List<PersistentTransaction> persistentTransactionList = transactionStore.read();

        for (PersistentTransaction persistentTransaction : persistentTransactionList) {
            currentTransaction = new VolatileTransaction();
            persistentTransaction.recover(transactionalMemoryHeap);
            currentTransaction = null;
        }

        logger.exit();
    }
}
