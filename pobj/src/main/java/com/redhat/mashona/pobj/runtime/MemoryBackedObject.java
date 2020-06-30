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
package com.redhat.mashona.pobj.runtime;

/**
 * Interface for Objects whose state is mostly held in external memory, not on the JVM heap.
 * The Java Object implementing this interface is an on-heap stub, connecting to an off-heap
 * area of memory, accessed via the MemoryOperations interface, in which it can store state.
 * Implementations of this interface are expected to be largely stateless,
 * except for the MemoryOperations reference. Think of them as over-engineered type-safe pointers...
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-06
 */
public interface MemoryBackedObject {

    /**
     * Provides the size of the object's required backing memory.
     *
     * @return the size, in bytes, of the required state storage space.
     */
    int size();

    /**
     * Sets the backing memory, connecting the on-heap stub to the off-heap store.
     *
     * @param memory An object through which the backing memory area may be accessed.
     */
    void setMemory(MemoryOperations memory);

    /**
     * Gets the backing memory for the object, which may contains its state representation.
     *
     * @return An object through which the backing memory area may be accessed.
     */
    MemoryOperations getMemory();
}
