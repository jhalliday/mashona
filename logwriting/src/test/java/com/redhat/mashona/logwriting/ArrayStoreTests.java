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
package com.redhat.mashona.logwriting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class ArrayStoreTests {

    private static final File file = new File(System.getenv("PMEM_TEST_DIR"), "test");
    private static final int NUMBER_OF_SLOTS = 100;
    private static final int SLOT_DATA_CAPACITY = 96;

    private ArrayStore arrayStore;

    @BeforeEach
    public void setUp() throws IOException {
        if (file.exists()) {
            file.delete();
        }

        arrayStore = new ArrayStoreImpl(file, NUMBER_OF_SLOTS, SLOT_DATA_CAPACITY);
    }

    @AfterEach
    public void tearDown() {
        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    public void testConfigGetters() {
        assertEquals(NUMBER_OF_SLOTS, ((ArrayStoreImpl) arrayStore).getNumberOfSlots());
        assertEquals(SLOT_DATA_CAPACITY, ((ArrayStoreImpl) arrayStore).getSlotDataCapacity());
    }

    @ParameterizedTest
    @CsvSource({
            "byte[], byte[]",
            "ByteBuffer, byte[]",
            "byte[], ByteBuffer",
            "ByteBuffer, ByteBuffer"
    })
    public void testWriteAndReadBack(String readMethod, String writeMethod) throws IOException {

        for (int i = 0; i < NUMBER_OF_SLOTS; i++) {
            // write at least one byte and no more than slot size bytes, preferring to write exactly i bytes.
            int length = Math.max(1, Math.min(i, SLOT_DATA_CAPACITY));
            byte[] data = new byte[length];
            Arrays.fill(data, (byte) i);

            switch (writeMethod) {
                case "byte[]":
                    arrayStore.write(i, data);
                case "ByteBuffer":
                    arrayStore.write(i, ByteBuffer.wrap(data));
            }
        }

        for (int i = 0; i < NUMBER_OF_SLOTS; i++) {

            byte[] data = null;
            switch (readMethod) {
                case "byte[]":
                    data = arrayStore.readAsByteArray(i);
                case "ByteBuffer":
                    ByteBuffer byteBuffer = arrayStore.readAsByteBuffer(i);
                    data = byteBuffer.array(); // implementation specific shortcut.
            }

            int expectedLength = Math.max(1, Math.min(i, SLOT_DATA_CAPACITY));
            assertEquals(expectedLength, data.length);
            for (int j = 0; j < expectedLength; j++) {
                assertEquals(i, data[j]);
            }
        }
    }

    @Test
    public void testClear() throws IOException {

        byte[] data = new byte[]{1};
        arrayStore.write(0, data);
        byte[] read = arrayStore.readAsByteArray(0);
        assertArrayEquals(data, read);

        arrayStore.clear(0, false);
        assertNull(arrayStore.readAsByteArray(0));
        assertNull(arrayStore.readAsByteBuffer(0));

        arrayStore.write(0, data);
        read = arrayStore.readAsByteArray(0);
        assertArrayEquals(data, read);

        arrayStore.clear(0, true);
        assertNull(arrayStore.readAsByteArray(0));
        assertNull(arrayStore.readAsByteBuffer(0));
    }

    @Test
    public void testZeroLengthWrite() throws IOException {
        assertNull(arrayStore.readAsByteArray(0));
        arrayStore.write(0, new byte[0]);
        assertNull(arrayStore.readAsByteArray(0));
    }

    @Test
    public void testWriteTooLong() throws IOException {
        assertThrows(IOException.class, () -> arrayStore.write(0, new byte[SLOT_DATA_CAPACITY + 1]));
    }

    @Test
    public void testIndexOutOfBounds() throws IOException {
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> arrayStore.readAsByteArray(-1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> arrayStore.readAsByteArray(NUMBER_OF_SLOTS));
    }
}
