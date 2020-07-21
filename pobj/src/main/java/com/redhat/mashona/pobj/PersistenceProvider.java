package com.redhat.mashona.pobj;

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
