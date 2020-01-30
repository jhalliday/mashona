/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
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
package com.redhat.mashona;

import jdk.nio.mapmode.ExtendedMapMode;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import sun.misc.Unsafe;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An append-only log structure built over memory-mapped pmem, pretending to be a FileChannel for easy integration.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2019-04
 */
public class MappedFileChannel extends FileChannel {

    private static final XLogger logger = XLoggerFactory.getXLogger(MappedFileChannel.class);

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

    /**
     * Returns the metadata file associated with the given file.
     *
     * @param file the log file.
     * @return the metadata file.
     * @throws IOException if the given file is invalid.
     */
    public static File getMetadataFile(File file) throws IOException {
        return new File(file.getCanonicalPath()+".pmem");
    }

    private final PersistenceHandle persistenceHandle;

    private final Lock lock = new ReentrantLock();

    private final File file;
    private final FileChannel fileChannel;
    private final ByteBuffer rawBuffer;
    private final ByteBuffer dataBuffer;

    private final MappedFileChannelMetadata metadata;

    /**
     * Initializes a new MappedFileChannel over the provided FileChannel, with a fixed length.
     * The data capacity will be less than the given length, due to metadata overhead.
     *
     * @param file The file over which to map.
     * @param length The required raw capacity, including metadata space.
     *
     * @throws IOException if the mapping cannot be created, such as when the File is on a filesystem that does not support DAX.
     */
    public MappedFileChannel(File file, int length) throws IOException {
        logger.entry(file, length);

        this.file = file;

        this.fileChannel = (FileChannel) Files
                .newByteChannel(file.toPath(), EnumSet.of(
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE));

        MappedByteBuffer tmpRawBuffer = fileChannel.map(ExtendedMapMode.READ_WRITE_SYNC, 0, length);
        this.rawBuffer = tmpRawBuffer;

        // force MUST be called on the original buffer, NOT a duplicate or slice,
        // so we need to keep a handle on it. However, we don't want to inadvertently
        // rely on or change its state, so we wrap it in a restrictive API.
        persistenceHandle = new PersistenceHandle(tmpRawBuffer, 0, length);

        // we slice the origin buffer, so that we can rely on limit to stop us overwriting the trailing metadata area
        ByteBuffer tmp = tmpRawBuffer.slice();
        tmp.position(0);
        tmp.limit(length);
        dataBuffer = tmp.slice();

        metadata = new MappedFileChannelMetadata( getMetadataFile(file) );
        clearDataFromOffset( metadata.getPersistenceIndex() ); // TODO make less dangerous. in general, deal with concurrent instances
        dataBuffer.position(0);

        logger.exit();
    }

    /**
     * Delete the metadata file that is associated with this channel.
     *
     * @throws IOException if the channel is still open.
     */
    public void deleteMetadata() throws IOException {
        logger.entry();

        if(fileChannel.isOpen()) {
            IOException ioException = new IOException("Unable to delete metadata for an open channel");
            logger.throwing(ioException);
            throw ioException;
        }

        File metadata = getMetadataFile(file);
        if(metadata.exists()) {
            metadata.delete();
        }

        logger.exit();
    }

    /**
     * Reads a sequence of bytes from this channel into the given buffer.
     *
     * <p> Bytes are read starting at this channel's current file position, and
     * then the file position is updated with the number of bytes actually
     * read.  Otherwise this method behaves exactly as specified in the {@link
     * ReadableByteChannel} interface. </p>
     *
     * @param dst The buffer into which bytes are to be transferred.
     *
     * @return The number of bytes read, possibly zero.
     */
    @Override
    public int read(ByteBuffer dst) throws IOException {
        logger.entry(dst);

        lock.lock();
        int result = 0;

        try {
            validateIsOpen();

            int position = dataBuffer.position();
            int readLength = read(dst, position);

            if (readLength > 0) {
                dataBuffer.position(position + readLength);
            }

            result = readLength;
        } finally {
            lock.unlock();
        }

        logger.exit(result);
        return result;
    }

