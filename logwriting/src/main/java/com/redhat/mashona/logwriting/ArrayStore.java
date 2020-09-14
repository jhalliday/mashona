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
package com.redhat.mashona.logwriting;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * An array-like persistent storage structure with a number of fixed size slots, accessible concurrently.
 * The structure is zero-indexed, as with Java arrays or lists.
 * <p>
 * Implementations are expected to provide safe concurrent access to different slots,
 * but not required to provide guarantees regarding concurrent access to the same slot.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-04
 */
public interface ArrayStore {

    /**
     * Update the given slot with the provided data, overwriting (non-atomically) any existing data.
     * After this method returns successfully, the data is guaranteed persisted (i.e. flushed).
     *
     * @param slotIndex the location.
     * @param data      the content. zero length content is equivalent to clearing the slot.
     */
    default void write(int slotIndex, ByteBuffer data) throws IOException {
        write(slotIndex, data, true);
    }

    /**
     * Update the given slot with the provided data, overwriting (non-atomically) any existing data.
     *
     * @param slotIndex the location.
     * @param data      the content. zero length content is equivalent to clearing the slot.
     * @param force     if the change should be immediately persistent or not.
     */
    void write(int slotIndex, ByteBuffer data, boolean force) throws IOException;

    /**
     * Update the given slot with the provided data, overwriting (non-atomically) any existing data.
     * After this method returns successfully, the data is guaranteed persisted (i.e. flushed).
     *
     * @param slotIndex the location.
     * @param data      the content. zero length content is equivalent to clearing the slot.
     */
    default void write(int slotIndex, byte[] data) throws IOException {
        write(slotIndex, data, true);
    }

    /**
     * Update the given slot with the provided data, overwriting (non-atomically) any existing data.
     *
     * @param slotIndex the location.
     * @param data      the content. zero length content is equivalent to clearing the slot.
     * @param force     if the change should be immediately persistent or not.
     */
    void write(int slotIndex, byte[] data, boolean force) throws IOException;

    /**
     * Read the given slot, returning a copy of its contents.
     *
     * @param slotIndex the location.
     * @return a copy of the content, or null if the slot has not been written or has been cleared.
     */
    ByteBuffer readAsByteBuffer(int slotIndex) throws IOException;

    /**
     * Read the given slot, returning a copy of its contents.
     *
     * @param slotIndex the location.
     * @return a copy of the content, or null if the slot has not been written or has been cleared.
     */
    byte[] readAsByteArray(int slotIndex) throws IOException;

    /**
     * Update the given slot, discarding the contents.
     * After this method returns successfully, the data is guaranteed persisted (i.e. flushed).
     *
     * @param slotIndex location.
     * @param scrub     overwrite the entire slot with zero data, which is slower than simply marking it invalid.
     */
    default void clear(int slotIndex, boolean scrub) throws IOException {
        clear(slotIndex, scrub, true);
    }

    /**
     * Update the given slot, discarding the contents.
     *
     * @param slotIndex location.
     * @param scrub     overwrite the entire slot with zero data, which is slower than simply marking it invalid.
     * @param force     if the change should be immediately persistent or not.
     */
    void clear(int slotIndex, boolean scrub, boolean force) throws IOException;

    /**
     * Close the store, preventing subsequent reads and writes.
     */
    void close() throws IOException;
}
