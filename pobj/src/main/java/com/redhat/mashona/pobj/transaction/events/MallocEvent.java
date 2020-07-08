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
package com.redhat.mashona.pobj.transaction.events;

/**
 * Transaction log entry for recording memory allocation operations.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-07
 */
public class MallocEvent implements TransactionEvent {

    private final long offset;
    private final long size;
    private final boolean forInternalUse;

    /**
     * Creates a record of the allocation of a region of memory from a heap.
     *
     * @param offset         the starting location of the memory, measured from the base of the heap.
     * @param size           the length of the memory region.
     * @param forInternalUse true if the memory is for bookkeeping use by the allocator itself, false for user requests.
     */
    public MallocEvent(long offset, long size, boolean forInternalUse) {
        this.offset = offset;
        this.size = size;
        this.forInternalUse = forInternalUse;
    }

    public long getOffset() {
        return offset;
    }

    public long getSize() {
        return size;
    }

    public boolean isForInternalUse() {
        return forInternalUse;
    }
}
