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

import jdk.nio.mapmode.ExtendedMapMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertThrows;

@WithBytemanFrom(source = ExecutionTracer.class)
public class PersistenceHandleTests {

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


    @Test
    public void testConfiguration() throws Exception {

        PersistenceHandle.setParanoid(false);

        new PersistenceHandle(mappedByteBuffer, 0, 0);

        ByteBuffer dupl =  mappedByteBuffer.duplicate();

        new PersistenceHandle(((MappedByteBuffer)dupl), 0, 0);

        PersistenceHandle.setParanoid(true);

        new PersistenceHandle(mappedByteBuffer, 0, 0);

        assertThrows(IllegalArgumentException.class, () -> new PersistenceHandle(((MappedByteBuffer) dupl), 0, 0));
    }

    @Test
    public void testWholeRangePersistence() {

        PersistenceHandle persistenceHandle = new PersistenceHandle(mappedByteBuffer, 128, 128);

        mappedByteBuffer.put(128, new byte[128]);
        persistenceHandle.persist();

        mappedByteBuffer.put(256, new byte[128]);
        persistenceHandle.duplicate(128, 128).persist();
    }

    @Test
    public void testSubRangePersistence() {

        PersistenceHandle persistenceHandle = new PersistenceHandle(mappedByteBuffer, 128, 512);

        mappedByteBuffer.put(128, new byte[256]);
        persistenceHandle.persist(0, 256);

        mappedByteBuffer.put(384, new byte[256]);
        persistenceHandle.duplicate(128, 384).persist(128, 256);
    }
}
