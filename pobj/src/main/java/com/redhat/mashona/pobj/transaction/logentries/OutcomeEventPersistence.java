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
 * Persistence helper for (de)serializing terminal commit/rollback decision OutcomeEvent records in a ByteBuffer backed transaction log.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-07
 */
public class OutcomeEventPersistence implements LoggableTransactionEventPersistence<OutcomeEvent> {

    private static final XLogger logger = XLoggerFactory.getXLogger(OutcomeEventPersistence.class);

    public static final long pmemUID = 0xFDFD;

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
    public OutcomeEvent readFrom(ByteBuffer byteBuffer) {
        logger.entry(byteBuffer);

        int commit = byteBuffer.getInt();
        OutcomeEvent outcomeEvent = new OutcomeEvent(commit == 1);

        logger.exit(outcomeEvent);
        return outcomeEvent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeInto(OutcomeEvent outcomeEvent, ByteBuffer byteBuffer) {
        logger.entry(outcomeEvent, byteBuffer);

        byteBuffer.putLong(pmemUID);
        byteBuffer.putInt(outcomeEvent.commit ? 1 : 0);

        logger.exit();
    }
}
