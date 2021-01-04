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
package io.mashona.pobj.allocator;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Provides memory tracking for allocations of varied sizes within a contiguous range,
 * by treating the overall space as dynamically composed of regions dedicated for each allocation size.
 * <p>
 * This class provides bookkeeping only. The actual memory space being managed is theoretical
 * to the allocator and must be provided elsewhere, as by e.g. MemoryHeap.
 * <p>
 * This class is NOT threadsafe.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-06
 */
public class CompositeAllocator {

    private static final XLogger logger = XLoggerFactory.getXLogger(CompositeAllocator.class);

    protected final long baseAddress;
    protected final long backingSize;
    protected final List<RegionConfig> regionConfigList;
    protected final long[] elementSizes;
    protected final List<RegionBitmap>[] regionBitmaps;

    /**
     * Creates a new allocator with default region configuration, the overall area starting
     * at baseAddress and extending for backingSize bytes.
     * <p>
     * A minimum backing size of 4MB is required and an 8-byte aligned base address is recommended.
     *
     * @param baseAddress The starting point of the memory range.
     * @param backingSize The total length of the memory region.
     */
    public CompositeAllocator(long baseAddress, long backingSize) {
        logger.entry(baseAddress, backingSize);

        this.baseAddress = baseAddress;
        this.backingSize = backingSize;
        this.regionConfigList = new ArrayList<>(getRegionConfigList(backingSize));
        Collections.sort(this.regionConfigList);

        elementSizes = new long[this.regionConfigList.size()];
        regionBitmaps = new List[this.regionConfigList.size()];
        for (int i = 0; i < elementSizes.length; i++) {
            elementSizes[i] = this.regionConfigList.get(i).getElementSize();
            regionBitmaps[i] = new ArrayList<>();
        }

        int biggestIndex = this.regionConfigList.size() - 1;
        RegionConfig biggestRegionConfig = this.regionConfigList.get(biggestIndex);
        regionBitmaps[biggestIndex].add(allocateRegion(baseAddress, biggestRegionConfig));

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

    protected RegionBitmap allocateRegion(long baseAddress, RegionConfig regionConfig) {
        return new RegionBitmap(baseAddress, regionConfig);
    }

    /**
     * Attempts to allocate a contiguous region of memory of at least the requested size.
     * i.e. this is  'malloc'. The returned region will be at least 8 bytes and 8-byte aligned
     * to the region baseAddress.
     *
     * @param size The request region size, in bytes.
     * @return a memory address on success, or -1 on failure.
     */
    public long allocate(long size) {
        return allocate(size, false);
    }

    protected long allocate(long size, boolean forInternalUse) {
        logger.entry(size, forInternalUse);

        int x = Arrays.binarySearch(elementSizes, size);

        if (x < 0) {
            x = Math.abs(x) - 1;
            if (x == elementSizes.length) {
                logger.exit(-1);
                return -1;
            }
        }

        List<RegionBitmap> regionBitmapList = regionBitmaps[x];

        for (int i = regionBitmapList.size() - 1; i >= 0; i--) {
            RegionBitmap regionBitmap = regionBitmapList.get(i);
            long result = regionBitmap.allocate();
            if (result != -1) {
                logger.exit(result);
                return result;
            }
        }
        RegionBitmap regionBitmap = increaseForAllocationClass(x);
        if (regionBitmap == null) {
            logger.exit(-1);
            return -1;
        }

        long result = regionBitmap.allocate();
        logger.exit(result);
        return result;
    }

    /**
     * Release a previously allocated region of memory back to the allocator pool.
     *
     * @param address The memory address, as previously returned by allocate.
     * @param size    The memory region size, as requested when calling allocate.
     */
    public void free(long address, long size) {
        logger.entry(address, size);

        RegionBitmap regionBitmap = findAllocationBitmap(address, size);
        regionBitmap.free(address);

        logger.exit();
    }

    public boolean isFree(long address, long size) {
        logger.entry(address, size);

        RegionBitmap regionBitmap = findAllocationBitmap(address, size);
        boolean isFree = regionBitmap == null || regionBitmap.isFree(address);

        logger.exit(isFree);
        return isFree;
    }

    protected RegionBitmap findAllocationBitmap(long address, long size) {
        int x = Arrays.binarySearch(elementSizes, size);

        if (x < 0) {
            x = Math.abs(x) - 1;
            if (x == elementSizes.length) {
                IllegalArgumentException e = new IllegalArgumentException();
                logger.throwing(e);
                throw e;
            }
        }

        List<RegionBitmap> regionBitmapList = regionBitmaps[x];

        int y = Collections.binarySearch(regionBitmapList, address);
        if (y < 0) {
            y = Math.abs(y) - 2;
        }
        if(y < 0) {
            return null;
        }
        RegionBitmap regionBitmap = regionBitmapList.get(y);
        return regionBitmap;
    }

    protected RegionBitmap increaseForAllocationClass(int x) {
        logger.entry(baseAddress, backingSize);

        RegionConfig regionConfig = regionConfigList.get(x);
        if (x == elementSizes.length - 1) {
            logger.exit(null);
            return null;
        }
        long address = allocate(regionConfig.getBackingSize(), true);
        if (address == -1) {
            logger.exit(null);
            return null;
        }
        RegionBitmap regionBitmap = allocateRegion(address, regionConfig);
        regionBitmaps[x].add(regionBitmap);
        regionBitmaps[x].sort(RegionBitmap.COMPARATOR);

        logger.exit(regionBitmap);
        return regionBitmap;
    }

    /*
    Eventually this should be user configurable, but there are a lot of undocumented/unchecked
    constraints that make such flexibility unsafe at present...
     */
    private List<RegionConfig> getRegionConfigList(long backingSize) {
        int PAGE_SIZE = 1024 * 1024 * 4;

        if (backingSize < PAGE_SIZE) {
            throw new IllegalArgumentException("Minimum heap size " + PAGE_SIZE);
        }

        List<RegionConfig> regionConfigList = new ArrayList<>();
        regionConfigList.add(new RegionConfig(PAGE_SIZE, backingSize));
        regionConfigList.add(new RegionConfig(1024 * 1024, PAGE_SIZE));
        regionConfigList.add(new RegionConfig(512 * 1024, PAGE_SIZE));
        regionConfigList.add(new RegionConfig(256 * 1024, PAGE_SIZE));
        regionConfigList.add(new RegionConfig(128 * 1024, PAGE_SIZE));
        regionConfigList.add(new RegionConfig(64 * 1024, PAGE_SIZE));
        regionConfigList.add(new RegionConfig(32 * 1024, PAGE_SIZE));
        regionConfigList.add(new RegionConfig(16 * 1024, PAGE_SIZE));
        regionConfigList.add(new RegionConfig(8 * 1024, PAGE_SIZE));
        regionConfigList.add(new RegionConfig(4 * 1024, PAGE_SIZE));
        regionConfigList.add(new RegionConfig(2 * 1024, PAGE_SIZE));
        regionConfigList.add(new RegionConfig(1 * 1024, PAGE_SIZE));
        regionConfigList.add(new RegionConfig(512, PAGE_SIZE));
        regionConfigList.add(new RegionConfig(256, PAGE_SIZE));
        regionConfigList.add(new RegionConfig(128, PAGE_SIZE));
        regionConfigList.add(new RegionConfig(64, PAGE_SIZE));
        regionConfigList.add(new RegionConfig(32, PAGE_SIZE));
        regionConfigList.add(new RegionConfig(16, PAGE_SIZE));
        regionConfigList.add(new RegionConfig(8, PAGE_SIZE));
        return regionConfigList;
    }
}
