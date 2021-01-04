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
package io.mashona.pobj.transaction.events;

import io.mashona.pobj.transaction.TransactionalMemoryOperations;

/**
 * Transaction log entry for recording object memory segment open events.
 * Note these are volatile and distinct from the persistent MallocEvent.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-07
 */
public class CreateEvent implements TransactionEvent {

    private final TransactionalMemoryOperations memory;

    /**
     * Creates a record of the creation of a memory-backed object.
     *
     * @param memory the wrapper of the backing memory for the object.
     */
    public CreateEvent(TransactionalMemoryOperations memory) {
        this.memory = memory;
    }

    public TransactionalMemoryOperations getMemory() {
        return memory;
    }
}
