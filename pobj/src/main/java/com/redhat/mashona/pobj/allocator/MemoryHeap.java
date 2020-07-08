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
package com.redhat.mashona.pobj.allocator;

import com.redhat.mashona.pobj.runtime.MemoryOperations;
import com.redhat.mashona.pobj.runtime.MemoryBackedObject;

import jdk.incubator.foreign.MemorySegment;
import jdk.nio.mapmode.ExtendedMapMode;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import sun.misc.Unsafe;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;

/**
 * Manages a contiguous region of memory, mapped from a file,
 * as a heap space within which objects of varying size may be dynamically allocated.
 * <p>
 * Note that the allocation tracking is not done within the file itself
 * and should be persisted independently if required.
 * <p>
 * Instances of this class are threadsafe if provided exclusive access to the underlying file and allocator.
 * If other instances (or external processes) access the same structures, all bets are off.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-06
 */
public class MemoryHeap {

    private static Unsafe unsafe;

    static {
        // ugliness required for clone, until the JDK's unmapping behavior is fixed.
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final XLogger logger = XLoggerFactory.getXLogger(MemoryHeap.class);

    private final FileChannel fileChannel;
    private final ByteBuffer buffer;
    protected final MemorySegment memorySegment;

    protected final CompositeAllocator compositeAllocator;

    public MemoryHeap(File file, long length, CompositeAllocator compositeAllocator) throws IOException {
        logger.entry(file, length, compositeAllocator);

        this.fileChannel = (FileChannel) Files
                .newByteChannel(file.toPath(), EnumSet.of(
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE));

        buffer = fileChannel.map(ExtendedMapMode.READ_WRITE_SYNC, 0, length);
        memorySegment = MemorySegment.ofByteBuffer(buffer);

        this.compositeAllocator = compositeAllocator;

        logger.exit();
    }

    public synchronized void close() throws IOException {

        if (!memorySegment.isAlive()) {
            return;
        }

        memorySegment.close();

        // https://bugs.openjdk.java.net/browse/JDK-4724038
        unsafe.invokeCleaner(buffer);

        fileChannel.close();
    }

    /**
     * Create and return a new instance of the provided Class,
     * with its state backed by memory dynamically allocated from the heap space.
     *
     * @param objectClass The class to instantiate. Must have an accessible default constructor.
     * @param <T>         extends MemoryBackedObject, the interface used for sizing and wiring up the backing memory.
     * @return an Object instance.
     * @throws RuntimeException for failures relating to object instantiation, including insufficient memory.
     */
    public synchronized <T extends MemoryBackedObject> T newInstance(Class<T> objectClass) {
        logger.entry(objectClass.getName());

        validateIsOpen();

        try {
            T instance = objectClass.getConstructor().newInstance();
            long size = instance.size();
            long addr = compositeAllocator.allocate(size);
            if (addr == -1) {
                RuntimeException runtimeException = new RuntimeException(new OutOfMemoryError());
                logger.throwing(runtimeException);
                throw runtimeException;
            }
            // there is a window here where we'll leak the allocated memory if the following operations fail...
            MemorySegment segment = memorySegment.asSlice(addr, size).acquire();
            MemoryOperations memoryOperations = wrapMemory(addr, segment);
            instance.setMemory(memoryOperations);
            logger.exit(instance);
            return instance;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
            RuntimeException runtimeException = new RuntimeException(e);
            logger.throwing(runtimeException);
            throw runtimeException;
        }
    }

    /**
     * Release an allocated object, returning its backing memory to the heap. (Sorry, no GC here!)
     *
     * @param object an instance previously supplied by newInstance.
     * @param <T>    extends MemoryBackedObject, the interface used for accessing the memory management information.
     */
    public synchronized <T extends MemoryBackedObject> void delete(T object) {
        logger.entry(object);

        validateIsOpen();

        MemorySegment memorySegment = object.getMemory().getMemorySegment();
        long heapOffset = object.getMemory().getHeapOffset();
        compositeAllocator.free(heapOffset, memorySegment.byteSize());
        memorySegment.close();

        logger.exit();
    }

    private void validateIsOpen() {
        if (!memorySegment.isAlive()) {
            IllegalStateException illegalStateException = new IllegalStateException();
            logger.throwing(illegalStateException);
            throw illegalStateException;
        }
    }

    protected MemoryOperations wrapMemory(long addr, MemorySegment memorySegment) {
        return new MemoryOperations(addr, memorySegment);
    }
}
