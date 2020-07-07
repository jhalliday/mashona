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
package com.redhat.mashona.pobj.transaction.logentries;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.nio.ByteBuffer;

/**
 * Persistence helper for (de)serializing BeforeWriteEvent records to a ByteBuffer backed transaction log.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-07
 */
public class BeforeWriteEventPersistence implements LoggableTransactionEventPersistence<BeforeWriteEvent> {

    private static final XLogger logger = XLoggerFactory.getXLogger(BeforeWriteEventPersistence.class);

    public static final long pmemUID = 0xFEFE;

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
    public BeforeWriteEvent readFrom(ByteBuffer byteBuffer) {
        logger.entry(byteBuffer);

        long offset = byteBuffer.getLong();
        long size = byteBuffer.getLong();
        ByteBuffer dataBuffer = ByteBuffer.allocate((int) size);
        dataBuffer.put(byteBuffer.duplicate().limit(byteBuffer.position() + (int) size));
        byteBuffer.position(byteBuffer.position() + (int) size);
        BeforeWriteEvent beforeWriteEvent = new BeforeWriteEvent(offset, size, dataBuffer.rewind());

        logger.exit(beforeWriteEvent);
        return beforeWriteEvent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeInto(BeforeWriteEvent beforeWriteEvent, ByteBuffer byteBuffer) {
        logger.entry(beforeWriteEvent, byteBuffer);

        byteBuffer.putLong(pmemUID);
        byteBuffer.putLong(beforeWriteEvent.getOffset());
        byteBuffer.putLong(beforeWriteEvent.getSize());
        byteBuffer.put(beforeWriteEvent.getByteBuffer().duplicate().rewind());

        logger.exit();
    }
}
