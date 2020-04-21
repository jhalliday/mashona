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

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
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

    private static final XLogger logger = XLoggerFactory.getXLogger(ArrayStoreImpl.class);

    private static final int BLOCK_SIZE = 256; // pmem hardware block
    private static final int RECORD_METADATA_SIZE = 2 * Integer.BYTES; // one int for size field and one for checksum
    private static final byte[] ZERO_ARRAY = new byte[0];

    private final File file;
    private final int numberOfSlots;
    private final int slotDataCapacity;
    private final int slotSize;

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
        logger.entry(file, numberOfSlots, slotDataCapacity);

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

        dataBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, length);

        // force MUST be called on the original buffer, NOT a duplicate or slice,
        // so we need to keep a handle on it. However, we don't want to inadvertently
        // rely on or change its state, so we wrap it in a restrictive API.
        persistenceHandle = new PersistenceHandle(dataBuffer, 0, length);

        logger.exit();
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
    public void write(int slotIndex, ByteBuffer src, boolean force) throws IOException {
        logger.entry(this, slotIndex, src, force);

        validateIndex(slotIndex);

        int dataSize = src.remaining();
        if (dataSize > slotDataCapacity) {
            IOException e = new IOException("data of size " + dataSize + " too big for slot of size " + slotDataCapacity);
            logger.throwing(e);
            throw e;
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

        logger.exit();
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
        validateIndex(slotIndex);

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
        logger.entry(slotIndex);

        validateIndex(slotIndex);

        int position = slotIndex * slotSize;

        ByteBuffer recordBuffer = dataBuffer.duplicate();
        recordBuffer.position(position);

        int payloadLength = recordBuffer.getInt();
        if (payloadLength == 0) {
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

        byte[] result = null;
        if (actualChecksum == expectedChecksum) {
            result = new byte[payloadLength];
            payloadBuffer.get(result);
        }

        logger.exit(result);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear(int slotIndex, boolean scrub, boolean force) throws IOException {
        logger.entry(this, slotIndex, scrub, force);

        if (scrub) {
            // this is not very efficient, but will do for now.
            byte[] data = new byte[slotDataCapacity];
            write(0, data, true);
        }

        write(slotIndex, ZERO_ARRAY, force);

        logger.exit();
    }

    private void validateIndex(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= numberOfSlots) {
            ArrayIndexOutOfBoundsException e = new ArrayIndexOutOfBoundsException(slotIndex);
            logger.throwing(e);
            throw e;
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