    /**
     * Reads a sequence of bytes from this channel into the given buffer,
     * starting at the given file position.
     *
     * <p> This method works in the same manner as the {@link
     * #read(ByteBuffer)} method, except that bytes are read starting at the
     * given file position rather than at the channel's current position.  This
     * method does not modify this channel's position.  If the given position
     * is greater than the file's current size then no bytes are read.  </p>
     *
     * @param dst The buffer into which bytes are to be transferred.
     *
     * @param position The file position at which the transfer is to begin.
     *
     * @return  The number of bytes read, possibly zero, or {@code -1} if the
     *          given position is greater than or equal to the file's current
     *          size
     */
    @Override
    public int read(ByteBuffer dst, long position) throws IOException {
        logger.entry(dst, position);

        lock.lock();
        int result = 0;

        try {
            validateIsOpen();
            validatePosition(position);

            int length = metadata.getPersistenceIndex()-(int)position;
            if(length <= 0) {
                length = -1;
            }
            length = Math.min(length, dst.remaining());

            if(length > 0) {
                ByteBuffer srcSlice = dataBuffer.duplicate().position((int) position).limit(((int) position) + length).duplicate();
                int begin = srcSlice.position();
                // JDK-14: ByteBuffer srcSlice = dataBuffer.slice((int) position, length);
                dst.put(srcSlice);
                result = srcSlice.position() - begin;
                // JDK-14: result = srcSlice.position();
            } else {
                result = length;
            }

        } finally {
            lock.unlock();
        }

        logger.exit(result);
        return result;
    }

    /**
     * Writes a sequence of bytes to this channel from the given buffer.
     * <p>
     * After this method returns successfully, the data is guaranteed persisted (i.e. flushed).
     * This channel's position will be advanced by the returned number of bytes.
     *
     * @param src The buffer from which bytes are to be transferred.
     *            Its position will be advanced by the returned number of bytes.
     *
     * @return The number of bytes written, possibly zero.
     */
    @Override
    public int write(ByteBuffer src) throws IOException {
        logger.entry(src);

        lock.lock();
        int result = 0;

        try {
            validateIsOpen();

            result = writeInternal(src, dataBuffer.position());

            dataBuffer.position(dataBuffer.position()+result);

        } finally {
            lock.unlock();
        }

        logger.exit(result);
        return result;
    }

    /**
     * Writes a sequence of bytes to this channel from the given buffer,
     * starting at the given file position.
     * <p>
     * After this method returns successfully, the data is guaranteed persisted (i.e. flushed).
     *
     * <p> This method works in the same manner as the {@link
     * #write(ByteBuffer)} method, except that bytes are written starting at
     * the given file position rather than at the channel's current position.
     * This method does not modify this channel's position.
     *
     * @param src The buffer from which bytes are to be transferred.
     *
     * @param position The file position at which the transfer is to begin.
     *
     * @return The number of bytes written, possibly zero.
     */
    @Override
    public int write(ByteBuffer src, long position) throws IOException {
        logger.entry(src, position);

        lock.lock();
        int result = 0;

        try {
            validateIsOpen();
            validatePosition(position);

            result = writeInternal(src, (int)position);

        } finally {
            lock.unlock();
        }

        logger.exit(result);
        return result;
    }

    private int writeInternal(ByteBuffer src, int position) throws ClosedChannelException {

        if(position < metadata.getPersistenceIndex()) {
            throw new IllegalArgumentException();
        }

        int length = Math.min(dataBuffer.remaining(), src.remaining());

        ByteBuffer srcSlice = src.duplicate().position(src.position()).limit(src.position() + length).duplicate();
        // JDK-14: ByteBuffer srcSlice = src.slice(src.position(), length);
        ByteBuffer dst = dataBuffer.duplicate().position(position).limit(position + length).duplicate();
        // JDK-14: ByteBuffer dst = dataBuffer.slice(position, length);

        dst.put(srcSlice);
        src.position(src.position()+length);

        int startIndex = position;

        persist(startIndex, length);

        return length;
    }

    private void persist(int startIndex, int length) throws ClosedChannelException {
        persistenceHandle.persist(startIndex, length);
        metadata.persist(startIndex, length);
    }

    /**
     * Returns this channel's file position.
     *
     * @return This channel's file position.
     */
    @Override
    public long position() throws IOException {
        logger.entry();

        lock.lock();
        int result = 0;

        try {
            validateIsOpen();

            result = dataBuffer.position();
        } finally {
            lock.unlock();
        }

        logger.exit(result);
        return result;
    }

    /**
     * Sets this channel's file position.
     *
     * <p> Setting the position to a value that is greater than the file's
     * current size is legal but does not change the size of the file.  A later
     * attempt to read bytes at such a position will immediately return an
     * end-of-file indication.  A later attempt to write bytes at such a
     * position will cause the file to be grown to accommodate the new bytes;
     * the values of any bytes between the previous end-of-file and the
     * newly-written bytes are unspecified.  </p>
     *
     * @param newPosition The new position.
     *
     * @return This file channel.
     */
    @Override
    public FileChannel position(long newPosition) throws ClosedChannelException, IOException {
        logger.entry(newPosition);

        lock.lock();

        try {
            validateIsOpen();
            validatePosition(newPosition);

            dataBuffer.position((int) newPosition);

        } finally {
            lock.unlock();
        }

        logger.exit(this);
        return this;
    }

