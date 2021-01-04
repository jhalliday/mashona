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

import io.mashona.pobj.transaction.events.*;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

/**
 * A logged (i.e. fault-tolerant) transaction.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-07
 */
public class PersistentTransaction extends VolatileTransaction {

    private static final XLogger logger = XLoggerFactory.getXLogger(PersistentTransaction.class);

    private final TransactionStore transactionStore;

    /**
     * Create a new transaction instance that will be logged to the provided store.
     *
     * @param transactionStore the backing storage used to persist the transaction state.
     */
    public PersistentTransaction(TransactionStore transactionStore) {
        this.transactionStore = transactionStore;
    }

    @Override
    protected void recordBeforeWriteEvent(BeforeWriteEvent beforeWriteEvent) {
        logger.entry(beforeWriteEvent);

        super.recordBeforeWriteEvent(beforeWriteEvent);
        transactionStore.persistBeforeWrite(beforeWriteEvent);

        logger.exit();
    }

    @Override
    protected void recordMallocEvent(MallocEvent mallocEvent) {
        logger.entry(mallocEvent);

        super.recordMallocEvent(mallocEvent);
        transactionStore.persistMemoryAllocation(mallocEvent);

        logger.exit();
    }

    @Override
    protected void recordDeallocateEvent(DeallocateEvent deallocateEvent) {
        logger.entry(deallocateEvent);

        super.recordDeallocateEvent(deallocateEvent);
        transactionStore.persistMemoryDelete(deallocateEvent);

        logger.exit();
    }

    @Override
    protected void recordOutcomeEvent(OutcomeEvent outcomeEvent) {
        logger.entry(outcomeEvent);

        super.recordOutcomeEvent(outcomeEvent);
        transactionStore.persistOutcomeEvent(outcomeEvent);

        logger.exit();
    }

    /**
     * Recover the transaction, bringing the state of the heap back to a consistent point.
     *
     * @param transactionalMemoryHeap The heap against which to apply the transactional changes.
     */
    public void recover(TransactionalMemoryHeap transactionalMemoryHeap) {
        logger.entry(transactionalMemoryHeap);

        TransactionEvent lastEntry = events.get(events.size() - 1);
        if (lastEntry instanceof OutcomeEvent && ((OutcomeEvent) lastEntry).isCommit()) {
            redo(transactionalMemoryHeap);
        } else {
            undo(transactionalMemoryHeap);
        }

        logger.exit();
    }

    void redo(TransactionalMemoryHeap transactionalPmemHeap) {
        for (TransactionEvent transactionEvent : events) {
            if (transactionEvent instanceof MallocEvent) {
                MallocEvent mallocTxEntry = (MallocEvent) transactionEvent;
                transactionalPmemHeap.getTransactionalCompositeAllocator().redo(mallocTxEntry);
            } else if (transactionEvent instanceof DeallocateEvent) {
                DeallocateEvent deleteTxEntry = (DeallocateEvent) transactionEvent;
                transactionalPmemHeap.getTransactionalCompositeAllocator().redo(deleteTxEntry);
            }
        }
    }
}
