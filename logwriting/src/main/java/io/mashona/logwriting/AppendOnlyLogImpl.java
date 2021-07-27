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

import org.jboss.logging.Logger;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.charset.StandardCharsets;

import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.CRC32C;

/**
 * An append-only log structure built over memory-mapped pmem.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2019-04
 */
public class AppendOnlyLogImpl implements AppendOnlyLogWithLocation {

    private static final Logger logger = Logger.getLogger(AppendOnlyLogImpl.class);

    // change this if changing the data layout!
    private static final byte[] MAGIC_HEADER = new String("TRBAOL01").getBytes(StandardCharsets.UTF_8);

    private static final int INT_SIZE = 4;
    private static final int BLOCK_SIZE = 256;

    private static final int CACHE_LINE_SIZE = 64; // safe bet for Intel. the JVM knows the actual runtime value, but doesn't expose it except via unsafe.dataCacheLineFlushSize

    // these offsets are relative to 'buffer'
    private static final int MAGIC_OFFSET = 0;
    private static final int PADDING_SIZE_OFFSET = MAGIC_OFFSET + MAGIC_HEADER.length;
    private static final int CHECKPOINT_OFFSET = PADDING_SIZE_OFFSET + 4;
    private static final int LINEAR_ORDERING_OFFSET = CHECKPOINT_OFFSET + 4;
    private static final int FIRST_RECORD_OFFSET = LINEAR_ORDERING_OFFSET + 4;

    private static final int LOG_HEADER_BYTES = FIRST_RECORD_OFFSET;

    private static final int ENTRY_HEADER_SIZE = 8; // int payload length + int checksum
    private static final int PER_ENTRY_OVERHEAD = ENTRY_HEADER_SIZE;

    private final Lock lock = new ReentrantLock();

    private final PersistenceHandle persistenceHandle;

    private final ByteBuffer buffer;

    private int effectivePaddingSize;
    private final int requestedPaddingSize;

    private boolean effectiveLinearOrdering;
    private final boolean requestedLinearOrdering;

    // The number of times this log has been cleared, used to keep Iterators in sync.
    private int epoch = 0;

