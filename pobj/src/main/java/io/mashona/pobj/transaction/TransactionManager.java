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

/**
 * Provides management of transaction boundaries.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-07
 */
public class TransactionManager {

    private static final XLogger logger = XLoggerFactory.getXLogger(TransactionManager.class);

    private TransactionalMemoryHeap transactionalMemoryHeap;
    protected VolatileTransaction currentTransaction;

    /**
     * Create a new transaction
     */
    public void begin() {
        logger.entry();

        currentTransaction = new VolatileTransaction();

        logger.exit();
    }

    /**
     * Complete the current transaction
     */
    public void commit() {
        logger.entry();

        currentTransaction.commit();

        logger.exit();
    }

    /**
     * Rolls back the current transaction
     */
    public void rollback() {
        logger.entry();

        currentTransaction.rollback(transactionalMemoryHeap);

        logger.exit();
    }

    protected void setTransactionalMemoryHeap(TransactionalMemoryHeap transactionalMemoryHeap) {
        this.transactionalMemoryHeap = transactionalMemoryHeap;
    }

    protected VolatileTransaction getCurrent() {
        return currentTransaction;
    }
}
