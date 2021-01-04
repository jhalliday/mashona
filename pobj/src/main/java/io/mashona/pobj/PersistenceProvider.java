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
package io.mashona.pobj;

import java.nio.ByteBuffer;

/**
 * Interface for support classes that provide persistence operations for a given type.
 *
 * @param <T> The Object type that the provider can persist.
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-06
 */
public interface PersistenceProvider<T> {

    /**
     * Persist the given object instance into the provided buffer,
     * starting at its current position.
     * This will advance the buffer's position by the objects size.
     *
     * @param t          The object to persist.
     * @param byteBuffer The buffer into which to write the object state.
     */
    void writeInto(T t, ByteBuffer byteBuffer);

    /**
     * Recreate an object instance by reading its persisted state from the provided buffer,
     * stating at its current position.
     * This will advance the buffer's position by the objects size.
     *
     * @param byteBuffer The buffer from which to read the object state.
     * @return An object instance
     */
    T readFrom(ByteBuffer byteBuffer);
}
