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
package com.redhat.mashona.pobj.allocator;

import com.redhat.mashona.pobj.runtime.MemoryBackedObject;
import com.redhat.mashona.pobj.runtime.MemoryOperations;

/**
 * Test case helper that purposefully has a private constructor, which is Not Allowed.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-06
 */
public class BogusMemoryBackedObject implements MemoryBackedObject {

    private MemoryOperations memory;

    private BogusMemoryBackedObject() {
    }

    @Override
    public void setMemory(MemoryOperations memory) {
        this.memory = memory;
    }

    @Override
    public int size() {
        return 8;
    }

    @Override
    public MemoryOperations getMemory() {
        return memory;
    }
}
