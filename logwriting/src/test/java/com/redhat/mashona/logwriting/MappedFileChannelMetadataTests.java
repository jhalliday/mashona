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
package com.redhat.mashona.logwriting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MappedFileChannelMetadataTests {

    private static File file = new File(System.getenv("PMEM_TEST_DIR"), "testmeta");

    private MappedFileChannelMetadata mappedFileChannelMetadata;

    @BeforeEach
    public void setUp() throws IOException {

        if (file.exists()) {
            file.delete();
        }
    }

    @AfterEach
    public void tearDown() throws IOException {
        if(mappedFileChannelMetadata != null) {
            mappedFileChannelMetadata.close();
        }

        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    public void testInitializationFailure() throws IOException {

        IOException e = assertThrows(IOException.class, () -> new MappedFileChannelMetadata(new File("/tmp/bogus")));
        assertEquals("Operation not supported", e.getMessage());
    }

    @Test
    public void testInitialization() throws IOException {

        mappedFileChannelMetadata = new MappedFileChannelMetadata(file);
        assertEquals(0, mappedFileChannelMetadata.getPersistenceIndex());
    }

    @ParameterizedTest
    @CsvSource({"false", "true"})
    public void testWriteAndReadBack(boolean recover) throws IOException {

        mappedFileChannelMetadata = new MappedFileChannelMetadata(file);
        mappedFileChannelMetadata.persist(0, Integer.MAX_VALUE);

        if(recover) {
            mappedFileChannelMetadata.close();
            mappedFileChannelMetadata = new MappedFileChannelMetadata(file);
        }

        assertEquals(Integer.MAX_VALUE, mappedFileChannelMetadata.getPersistenceIndex());
    }

    @Test
    public void testExceptionWhenClosed() throws IOException {

        mappedFileChannelMetadata = new MappedFileChannelMetadata(file);
        mappedFileChannelMetadata.close();

        assertThrows(ClosedChannelException.class, () -> mappedFileChannelMetadata.persist(0, 1));
        assertThrows(ClosedChannelException.class, () -> mappedFileChannelMetadata.clear());
        assertThrows(ClosedChannelException.class, () -> mappedFileChannelMetadata.getPersistenceIndex());
    }

    @Test
    public void testReadSharing() throws IOException {

        mappedFileChannelMetadata = new MappedFileChannelMetadata(file);
        assertEquals(0, mappedFileChannelMetadata.getPersistenceIndex());
        MappedFileChannelMetadata readFollower = new MappedFileChannelMetadata(file, true);
        assertEquals(0, readFollower.getPersistenceIndex());

        mappedFileChannelMetadata.persist(0, 10);
        assertEquals(10, mappedFileChannelMetadata.getPersistenceIndex());
        assertEquals(10, readFollower.getPersistenceIndex());

        readFollower.persist(10, 10);
        assertEquals(10, mappedFileChannelMetadata.getPersistenceIndex());
        assertEquals(20, readFollower.getPersistenceIndex());
    }

}
