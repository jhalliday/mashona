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

import java.nio.ByteBuffer;

/**
 * Interface for helper classes that provide I/O support for persisting transaction events.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-07
 */
public interface TransactionEventPersistence<T extends TransactionEvent> {

    /**
     * Provides a unique value to distinguish a type of persisted record from others.
     * Broadly equivalent in role to Java serialization's serialVersionUID.
     *
     * @return a record type discriminator value, unique in the scope of the log.
     */
    long getFormatId();

    /**
     * Instantiate an object instance from its persisted form.
     * <p>
     * Note that this method is not entirely symmetric with writeInto - the formatId discriminator prefix is
     * expected to be read prior to invoking this method, as it is required for dispatch to the correct
     * persistence instance for the type.
     *
     * @param byteBuffer a buffer containing the persisted state, which will be read and advanced from its current position.
     * @return T a recreated instance of the appropriate type
     */
    T readFrom(ByteBuffer byteBuffer);

    /**
     * Persist an object instance into the provided buffer.
     * <p>
     * Note that this method is expected to prefix the written object state with the format id, which is asymmetric
     * to the behaviour of readFrom, which does not expect to read that prefix.
     *
     * @param t          a LoggableTransactionEvent instance.
     * @param byteBuffer the store in which state will be written, starting and advancing from the current position.
     */
    void writeInto(T t, ByteBuffer byteBuffer);
}
