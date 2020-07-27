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
 *
 * This class based on source originally forked from Netty's PoolSubpage,
 * Copyright 2012 The Netty Project, which is likewise APL 2.0 licensed.
 */
package com.redhat.mashona.pobj.allocator;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.Comparator;

/**
 * Provides memory tracking for allocations of uniform size within a contiguous memory range.
 * <p>
 * This class provides bookkeeping only. The actual memory space being managed is theoretical
 * to the allocator and must be provided elsewhere, as by e.g. PmemHeap.
 * <p>
 * This class is NOT threadsafe.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-06
 */
public class RegionBitmap implements Comparable<Long> {

    private static final XLogger logger = XLoggerFactory.getXLogger(RegionBitmap.class);

    protected final long baseAddress;
    protected final long backingSize;
    protected final long elementSize;

    protected final int maxElements;
    protected final int bitmapLength; // in longs.
    protected final long[] bitmap;
    protected int numAvail;
    protected int nextAvail;

    /**
     * Creates a new region, the overall area starting, at baseAddress and extending for backingSize bytes.
     *
     * @param baseAddress The starting point of the region allocation space.
     * @param regionConfig The region configuration properties.
     */
    public RegionBitmap(long baseAddress, RegionConfig regionConfig) {
        logger.entry(baseAddress, regionConfig);

        this.baseAddress = baseAddress;
        this.backingSize = regionConfig.getBackingSize();
        this.elementSize = regionConfig.getElementSize(); // align? regionConfig.getElementSize() + 8 - 1) & -8;

        long numElements = backingSize / elementSize;
        if (numElements > Integer.MAX_VALUE) {
            // this is a little stricter than it needs to be, as array indexing on bitmapLength is the real constraint.
            IllegalArgumentException illegalArgumentException = new IllegalArgumentException();
            logger.throwing(illegalArgumentException);
            throw illegalArgumentException;
        }

        maxElements = numAvail = (int) numElements;
        nextAvail = 0;
        // shift 6 = 64 i.e. num bits in a long
        int tmpBitmapLength = maxElements >>> 6;
        if ((maxElements & 63) != 0) {
            tmpBitmapLength++;
        }

        bitmapLength = tmpBitmapLength;
        bitmap = new long[bitmapLength];

        logger.exit();
    }

    /**
     * Returns the size in bytes of the overall memory region managed by this allocator.
     *
     * @return The total length of the memory region.
     */
    public long getBackingSize() {
        return backingSize;
    }

    /**
     * Returns the unit size in bytes of the allocations provided by this region.
     *
     * @return The allocation unit size.
     */
    public long getElementSize() {
        return elementSize;
    }

    /**
     * Returns the maximum number of allocations this region can provide.
     * i.e. the backing size divided by the element size.
     *
     * @return The number of elements that will fit in the region.
     */
    public int getMaxElements() {
        return maxElements;
    }

    /**
     * Returns the number of allocations remaining unused in the region.
     *
     * @return The number of currently unused element spaces.
     */
    public int getNumAvail() {
        return numAvail;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(Long o) {
        return Long.compare(baseAddress, o);
    }

    public static final Comparator<RegionBitmap> COMPARATOR = new Comparator<>() {
        @Override
        public int compare(RegionBitmap o1, RegionBitmap o2) {
            return o1.compareTo(o2.baseAddress);
        }
    };

    /**
     * Attempts to allocate a region of memory of the configured size and provides its base address.
     *
     * @return a memory address on success, or -1 on failure.
     */
    public long allocate() {
        logger.entry();

        if (numAvail == 0) {
            return -1;
        }

        final int bitmapIdx = getNextAvail();
        int q = bitmapIdx >>> 6;
        int r = bitmapIdx & 63;
        assert (bitmap[q] >>> r & 1) == 0;
        bitmap[q] |= 1L << r;
        numAvail--;

        long handle = toHandle(bitmapIdx);

        return handle;
    }

    /**
     * Release a previously allocated region of memory back to the allocator pool.
     *
     * @param handle The memory address, as previously returned by allocate.
     */
    public void free(long handle) {
        logger.entry(handle);

        if (handle < baseAddress || handle > toHandle(getMaxElements())) {
            throw new IllegalArgumentException();
        }

        int bitmapIdx = toBitmapIndex(handle);
        int q = bitmapIdx >>> 6;
        int r = bitmapIdx & 63;

        if ((bitmap[q] >>> r & 1) == 0) {
            throw new IllegalArgumentException();
        }

        bitmap[q] ^= 1L << r;
        nextAvail = bitmapIdx;
        numAvail++;

        logger.exit();
    }

    /**
     * Determine if a given handle is currently allocated or now.
     *
     * @param handle The memory address, as previously returned by allocate.
     * @return true if the handle is unused (free), false if it is currently allocated.
     */
    public boolean isFree(long handle) {
        logger.entry(handle);

        if (handle < baseAddress || handle > toHandle(getMaxElements())) {
            throw new IllegalArgumentException();
        }

        int bitmapIdx = toBitmapIndex(handle);
        int q = bitmapIdx >>> 6;
        int r = bitmapIdx & 63;

        boolean isFree = ((bitmap[q] >>> r & 1) == 0);

        logger.exit(isFree);
        return isFree;
    }

    private long toHandle(int bitmapIdx) {
        return baseAddress + (elementSize * bitmapIdx);
    }

    private int toBitmapIndex(long handle) {
        return (int) ((handle - baseAddress) / elementSize);
    }

    private int findNextAvail() {
        final long[] bitmap = this.bitmap;
        final int bitmapLength = this.bitmapLength;
        for (int i = 0; i < bitmapLength; i++) {
            long bits = bitmap[i];
            if (~bits != 0) {
                return findNextAvail0(i, bits);
            }
        }
        return -1;
    }

    private int getNextAvail() {
        int nextAvail = this.nextAvail;
        if (nextAvail >= 0) {
            this.nextAvail = -1;
            return nextAvail;
        }
        return findNextAvail();
    }

    private int findNextAvail0(int i, long bits) {
        final int maxNumElems = this.maxElements;
        final int baseVal = i << 6;

        for (int j = 0; j < 64; j++) {
            if ((bits & 1) == 0) {
                int val = baseVal | j;
                if (val < maxNumElems) {
                    return val;
                } else {
                    break;
                }
            }
            bits >>>= 1;
        }
        return -1;
    }
}
