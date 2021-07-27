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

import org.jboss.logging.Logger;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.channels.FileChannel;

/**
 * Utility functions for accessing pmem abstractions.
 * This class is Java 8 compatible, though the rest of the library is not.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2019-06
 */
public class PmemUtil {

    private static final Logger logger = Logger.getLogger(PmemUtil.class);

    // most classes are built for jdk 17, whereas this class is built for 8,
    // so we want to avoid hard linking or we'll get headaches.
    private static final Constructor mappedFileChannelConstructor;
    private static final Constructor mappedFileChannelMetadataConstructor;

    private static final Constructor arrayStoreConstructor;

    static {

        Constructor fccTmp = null;
        Constructor fccmdTmp = null;
        Constructor asTmp = null;
        try {
            String specVersion = System.getProperty("java.specification.version");
            if (!specVersion.contains(".") && Integer.parseInt(specVersion) >= 14) {
                fccTmp = Class.forName("io.mashona.logwriting.MappedFileChannel").getDeclaredConstructor(File.class, int.class, boolean.class);
                fccmdTmp = Class.forName("io.mashona.logwriting.MappedFileChannelMetadata").getDeclaredConstructor(File.class);
                asTmp = Class.forName("io.mashona.logwriting.ArrayStoreImpl").getDeclaredConstructor(File.class, int.class, int.class);
            }
        } catch (Exception e) {
            logger.debug("Can't wire constructor functions", e);
        }
        mappedFileChannelConstructor = fccTmp;
        mappedFileChannelMetadataConstructor = fccmdTmp;
        arrayStoreConstructor = asTmp;
    }

    public static void main(String[] args) {
        File dir = new File(args[0]);
        System.out.println(dir.getAbsolutePath() + ": pmem is " + PmemUtil.isPmemSupportedFor(dir));
    }

    /**
     * Validate if it's possible to use pmem via a DAX mmap for the given path.
     *
     * <p>Note that pmemChannelFor implicitly makes this check, so direct calls are not normally required.</p>
     *
     * <p>This method checks that
     * a) the JVM is recent enough to have pmem support
     * b) the directory is on a DAX mode file system mount
     * </p>
     *
     * <p>Unfortunately due to API limitations the only way perform the latter check to to try a mmap and see if it fails.
     * There is a nasty side effect of that - the file is expanded to the desired mmap size BEFORE the mmap is done,
     * so if the map fails it needs to be shrunk back. BUT it can't be reliably locked during that window, which can
     * cause race conditions. Therefore we perform the check using a temporary file in the same directory instead.</p>
     *
     * @param dir The directory to validate. A temporary file will be created in this directory.
     * @return true if pmem is available at the given location, false otherwise
     */
    public static synchronized boolean isPmemSupportedFor(File dir) {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry with dir={0}", dir.getAbsolutePath());
        }

        if (mappedFileChannelConstructor == null) {
            if(logger.isTraceEnabled()) {
                logger.tracev("exit returning false");
            }
            return false;
        }

        if (!dir.exists()) {
            IllegalArgumentException illegalArgumentException =
                    new IllegalArgumentException("The directory " + dir.getAbsolutePath() + " must exist");
            if(logger.isTraceEnabled()) {
                logger.tracev(illegalArgumentException,"throwing {0}", illegalArgumentException.toString());
            }
            throw illegalArgumentException;
        }
        if (!dir.isDirectory()) {
            IllegalArgumentException illegalArgumentException =
                    new IllegalArgumentException(dir.getAbsolutePath() + " must be a directory");
            if(logger.isTraceEnabled()) {
                logger.tracev(illegalArgumentException,"throwing {0}", illegalArgumentException.toString());
            }
            throw illegalArgumentException;
        }

