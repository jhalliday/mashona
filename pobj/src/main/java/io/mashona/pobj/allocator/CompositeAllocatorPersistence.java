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

import io.mashona.pobj.PersistenceProvider;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Persists a CompositeAllocator, such that its state can be read/written to ByteBuffer.
 * <p>
 * Instances of this class are stateless, but persistence is NOT threadsafe:
 * Snapshotting (i.e. 'serializing') a CompositeAllocator whilst it is servicing memory requests will have undefined results.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-06
 */
public class CompositeAllocatorPersistence implements PersistenceProvider<CompositeAllocator> {

    private static final XLogger logger = XLoggerFactory.getXLogger(CompositeAllocatorPersistence.class);

    private static final RegionBitmapPersistence REGION_BITMAP_PERSISTENCE = new RegionBitmapPersistence();

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeInto(CompositeAllocator compositeAllocator, ByteBuffer byteBuffer) {
        logger.entry(byteBuffer);

        byteBuffer.putLong(compositeAllocator.baseAddress);
        byteBuffer.putLong(compositeAllocator.backingSize);
        for (List<RegionBitmap> list : compositeAllocator.regionBitmaps) {
            byteBuffer.putInt(list.size());
            for (RegionBitmap regionBitmap : list) {
                REGION_BITMAP_PERSISTENCE.writeInto(regionBitmap, byteBuffer);
            }
        }

        logger.exit();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CompositeAllocator readFrom(ByteBuffer byteBuffer) {
        logger.entry(byteBuffer);

        long baseAddress = byteBuffer.getLong();
        long backingSize = byteBuffer.getLong();

        CompositeAllocator instance = new CompositeAllocator(baseAddress, backingSize);
        instance.regionBitmaps[instance.regionConfigList.size() - 1].clear();

        for (int i = 0; i < instance.regionBitmaps.length; i++) {
            int length = byteBuffer.getInt();
            for (int j = 0; j < length; j++) {
                RegionBitmap regionBitmap = REGION_BITMAP_PERSISTENCE.readFrom(byteBuffer);
                instance.regionBitmaps[i].add(regionBitmap);
            }
        }

        logger.exit(instance);
        return instance;
    }
}
