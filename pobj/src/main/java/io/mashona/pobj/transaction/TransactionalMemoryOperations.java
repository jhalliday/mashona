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

import io.mashona.pobj.runtime.MemoryOperations;
import io.mashona.pobj.transaction.events.BeforeWriteEvent;
import io.mashona.pobj.transaction.events.CreateEvent;
import io.mashona.pobj.transaction.events.DeleteEvent;

import jdk.incubator.foreign.MemorySegment;

/**
 * Provides methods to store Java primitive types in a region of memory,
 * such as to allow an object to 'serialize' state to an off-heap store
 * at field granularity and access them in-place individually.
 *
 * Event interceptors are used to provide atomic transaction support, but not locking.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-07
 */
public class TransactionalMemoryOperations extends MemoryOperations {

    private boolean isDeleted;
    private final TransactionManager transactionManager;

    /**
     * Creates a new instance by wrapping the provided segment.
     *
     * @param heapOffset the base address of the object, relative to the heap it is allocated from.
     * @param memorySegment the area of backing memory to use.
     * @param transactionManager The transaction coordination service to use.
     */
    public TransactionalMemoryOperations(long heapOffset, MemorySegment memorySegment, TransactionManager transactionManager) {
        super(heapOffset, memorySegment);
        this.transactionManager = transactionManager;
        transactionManager.getCurrent().recordCreateEvent(new CreateEvent(this));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete() {
        delete0();
        transactionManager.getCurrent().recordDeleteEvent(new DeleteEvent(this));
    }

    protected void complete() {
        super.delete();
    }

    protected void delete0() {
        isDeleted = true;
    }

    protected void undelete() {
        isDeleted = false;
    }

    /**
     * Access interception hook, invoked by getters prior to read operations on a part of the memory.
     *
     * An attempt to read a deleted object will result in a IllegalStateException.
     *
     * @param offset the starting offset within the memory.
     * @param length the size of the area being accessed (usually the size of a primitive datatype).
     * @throws IllegalStateException if the object has been deleted from the backing memory.
     */
    @Override
    public void beforeRead(int offset, int length) {
        if (isDeleted) {
            throw new IllegalStateException();
        }
    }

    /**
     * Access interception hook, invoked by setters prior to write operations on a part of the memory.
     *
     * An attempt to write a deleted object will result in a IllegalStateException.
     * An attempt to write a deleted object outside a transaction will result in an IllegalStateException
     *
     * @param offset the starting offset within the memory.
     * @param length the size of the area being accessed (usually the size of a primitive datatype).
     * @throws IllegalStateException if the object has been deleted from the backing memory,
     * or there is no transaction in progress.
     */
    @Override
    public void beforeWrite(int offset, int length) {
        VolatileTransaction transaction = transactionManager.getCurrent();
        if (isDeleted || transaction == null) {
            throw new IllegalStateException();
        }

        BeforeWriteEvent beforeWriteEvent = new BeforeWriteEvent(heapOffset + offset, length, memorySegment.asSlice(offset, length).asByteBuffer());

        transaction.recordBeforeWriteEvent(beforeWriteEvent);
    }
}
