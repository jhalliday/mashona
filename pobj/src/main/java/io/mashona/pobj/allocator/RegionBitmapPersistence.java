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

/**
 * Persists a RegionBitmap, such that its state can be read/written to ByteBuffer.
 * <p>
 * Instances of this class are stateless, but persistence is NOT threadsafe:
 * Snapshotting (i.e. 'serializing') a RegionBitmap whilst it is servicing memory requests will have undefined results.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-06
 */
public class RegionBitmapPersistence implements PersistenceProvider<RegionBitmap> {

    private static final XLogger logger = XLoggerFactory.getXLogger(RegionBitmapPersistence.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeInto(RegionBitmap regionBitmap, ByteBuffer byteBuffer) {
        logger.entry(byteBuffer);

        byteBuffer.putLong(regionBitmap.baseAddress);
        byteBuffer.putLong(regionBitmap.backingSize);
        byteBuffer.putLong(regionBitmap.elementSize);
        byteBuffer.asLongBuffer().put(regionBitmap.bitmap);
        byteBuffer.position(byteBuffer.position() + (regionBitmap.bitmapLength * Long.BYTES));
        byteBuffer.putInt(regionBitmap.numAvail);
        byteBuffer.putInt(regionBitmap.nextAvail);

        logger.exit();
    }

    /**
     * {@inheritD
     */
    @Override
    public RegionBitmap readFrom(ByteBuffer byteBuffer) {
        logger.entry();

        long baseAddress = byteBuffer.getLong();
        long backingSize = byteBuffer.getLong();
        long elementSize = byteBuffer.getLong();

        RegionBitmap instance = new RegionBitmap(baseAddress, new RegionConfig(elementSize, backingSize));

        byteBuffer.asLongBuffer().get(instance.bitmap);
        byteBuffer.position(byteBuffer.position() + (instance.bitmapLength * Long.BYTES));
        instance.numAvail = byteBuffer.getInt();
        instance.nextAvail = byteBuffer.getInt();

        logger.exit(instance);
        return instance;
    }
}
