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

import jdk.nio.mapmode.ExtendedMapMode;
import sun.misc.Unsafe;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.CRC32C;

/**
 * An array-like persistent storage structure with a fixed number of fixed size slots, accessible concurrently.
 * <p>
 * This implementation provides safe concurrent access to different slots,
 * but concurrent operations on the same slot must be externally synchronized.
 * The thread-safety behaviour in cases where multiple instances are created over the same file is undefined.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-04
 */
public class ArrayStoreImpl implements ArrayStore {

    private static final Logger logger = Logger.getLogger(ArrayStoreImpl.class);

    private static Unsafe unsafe;

    static {
        // ugliness required for close, until the JDK's unmapping behavior is fixed.
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final int BLOCK_SIZE = 256; // pmem hardware block
    private static final int RECORD_METADATA_SIZE = 2 * Integer.BYTES; // one int for size field and one for checksum
    private static final byte[] ZERO_ARRAY = new byte[0];

    private final File file;
    private final int numberOfSlots;
    private final int slotDataCapacity;
    private final int slotSize;

    // this lock guards the open/closed state of the mmap, NOT the data.
    // Therefore a read or write of data needs only a shared READ lock,
    // whilst a close() needs an exclusive WRITE lock.
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final FileChannel fileChannel;
    private final MappedByteBuffer dataBuffer;
    private final PersistenceHandle persistenceHandle;

    /**
     * Establishes an array storage structure of the provided file.
     * <p>
     * Note that the configuration (number and size of slots) is NOT persistent.
     * Creating a new instance over a file previously used with different parameters is likely to result in data corruption.
     *
     * @param file             the backing file to use.
     * @param numberOfSlots    the number of individually accessible storage regions.
     * @param slotDataCapacity the maximum data storage size of each slot.
     */
    public ArrayStoreImpl(File file, int numberOfSlots, int slotDataCapacity) throws IOException {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry with file={0}, numberOfSlots={1}, slotDataCapacity={2}",
                    file, numberOfSlots, slotDataCapacity);
        }

        this.file = file;
        this.numberOfSlots = numberOfSlots;
        this.slotDataCapacity = slotDataCapacity;
        this.slotSize = calculateSlotSize(slotDataCapacity);

        int length = numberOfSlots * slotSize;

        this.fileChannel = (FileChannel) Files
                .newByteChannel(file.toPath(), EnumSet.of(
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE));

        dataBuffer = fileChannel.map(ExtendedMapMode.READ_WRITE_SYNC, 0, length);

        // force MUST be called on the original buffer, NOT a duplicate or slice,
        // so we need to keep a handle on it. However, we don't want to inadvertently
        // rely on or change its state, so we wrap it in a restrictive API.
        persistenceHandle = new PersistenceHandle(dataBuffer, 0, length);

