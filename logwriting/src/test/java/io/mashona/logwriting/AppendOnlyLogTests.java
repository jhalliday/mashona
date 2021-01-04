/*
 * Copyright Red Hat
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
package io.mashona.logwriting;

import jdk.nio.mapmode.ExtendedMapMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sun.misc.Unsafe;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.*;

@WithBytemanFrom(source = ExecutionTracer.class)
class AppendOnlyLogTests {

    static Unsafe unsafe;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static File file = new File(System.getenv("PMEM_TEST_DIR"), "test");

    private FileChannel fileChannel;
    private MappedByteBuffer mappedByteBuffer;

    @BeforeEach
    public void setUp() throws IOException {

        if (file.exists()) {
            file.delete();
        }

        fileChannel = (FileChannel) Files
                .newByteChannel(file.toPath(), EnumSet.of(
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE));

        mappedByteBuffer = fileChannel.map(ExtendedMapMode.READ_WRITE_SYNC, 0, 1024);
        forceInvalidation();
    }

    @AfterEach
    public void tearDown() throws IOException {

        // https://bugs.openjdk.java.net/browse/JDK-4724038
        unsafe.invokeCleaner(mappedByteBuffer);

        fileChannel.close();

        if (file.exists()) {
            file.delete();
        }
    }

    private void forceInvalidation() {
        mappedByteBuffer.put(0, (byte) 0); // destroy the header so the log structure won't be recognized/recovered
    }

    @Test
    public void testIteratorExhaustion() {

        AppendOnlyLog appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, false, true);
        appendOnlyLog.put(new byte[10]);

        Iterator<ByteBuffer> iter = appendOnlyLog.iterator();
        assertTrue(iter.hasNext());
        assertNotNull(iter.next());
        assertFalse(iter.hasNext());
        assertThrows(NoSuchElementException.class, iter::next);
    }

    @Test
    public void testIteratorEpoch() {

        AppendOnlyLog appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, false, true);
        appendOnlyLog.put(new byte[10]);

        Iterator<ByteBuffer> iter = appendOnlyLog.iterator();
        assertTrue(iter.hasNext());
        appendOnlyLog.clear();
        assertThrows(ConcurrentModificationException.class, iter::hasNext);
        assertThrows(ConcurrentModificationException.class, iter::next);
    }

    @Test
    public void testIteratorCopying() {

        AppendOnlyLog appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, false, true);
        byte[] data = new byte[]{(byte) 1};
        appendOnlyLog.put(data);

        Iterator<ByteBuffer> viewIterator = appendOnlyLog.iterator();
        assertTrue(viewIterator.hasNext());
        ByteBuffer view = viewIterator.next();
        assertEquals((byte) 1, view.get(0));

        Iterator<ByteBuffer> copyingIterator = appendOnlyLog.copyingIterator();
        assertTrue(copyingIterator.hasNext());
        ByteBuffer copy = copyingIterator.next();
        assertEquals((byte) 1, view.get(0));

        appendOnlyLog.clear();
        assertEquals((byte) 0, view.get(0)); // the log has been cleared, so the view changes
        assertEquals((byte) 1, copy.get(0)); // the log has been cleared, but the independent copy is unchanged
    }

    @Test
    public void testPaddingConfig() {

        AppendOnlyLog appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, false, true);
        assertFalse(appendOnlyLog.isEffectivelyPadded());
        assertFalse(appendOnlyLog.isPaddingRequested());

        // check that settings persist
        appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, false, true);
        assertFalse(appendOnlyLog.isEffectivelyPadded());
        assertFalse(appendOnlyLog.isPaddingRequested());

        appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, true, true);
        assertFalse(appendOnlyLog.isEffectivelyPadded());
        assertTrue(appendOnlyLog.isPaddingRequested());
        appendOnlyLog.clear();
        assertTrue(appendOnlyLog.isEffectivelyPadded());
        assertTrue(appendOnlyLog.isPaddingRequested());

        appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, true, true);
        assertTrue(appendOnlyLog.isEffectivelyPadded());
        assertTrue(appendOnlyLog.isPaddingRequested());
    }

    @Test
    public void testOrderingConfig() {

        AppendOnlyLog appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, false, true);
        assertTrue(appendOnlyLog.isEffectiveLinearOrdering());
        assertTrue(appendOnlyLog.isRequestedLinearOrdering());

        // check that settings persist
        appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, false, true);
        assertTrue(appendOnlyLog.isEffectiveLinearOrdering());
        assertTrue(appendOnlyLog.isRequestedLinearOrdering());

        appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, false, false);
        assertTrue(appendOnlyLog.isEffectiveLinearOrdering());
        assertFalse(appendOnlyLog.isRequestedLinearOrdering());
        appendOnlyLog.clear();
        assertFalse(appendOnlyLog.isEffectiveLinearOrdering());
        assertFalse(appendOnlyLog.isRequestedLinearOrdering());

        appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, false, false);
        assertFalse(appendOnlyLog.isEffectiveLinearOrdering());
        assertFalse(appendOnlyLog.isRequestedLinearOrdering());
    }

    @Test
    public void testCheckpoint() {

        AppendOnlyLog appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, false, true);
        appendOnlyLog.put(new byte[10]);
        appendOnlyLog.checkpoint();
        appendOnlyLog.put(new byte[10]);

        appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, false, true);

        Iterator<ByteBuffer> iter = appendOnlyLog.iterator();
        assertNotNull(iter.next());
        assertNotNull(iter.next());
        assertFalse(iter.hasNext());
    }

    @Test
    public void testChecksumCorruption() throws Exception {

        AppendOnlyLog appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, false, true);
        byte[] data = new byte[]{(byte) 1};
        appendOnlyLog.put(data);

        Iterator<ByteBuffer> iter = appendOnlyLog.iterator();
        assertTrue(iter.hasNext());

        Field f = AppendOnlyLogImpl.class.getDeclaredField("buffer");
        f.setAccessible(true);
        MappedByteBuffer buffer = (MappedByteBuffer) f.get(appendOnlyLog);

        ExecutionTracer.INSTANCE.allowNonFlushingOfDirtyLines = true;
        buffer.putInt(buffer.position() - 4, 0); // overwrite the payload to cause checksum mismatch

        iter = appendOnlyLog.iterator();
        assertFalse(iter.hasNext());
    }

    @Test
    public void testEmptyWrite() {

        AppendOnlyLog appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, false, true);

        int remaining = appendOnlyLog.remaining();
        assertThrows(BufferOverflowException.class, () -> appendOnlyLog.put(new byte[0]));

        assertEquals(remaining, appendOnlyLog.remaining(), "empty write should not have side-effects");
    }

    @Test
    public void testWriteTooLarge() {

        final AppendOnlyLog appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, false, true);
        int remaining = appendOnlyLog.remaining();
        byte[] data = new byte[remaining + 10];

        assertThrows(BufferOverflowException.class, () -> appendOnlyLog.put(data));
        assertFalse(appendOnlyLog.tryPut(data));
        assertEquals(remaining, appendOnlyLog.remaining(), "failed write should not change log content");

        assertThrows(BufferOverflowException.class, () -> appendOnlyLog.put(data, 0, data.length));
        assertFalse(appendOnlyLog.tryPut(data, 0, data.length));
        assertEquals(remaining, appendOnlyLog.remaining(), "failed write should not change log content");

        assertThrows(BufferOverflowException.class, () -> appendOnlyLog.put(ByteBuffer.wrap(data)));
        assertFalse(appendOnlyLog.tryPut(ByteBuffer.wrap(data)));
        assertEquals(remaining, appendOnlyLog.remaining(), "failed write should not change log content");
    }

    @Test
    public void testNonLinearWrite() {
        final AppendOnlyLog appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, false, false);

        byte[] data = new byte[80];  // more than one cache line in size

        appendOnlyLog.put(data);

        Iterator<ByteBuffer> iter = appendOnlyLog.iterator();
        assertEquals(data.length, iter.next().remaining());
    }

    @Test
    public void testNonLinearReadSkip() throws Exception {
        final AppendOnlyLog appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, false, false);

        Field f = AppendOnlyLogImpl.class.getDeclaredField("buffer");
        f.setAccessible(true);
        MappedByteBuffer buffer = (MappedByteBuffer) f.get(appendOnlyLog);
        ExecutionTracer.INSTANCE.allowNonFlushingOfDirtyLines = true;

        byte[] record1 = new byte[]{(byte) 1};
        byte[] record2 = new byte[]{(byte) 2, (byte) 2};
        byte[] record3 = new byte[]{(byte) 3, (byte) 3, (byte) 3};

        appendOnlyLog.put(record1);
        // corrupt the record to simulate failure to flush.
        buffer.putInt(buffer.position() - 4, 0);

        appendOnlyLog.put(record2);
        // corrupt the record to simulate failure to flush.
        buffer.putInt(buffer.position() - 4, 0);

        appendOnlyLog.put(record3);

        Iterator<ByteBuffer> iter = appendOnlyLog.iterator();
        // should skip the two corrupted ones and return the third
        assertEquals(record3.length, iter.next().remaining());
    }

    @Test
    public void testPadding() {

        final AppendOnlyLog appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, false, true);
        assertTrue(appendOnlyLog.canAccept(appendOnlyLog.remaining() - 8));

        forceInvalidation();

        final AppendOnlyLog paddedAppendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, true, true);
        assertFalse(paddedAppendOnlyLog.canAccept(paddedAppendOnlyLog.remaining() - 10));
    }


    @Test
    public void testRecovery() {

        AppendOnlyLog appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, false, true);

        int numEntriesWritten = 0;
        int i = 10;
        while (appendOnlyLog.canAccept(i)) {
            byte[] data = new byte[i];
            for (int j = 0; j < data.length; j++) {
                data[j] = (byte) i;
            }
            appendOnlyLog.put(data);
            i++;
            numEntriesWritten++;
        }

        appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, false, true);

        int numEntriesRead = 0;
        Iterator<ByteBuffer> iter = appendOnlyLog.iterator();
        int expectedX = 10;
        while (iter.hasNext()) {
            ByteBuffer x = iter.next();

            if (expectedX != x.limit()) {
                throw new IllegalStateException();
            }

            for (i = 0; i < expectedX; i++) {
                if (x.get(i) != (byte) expectedX) {
                    throw new IllegalStateException();
                }
            }
            numEntriesRead++;
            expectedX++;
        }

        assertEquals(numEntriesWritten, numEntriesRead);
    }

    @ParameterizedTest
    // sadly JUnit5 doesn't have a CombinatorialSource...
    @CsvSource({
            "full_array, false, false",
            "full_array, true, false",
            "full_array, false, true",
            "full_array, true, true",

            "slice_array, false, false",
            "slice_array, true, false",
            "slice_array, false, true",
            "slice_array, true, true",

            "ByteBuffer, false, false",
            "ByteBuffer, true, false",
            "ByteBuffer, false, true",
            "ByteBuffer, true, true",

            "try_full_array, false, false",
            "try_full_array, true, false",
            "try_full_array, false, true",
            "try_full_array, true, true",

            "try_slice_array, false, false",
            "try_slice_array, true, false",
            "try_slice_array, false, true",
            "try_slice_array, true, true",

            "try_ByteBuffer, false, false",
            "try_ByteBuffer, true, false",
            "try_ByteBuffer, false, true",
            "try_ByteBuffer, true, true"
    })
    public void testWriteAndReadBack(String put, boolean padding, boolean linear) {

        AppendOnlyLog appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, padding, linear);

        if(padding) {
            // in padded mode, the log will try to flush the unused padding range. The hw will elide that, since
            // it knows the lines are clean. But our test harness will complain unless we suppress it...
            ExecutionTracer.INSTANCE.allowFlushingOfCleanLines = true;
        }

        int numEntriesWritten = 0;
        int i = 1;
        while (appendOnlyLog.canAccept(i)) {
            byte[] data = new byte[i];
            for (int j = 0; j < data.length; j++) {
                data[j] = (byte) i;
            }

            switch (put) {
                case "full_array":
                    appendOnlyLog.put(data);
                    break;
                case "try_full_array":
                    assertTrue( appendOnlyLog.tryPut(data) );
                    break;
                case "slice_array":
                    appendOnlyLog.put(data, 0, data.length);
                    break;
                case "try_slice_array":
                    assertTrue( appendOnlyLog.tryPut(data, 0, data.length) );
                    break;
                case "ByteBuffer":
                    appendOnlyLog.put(ByteBuffer.wrap(data));
                    break;
                case "try_ByteBuffer":
                    assertTrue( appendOnlyLog.tryPut(ByteBuffer.wrap(data)) );
                    break;
            }

            i++;
            numEntriesWritten++;
        }

        int numEntriesRead = 0;
        Iterator<ByteBuffer> iter = appendOnlyLog.iterator();
        int expectedX = 1;
        while (iter.hasNext()) {
            ByteBuffer x = iter.next();

            if (expectedX != x.limit()) {
                throw new IllegalStateException();
            }

            for (i = 0; i < expectedX; i++) {
                if (x.get(i) != (byte) expectedX) {
                    throw new IllegalStateException();
                }
            }
            numEntriesRead++;
            expectedX++;
        }

        assertEquals(numEntriesWritten, numEntriesRead);
    }


    @ParameterizedTest
    // sadly JUnit5 doesn't have a CombinatorialSource...
    @CsvSource({
            "full_array, false, false",
            "full_array, true, false",
            "full_array, false, true",
            "full_array, true, true",

            "slice_array, false, false",
            "slice_array, true, false",
            "slice_array, false, true",
            "slice_array, true, true",

            "ByteBuffer, false, false",
            "ByteBuffer, true, false",
            "ByteBuffer, false, true",
            "ByteBuffer, true, true",

            "try_full_array, false, false",
            "try_full_array, true, false",
            "try_full_array, false, true",
            "try_full_array, true, true",

            "try_slice_array, false, false",
            "try_slice_array, true, false",
            "try_slice_array, false, true",
            "try_slice_array, true, true",

            "try_ByteBuffer, false, false",
            "try_ByteBuffer, true, false",
            "try_ByteBuffer, false, true",
            "try_ByteBuffer, true, true"

    })
    public void testWriteAndReadBackWithLocation(String put, boolean padding, boolean linear) {

        AppendOnlyLogWithLocation appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, 1024, padding, linear);

        if(padding) {
            // in padded mode, the log will try to flush the unused padding range. The hw will elide that, since
            // it knows the lines are clean. But our test harness will complain unless we suppress it...
            ExecutionTracer.INSTANCE.allowFlushingOfCleanLines = true;
        }

        int numEntriesWritten = 0;
        List<Integer> locations = new LinkedList<>();
        int i = 1;
        while (appendOnlyLog.canAccept(i)) {
            byte[] data = new byte[i];
            for (int j = 0; j < data.length; j++) {
                data[j] = (byte) i;
            }

            int location = -1;
            switch (put) {
                case "full_array":
                    location = appendOnlyLog.putWithLocation(data);
                    break;
                case "try_full_array":
                    location = appendOnlyLog.tryPutWithLocation(data);
                    break;
                case "slice_array":
                    location = appendOnlyLog.putWithLocation(data, 0, data.length);
                    break;
                case "try_slice_array":
                    location = appendOnlyLog.tryPutWithLocation(data, 0, data.length);
                    break;
                case "ByteBuffer":
                    location = appendOnlyLog.putWithLocation(ByteBuffer.wrap(data));
                    break;
                case "try_ByteBuffer":
                    location = appendOnlyLog.tryPutWithLocation(ByteBuffer.wrap(data));
                    break;
            }

            assertNotEquals(AppendOnlyLogImpl.ERROR_LOCATION, location);

            i++;
            numEntriesWritten++;
            locations.add(location);
        }

        assertEquals(numEntriesWritten, locations.size());

        int numEntriesRead = 0;
        Iterator<ByteBuffer> iter = appendOnlyLog.iterator();
        int expectedX = 1;
        while (iter.hasNext()) {
            ByteBuffer x = iter.next();

            if (expectedX != x.limit()) {
                throw new IllegalStateException();
            }

            for (i = 0; i < expectedX; i++) {
                if (x.get(i) != (byte) expectedX) {
                    throw new IllegalStateException();
                }
            }

            ByteBuffer y = appendOnlyLog.readRecordAt(locations.get(numEntriesRead));
            assertEquals(expectedX, y.limit());
            for (i = 0; i < expectedX; i++) {
                if (y.get(i) != (byte) expectedX) {
                    throw new IllegalStateException();
                }
            }

            numEntriesRead++;
            expectedX++;
        }

        assertEquals(numEntriesWritten, numEntriesRead);
    }
}