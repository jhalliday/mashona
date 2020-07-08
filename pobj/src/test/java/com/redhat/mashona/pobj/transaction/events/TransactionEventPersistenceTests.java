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
package com.redhat.mashona.pobj.transaction.events;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for various persistence helper classes responsible for (de)serializing LoggableTransactionEvent types.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-07
 */
public class TransactionEventPersistenceTests {

    @Test
    public void testMallocEventPersistence() {
        MallocEvent before = new MallocEvent(100, 200, true);
        MallocEventPersistence persistence = new MallocEventPersistence();

        ByteBuffer byteBuffer = ByteBuffer.allocate(50);
        persistence.writeInto(before, byteBuffer);
        byteBuffer.rewind();

        assertEquals(persistence.getFormatId(), byteBuffer.getLong());
        MallocEvent after = persistence.readFrom(byteBuffer);

        assertEquals(before.getOffset(), after.getOffset());
        assertEquals(before.getSize(), after.getSize());
        assertEquals(before.isForInternalUse(), after.isForInternalUse());
    }

    @Test
    public void testDeleteEventPersistence() {
        DeallocateEvent before = new DeallocateEvent(100, 200);
        DeallocateEventPersistence persistence = new DeallocateEventPersistence();

        ByteBuffer byteBuffer = ByteBuffer.allocate(50);
        persistence.writeInto(before, byteBuffer);
        byteBuffer.rewind();

        assertEquals(persistence.getFormatId(), byteBuffer.getLong());
        DeallocateEvent after = persistence.readFrom(byteBuffer);

        assertEquals(before.getOffset(), after.getOffset());
        assertEquals(before.getSize(), after.getSize());
    }

    @Test
    public void testBeforeWriteEventPersistence() {
        ByteBuffer payloadBuffer = ByteBuffer.allocate(16);
        payloadBuffer.putLong(Long.MIN_VALUE);
        payloadBuffer.putLong(Long.MAX_VALUE);
        payloadBuffer.rewind();
        BeforeWriteEvent before = new BeforeWriteEvent(100, 16, payloadBuffer);
        BeforeWriteEventPersistence persistence = new BeforeWriteEventPersistence();

        ByteBuffer persistenceBuffer = ByteBuffer.allocate(50);
        persistence.writeInto(before, persistenceBuffer);
        persistenceBuffer.rewind();

        assertEquals(persistence.getFormatId(), persistenceBuffer.getLong());
        BeforeWriteEvent after = persistence.readFrom(persistenceBuffer);

        assertEquals(before.getOffset(), after.getOffset());
        assertEquals(before.getSize(), after.getSize());

        ByteBuffer afterPayloadBuffer = after.getByteBuffer();
        assertEquals(Long.MIN_VALUE, afterPayloadBuffer.getLong());
        assertEquals(Long.MAX_VALUE, afterPayloadBuffer.getLong());
    }

    @Test
    public void testOutcomeEventPersistence() {
        OutcomeEvent before = new OutcomeEvent(true);
        OutcomeEventPersistence persistence = new OutcomeEventPersistence();

        ByteBuffer byteBuffer = ByteBuffer.allocate(50);
        persistence.writeInto(before, byteBuffer);
        byteBuffer.rewind();

        assertEquals(persistence.getFormatId(), byteBuffer.getLong());
        OutcomeEvent after = persistence.readFrom(byteBuffer);

        assertEquals(before.isCommit(), after.isCommit());
    }
}
