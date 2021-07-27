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
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An append-only log structure built over memory-mapped pmem, pretending to be a FileChannel for easy integration.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2019-04
 */
public class MappedFileChannel extends FileChannel {

    private static final Logger logger = Logger.getLogger(MappedFileChannel.class);

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
        return new File(file.getCanonicalPath() + ".pmem");
    }

    private final PersistenceHandle persistenceHandle;

    private final Lock lock = new ReentrantLock();

    private final File file;
    private final FileChannel fileChannel;
    private final ByteBuffer rawBuffer;
    private final ByteBuffer dataBuffer;

    private final MappedFileChannelMetadata metadata;

    /**
     * Initializes a new MappedFileChannel over the provided File, with a fixed length.
     *
     * @param file               The file over which to map.
     * @param length             The required raw capacity.
     * @param readSharedMetadata The sharing mode for the persistence metadata.
     * @throws IOException if the mapping cannot be created, such as when the File is on a filesystem that does not support DAX.
     */
    public MappedFileChannel(File file, int length, boolean readSharedMetadata) throws IOException {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry with file={0}, length={1}, readSharedMetadata={2}", file, length, readSharedMetadata);
        }

        this.file = file;

        if (!file.exists() && getMetadataFile(file).exists()) {
            if(logger.isDebugEnabled()) {
                logger.debugv("deleting orphan metadata for {0}", file.getAbsolutePath());
            }
            getMetadataFile(file).delete();
        }

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

        metadata = new MappedFileChannelMetadata(getMetadataFile(file), readSharedMetadata);
        dataBuffer.position(0);

        if(logger.isTraceEnabled()) {
            logger.tracev("exit {0}", this);
        }
    }

    /**
     * Initializes a new MappedFileChannel over the provided File, with a fixed length.
     *
     * @param file   The file over which to map.
     * @param length The required raw capacity.
     * @throws IOException if the mapping cannot be created, such as when the File is on a filesystem that does not support DAX.
     */
    public MappedFileChannel(File file, int length) throws IOException {
        this(file, length, false);
    }

    /**
     * Delete the metadata file that is associated with this channel.
     *
     * @throws IOException if the channel is still open.
     */
    public void deleteMetadata() throws IOException {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0}", this);
        }

        if (fileChannel.isOpen()) {
            IOException ioException = new IOException("Unable to delete metadata for an open channel");
            if(logger.isTraceEnabled()) {
                logger.tracev(ioException, "throwing {0}", ioException.toString());
            }
            throw ioException;
        }

        File metadata = getMetadataFile(file);
        if (metadata.exists()) {
            metadata.delete();
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit");
        }
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
     * @return The number of bytes read, possibly zero.
     */
    @Override
    public int read(ByteBuffer dst) throws IOException {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0} with dst={1}", this, dst);
        }

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

        if(logger.isTraceEnabled()) {
            logger.tracev("exit returning {0}", result);
        }
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
     * @param dst      The buffer into which bytes are to be transferred.
     * @param position The file position at which the transfer is to begin.
     * @return The number of bytes read, possibly zero, or {@code -1} if the
     * given position is greater than or equal to the file's current
     * persisted size
     */
    @Override
    public int read(ByteBuffer dst, long position) throws IOException {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0} with dst={1} and position={2}", this, dst, position);
        }

        lock.lock();
        int result = 0;

        try {
            validateIsOpen();
            validatePosition(position);

            int length = metadata.getPersistenceIndex() - (int) position;
            if (length <= 0) {
                length = -1;
            }
            length = Math.min(length, dst.remaining());

            if (length > 0) {
                ByteBuffer srcSlice = dataBuffer.slice((int) position, length);
                dst.put(srcSlice);
                result = srcSlice.position();
            } else {
                result = length;
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
     * Writes a sequence of bytes to this channel from the given buffer.
     * <p>
     * After this method returns successfully, the data is guaranteed persisted (i.e. flushed).
     * This channel's position will be advanced by the returned number of bytes.
     *
     * @param src The buffer from which bytes are to be transferred.
     *            Its position will be advanced by the returned number of bytes.
     * @return The number of bytes written, possibly zero.
     */
    @Override
    public int write(ByteBuffer src) throws IOException {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0} with src={1}", this, src);
        }

        lock.lock();
        int result = 0;

        try {
            validateIsOpen();

            result = writeInternal(src, dataBuffer.position());

            dataBuffer.position(dataBuffer.position() + result);

        } finally {
            lock.unlock();
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit returning {0}", result);
        }
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
     * @param src      The buffer from which bytes are to be transferred.
     * @param position The file position at which the transfer is to begin.
     * @return The number of bytes written, possibly zero.
     */
    @Override
    public int write(ByteBuffer src, long position) throws IOException {
        if(logger.isTraceEnabled()) {
            logger.tracev ("entry for {0} with src={1}, position={2}", this, src, position);
        }

        lock.lock();
        int result = 0;

        try {
            validateIsOpen();
            validatePosition(position);

            result = writeInternal(src, (int) position);

        } finally {
            lock.unlock();
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit returning {0}", result);
        }
        return result;
    }

    private int writeInternal(ByteBuffer src, int position) throws ClosedChannelException {

        if(metadata.isReadShared()) {
            IllegalStateException illegalStateException = new IllegalStateException("ReadShared views can not be used for writes");
            if(logger.isTraceEnabled()) {
                logger.tracev(illegalStateException, "throwing {0}", illegalStateException.toString());
            }
            throw illegalStateException;
        }

        if (position < metadata.getPersistenceIndex()) {
            IllegalArgumentException illegalArgumentException = new IllegalArgumentException(
                    "Write position "+position+" is before tail position "+metadata.getPersistenceIndex()+" - can not overwrite existing data");
            if(logger.isTraceEnabled()) {
                logger.tracev(illegalArgumentException, "throwing {0}", illegalArgumentException.toString());
            }
            throw illegalArgumentException;
        }

        int length = Math.min(dataBuffer.remaining(), src.remaining());

        ByteBuffer srcSlice = src.slice(src.position(), length);
        ByteBuffer dst = dataBuffer.slice(position, length);

        dst.put(srcSlice);
        src.position(src.position() + length);

        persist(position, length);

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
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0}", this);
        }

        lock.lock();
        int result = 0;

        try {
            validateIsOpen();

            result = dataBuffer.position();
        } finally {
            lock.unlock();
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit returning {0}", result);
        }
        return result;
    }

    /**
     * Sets this channel's file position.
     *
     * <p> Setting the position to a value that is greater than the file's
     * current persisted size is legal but does not change the size of the file.  A later
     * attempt to read bytes at such a position will immediately return an
     * end-of-file indication.  A later attempt to write bytes at such a
     * position will cause the values of any bytes between the previous end-of-file
     * and the newly-written bytes to be unspecified.</p>
     *
     * @param newPosition The new position.
     * @return This file channel.
     */
    @Override
    public FileChannel position(long newPosition) throws ClosedChannelException, IOException {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0} with newPosition={1}", this, newPosition);
        }

        lock.lock();

        try {
            validateIsOpen();
            validatePosition(newPosition);

            dataBuffer.position((int) newPosition);

        } finally {
            lock.unlock();
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit {0}", this);
        }
        return this;
    }

    /**
     * Returns the current size of this channel's mapped area, as provided to the constructor.
     *
     * <p>Note: This may differ from the size on disk or the readable size.</p>
     *
     * @return The current size of this channel's mapped area, measured in bytes.
     */
    @Override
    public long size() throws IOException {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0}", this);
        }

        lock.lock();
        long result = 0;

        try {
            validateIsOpen();

            result = dataBuffer.limit();
        } finally {
            lock.unlock();
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit returning {0}", result);
        }
        return result;
    }

    /**
     * Returns the size of the channel's file on disk.
     *
     * <p>Note: This may be more than the mapped area - use .size() instead for that.</p>
     * <p></p>Note: This may be more than the readable size, as non persisted trailing data is inacessible.</p>
     *
     * @return The size of the channel's file on disk, measured in bytes.
     * @see #size()
     * @see #getPersistedSize()
     */
    public long getFileSize() {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0}", this);
        }

        long result = file.length();

        if(logger.isTraceEnabled()) {
            logger.tracev("exit returning {0}", result);
        }
        return result;
    }

    /**
     * Returns the size of the persisted data within the channel.
     *
     * <p>Note: this may be less than the mapped area (i.e. capacity) of the channel.</p>
     * <p>Note: this may be less than the file size on disk.</p>
     *
     * @return The size of the currently persisted data, measured in bytes.
     * @throws ClosedChannelException if the channel is not open.
     * @see #size()
     * @see #getFileSize()
     */
    public long getPersistedSize() throws ClosedChannelException {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0}", this);
        }

        lock.lock();
        long result = 0;

        try {
            validateIsOpen();

            result = metadata.getPersistenceIndex();
        } finally {
            lock.unlock();
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit returning {0}", result);
        }
        return result;
    }

    /**
     * A null-op, since the write methods are immediately persistent.
     *
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
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0}", this);
        }

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

        if(logger.isTraceEnabled()) {
            logger.tracev("exit");
        }
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
        persistenceHandle.persist(0, dataBuffer.capacity() - offset);
    }

    /**
     * Closes this channel.
     */
    @Override
    protected void implCloseChannel() throws IOException {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0}", this);
        }

        lock.lock();

        try {
            // https://bugs.openjdk.java.net/browse/JDK-4724038
            unsafe.invokeCleaner(rawBuffer);

            if(!metadata.isReadShared()) {
                int persistenceIndex = metadata.getPersistenceIndex();
                if(logger.isDebugEnabled()) {
                    logger.debugv("truncating file={0} to length={1}", file.getAbsolutePath(), persistenceIndex);
                }
                fileChannel.truncate(persistenceIndex);
            }

            fileChannel.close();

            metadata.close();

        } finally {
            lock.unlock();
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit");
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

    private void validatePosition(long position) throws IndexOutOfBoundsException {
        if (position > dataBuffer.limit()) {
            IndexOutOfBoundsException indexOutOfBoundsException =
                    new IndexOutOfBoundsException("Position " + position + " exceeds limit " + dataBuffer.limit());
            if(logger.isTraceEnabled()) {
                logger.tracev(indexOutOfBoundsException, "throwing {0}", indexOutOfBoundsException.toString());
            }
            throw indexOutOfBoundsException;
        }
    }

    ////////////////

    private static String NOT_IMPLEMENTED = "Method not implemented";

    /**
     * This method is not supported by this implementation.
     */
    @Override
    public long read(ByteBuffer[] byteBuffers, int offset, int length) throws IOException {
        throw new IOException(NOT_IMPLEMENTED);
    }

    /**
     * This method is not supported by this implementation.
     */
    @Override
    public long write(ByteBuffer[] byteBuffers, int offset, int length) throws IOException {
        throw new IOException(NOT_IMPLEMENTED);
    }

    /**
     * This method is not supported by this implementation.
     */
    @Override
    public FileChannel truncate(long size) throws IOException {
        throw new IOException(NOT_IMPLEMENTED);
    }

    /**
     * This method is not supported by this implementation.
     */
    @Override
    public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
        throw new IOException(NOT_IMPLEMENTED);
    }

    /**
     * This method is not supported by this implementation.
     */
    @Override
    public long transferFrom(ReadableByteChannel src, long position, long count) throws IOException {
        throw new IOException(NOT_IMPLEMENTED);
    }

    /**
     * This method is not supported by this implementation.
     */
    @Override
    public MappedByteBuffer map(MapMode mapMode, long position, long size) throws IOException {
        throw new IOException(NOT_IMPLEMENTED);
    }

    /**
     * This method is not supported by this implementation.
     */
    @Override
    public FileLock lock(long position, long size, boolean shared) throws IOException {
        throw new IOException(NOT_IMPLEMENTED);
    }

    /**
     * This method is not supported by this implementation.
     */
    @Override
    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        throw new IOException(NOT_IMPLEMENTED);
    }

}
