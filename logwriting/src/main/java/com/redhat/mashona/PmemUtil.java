/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
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
package com.redhat.mashona;

import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
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

    private static final XLogger logger = XLoggerFactory.getXLogger(PmemUtil.class);

    // most classes are built for jdk 14, whereas this class is built for 8,
    // so we want to avoid hard linking or we'll get headaches.
    private static final Constructor mappedFileChannelConstructor;
    private static final Constructor mappedFileChannelMetadataConstructor;

    static {

        Constructor fccTmp = null;
        Constructor fccmdTmp = null;
        try {
            String specVersion = System.getProperty("java.specification.version");
            if(!specVersion.contains(".") && Integer.parseInt(specVersion) >= 14) {
                fccTmp = Class.forName("com.redhat.mashona.MappedFileChannel").getDeclaredConstructor(File.class, int.class);
                fccmdTmp = Class.forName("com.redhat.mashona.MappedFileChannelMetadata").getDeclaredConstructor(File.class);
            }
        } catch (Exception e) {
            logger.debug("Can't wire MappedFileChannel constructor", e);
        }
        mappedFileChannelConstructor = fccTmp;
        mappedFileChannelMetadataConstructor = fccmdTmp;
    }

    public static void main(String[] args) {
        File dir = new File(args[0]);
        System.out.println(dir.getAbsolutePath()+": pmem is "+ PmemUtil.isPmemSupportedFor(dir));
    }

    /**
     * Validate if it's possible to use pmem via a DAX mmap for the given path.
     *
     * <p>Note that pmemChannelFor implicitly makes this check, so direct calls are not normally required.</p>
     *
     * <p>This method checks that
     *   a) the JVM is recent enough to have pmem support
     *   b) the directory is on a DAX mode file system mount
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
        logger.entry(dir.getAbsolutePath());

        if(mappedFileChannelConstructor == null) {
            return false;
        }

        if(!dir.exists()) {
            IllegalArgumentException e = new IllegalArgumentException("The directory "+dir.getAbsolutePath()+" must exist");
            logger.throwing(e);
            throw e;
        }
        if(!dir.isDirectory()) {
            IllegalArgumentException e = new IllegalArgumentException(dir.getAbsolutePath()+" must be a directory");
            logger.throwing(e);
            throw e;
        }

        File testFile = null;
        try {
            testFile = File.createTempFile("isPmemSupportedFor", "", dir);

            Closeable mappedFileChannelMetadata = (Closeable) mappedFileChannelMetadataConstructor.newInstance(testFile);
            mappedFileChannelMetadata.close();

            logger.exit(true);
            return true;
        } catch (Exception e) {
            logger.trace("mmap failed for path {}", dir.getAbsolutePath(), e);
            logger.exit(false);
            return false;
        } finally {
            if(testFile != null && testFile.exists()) {
                testFile.delete();
            }
        }
    }

    /**
     * Create a FileChannel over pmem.
     *
     * @param file The file to map.
     * @param length The length of the map. The file will be expanded if necessary.
     * @param create if true, the file will be created. If false and the file does not already exist, a FileNotFoundException is thrown.
     * @return a FileChannel using pmem, or null if pmem is not supported.
     * @throws FileNotFoundException if the file does not exist and create is false.
     */
    public static FileChannel pmemChannelFor(File file, int length, boolean create) throws FileNotFoundException {
        logger.entry(file.getAbsolutePath(), create);

        if(!isPmemSupportedFor(file.getParentFile())) {
            logger.exit(null);
            return null;
        }

        long initialSize = 0;
        if(!create) {
            if(!file.exists()) {
                FileNotFoundException e = new FileNotFoundException(file.getAbsolutePath());
                logger.throwing(e);
                throw e;
            } else {
                initialSize = file.length();
            }
        }

        FileChannel fileChannel = null;
        try {
            fileChannel = (FileChannel)mappedFileChannelConstructor.newInstance(file, length);

        } catch (Exception e) {
            // as long as the isPmemSupportedFor works this should not happen, but...
            // if the mapping fails after it's already expanded the file to the required size, there is a mess to clean up
            logger.debug("pmem mapping failed for {}. File may need truncation.", file.getAbsolutePath(), e);
            fileChannel = null;
            if(create) {
                file.delete();
            }
        }

        logger.exit(fileChannel);
        return fileChannel;
    }
}