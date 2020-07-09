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

import com.redhat.mashona.pobj.transaction.events.*;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * An in-memory (i.e. non-persistent) transaction.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-07
 */
public class VolatileTransaction {

    private static final XLogger logger = XLoggerFactory.getXLogger(VolatileTransaction.class);

    protected final List<TransactionEvent> events = new ArrayList<>();

    protected VolatileTransaction() {
    }

    protected void recordBeforeWriteEvent(BeforeWriteEvent beforeWriteEvent) {
        logger.entry(beforeWriteEvent);

        ByteBuffer byteBuffer = ByteBuffer.allocate((int) beforeWriteEvent.getSize()); // TODO force int, or require MemorySegment.copy
        byteBuffer.put(beforeWriteEvent.getByteBuffer());
        byteBuffer.rewind();

        beforeWriteEvent = new BeforeWriteEvent(beforeWriteEvent.getOffset(), beforeWriteEvent.getSize(), byteBuffer);
        events.add(beforeWriteEvent);

        logger.exit();
    }

    protected void recordMallocEvent(MallocEvent mallocEvent) {
        logger.entry(mallocEvent);

        events.add(mallocEvent);

        logger.exit();
    }

    protected void recordDeallocateEvent(DeallocateEvent deallocateEvent) {
        logger.entry(deallocateEvent);

        events.add(deallocateEvent);

        logger.exit();
    }

    protected void recordDeleteEvent(DeleteEvent deleteEvent) {
        logger.entry(deleteEvent);

        events.add(deleteEvent);

        logger.exit();
    }

    protected void recordCreateEvent(CreateEvent createEvent) {
        logger.entry(createEvent);

        events.add(createEvent);

        logger.exit();
    }

    protected void commit() {
        logger.entry();

        OutcomeEvent outcomeEvent = new OutcomeEvent(true);
        events.add(outcomeEvent);
        complete();

        logger.exit();
    }

    protected void rollback(TransactionalMemoryHeap transactionalPmemHeap) {
        logger.entry(transactionalPmemHeap);

        OutcomeEvent outcomeEvent = new OutcomeEvent(false);
        events.add(outcomeEvent);
        undo(transactionalPmemHeap);

        logger.exit();
    }

    protected void complete() {
        for (int i = 0; i < events.size(); i++) {
            TransactionEvent event = events.get(i);
            if (event instanceof DeleteEvent) {
                DeleteEvent deleteEvent = (DeleteEvent) event;
                deleteEvent.getMemory().complete();
            }
        }
    }

    protected void undo(TransactionalMemoryHeap transactionalPmemHeap) {

        for (int i = 0; i < events.size(); i++) {
            TransactionEvent event = events.get(i);
            if (event instanceof BeforeWriteEvent) {
                BeforeWriteEvent beforeWriteEvent = (BeforeWriteEvent) event;
                transactionalPmemHeap.undo(beforeWriteEvent);
            }
        }

        for (int i = events.size() - 1; i >= 0; i--) {
            TransactionEvent event = events.get(i);

            if (event instanceof MallocEvent) {
                MallocEvent mallocEvent = (MallocEvent) event;
                transactionalPmemHeap.getTransactionalCompositeAllocator().undo(mallocEvent);
            }
            if (event instanceof CreateEvent) {
                CreateEvent createEvent = (CreateEvent) event;
                createEvent.getMemory().delete0();
            }

            if (event instanceof DeallocateEvent) {
                DeallocateEvent deallocateEvent = (DeallocateEvent) event;
                transactionalPmemHeap.getTransactionalCompositeAllocator().undo(deallocateEvent);
            }
            if (event instanceof DeleteEvent) {
                DeleteEvent deleteEvent = (DeleteEvent) event;
                deleteEvent.getMemory().undelete();
            }
        }
    }
}
