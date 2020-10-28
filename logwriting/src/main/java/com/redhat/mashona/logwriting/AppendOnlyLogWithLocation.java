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
package com.redhat.mashona.logwriting;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

public interface AppendOnlyLogWithLocation extends AppendOnlyLog {

    /**
     * The error value returned from tryPutWithLocation methods on failure.
     */
    static final int ERROR_LOCATION = -1;

    /**
     * This method transfers the entire content of the given source byte array into this log and returns its location,
     * failing with an Exception if insufficient space exists.
     * <p>
     * Note that this requires more than <i>src.length</i> bytes of space in the log.
     * <p>
     * Use {@link #canAccept(int)} to determine if there is sufficient space remaining.
     * <p>
     * After this method returns successfully, the data is guaranteed persisted (i.e. flushed).
     *
     * @param src The source array
     * @return The location of the data within the log
     * @throws BufferOverflowException If there is insufficient space in this log
     * @see #tryPutWithLocation(byte[])
     */
    int putWithLocation(byte[] src);

    /**
     * This method transfers bytes into this log from the given source array and returns its location,
     * or {@link #ERROR_LOCATION} if the operation fails.
     * <p>
     * Note that this requires more than <i>src.length</i> bytes of space in the log.
     * <p>
     * Use {@link #canAccept(int)} to determine if there is sufficient space remaining.
     * <p>
     * After this method returns != {@link #ERROR_LOCATION}, the data is guaranteed persisted (i.e. flushed).
     *
     * @param src The source array
     * @return The location of the data within the log on success, {@link #ERROR_LOCATION} if an error occurs.
     * @see #putWithLocation(byte[])
     */
    int tryPutWithLocation(byte[] src);

    /**
     * This method transfers bytes into this log from the given source array and returns its location,
     * failing with an Exception if insufficient space exists.
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
     * @return The location of the data within the log.
     * @throws BufferOverflowException If there is insufficient space in this log
     */
    int putWithLocation(byte[] src, int offset, int length);

    /**
     * This method transfers bytes into this log from the given source array and returns its location,
     * or {@link #ERROR_LOCATION} if the operation fails.
     * <p>
     * Note that this requires more than <i>length</i> bytes of space in the log.
     * <p>
     * Use {@link #canAccept(int)} to determine if there is sufficient space remaining.
     * <p>
     * After this method returns != {@link #ERROR_LOCATION}, the data is guaranteed persisted (i.e. flushed).
     *
     * @param src    The array from which bytes are to be read
     * @param offset The offset within the array of the first byte to be read
     * @param length The number of bytes to be read from the given array
     * @return The location of the data within the log on success, {@link #ERROR_LOCATION} if an error occurs.
     * @see #put(byte[], int, int)
     */
    int tryPutWithLocation(byte[] src, int offset, int length);

    /**
     * This method transfers the bytes remaining in the given source buffer into this log and returns its location,
     * failing with an Exception if insufficient space exists.
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
     * @return The location of the data within the log.
     * @throws BufferOverflowException If there is insufficient space in this log
     * @see #tryPut(ByteBuffer)
     */
    int putWithLocation(ByteBuffer src);

    /**
     * This method transfers the bytes remaining in the given source buffer into this log and returns its location,,
     * or {@link #ERROR_LOCATION} if the operation fails.
     * <p>
     * Copies <i>n</i>&nbsp;=&nbsp;{@code src.remaining()} bytes from the given buffer
     * into the log, incrementing the position of src by <i>n</i>.
     * <p>
     * Note that this requires more than <i>n</i> bytes of space in the log.
     * <p>
     * This method is preferred in concurrent environments where racing writes make canAccept() unreliable.
     * <p>
     * After this method returns != {@link #ERROR_LOCATION}, the data is guaranteed persisted (i.e. flushed) and the src buffer has been read.
     * <p>
     * After this method returns {@link #ERROR_LOCATION}, the log is unwritten and the src is unread.
     *
     * @param src The source buffer from which bytes are to be read
     * @return The location of the data within the log on success, {@link #ERROR_LOCATION} if an error occurs.
     */
    int tryPutWithLocation(ByteBuffer src);

    /**
     * Read a log entry starting from a specific location.
     *
     * This method allows for unordered access rather than linear iteration.
     * Typically used with arguments (locations) previously obtained via putWithLocation methods.
     *
     * @param location The starting location of the data within the log. Must correspond to the stating location of a valid record.
     * @return A ByteBuffer containg the record present at the specified location.
     */
    ByteBuffer readRecordAt(int location);
}
