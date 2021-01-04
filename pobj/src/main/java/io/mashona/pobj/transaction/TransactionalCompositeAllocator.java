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

import io.mashona.pobj.allocator.CompositeAllocator;
import io.mashona.pobj.transaction.events.DeallocateEvent;
import io.mashona.pobj.transaction.events.MallocEvent;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

/**
 * Provides transactional memory tracking for allocations of varied sizes within a contiguous range,
 * by treating the overall space as dynamically composed of regions dedicated for each allocation size.
 * <p>
 * This class provides bookkeeping only. The actual memory space being managed is theoretical
 * to the allocator and must be provided elsewhere, as by e.g. TransactionalHeap.
 * <p>
 * This class is NOT threadsafe.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @see CompositeAllocator
 * @since 2020-07
 */
public class TransactionalCompositeAllocator extends CompositeAllocator {

    private static final XLogger logger = XLoggerFactory.getXLogger(TransactionalCompositeAllocator.class);

    private final TransactionManager transactionManager;

    /**
     * Creates a new allocator with default region configuration, the overall area starting
     * at baseAddress and extending for backingSize bytes.
     * <p>
     * A minimum backing size of 4MB is required and an 8-byte aligned base address is recommended.
     *
     * @param baseAddress The starting point of the memory range.
     * @param backingSize The total length of the memory region.
     * @param transactionManager the transaction manager to which state change events should be recorded.
     */
    public TransactionalCompositeAllocator(long baseAddress, long backingSize, TransactionManager transactionManager) {
        super(baseAddress, backingSize);
        logger.entry(baseAddress, backingSize, transactionManager);

        this.transactionManager = transactionManager;

        logger.exit();
    }

    @Override
    protected long allocate(long size, boolean forInternalUse) {
        long offset = allocate0(size, forInternalUse);
        if (!forInternalUse && offset != -1) {
            transactionManager.getCurrent().recordMallocEvent(new MallocEvent(offset, size, forInternalUse));
        }
        return offset;
    }

    protected long allocate0(long size, boolean forInternalUse) {
        long offset = super.allocate(size, forInternalUse);
        return offset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void free(long address, long size) {
        logger.entry(address, size);

        free0(address, size);
        transactionManager.getCurrent().recordDeallocateEvent(new DeallocateEvent(address, size));

        logger.exit();
    }

    protected void free0(long address, long size) {
        super.free(address, size);
    }

    protected void redo(MallocEvent mallocEvent) {
        allocate0(mallocEvent.getSize(), false);
    }

    protected void redo(DeallocateEvent deallocateEvent) {
        free0(deallocateEvent.getOffset(), deallocateEvent.getSize());
    }

    protected void undo(MallocEvent mallocEvent) {
        free0(mallocEvent.getOffset(), mallocEvent.getSize());
    }

    protected void undo(DeallocateEvent deallocateEvent) {
        allocate0(deallocateEvent.getSize(), false);
    }
}
