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
 * Persistence helper for (de)serializing memory release (free) DeleteEvent records in a ByteBuffer backed transaction log.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-07
 */
public class DeleteEventPersistence implements LoggableTransactionEventPersistence<DeleteEvent> {

    private static final XLogger logger = XLoggerFactory.getXLogger(DeleteEventPersistence.class);

    public static final long pmemUID = 0xFCFC;

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
    public DeleteEvent readFrom(ByteBuffer byteBuffer) {
        logger.entry(byteBuffer);

        long offset = byteBuffer.getLong();
        long size = byteBuffer.getLong();
        DeleteEvent deleteEvent = new DeleteEvent(offset, size);

        logger.exit(deleteEvent);
        return deleteEvent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeInto(DeleteEvent deleteEvent, ByteBuffer byteBuffer) {
        logger.entry(deleteEvent, byteBuffer);

        byteBuffer.putLong(pmemUID);
        byteBuffer.putLong(deleteEvent.getOffset());
        byteBuffer.putLong(deleteEvent.getSize());

        logger.exit();
    }
}
