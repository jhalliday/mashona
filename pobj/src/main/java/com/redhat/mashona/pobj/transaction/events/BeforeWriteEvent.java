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

import java.nio.ByteBuffer;

/**
 * Transaction log entry for recording pre-modification state of an area of memory.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-07
 */
public class BeforeWriteEvent implements TransactionEvent {

    private final long offset;
    private final long size;
    private final ByteBuffer byteBuffer;

    /**
     * Create a transaction log entry capturing the pre-modification value of a region of memory.
     *
     * @param offset     the starting location of the memory, measured from the base of the heap.
     * @param size       the length of the memory region.
     * @param byteBuffer A buffer holding the memory value. This buffer will NOT be copied,
     *                   so must already be backed by memory independent of that about to be modified.
     */
    public BeforeWriteEvent(long offset, long size, ByteBuffer byteBuffer) {
        this.offset = offset;
        this.size = size;
        this.byteBuffer = byteBuffer;
    }

    public long getOffset() {
        return offset;
    }

    public long getSize() {
        return size;
    }

    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }
}