        if(logger.isTraceEnabled()) {
            logger.tracev("exit {0}", this);
        }
    }

    /**
     * Returns the number of slots offered by this store instance.
     * Slot indexes are 0 to numberOfSlots-1 inclusive, as with arrays.
     *
     * @return the number of slots in the store.
     */
    public int getNumberOfSlots() {
        return numberOfSlots;
    }

    /**
     * Returns the maximum capacity of each slot in the store.
     *
     * @return the capacity of a slot, in bytes.
     */
    public int getSlotDataCapacity() {
        return slotDataCapacity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0}", this);
        }

        lock.writeLock().lock();

        try {
            unsafe.invokeCleaner(dataBuffer);
            fileChannel.close();
        } finally {
            lock.writeLock().unlock();
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(int slotIndex, ByteBuffer src, boolean force) throws IOException {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0} with slotIndex={1}, src={2}, force={3}", this, slotIndex, src, force);
        }

        validateIndex(slotIndex);

        lock.readLock().lock();

        try {
            validateIsOpen();

            int dataSize = src.remaining();
            if (dataSize > slotDataCapacity) {
                IOException ioException = new IOException("data of size " + dataSize + " too big for slot of size " + slotDataCapacity);
                if(logger.isTraceEnabled()) {
                    logger.tracev(ioException, "throwing {0}", ioException.toString());
                }
                throw ioException;
            }
            int position = slotIndex * slotSize;

            ByteBuffer srcSlice = src.duplicate().position(src.position()).limit(src.position() + dataSize).duplicate();
            // JDK-14: ByteBuffer srcSlice = src.slice(src.position(), length);
            ByteBuffer dst = dataBuffer.duplicate().position(position).limit(position + slotSize).duplicate();
            // JDK-14: ByteBuffer dst = dataBuffer.slice(position, length);

            CRC32C crc32c = new CRC32C();
            crc32c.update(srcSlice);
            int checksum = (int) crc32c.getValue();
            // the checksum above consumed the content, but the put below needs to re-read it.
            srcSlice.rewind();

            dst.putInt(dataSize);
            dst.putInt(checksum);
            dst.put(srcSlice);

            if (force) {
                persistenceHandle.persist(position, dataSize + RECORD_METADATA_SIZE);
            }
        } finally {
            lock.readLock().unlock();
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(int slotIndex, byte[] data, boolean force) throws IOException {
        write(slotIndex, ByteBuffer.wrap(data), force);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ByteBuffer readAsByteBuffer(int slotIndex) throws IOException {
        byte[] data = readAsByteArray(slotIndex);
        if (data != null) {
            return ByteBuffer.wrap(data);
        } else {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] readAsByteArray(int slotIndex) throws IOException {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0} with slotIndex={1}", this, slotIndex);
        }

        validateIndex(slotIndex);

        byte[] result = null;

        lock.readLock().lock();

        try {
            validateIsOpen();

            int position = slotIndex * slotSize;

            ByteBuffer recordBuffer = dataBuffer.duplicate();
            recordBuffer.position(position);

            int payloadLength = recordBuffer.getInt();
            if (payloadLength == 0) {
                if(logger.isTraceEnabled()) {
                    logger.tracev("exit returning null");
                }
                return null;
            }

            int expectedChecksum = recordBuffer.getInt();
            ByteBuffer payloadBuffer = recordBuffer.slice();
            payloadBuffer.limit(payloadLength);
            recordBuffer.position(recordBuffer.position() + payloadLength);

            CRC32C crc32c = new CRC32C();
            crc32c.reset();
            crc32c.update(payloadBuffer); // this advances the src buffers position to its limit.
            int actualChecksum = (int) crc32c.getValue();
            payloadBuffer.rewind();

            if (actualChecksum == expectedChecksum) {
                result = new byte[payloadLength];
                payloadBuffer.get(result);
            }
        } finally {
            lock.readLock().unlock();
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
    public void clear(int slotIndex, boolean scrub, boolean force) throws IOException {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0} with slotIndex={1}, scrub={2}, force={3}",
                    this, slotIndex, scrub, force);
        }

        lock.readLock().lock();

        try {
            validateIsOpen();

            if (scrub) {
                // this is not very efficient, but will do for now.
                byte[] data = new byte[slotDataCapacity];
                write(slotIndex, data, true);
            }

            write(slotIndex, ZERO_ARRAY, force);

        } finally {
            lock.readLock().unlock();
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit");
        }
    }

    private void validateIndex(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= numberOfSlots) {
            ArrayIndexOutOfBoundsException arrayIndexOutOfBoundsException = new ArrayIndexOutOfBoundsException(slotIndex);
            if(logger.isTraceEnabled()) {
                logger.tracev(arrayIndexOutOfBoundsException, "throwing {0}", arrayIndexOutOfBoundsException.toString());
            }
            throw arrayIndexOutOfBoundsException;
        }
    }

    private void validateIsOpen() throws ClosedChannelException {
        if (!fileChannel.isOpen()) {
            ClosedChannelException closedChannelException = new ClosedChannelException();
            if(logger.isTraceEnabled()) {
                logger.tracev(closedChannelException, "throwing {0}", closedChannelException.toString());
            }
            throw closedChannelException;
        }
    }

    private int calculateSlotSize(int slotDataCapacity) {
        slotDataCapacity += RECORD_METADATA_SIZE;
        int remainder = (slotDataCapacity) % BLOCK_SIZE;
        if (remainder == 0) {
            return slotDataCapacity;
        } else {
            return slotDataCapacity + BLOCK_SIZE - remainder;
        }
    }
}
