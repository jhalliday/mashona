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

import org.jboss.byteman.contrib.bmunit.BMRule;
import org.jboss.byteman.contrib.bmunit.BMUnitConfig;

import org.jboss.byteman.rule.exception.ThrowException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.opentest4j.AssertionFailedError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileDescriptor;

@BMUnitConfig(loadDirectory = "target/test-classes", verbose = false, bmunitVerbose = false)
public class ExecutionTracer {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionTracer.class);

    private static final long CACHE_LINE_FLUSH_SIZE = 64; // UnsafeConstants.CACHE_LINE_FLUSH_SIZE;
    private static final long CACHE_LINE_MASK = ~(CACHE_LINE_FLUSH_SIZE - 1);

    public static ExecutionTracer INSTANCE = new ExecutionTracer();

    public long address;
    public boolean[] dirtyLines;

    public boolean allowFlushingOfCleanLines;
    public boolean allowNonFlushingOfDirtyLines;
    private boolean flushedCleanLine;

    @BeforeEach
    public static void setUp() {
        logger.debug("setUp");
        reset();
    }

    @AfterEach
    public static void tearDown() {
        logger.debug("tearDown");
        assertFlushingWasEfficient();
        assertFlushingWasSufficient();
    }

    public static void assertFlushingWasEfficient() {
        if(INSTANCE.flushedCleanLine && !INSTANCE.allowFlushingOfCleanLines) {
            INSTANCE.fail("At least one clean cache line was flushed.");
        }
    }

    public static void assertFlushingWasSufficient() {

        if(INSTANCE.allowNonFlushingOfDirtyLines) {
            return;
        }

        for(int i = 0; i < INSTANCE.dirtyLines.length; i++) {
            if(INSTANCE.dirtyLines[i]) {
                INSTANCE.fail("Dirty cache line "+i+" was not flushed.");
            }
        }
    }

    public static void reset() {
        INSTANCE.flushedCleanLine = false;
        INSTANCE.allowFlushingOfCleanLines = false;
        INSTANCE.allowNonFlushingOfDirtyLines = false;
    }


    public int addressToLineIndex(long address) {
        address = address - this.address;
        address = address & CACHE_LINE_MASK;
        return (int) (address / CACHE_LINE_FLUSH_SIZE);
    }

    private void markDirty(long address) {
        int idx = addressToLineIndex(address);
        if (dirtyLines != null && idx >= 0 && idx <= dirtyLines.length) {

            if (dirtyLines[idx]) {
                logger.debug("modifying line {}, which is already dirty", idx);
            } else {
                logger.debug("modifying line {}, which was clean", idx);
            }

            dirtyLines[idx] = true;
        }
    }

    private void markClean(long address) {
        int idx = addressToLineIndex(address);
        if (dirtyLines != null && idx >= 0 && idx <= dirtyLines.length) {

            if (dirtyLines[idx]) {
                logger.debug("flushing line {}, which was dirty", idx);
            } else if(allowFlushingOfCleanLines) {
                logger.debug("flushing line {}, which was clean", idx);
                flushedCleanLine = true;
            } else {
                logger.warn("flushing line {}, which was clean", idx);
                flushedCleanLine = true;
            }

            dirtyLines[idx] = false;
        }
    }

    private void fail(String message) {
        try {
            org.junit.jupiter.api.Assertions.fail(message);
        } catch (AssertionFailedError e) {
            throw new ThrowException(e);
        }
    }

    /////////////////////////

    @BMRule(name = "trace_force_0",
            targetClass = "MappedByteBuffer", targetMethod = "force()", targetLocation = "ENTRY",
            condition = "com.redhat.mashona.ExecutionTracer.INSTANCE != null",
            action = "com.redhat.mashona.ExecutionTracer.INSTANCE.force();")
    public void force() {
        logger.debug("force()");
        // this calls force(from,length), so we don't need to take action here.
    }

    @BMRule(name = "trace_force_1",
            targetClass = "MappedByteBuffer", targetMethod = "force(int,int)", targetLocation = "ENTRY",
            condition = "com.redhat.mashona.ExecutionTracer.INSTANCE != null",
            action = "com.redhat.mashona.ExecutionTracer.INSTANCE.force($1, $2);")
    public void force(int from, int length) {
        logger.debug("force(from={}, length={})", from, length);
        // this calls writebackMemory(a,l), so we don't need to take action here.
    }

    @BMRule(name = "trace_writebackMemory_0",
            targetClass = "Unsafe", targetMethod = "writebackMemory(long,long)", targetLocation = "ENTRY",
            condition = "com.redhat.mashona.ExecutionTracer.INSTANCE != null",
            action = "com.redhat.mashona.ExecutionTracer.INSTANCE.writebackMemory($1,$2);")
    public void writebackMemory(long address, long length) {
        logger.debug("writebackMemory(address={}, length={})", address, length);

        long line = (address & CACHE_LINE_MASK);
        long end = address + length;
        while (line < end) {
            markClean(line);
            line += CACHE_LINE_FLUSH_SIZE;
        }
    }

    @BMRule(name = "trace_writebackMemory_1",
            targetClass = "Unsafe", targetMethod = "writebackMemory(long,long)", targetLocation = "INVOKE writeback0(long)",
            condition = "com.redhat.mashona.ExecutionTracer.INSTANCE != null",
            action = "com.redhat.mashona.ExecutionTracer.INSTANCE.writebackMemory((Long)$@[1]);")
    public void writebackMemory(long line) {
        logger.debug("writebackMemory(line={})", line);
        // this no longer exists in the jdk13 version of the patch
        //markClean(line);
    }

    @BMRule(name = "trace_putShort_0",
            targetClass = "DirectByteBuffer", targetMethod = "putShort(long,short)", targetLocation = "ENTRY",
            condition = "com.redhat.mashona.ExecutionTracer.INSTANCE != null",
            action = "com.redhat.mashona.ExecutionTracer.INSTANCE.putShort($1, $2);")
    public void putShort(long address, short value) {
        logger.debug("putShort(address={}, value={})", address, value);

        if ((address & 1) != 0) {
            logger.warn("UNALIGNED putShort(address=" + address + ", value=" + value + ") with call stack:", new Exception("UNALIGNED putShort"));
            fail("UNALIGNED putShort(address=" + address + ", value=" + value + ")");
        }

        markDirty(address);
    }

    @BMRule(name = "trace_putInt_0",
            targetClass = "DirectByteBuffer", targetMethod = "putInt(long,int)", targetLocation = "ENTRY",
            condition = "com.redhat.mashona.ExecutionTracer.INSTANCE != null",
            action = "com.redhat.mashona.ExecutionTracer.INSTANCE.putInt($this, $1, $2);")
    public void putInt(Object buffer, long address, int value) {
        logger.debug("putInt(address={}, value={})", address, value);


        if ((address & 3) != 0) {
            logger.warn("UNALIGNED putInt(address=" + address + ", value=" + value + ") with call stack:", new Exception("UNALIGNED putInt"));
            fail("UNALIGNED putInt(address=" + address + ", value=" + value + ")");
        }

        markDirty(address);
    }

    @BMRule(name = "trace_putLong_0",
            targetClass = "DirectByteBuffer", targetMethod = "putLong(long,long)", targetLocation = "ENTRY",
            condition = "com.redhat.mashona.ExecutionTracer.INSTANCE != null",
            action = "com.redhat.mashona.ExecutionTracer.INSTANCE.putLong($1, $2);")
    public void putLong(long address, long value) {
        logger.debug("putLong(address={}, value={})", address, value);

        if ((address & 7) != 0) {
            logger.warn("UNALIGNED putLong(address=" + address + ", value=" + value + ") with call stack:", new Exception("UNALIGNED putLong"));
            fail("UNALIGNED putLong(address=" + address + ", value=" + value + ")");
        }

        markDirty(address);
    }

    @BMRule(name = "trace_putByte_0",
            targetClass = "Unsafe", targetMethod = "putByte(long,byte)", targetLocation = "ENTRY",
            condition = "com.redhat.mashona.ExecutionTracer.INSTANCE != null",
            action = "com.redhat.mashona.ExecutionTracer.INSTANCE.putByte($1, $2);")
    public void putByte(long address, byte value) {
        logger.debug("putByte(address={}, value={})", address, value);

        markDirty(address);
    }

    // caution: updated for jdk.internal.access.foreign.MemorySegmentProxy API change. Won't work on older JDK14 builds, causing tests to break in weird ways.
    @BMRule(name = "trace_mbbConstructor_0",
            targetClass = "DirectByteBuffer", targetMethod = "<init>(int,long,FileDescriptor,Runnable,boolean,MemorySegmentProxy)", targetLocation = "ENTRY",
            condition = "com.redhat.mashona.ExecutionTracer.INSTANCE != null",
            action = "com.redhat.mashona.ExecutionTracer.INSTANCE.mbbConstructor($1, $2, $3, $4, $5);")
    public void mbbConstructor(int cap, long address, FileDescriptor fd, Runnable unmapper, boolean isSync) {
        if (isSync) {
            logger.debug("MappedByteBuffer<load>(address={}, cap={})", address, cap);

            this.address = address;
            int lines = (int) Math.ceil(1.0 * cap / CACHE_LINE_FLUSH_SIZE);
            dirtyLines = new boolean[lines];
        }
    }

    @BMRule(name = "trace_copyMemory_0",
            targetClass = "Unsafe", targetMethod = "copyMemory(long,long,long)", targetLocation = "ENTRY",
            condition = "com.redhat.mashona.ExecutionTracer.INSTANCE != null",
            action = "com.redhat.mashona.ExecutionTracer.INSTANCE.copyMemory($1, $2, $3);")
    public void copyMemory(long srcAddress, long destAddress, long bytes) {
        logger.debug("copyMemory(address={}, bytes={})", destAddress, bytes);
        // this version always calls the other, so we don't need to instrument it.
    }

    @BMRule(name = "trace_copyMemory_1",
            targetClass = "Unsafe", targetMethod = "copyMemory(Object,long,Object,long,long)", targetLocation = "ENTRY",
            condition = "com.redhat.mashona.ExecutionTracer.INSTANCE != null",
            action = "com.redhat.mashona.ExecutionTracer.INSTANCE.copyMemory($1, $2, $3, $4, $5);")
    public void copyMemory(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {
        logger.debug("copyMemory(address={}, bytes={})", destOffset, bytes);

        long line = (destOffset & CACHE_LINE_MASK);
        long end = destOffset + bytes;
        while (line < end) {
            markDirty(line);
            line += CACHE_LINE_FLUSH_SIZE;
        }
    }
}

