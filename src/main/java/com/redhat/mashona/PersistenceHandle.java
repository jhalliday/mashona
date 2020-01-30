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

import java.lang.reflect.Field;
import java.nio.MappedByteBuffer;

/**
 * A holder for a reference to a MappedByteBuffer backed by persistent memory,
 * via which data ranges may be flushed from cache to the persistence domain.
 *
 * Users of higher level abstractions should not normally need to use this class directly.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2019-04
 */
public class PersistenceHandle {

    private static final XLogger logger = XLoggerFactory.getXLogger(PersistenceHandle.class);

    private static Field fdField;

    static {
        try {
            setParanoid( Boolean.getBoolean("persistent.paranoia") );
        } catch (Exception e) {
            logger.debug("Exception whilst configuring persistent handle validation. Validation will be disabled.", e);
            fdField = null;
        }
    }

    /**
     * Configure validation of buffers passed to the constructor.
     *
     * <p>Caution: this method is NOT threadsafe with respect to instance constructor calls.</p>
     *
     * @param value true to enable validation, false to disable.
     * @throws Exception if configuration enablement fails.
     */

    public static void setParanoid(boolean value) throws Exception {
        logger.entry(value);

        if(value) {
            fdField = MappedByteBuffer.class.getDeclaredField("fd");
            fdField.setAccessible(true);
        } else {
            fdField = null;
        }

        logger.exit();
    }

    private final MappedByteBuffer buffer;
    private final int offset;
    private final int length;

    /**
     * Initializes a new PersistenceHandle for the specified region of the provided buffer.
     *
     * <p>Note that the MappedByteBuffer MUST be the original instance obtained
     * from fileChannel.map and NOT a duplicate or slice thereof.</p>
     *
     * @param buffer The MappedByteBuffer over which to operate.
     * @param offset the offset in the buffer at which to base our operational area.
     * @param length the number of bytes in the operational area.
     */
    public PersistenceHandle(MappedByteBuffer buffer, int offset, int length) {
        logger.entry(buffer, offset, length);

        validateBuffer(buffer);

        this.buffer = buffer;
        this.offset = offset;
        this.length = length;

        logger.exit();
    }

    private void validateBuffer(MappedByteBuffer buff) {

        if (fdField != null ) {
            boolean ok = true;
            try {
                ok = (fdField.get(buff) != null);
            } catch (Exception e) {
                logger.debug("Exception whilst trying to validate handle for {}. Continuing anyhow.", buff, e);
            }

            if(!ok) {
                IllegalArgumentException exception = new IllegalArgumentException("Persistence would be ineffective on " + buff);
                logger.throwing(exception);
                throw exception;
            }
        }
    }

    // TODO non-public
    public PersistenceHandle duplicate(int offset, int length) {
        logger.entry(offset, length);

        if (length > this.length) {
            throw new IllegalArgumentException("given length of " + length + " exceeds max of " + this.length);
        }

        PersistenceHandle persistenceHandle = new PersistenceHandle(buffer, this.offset + offset, length);
        logger.exit(persistenceHandle);
        return persistenceHandle;
    }

    /**
     * Forces any changes made within the specified area to be written to the persistence domain.
     *
     * @param from the base offset.
     * @param length the number of bytes.
     */
    public void persist(int from, int length) {
        logger.entry(from, length);

        if (length > this.length) {
            throw new IllegalArgumentException("given length of " + length + " exceeds max of " + this.length);
        }

        buffer.force(from + offset, length);

        logger.exit();
    }

    /**
     * Forces any changes made to be written to the persistence domain.
     */
    public void persist() {
        logger.entry();

        persist(0, length);

        logger.exit();
    }

    // TODO non-public
    public int getOffset() {
        return offset;
    }
}