    /**
     * Establishes an append-only log structure over a given range of mapped memory.
     *
     * @param byteBuffer   The mapped memory to use.  It MUST NOT be a slice or duplicate.
     * @param offset       The offset within the allocatedMemory, from which to start the log structure. This MUST be cache line aligned and SHOULD be 256-byte block aligned.
     * @param length       The size of the region within the buffer which is available for the log.
     * @param blockPadding true if extra space should be used to increase performance, or false for a slower but more compact record format.
     * @param linearOrdering true is strict serial ordering of writes is required, false for more relaxed ordering guarantees.
     */
    public AppendOnlyLogImpl(MappedByteBuffer byteBuffer, int offset, int length, boolean blockPadding, boolean linearOrdering) {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry with byteBuffer={0}, offset={1}, length={2}, blockPadding={3}",
                    byteBuffer, offset, length, blockPadding);
        }

        lock.lock();
        try {

            if (blockPadding) {
                requestedPaddingSize = BLOCK_SIZE; // Optane internal block alignment, for performance.
            } else {
                requestedPaddingSize = INT_SIZE; // int alignment, the minimum for persistence safety.
            }

            requestedLinearOrdering = linearOrdering;

            // force MUST be called on the original buffer, NOT a duplicate or slice,
            // so we need to keep a handle on it. However, we don't want to inadvertently
            // rely on or change its state, so we wrap it in a restrictive API.
            persistenceHandle = new PersistenceHandle(byteBuffer, offset, length);

            // we slice the origin buffer, so that we have a zero origin to make math easier
            // and our own position/limit/capacity so we can reason about concurrency better
            ByteBuffer tmp = byteBuffer.slice();
            tmp.position(offset);
            tmp.limit(length);
            buffer = tmp.slice();

            byte[] header = new byte[MAGIC_HEADER.length];
            buffer.get(header);
            if (Arrays.equals(header, MAGIC_HEADER)) {
                // pre-existing data in known format.
                // persisted config takes priority, or we'll get inconsistencies
                effectivePaddingSize = buffer.getInt(PADDING_SIZE_OFFSET);
                effectiveLinearOrdering = buffer.getInt(LINEAR_ORDERING_OFFSET) == 1;
                // re-read to seek to buffer's end position
                recoverRecords();
            } else {
                effectivePaddingSize = requestedPaddingSize;
                effectiveLinearOrdering = requestedLinearOrdering;
                // we don't know what's in the provided buffer, so zero it out for safety
                clear();
            }


        } finally {
            lock.unlock();
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit {0}", this);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEffectivelyPadded() {
        return effectivePaddingSize == BLOCK_SIZE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isPaddingRequested() {
        return requestedPaddingSize == BLOCK_SIZE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEffectiveLinearOrdering() {
        return effectiveLinearOrdering;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRequestedLinearOrdering() {
        return requestedLinearOrdering;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void checkpoint() {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0}", this);
        }

        lock.lock();
        try {
            buffer.putInt(CHECKPOINT_OFFSET, buffer.position());
            persistenceHandle.persist(MAGIC_OFFSET, LOG_HEADER_BYTES);
        } finally {
            lock.unlock();
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void put(byte[] src) {
        put(ByteBuffer.wrap(src));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int putWithLocation(byte[] src) {
        return putWithLocation(ByteBuffer.wrap(src));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean tryPut(byte[] src) {
        return tryPut(ByteBuffer.wrap(src));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int tryPutWithLocation(byte[] src) {
        return tryPutWithLocation(ByteBuffer.wrap(src));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void put(byte[] src, int offset, int length) {
        put(ByteBuffer.wrap(src, offset, length));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int putWithLocation(byte[] src, int offset, int length) {
        return putWithLocation(ByteBuffer.wrap(src, offset, length));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean tryPut(byte[] src, int offset, int length) {
        return tryPut(ByteBuffer.wrap(src, offset, length));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int tryPutWithLocation(byte[] src, int offset, int length) {
        return tryPutWithLocation(ByteBuffer.wrap(src, offset, length));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void put(ByteBuffer src) {
        putWithLocation(src);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int putWithLocation(ByteBuffer src) {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0} with src={1}", this, src);
        }

        int location = tryPutWithLocation(src);
        if (location == ERROR_LOCATION) {
            BufferOverflowException bufferOverflowException = new BufferOverflowException();
            if(logger.isTraceEnabled()) {
                logger.tracev(bufferOverflowException, "throwing {0}", bufferOverflowException.toString());
            }
            throw bufferOverflowException;
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit returning {0}", location);
        }
        return location;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean tryPut(ByteBuffer src) {
        return tryPutWithLocation(src) != ERROR_LOCATION;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int tryPutWithLocation(ByteBuffer src) {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0} with src={1}", this, src);
        }

        ByteBuffer srcSlice = src.slice();
        int payloadLength = srcSlice.remaining();

        if (payloadLength == 0) {
            if(logger.isTraceEnabled()) {
                logger.tracev("exit returning {0}", ERROR_LOCATION);
            }
            return ERROR_LOCATION;
        }

        // CSC32C impl should be faster than CSC32 as it is coded to use hardware support (SSE4.2) when available,
        // whilst the CSC32 impl has not been retrofitted.
        // CSC32C is Java9+ only, which is why much code still uses the older CRC32, but we need Java13+ for pmem anyhow.
        // note that if still using CSC32, there is a faster impl in kafka/hadoop than the one in jdk's zip package.
        CRC32C crc32c = new CRC32C();
        crc32c.update(srcSlice);
        int checksum = (int) crc32c.getValue();
        // the checksum above consumed the content, but the put below needs to re-read it.
        srcSlice.rewind();

        int recordStartPosition = 0;
        int recordLength = 0;
        ByteBuffer payloadBuffer = null;
        ByteBuffer deferredSlice = null;

        int recordBytesFittingInFirstCacheLine;
        int deferredRecordBytesLength;

        lock.lock();
        try {

            if (!canAcceptInternal(payloadLength)) {
                if(logger.isTraceEnabled()) {
                    logger.tracev("exit returning {0}", ERROR_LOCATION);
                }
                return ERROR_LOCATION;
            }

            recordStartPosition = buffer.position();

            buffer.putInt(payloadLength);
            buffer.putInt(checksum);
            int payloadStartPosition = buffer.position();

            // https://bugs.openjdk.java.net/browse/JDK-8219014
            payloadBuffer = buffer.slice();
            payloadBuffer.limit(payloadLength);
            payloadBuffer = payloadBuffer.slice();
            // JDK-14: payloadBuffer = buffer.slice(buffer.position(), payloadLength);


            // skip the space for the payload, we'll fill it in later
            buffer.position(buffer.position() + payloadLength);
            padRecord();
            recordLength = buffer.position() - recordStartPosition;

            // At this point we have written, but not yet explicitly persisted, the record header and footer
            // but not the payload in between. The overall record may span multiple cache lines.
            // If we're in strict ordering mode, we have to write+persist the entire entry before releasing the lock.
            // However, in relaxed mode we need to persist only the first cache line, since that contains the length header
            // necessary for linking to subsequent records. But... we don't want to persist the same line multiple times
            // since that's slow. We therefore need to fill in the rest of the line containing the header with payload
            // before persisting it, deferring the remainder of the payload write+persist to outside the lock.

            int nonPayloadBytesInFirstCacheLine = payloadStartPosition % CACHE_LINE_SIZE;
            int payloadCapacityInFistCacheLine = nonPayloadBytesInFirstCacheLine == 0 ? 0 : CACHE_LINE_SIZE - nonPayloadBytesInFirstCacheLine;

            recordBytesFittingInFirstCacheLine = ENTRY_HEADER_SIZE + payloadCapacityInFistCacheLine;
            deferredRecordBytesLength = recordLength - recordBytesFittingInFirstCacheLine;

            if (!effectiveLinearOrdering && payloadCapacityInFistCacheLine != 0 && payloadCapacityInFistCacheLine < payloadLength) {
                ByteBuffer immediateSlice = srcSlice.duplicate().position(0).limit(payloadCapacityInFistCacheLine).duplicate();
                // JDK-14: ByteBuffer immediateSlice = srcSlice.slice(0, payloadCapacityInFistCacheLine);
                deferredSlice = srcSlice.duplicate().position(payloadCapacityInFistCacheLine).limit(payloadLength).slice();
                // JDK-14: deferredSlice = srcSlice.slice(payloadCapacityInFistCacheLine, payloadLength - payloadCapacityInFistCacheLine);
                payloadBuffer.put(immediateSlice);
                persistenceHandle.persist(recordStartPosition, (ENTRY_HEADER_SIZE + payloadCapacityInFistCacheLine));
            } else {
                payloadBuffer.put(srcSlice);
                persistenceHandle.persist(recordStartPosition, recordLength);
            }

            // we've been operating on a slice, but need to reflect the read in the original
            src.position(src.position() + payloadLength);

        } finally {
            lock.unlock();
        }

        if (deferredSlice != null) {
            payloadBuffer.put(deferredSlice);
            persistenceHandle.persist(recordStartPosition + recordBytesFittingInFirstCacheLine, deferredRecordBytesLength);
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit returning {0}", recordStartPosition);
        }
        return recordStartPosition;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0}", this);
        }

        lock.lock();
        try {

            // first overwrite the header to invalidate the file,
            // in case we crash in inconsistent state whilst zeroing the rest
            buffer.clear();

            buffer.position(MAGIC_OFFSET);
            buffer.put(MAGIC_HEADER);
            // JDK-14: buffer.put(MAGIC_OFFSET, MAGIC_HEADER);
            buffer.position(PADDING_SIZE_OFFSET);
            buffer.putInt(effectivePaddingSize);
            // JDK-14: buffer.putInt(PADDING_SIZE_OFFSET, effectivePaddingSize);
            buffer.position(LINEAR_ORDERING_OFFSET);
            buffer.putInt(effectiveLinearOrdering ? 1 : 0);
            // JDK-14: buffer.putInt(LINEAR_ORDERING_OFFSET, effectiveLinearOrdering ? 1 : 0);

            persistenceHandle.persist(MAGIC_OFFSET, LOG_HEADER_BYTES);

            // sun.misc.Unsafe.setMemory may be faster, but would require linking against jdk.unsupported module
            buffer.clear();
            byte[] zeros = new byte[1024 * 1024];
            while (buffer.remaining() > 0) {
                buffer.put(zeros, 0, buffer.remaining() > zeros.length ? zeros.length : buffer.remaining());
            }

            // we could force every N lines whilst looping above, but assume the hardware cache management
            // knows what it's doing and will elide flushes if they are for lines that have already been
            // evicted by cache pressure.
            persistenceHandle.persist(MAGIC_OFFSET, buffer.capacity());

            effectivePaddingSize = requestedPaddingSize;
            effectiveLinearOrdering = requestedLinearOrdering;

            buffer.clear();

            buffer.position(MAGIC_OFFSET);
            buffer.put(MAGIC_HEADER);
            // JDK-14: buffer.put(MAGIC_OFFSET, MAGIC_HEADER);
            buffer.position(PADDING_SIZE_OFFSET);
            buffer.putInt(effectivePaddingSize);
            // JDK-14: buffer.putInt(PADDING_SIZE_OFFSET, effectivePaddingSize);
            buffer.position(LINEAR_ORDERING_OFFSET);
            buffer.putInt(effectiveLinearOrdering ? 1 : 0);
            // JDK-14: buffer.putInt(LINEAR_ORDERING_OFFSET, effectiveLinearOrdering ? 1 : 0);

            persistenceHandle.persist(MAGIC_OFFSET, LOG_HEADER_BYTES);

            buffer.position(FIRST_RECORD_OFFSET);
            epoch++;

        } finally {
            lock.unlock();
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int remaining() {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0}", this);
        }

        int result;

        lock.lock();
        try {
            result = buffer.remaining();
        } finally {
            lock.unlock();
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit returning {0}", result);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canAccept(int length) {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0} with length={1}", this, length);
        }

        boolean result;

        lock.lock();
        try {
            result = canAcceptInternal(length);
        } finally {
            lock.unlock();
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit returning {0}", result);
        }
        return result;
    }

    private boolean canAcceptInternal(int length) {
        boolean result;

        int recordLength = length + PER_ENTRY_OVERHEAD;
        int realignment = (length % effectivePaddingSize);
        if (realignment != 0) {
            recordLength += (effectivePaddingSize - realignment); // pad to int alignment
        }

        result = buffer.remaining() - recordLength >= 0;
        return result;
    }

    private void padRecord() {
        int x = buffer.position() % effectivePaddingSize;
        if (x != 0) {
            buffer.position(buffer.position() + (effectivePaddingSize - x));
        }
    }

    /**
     * Scan the log, optionally from a known-good point, and set the position to a point just after the
     * last valid record.
     */
    private void recoverRecords() {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0}", this);
        }

        int checkpoint = buffer.getInt(CHECKPOINT_OFFSET);

        Itr iter = new Itr(checkpoint != 0 ? checkpoint : FIRST_RECORD_OFFSET, false);

        while (iter.hasNext()) {
            iter.next();
        }

        buffer.position(iter.iterBuffer.position());

        if(logger.isTraceEnabled()) {
            logger.tracev("exit");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteBuffer readRecordAt(int location) {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0} with location={1}", this, location);
        }

        CRC32C crc32c = new CRC32C();

        ByteBuffer recordBuffer = buffer.duplicate();
        recordBuffer.position(location);

        if (recordBuffer.remaining() < 4) {
            IllegalArgumentException illegalArgumentException = new IllegalArgumentException("invalid record location "+location);
            if(logger.isTraceEnabled()) {
                logger.tracev(illegalArgumentException, "throwing {0}", illegalArgumentException.toString());
            }
            throw illegalArgumentException;
        }

        int length = recordBuffer.getInt();

        int expectedChecksum = recordBuffer.getInt();
        ByteBuffer dataBuffer = recordBuffer.slice();
        dataBuffer.limit(length);
        recordBuffer.position(recordBuffer.position() + length);

        crc32c.reset();
        crc32c.update(dataBuffer); // this advances the src buffers position to its limit.
        int actualChecksum = (int) crc32c.getValue();
        dataBuffer.rewind();

        if(actualChecksum != expectedChecksum) {
            IllegalStateException illegalStateException = new IllegalStateException("invalid checksum");
            if(logger.isTraceEnabled()) {
                logger.tracev(illegalStateException, "throwing {0}", illegalStateException.toString());
            }
            throw illegalStateException;
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit returning {0}", dataBuffer);
        }
        return dataBuffer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<ByteBuffer> iterator() {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0}", this);
        }

        Iterator<ByteBuffer> result = new Itr(FIRST_RECORD_OFFSET, false);

        if(logger.isTraceEnabled()) {
            logger.tracev("exit returning {0}", result);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<ByteBuffer> copyingIterator() {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0}", this);
        }

        Iterator<ByteBuffer> result = new Itr(FIRST_RECORD_OFFSET, true);

        if(logger.isTraceEnabled()) {
            logger.tracev("exit returning {0}", result);
        }
        return result;
    }

    private class Itr implements Iterator<ByteBuffer> {

        private final ByteBuffer iterBuffer;
        private final int expectedEpoch;

        private final CRC32C crc32c = new CRC32C();

        private ByteBuffer lookahead;
        private int lookaheadPos = 0;

        private final boolean returnCopies;

        private Itr(int offset, boolean returnCopies) {
            if(logger.isTraceEnabled()) {
                logger.tracev("entry with offset={0}, returnCopies={1}", offset, returnCopies);
            }

            lock.lock();
            try {
                this.iterBuffer = buffer.duplicate();
                this.iterBuffer.position(offset);
                this.expectedEpoch = epoch;
                this.returnCopies = returnCopies;
            } finally {
                lock.unlock();
            }

            if(logger.isTraceEnabled()) {
                logger.tracev("exit {0}", this);
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasNext() {
            if(logger.isTraceEnabled()) {
                logger.tracev("entry for {0}", this);
            }

            boolean result = false;

            lock.lock();
            try {

                checkForReset();

                // the only way to know for sure if there is another entry, is to attempt to read it
                // we cache the result, so that a subsequent next() call doesn't need to repeat the work
                // we don't cache failure, so repeatedly calling hasNext after it returns false is expensive,
                // but should also be rare

                if (lookahead == null) {
                    lookahead();
                }

                result = lookahead != null;

            } finally {
                lock.unlock();
            }

            if(logger.isTraceEnabled()) {
                logger.tracev("exit returning {0}", result);
            }
            return result;
        }

        /**
         * Returns the next element in the iteration.
         * <p>
         * Note that by default the ByteBuffer returned is a read-only view onto the log, for performance reasons.
         * In such case, if the log is cleared, the content of the buffer is undefined.
         * Iterators created with log.copyingIterator() address this by returning a copy instead.
         *
         * @return The next element in the iteration
         * @throws NoSuchElementException If the iteration has no more elements
         */
        @Override
        public ByteBuffer next() {
            if(logger.isTraceEnabled()) {
                logger.tracev("entry for {0}", this);
            }

            ByteBuffer result = null;

            lock.lock();
            try {

                checkForReset();

                if (!hasNext()) {
                    NoSuchElementException noSuchElementException = new NoSuchElementException();
                    if(logger.isTraceEnabled()) {
                        logger.tracev(noSuchElementException, "throwing {0}", noSuchElementException.toString());
                    }
                    throw noSuchElementException;
                }

                // hasNext did the heavy lifting, but is idempotent, so we still need to update the iterator state
                result = lookahead;
                iterBuffer.position(lookaheadPos);
                lookahead = null;
                lookaheadPos = 0;

                if (returnCopies) {
                    ByteBuffer view = result;
                    result = ByteBuffer.allocate(view.remaining());
                    result.put(view);
                    result.rewind();
                }


            } finally {
                lock.unlock();
            }

            if(logger.isTraceEnabled()) {
                logger.tracev("exit returning {0}", result);
            }
            return result;
        }

        /**
         * Attempt to read and cache the next log entry.
         */
        private void lookahead() {
            if(logger.isTraceEnabled()) {
                logger.tracev("entry for {0}", this);
            }

            int originalPosition = iterBuffer.position();
            ByteBuffer byteBuffer = null;

            try {

                do {

                    if (iterBuffer.remaining() < 4) {
                        if(logger.isTraceEnabled()) {
                            logger.tracev("exit");
                        }
                        return;
                    }

                    int length = iterBuffer.getInt();
                    if (length == 0) {
                        if(logger.isTraceEnabled()) {
                            logger.tracev("exit");
                        }
                        return;
                    }
                    int expectedChecksum = iterBuffer.getInt();
                    byteBuffer = iterBuffer.slice();
                    byteBuffer.limit(length);
                    iterBuffer.position(iterBuffer.position() + length);

                    crc32c.reset();
                    crc32c.update(byteBuffer); // this advances the src buffers position to its limit.
                    int actualChecksum = (int) crc32c.getValue();
                    byteBuffer.rewind();

                    int realignment = (iterBuffer.position() % effectivePaddingSize);
                    if (realignment != 0) {
                        iterBuffer.position(iterBuffer.position() + (effectivePaddingSize - realignment));
                    }

                    if(actualChecksum == expectedChecksum) {
                        break; // found a valid entry, so we're done
                    }

                    if (isEffectiveLinearOrdering()) {
                        if(logger.isTraceEnabled()) {
                            logger.tracev("exit");
                        }
                        return; // entry is invalid, but we're not configured to skip bad ones
                    }

                    // keep looking, there may be good entries after the invalid one(s)...
                } while(iterBuffer.hasRemaining());


                lookahead = byteBuffer.asReadOnlyBuffer();
                lookaheadPos = iterBuffer.position();

            } finally {
                iterBuffer.position(originalPosition);
            }

            if(logger.isTraceEnabled()) {
                logger.tracev("exit");
            }
        }

        /**
         * Throw an Exception if the log has been cleared since the iterator was created
         */
        private void checkForReset() {
            if (epoch != expectedEpoch) {
                ConcurrentModificationException concurrentModificationException = new ConcurrentModificationException("Log cleared after iterator creation");
                if(logger.isTraceEnabled()) {
                    logger.tracev(concurrentModificationException, "throwing {0}",
                            concurrentModificationException.toString());
                }
                throw concurrentModificationException;
            }
        }

    }
}