    /**
     * Returns the current size of this channel's file.
     * This reports the effective data capacity, which is smaller than the raw size on disk due to the metadata overhead.
     * TODO fix me docs
     *
     * @return  The current size of this channel's file, measured in bytes.
     */
    @Override
    public long size() throws IOException {
        logger.entry();

        lock.lock();
        long result = 0;

        try {
            validateIsOpen();

            result = dataBuffer.limit();
        } finally {
            lock.unlock();
        }

        logger.exit(result);
        return result;
    }

    /**
     * A null-op, since the write methods are immediately persistent.

     * @param metaData ignored.
     * @throws IOException never.
     */
    @Override
    public void force(boolean metaData) throws IOException {
    }

    /**
     * Clears the file contents.
     * <p>
     * This operation overwrites the entire capacity, so may be slow on large files.
     *
     * @throws ClosedChannelException if the channel is not open.
     */
    public void clear() throws ClosedChannelException {
        logger.entry();

        lock.lock();
        try {

            // first overwrite the metadata to invalidate the file,
            // in case we crash in inconsistent state whilst zeroing the rest
            metadata.clear();
            clearDataFromOffset(0);
            dataBuffer.position(0);

        } finally {
            lock.unlock();
        }

        logger.exit();
    }

    private void clearDataFromOffset(int offset) {
        // sun.misc.Unsafe.setMemory may be faster, but would require linking against jdk.unsupported module
        dataBuffer.clear();
        dataBuffer.position(offset);
        byte[] zeros = new byte[1024 * 1024];
        while (dataBuffer.remaining() > 0) {
            dataBuffer.put(zeros, 0, dataBuffer.remaining() > zeros.length ? zeros.length : dataBuffer.remaining());
        }
        // we could force every N lines whilst looping above, but assume the hardware cache management
        // knows what it's doing and will elide flushes if they are for lines that have already been
        // evicted by cache pressure.
        persistenceHandle.persist(0, dataBuffer.capacity()-offset);
    }

    /**
     * Closes this channel.
     */
    @Override
    protected void implCloseChannel() throws IOException {
        logger.entry();

        lock.lock();

        try {
            // https://bugs.openjdk.java.net/browse/JDK-4724038
            unsafe.invokeCleaner(rawBuffer);

            fileChannel.truncate(metadata.getPersistenceIndex()); // TODO test me

            // TODO close metadata (assuming it's private instance)

            fileChannel.close();

        } finally {
            lock.unlock();
        }
        logger.exit();
    }

    private void validateIsOpen() throws ClosedChannelException {
        if(!fileChannel.isOpen()) {
            ClosedChannelException closedChannelException = new ClosedChannelException();
            logger.throwing(closedChannelException);
            throw closedChannelException;
        }
    }

    private void validatePosition(long position) throws IndexOutOfBoundsException {
        if (position > dataBuffer.limit()) {
            IndexOutOfBoundsException e = new IndexOutOfBoundsException("Position " + position + " exceeds limit " + dataBuffer.limit());
            logger.throwing(e);
            throw e;
        }
    }

    ////////////////

    /**
     * This method is not supported by this implementation.
     */
    @Override
    public long read(ByteBuffer[] byteBuffers, int offset, int length) throws IOException {
        throw new IOException("method not implemented");
    }

    /**
     * This method is not supported by this implementation.
     */
    @Override
    public long write(ByteBuffer[] byteBuffers, int offset, int length) throws IOException {
        throw new IOException("method not implemented");
    }

    /**
     * This method is not supported by this implementation.
     */
    @Override
    public FileChannel truncate(long size) throws IOException {
        throw new IOException("method not implemented");
    }

    /**
     * This method is not supported by this implementation.
     */
    @Override
    public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
        throw new IOException("method not implemented");
    }

    /**
     * This method is not supported by this implementation.
     */
    @Override
    public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
        throw new IOException("method not implemented");
    }

    /**
     * This method is not supported by this implementation.
     */
    @Override
    public MappedByteBuffer map(MapMode mapMode, long position, long size) throws IOException {
        throw new IOException("method not implemented");
    }

    /**
     * This method is not supported by this implementation.
     */
    @Override
    public FileLock lock(long position, long size, boolean shared) throws IOException {
        throw new IOException("method not implemented");
    }

    /**
     * This method is not supported by this implementation.
     */
    @Override
    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        throw new IOException("method not implemented");
    }

}
