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

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for various LoggableTransactionEvent types.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-07
 */
public class TransactionEventTests {

    @Test
    public void testMallocEvent() {
        MallocEvent mallocEvent = new MallocEvent(100, 200, true);
        assertEquals(100, mallocEvent.getOffset());
        assertEquals(200, mallocEvent.getSize());
        assertTrue(mallocEvent.isForInternalUse());
    }

    @Test
    public void testDeleteEvent() {
        DeleteEvent deleteEvent = new DeleteEvent(100, 200);
        assertEquals(100, deleteEvent.getOffset());
        assertEquals(200, deleteEvent.getSize());
    }

    @Test
    public void testBeforeWriteEvent() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(16);
        byteBuffer.putLong(Long.MIN_VALUE);
        byteBuffer.putLong(Long.MIN_VALUE);
        BeforeWriteEvent beforeWriteEvent = new BeforeWriteEvent(100, 16, byteBuffer);
        assertEquals(100, beforeWriteEvent.getOffset());
        assertEquals(16, beforeWriteEvent.getSize());
        assertEquals(byteBuffer, beforeWriteEvent.getByteBuffer());
    }

    @Test
    public void testOutcomeEvent() {
        OutcomeEvent outcomeEvent = new OutcomeEvent(true);
        assertTrue(outcomeEvent.isCommit());
    }
}
