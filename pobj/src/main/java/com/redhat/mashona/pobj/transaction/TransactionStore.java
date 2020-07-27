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
package com.redhat.mashona.pobj.transaction;

import com.redhat.mashona.logwriting.AppendOnlyLog;
import com.redhat.mashona.logwriting.AppendOnlyLogImpl;
import com.redhat.mashona.pobj.transaction.events.*;
import jdk.nio.mapmode.ExtendedMapMode;
import sun.misc.Unsafe;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;

/**
 * Manages a region of memory, mapped from a file, as a persistent storage space for transaction state.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-07
 */
public class TransactionStore implements Iterable<TransactionEvent> {

    private static Unsafe unsafe;

    static {
        // ugliness required for clone, until the JDK's unmapping behavior is fixed.
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final MallocEventPersistence MALLOC_EVENT_PERSISTENCE = new MallocEventPersistence();
    private static final BeforeWriteEventPersistence BEFORE_WRITE_EVENT_PERSISTENCE = new BeforeWriteEventPersistence();
    private static final OutcomeEventPersistence OUTCOME_EVENT_PERSISTENCE = new OutcomeEventPersistence();
    private static final DeallocateEventPersistence DELETE_EVENT_PERSISTENCE = new DeallocateEventPersistence();

    private final File file;
    private final FileChannel fileChannel;
    private final MappedByteBuffer mappedByteBuffer;
    private final AppendOnlyLog appendOnlyLog;

    /**
     * Create a new transaction persistence facility, backed by a memory-mapped region of the given file.
     *
     * @param file The file to be used for persistence.
     * @param length The size, in bytes, of the memory mapped region over the file.
     * @throws IOException if the mapping cannot be established on the given file.
     */
    public TransactionStore(File file, int length) throws IOException {
        this.file = file;

        fileChannel = (FileChannel) Files
                .newByteChannel(file.toPath(), EnumSet.of(
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE));

        mappedByteBuffer = fileChannel.map(ExtendedMapMode.READ_WRITE_SYNC, 0, length);
        appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, length, true, true);
    }

    /**
     * Close (unmap) the store.
     *
     * @throws IOException if shutdown fails.
     */
    public synchronized void close() throws IOException {

        // https://bugs.openjdk.java.net/browse/JDK-4724038
        unsafe.invokeCleaner(mappedByteBuffer);

        fileChannel.close();
    }

    protected void persistMemoryAllocation(MallocEvent mallocEvent) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(50);
        MALLOC_EVENT_PERSISTENCE.writeInto(mallocEvent, byteBuffer);
        byteBuffer.rewind();
        appendOnlyLog.put(byteBuffer);
    }

    protected void persistMemoryDelete(DeallocateEvent deallocateEvent) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(50);
        DELETE_EVENT_PERSISTENCE.writeInto(deallocateEvent, byteBuffer);
        byteBuffer.rewind();
        appendOnlyLog.put(byteBuffer);
    }

    protected void persistBeforeWrite(BeforeWriteEvent beforeWriteEvent) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(50);
        BEFORE_WRITE_EVENT_PERSISTENCE.writeInto(beforeWriteEvent, byteBuffer);
        byteBuffer.rewind();
        appendOnlyLog.put(byteBuffer);
    }

    protected void persistOutcomeEvent(OutcomeEvent outcomeTxEntry) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(50);
        OUTCOME_EVENT_PERSISTENCE.writeInto(outcomeTxEntry, byteBuffer);
        byteBuffer.rewind();
        appendOnlyLog.put(byteBuffer);
    }

    @Override
    public Iterator<TransactionEvent> iterator() {
        Iterator<TransactionEvent> itr = new Itr(appendOnlyLog.iterator());
        return itr;
    }

    protected List<PersistentTransaction> read() {
        List<PersistentTransaction> result = new ArrayList<>();
        List<TransactionEvent> events = new ArrayList<>();
        Iterator<TransactionEvent> iterator = iterator();
        while (iterator.hasNext()) {
            TransactionEvent transactionEvent = iterator.next();
            events.add(transactionEvent);
            if (transactionEvent instanceof OutcomeEvent) {
                PersistentTransaction persistentTransaction = new PersistentTransaction(this);
                persistentTransaction.events.addAll(events);
                result.add(persistentTransaction);
                events.clear();
            }
        }
        if (!events.isEmpty()) {
            PersistentTransaction persistentTransaction = new PersistentTransaction(this);
            persistentTransaction.events.addAll(events);
            result.add(persistentTransaction);
        }
        return result;
    }

    private class Itr implements Iterator<TransactionEvent> {

        private final Iterator<ByteBuffer> logIterator;

        public Itr(Iterator<ByteBuffer> logIterator) {
            this.logIterator = logIterator;
        }

        @Override
        public boolean hasNext() {
            return logIterator.hasNext();
        }

        @Override
        public TransactionEvent next() {

            ByteBuffer byteBuffer = logIterator.next();
            long pmemId = byteBuffer.getLong();
            if (pmemId == MALLOC_EVENT_PERSISTENCE.getFormatId()) {
                MallocEvent mallocTxEntry = MALLOC_EVENT_PERSISTENCE.readFrom(byteBuffer);
                return mallocTxEntry;
            } else if (pmemId == BEFORE_WRITE_EVENT_PERSISTENCE.getFormatId()) {
                BeforeWriteEvent preimageTxEntry = BEFORE_WRITE_EVENT_PERSISTENCE.readFrom(byteBuffer);
                return preimageTxEntry;
            } else if (pmemId == OUTCOME_EVENT_PERSISTENCE.getFormatId()) {
                OutcomeEvent outcomeTxEntry = OUTCOME_EVENT_PERSISTENCE.readFrom(byteBuffer);
                return outcomeTxEntry;
            } else if (pmemId == DELETE_EVENT_PERSISTENCE.getFormatId()) {
                DeallocateEvent deleteTxEntry = DELETE_EVENT_PERSISTENCE.readFrom(byteBuffer);
                return deleteTxEntry;
            } else {
                return null;
            }
        }
    }
}
