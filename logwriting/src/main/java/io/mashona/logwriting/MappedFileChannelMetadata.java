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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Abstraction over a mapped File containing out of band metadata for the MappedFileChannel.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2019-06
 */
public class MappedFileChannelMetadata implements Closeable {

    private static final Logger logger = Logger.getLogger(MappedFileChannelMetadata.class);

    // change this if changing the data layout!
    private static final byte[] MAGIC_HEADER = new String("TRBMFCM1").getBytes(StandardCharsets.UTF_8);

    private static final int FILE_SIZE = 256;

    private static Unsafe unsafe;

    static {
        // ugliness required for implCloseChannel, until the JDK's unmapping behavior is fixed.
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private final Lock lock = new ReentrantLock();

    private final FileChannel fileChannel;
    private final ByteBuffer buffer;
    private final PersistenceHandle persistenceHandle;
    private final boolean readShared;

    private int persistenceIndex;


    /**
     * Initialize a new MappedFileChannelMetadata over the given file.
     *
     * @param file The underlying File to use. Must be on DAX aware storage.
     * @throws IOException if the mapping fails.
     */
    public MappedFileChannelMetadata(File file) throws IOException {
        this(file, false);
    }

    /**
     * Initialize a new MappedFileChannelMetadata over the given file.
     *
     * @param file The underlying File to use. Must be on DAX aware storage.
     * @param readShared true if the underlying persistent state may be updated by other processes, false otherwise.
     * @throws IOException if the mapping fails.
     */
    public MappedFileChannelMetadata(File file, boolean readShared) throws IOException {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry with file={0}, readShared={1}", file, readShared);
        }

        fileChannel = (FileChannel) Files
                .newByteChannel(file.toPath(), EnumSet.of(
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE));

        MappedByteBuffer tmpRawBuffer = fileChannel.map(ExtendedMapMode.READ_WRITE_SYNC, 0, FILE_SIZE);

        persistenceHandle = new PersistenceHandle(tmpRawBuffer, 0, FILE_SIZE);

        this.readShared = readShared;

        buffer = tmpRawBuffer;

        byte[] header = new byte[MAGIC_HEADER.length];
        buffer.get(header);
        if (Arrays.equals(header, MAGIC_HEADER)) {
            // pre-existing data in known format.
            // re-read to seek to buffer's end position
            persistenceIndex = buffer.getInt(MAGIC_HEADER.length);
        } else {
            // we don't know what's in the provided buffer, so zero it out for safety
            clear();
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit {0}", this);
        }
    }

    /**
     * Closes the mapping, releasing the resources.
     *
     * @throws IOException if the operation fails.
     */
    @Override
    public void close() throws IOException {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0}", this);
        }

        lock.lock();

        try {
            // https://bugs.openjdk.java.net/browse/JDK-4724038
            unsafe.invokeCleaner(buffer);

            fileChannel.close();

        } finally {
            lock.unlock();
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit");
        }
    }

    /**
     * Returns the current persistent index value from the metadata.
     *
     * @return the persistence index.
     * @throws ClosedChannelException if the instance has previously been closed.
     */
    public int getPersistenceIndex() throws ClosedChannelException {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0}", this);
        }

        int value;
        lock.lock();
        try {
            validateIsOpen();
            refreshPersistenceIndex();
            value = persistenceIndex;
        } finally {
            lock.unlock();
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit returning {0}", value);
        }
        return value;
    }

    /**
     * Persistently record the given range as in use, advancing the persistence index accordingly.
     *
     * @param startIndex The offset from which to begin counting.
     * @param length The number of additional bytes to mark as used.
     * @throws ClosedChannelException if the instance has previously been closed.
     */
    public void persist(int startIndex, int length) throws ClosedChannelException {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0} with startIndex={1}, length={2}", this, startIndex, length);
        }

        lock.lock();

        try {
            validateIsOpen();
            refreshPersistenceIndex();
            persistenceIndex = startIndex + length;
            buffer.putInt(MAGIC_HEADER.length, persistenceIndex);
            persistenceHandle.persist(MAGIC_HEADER.length, 4);
        } finally {
            lock.unlock();
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit");
        }
    }

    /**
     * Returns the read sharing mode.
     *
     * @return true is read sharing is enabled, false otherwise.
     */
    public boolean isReadShared() {
        return readShared;
    }

    /**
     * Reinitializes the instance.
     *
     * @throws ClosedChannelException if the instance has previously been closed.
     */
    public void clear() throws ClosedChannelException {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0}", this);
        }

        lock.lock();

        try {
            validateIsOpen();
            byte[] zeros = new byte[FILE_SIZE];
            buffer.position(0);
            buffer.put(zeros);
            persistenceHandle.persist(0, FILE_SIZE);

            buffer.put(0, MAGIC_HEADER);

            persistenceHandle.persist(0, MAGIC_HEADER.length);
            buffer.position(0);

            persistenceIndex = buffer.getInt(MAGIC_HEADER.length);
        } finally {
            lock.unlock();
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit");
        }
    }

    private void validateIsOpen() throws ClosedChannelException {
        if(!fileChannel.isOpen()) {
            ClosedChannelException closedChannelException = new ClosedChannelException();
            if(logger.isTraceEnabled()) {
                logger.tracev(closedChannelException, "throwing {0}", closedChannelException.toString());
            }
            throw closedChannelException;
        }
    }

    private void refreshPersistenceIndex() {
        if (readShared) {
            persistenceIndex = buffer.getInt(MAGIC_HEADER.length);
        }
    }

}
