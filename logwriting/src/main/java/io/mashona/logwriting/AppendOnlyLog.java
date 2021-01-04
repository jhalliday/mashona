/*
 * Copyright Red Hat
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
package io.mashona.logwriting;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Iterator;

public interface AppendOnlyLog extends Iterable<ByteBuffer> {

    /**
     * Reports the padding mode currently in use by this log.
     *
     * @return true if padding is enabled, false otherwise.
     */
    boolean isEffectivelyPadded();

    /**
     * Reports the padding mode requested, which may or may not be currently active.
     * If the log is cleared, this mode will become effective.
     *
     * @return true if padding was requested, false otherwise.
     */
    boolean isPaddingRequested();

    /**
     * Reports the ordering mode currently in use by this log.
     *
     * @return true if strict ordering is enabled, false otherwise.
     */
    boolean isEffectiveLinearOrdering();

    /**
     * Reports the ordering mode requested, which may or may not be currently active.
     * If the log is cleared, this mode will become effective.
     *
     * @return true if strict ordering was requested, false otherwise.
     */
    boolean isRequestedLinearOrdering();

    /**
     * Persist the current position, allowing faster reopening of the log.
     * <p>
     * This may be called to signal a clean shutdown, though it is
     * neither necessary nor desirable to call it to ensure the persistence of writes.
     * <p>
     * Log entries written after a checkpoint will still be persistent, but log reopening time will be
     * proportional to the amount of data written after a checkpoint.
     */
    void checkpoint();

    /**
     * This method transfers the entire content of the given source byte array into this log, failing with an Exception if insufficient space exists.
     * <p>
     * Note that this requires more than <i>src.length</i> bytes of space in the log.
     * <p>
     * Use {@link #canAccept(int)} to determine if there is sufficient space remaining.
     * <p>
     * After this method returns successfully, the data is guaranteed persisted (i.e. flushed).
     *
     * @param src The source array
     * @throws BufferOverflowException If there is insufficient space in this log
     * @see #tryPut(byte[])
     */
    void put(byte[] src);

    /**
     * This method transfers bytes into this log from the given source array, returning false if the operation fails due to lack of space.
     * <p>
     * Note that this requires more than <i>src.length</i> bytes of space in the log.
     * <p>
     * Use {@link #canAccept(int)} to determine if there is sufficient space remaining.
     * <p>
     * After this method returns true, the data is guaranteed persisted (i.e. flushed).
     *
     * @param src The source array
     * @return true on successful persistence, false if insufficient space remains.
     * @see #put(byte[])
     */
    boolean tryPut(byte[] src);

    /**
     * This method transfers bytes into this log from the given source array, failing with an Exception if insufficient space exists.
     * <p>
     * Note that this requires more than <i>length</i> bytes of space in the log.
     * <p>
     * Use {@link #canAccept(int)} to determine if there is sufficient space remaining.
     * <p>
     * After this method returns successfully, the data is guaranteed persisted (i.e. flushed).
     *
     * @param src    The array from which bytes are to be read
     * @param offset The offset within the array of the first byte to be read
     * @param length The number of bytes to be read from the given array
     * @throws BufferOverflowException If there is insufficient space in this log
     * @see #tryPut(byte[], int, int)
     */
    void put(byte[] src, int offset, int length);

    /**
     * This method transfers bytes into this log from the given source array, returning false if the operation fails due to lack of space.
     * <p>
     * Note that this requires more than <i>length</i> bytes of space in the log.
     * <p>
     * Use {@link #canAccept(int)} to determine if there is sufficient space remaining.
     * <p>
     * After this method returns successfully, the data is guaranteed persisted (i.e. flushed).
     *
     * @param src    The array from which bytes are to be read
     * @param offset The offset within the array of the first byte to be read
     * @param length The number of bytes to be read from the given array
     * @return true on successful persistence, false if insufficient space remains.
     * @see #put(byte[], int, int)
     */
    boolean tryPut(byte[] src, int offset, int length);

    /**
     * This method transfers the bytes remaining in the given source buffer into this log, failing with an Exception if insufficient space exists.
     * <p>
     * Copies <i>n</i>&nbsp;=&nbsp;{@code src.remaining()} bytes from the given buffer
     * into the log, incrementing the position of src by <i>n</i>.
     * <p>
     * Note that this requires more than <i>n</i> bytes of space in the log.
     * <p>
     * Use {@link #canAccept(int)} to determine if there is sufficient space remaining.
     * <p>
     * After this method returns successfully, the data is guaranteed persisted (i.e. flushed).
     *
     * @param src The source buffer from which bytes are to be read
     * @throws BufferOverflowException If there is insufficient space in this log
     * @see #tryPut(ByteBuffer)
     */
    void put(ByteBuffer src);

    /**
     * This method transfers the bytes remaining in the given source buffer into this log, returning false if the operation fails due to lack of space.
     * <p>
     * Copies <i>n</i>&nbsp;=&nbsp;{@code src.remaining()} bytes from the given buffer
     * into the log, incrementing the position of src by <i>n</i>.
     * <p>
     * Note that this requires more than <i>n</i> bytes of space in the log.
     * <p>
     * This method is preferred in concurrent environments where racing writes make {@link #canAccept(int)} unreliable.
     * <p>
     * After this method returns true, the data is guaranteed persisted (i.e. flushed) and the src buffer has been read.
     * <p>
     * After this method returns false, the log is unwritten and the src is unread.
     *
     * @param src The source buffer from which bytes are to be read
     * @return true after a successful write, false if insufficient space remains to accommodate the src.
     * @see #put(ByteBuffer)
     */
    boolean tryPut(ByteBuffer src);

    /**
     * Clears this log.
     * <p>
     * This operation overwrites the entire log capacity, so may be slow on large logs.
     */
    void clear();

    /**
     * Returns the number of unused bytes available in the log.
     * <p>
     * Note that each write incurs a variable per-entry overhead, so this is an estimate.
     * <p>
     * Prefer {@link #canAccept(int)} to accurately determine if a write of known size will fit.
     *
     * @return The usable space remaining in the buffer
     */
    int remaining();

    /**
     * Returns true if the log can currently accommodate a write of the given size, false otherwise.
     *
     * @param length The length of the proposed entry
     * @return true if sufficient space remains, false otherwise.
     */
    boolean canAccept(int length);

    /**
     * Returns an iterator over the entries in this log, using non-copying views onto the log data.
     * <p>
     * If the log is cleared during the lifetime of the iterator, methods may throw ConcurrentModificationException.
     * <p>
     * If the log is appended during the lifetime of the iterator, iteration will encompass at least as many entries
     * as existed at the time the iterator was created.
     *
     * @return An {@code Iterator} over the entries in this log
     */
    Iterator<ByteBuffer> iterator();

    /**
     * Returns an iterator over the entries in this log, using data copying for thread safety.
     * <p>
     * If the log is cleared during the lifetime of the iterator, methods may throw ConcurrentModificationException.
     * <p>
     * If the log is appended during the lifetime of the iterator, iteration will encompass at least as many entries
     * as existed at the time the iterator was created.
     *
     * @return An {@code Iterator} over the entries in this log
     */
    Iterator<ByteBuffer> copyingIterator();
}
