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

    private static final Logger logger = Logger.getLogger(PersistenceHandle.class);

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
        if(logger.isTraceEnabled()) {
            logger.tracev("entry with buffer={0}, offset={1}, length={2}", buffer, offset, length);
        }

        this.buffer = buffer;
        this.offset = offset;
        this.length = length;

        if(logger.isTraceEnabled()) {
            logger.tracev("exit {0}", this);
        }
    }

    // mostly for testing.
    PersistenceHandle duplicate(int offset, int length) {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0} with offset={1}, length={2}", this, offset, length);
        }

        if (length > this.length) {
            IllegalArgumentException illegalArgumentException =
                    new IllegalArgumentException("Given length of " + length + " exceeds max of " + this.length);
            if(logger.isTraceEnabled()) {
                logger.tracev(illegalArgumentException, "throwing {0}", illegalArgumentException.toString());
            }
            throw illegalArgumentException;
        }

        PersistenceHandle persistenceHandle = new PersistenceHandle(buffer, this.offset + offset, length);

        if(logger.isTraceEnabled()) {
            logger.tracev("exit returning {0}", persistenceHandle);
        }
        return persistenceHandle;
    }

    /**
     * Forces any changes made within the specified area to be written to the persistence domain.
     *
     * @param from the base offset.
     * @param length the number of bytes.
     */
    public void persist(int from, int length) {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0} with from={1}, length={2}", this, from, length);
        }

        if (length > this.length) {
            IllegalArgumentException illegalArgumentException =
                    new IllegalArgumentException("Given length of " + length + " exceeds max of " + this.length);
            if(logger.isTraceEnabled()) {
                logger.tracev(illegalArgumentException, "throwing {0}", illegalArgumentException);
            }
            throw illegalArgumentException;
        }

        buffer.force(from + offset, length);

        if(logger.isTraceEnabled()) {
            logger.tracev("exit");
        }
    }

    /**
     * Forces any changes made to be written to the persistence domain.
     */
    public void persist() {
        if(logger.isTraceEnabled()) {
            logger.tracev("entry for {0}", this);
        }

        persist(0, length);

        if(logger.isTraceEnabled()) {
            logger.tracev("exit");
        }
    }
}
