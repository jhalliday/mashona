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
package io.mashona.pobj.transaction.events;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.nio.ByteBuffer;

/**
 * Persistence helper for (de)serializing memory allocation MallocEvent records in a ByteBuffer backed transaction log.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-07
 */
public class MallocEventPersistence implements TransactionEventPersistence<MallocEvent> {

    private static final XLogger logger = XLoggerFactory.getXLogger(MallocEventPersistence.class);

    public static final long pmemUID = 0xFFFF;

    /**
     * {@inheritDoc}
     */
    @Override
    public long getFormatId() {
        return pmemUID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MallocEvent readFrom(ByteBuffer byteBuffer) {
        logger.entry(byteBuffer);

        long offset = byteBuffer.getLong();
        long size = byteBuffer.getLong();
        int forInternalUse = byteBuffer.getInt();
        MallocEvent mallocEvent = new MallocEvent(offset, size, forInternalUse == 1);

        logger.exit(mallocEvent);
        return mallocEvent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeInto(MallocEvent mallocEvent, ByteBuffer byteBuffer) {
        logger.entry(mallocEvent, byteBuffer);

        byteBuffer.putLong(pmemUID);
        byteBuffer.putLong(mallocEvent.getOffset());
        byteBuffer.putLong(mallocEvent.getSize());
        byteBuffer.putInt(mallocEvent.isForInternalUse() ? 1 : 0);

        logger.exit();
    }
}
