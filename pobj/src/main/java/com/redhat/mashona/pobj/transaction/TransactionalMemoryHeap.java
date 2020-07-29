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

import com.redhat.mashona.pobj.allocator.MemoryHeap;
import com.redhat.mashona.pobj.runtime.MemoryOperations;
import com.redhat.mashona.pobj.transaction.events.BeforeWriteEvent;

import jdk.incubator.foreign.MemorySegment;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Manages a contiguous region of memory, mapped from a file,
 * as a heap space within which objects of varying size may be dynamically and transactionally allocated.
 * <p>
 * Note that the allocation tracking is not done within the file itself
 * and should be persisted independently if required.
 * <p>
 * Instances of this class are threadsafe if provided exclusive access to the underlying file and allocator.
 * If other instances (or external processes) access the same structures, all bets are off.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @see MemoryHeap
 * @since 2020-07
 */
public class TransactionalMemoryHeap extends MemoryHeap {

    private static final XLogger logger = XLoggerFactory.getXLogger(TransactionalMemoryHeap.class);

    private final TransactionManager transactionManager;

    /**
     * Create a new heap abstraction over a given file, with memory use tracking as provided by the allocator
     * and transaction tracking provided by the transaction manager.
     *
     * @param file               The backing file for persistent storage.
     * @param length             The size in bytes of the memory region. Care should be taken that this is at least as big
     *                           as that configured for the provide allocator.
     * @param compositeAllocator The memory use bookkeeping object.
     * @param transactionManager The transaction coordination service to use.
     * @throws IOException if memory mapping of the file fails.
     */
    public TransactionalMemoryHeap(File file, long length, TransactionalCompositeAllocator compositeAllocator,
                                   TransactionManager transactionManager) throws IOException {
        super(file, length, compositeAllocator);
        logger.entry(file, length, compositeAllocator, transactionManager);

        this.transactionManager = transactionManager;
        transactionManager.setTransactionalMemoryHeap(this);

        logger.exit();
    }

    @Override
    protected MemoryOperations wrapMemory(long addr, MemorySegment memorySegment) {
        return new TransactionalMemoryOperations(addr, memorySegment, transactionManager);
    }

    protected TransactionalCompositeAllocator getTransactionalCompositeAllocator() {
        return (TransactionalCompositeAllocator) compositeAllocator;
    }

    protected void undo(BeforeWriteEvent beforeWriteEvent) {
        MemorySegment segment = memorySegment.asSlice(beforeWriteEvent.getOffset(), beforeWriteEvent.getSize());
        segment.asByteBuffer().put(beforeWriteEvent.getByteBuffer());
        beforeWriteEvent.getByteBuffer().rewind();
    }
}