        File testFile = null;
        try {
            testFile = File.createTempFile("isPmemSupportedFor", "", dir);

            Closeable mappedFileChannelMetadata = (Closeable) mappedFileChannelMetadataConstructor.newInstance(testFile);
            mappedFileChannelMetadata.close();

            if(logger.isTraceEnabled()) {
                logger.tracev("exit returning true");
            }
            return true;
        } catch (Exception e) {
            if(logger.isDebugEnabled()) {
                logger.debugv(e, "mmap failed for path {0}", dir.getAbsolutePath()); // TODO be more specific
            }
            if(logger.isTraceEnabled()) {
                logger.tracev("exit returning false");
            }
            return false;
        } finally {
            if (testFile != null && testFile.exists()) {
                testFile.delete();
            }
        }
    }

    /**
     * Create a FileChannel over pmem.
     *
     * @param file               The file to map.
     * @param length             The length of the map. The file will be expanded if necessary.
     * @param create             if true, the file will be created. If false and the file does not already exist, a FileNotFoundException is thrown.
     * @param readSharedMetadata The sharing mode for the persistence metadata.
     * @return a FileChannel using pmem, or null if pmem is not supported.
     * @throws FileNotFoundException if the file does not exist and create is false.
     */
    public static FileChannel pmemChannelFor(File file, int length, boolean create, boolean readSharedMetadata)
            throws FileNotFoundException {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry with file={0}, length={1}, create={2}, readSharedMetadata={3}",
                    file.getAbsolutePath(), length, create, readSharedMetadata);
        }

        if (!isPmemSupportedFor(file.getParentFile())) {
            if(logger.isTraceEnabled()) {
                logger.tracev("exit returning null");
            }
            return null;
        }

        long initialSize = 0;
        if (!create) {
            if (!file.exists()) {
                FileNotFoundException fileNotFoundException = new FileNotFoundException(file.getAbsolutePath());
                if(logger.isTraceEnabled()) {
                    logger.tracev(fileNotFoundException,"throwing {0}", fileNotFoundException.toString());
                }
                throw fileNotFoundException;
            } else {
                initialSize = file.length();
            }
        }

        FileChannel fileChannel = null;
        try {
            fileChannel = (FileChannel) mappedFileChannelConstructor.newInstance(file, length, readSharedMetadata);

        } catch (Exception e) {
            // as long as the isPmemSupportedFor works this should not happen, but...
            // if the mapping fails after it has already expanded the file to the required size, there is a mess to clean up
            if(logger.isDebugEnabled()) {
                logger.debugv(e, "pmem mapping failed for {0}. File may need truncation.", file.getAbsolutePath());
            }
            fileChannel = null;
            if (create) {
                file.delete();
            }
        }

        if(logger.isTraceEnabled()) {
            logger.tracev("exit returning {0}", fileChannel);
        }
        return fileChannel;
    }

    /**
     * Create a FileChannel over pmem.
     *
     * @param file   The file to map.
     * @param length The length of the map. The file will be expanded if necessary.
     * @param create if true, the file will be created. If false and the file does not already exist, a FileNotFoundException is thrown.
     * @return a FileChannel using pmem, or null if pmem is not supported.
     * @throws FileNotFoundException if the file does not exist and create is false.
     */
    public static FileChannel pmemChannelFor(File file, int length, boolean create) throws FileNotFoundException {
        return pmemChannelFor(file, length, create, false);
    }

    /**
     * Create an ArrayStore over the given file.
     *
     * @param file             the backing file to use.
     * @param numberOfSlots    the number of individually accessible storage regions.
     * @param slotDataCapacity the maximum data storage size of each slot.
     * @return new newly created ArrayStore instance.
     * @throws IOException if the store cannot be created.
     */
    public static ArrayStore arrayStoreFor(File file, int numberOfSlots, int slotDataCapacity) throws IOException {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry with file={0}, numberOfSlots={1}, slotDataCapacity={2}",
                    file.getAbsolutePath(), numberOfSlots, slotDataCapacity);
        }

        if (!isPmemSupportedFor(file.getParentFile())) {
            if(logger.isTraceEnabled()) {
                logger.tracev("exit returning null");
            }
            return null;
        }

        try {

            ArrayStore arrayStore = (ArrayStore) arrayStoreConstructor.newInstance(file, numberOfSlots, slotDataCapacity);
            if(logger.isTraceEnabled()) {
                logger.tracev("exit returning {0}", arrayStore);
            }
            return arrayStore;

        } catch (Exception e) {
            IOException ioException = new IOException("Could not create array store", e);
            if(logger.isTraceEnabled()) {
                logger.tracev(ioException, "throwing {0}", ioException.toString());
            }
            throw ioException;
        }
    }
}
