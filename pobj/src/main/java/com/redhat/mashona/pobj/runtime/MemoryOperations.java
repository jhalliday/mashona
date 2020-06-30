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

import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryHandles;
import jdk.incubator.foreign.MemorySegment;

import java.lang.invoke.VarHandle;
import java.nio.*;

/**
 * Provides methods to store Java primitive types in a region of memory,
 * such as to allow an object to 'serialize' state to an off-heap store
 * at field granularity and access them in-place individually.
 * Provides access event hooks to allow subclasses to wrap interceptors
 * around memory use e.g. for transaction management.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-06
 */
public class MemoryOperations {

    private static final VarHandle byteHandle = MemoryHandles.varHandle(byte.class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle charHandle = MemoryHandles.varHandle(char.class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle doubleHandle = MemoryHandles.varHandle(double.class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle floatHandle = MemoryHandles.varHandle(float.class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle intHandle = MemoryHandles.varHandle(int.class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle longHandle = MemoryHandles.varHandle(long.class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle shortHandle = MemoryHandles.varHandle(short.class, ByteOrder.BIG_ENDIAN);

    protected MemorySegment memorySegment;

    protected MemoryAddress baseAddress;
    protected ByteBuffer byteBuffer;

    ///////////////////////////////////

    /**
     * Creates a new instance by wrapping the provided segment.
     *
     * @param memorySegment the area of backing memory to use.
     */
    public MemoryOperations(MemorySegment memorySegment) {
        this.memorySegment = memorySegment;
        baseAddress = memorySegment.baseAddress();
        byteBuffer = memorySegment.asByteBuffer();
    }

    /**
     * Provides access to the underlying memory segment.
     *
     * @return the backing memory this instance uses.
     */
    public MemorySegment getMemorySegment() {
        return memorySegment;
    }

    public void close() {
        memorySegment.close();
    }

    /**
     * Access interception hook, invoked by getters prior to read operations on a part of the memory.
     * This method does nothing, unless overridden by a subclass.
     *
     * @param offset the starting offset within the memory.
     * @param length the size of the area being accessed (usually the size of a primitive datatype).
     */
    public void beforeRead(int offset, int length) {
    }

    /**
     * Access interception hook, invoked by getters following read operations on a part of the memory.
     * This method does nothing, unless overridden by a subclass.
     *
     * @param offset the starting offset within the memory.
     * @param length the size of the area being accessed (usually the size of a primitive datatype).
     */
    public void afterRead(int offset, int length) {
    }

    /**
     * Access interception hook, invoked by setters prior to write operations on a part of the memory.
     * This method does nothing, unless overridden by a subclass.
     *
     * @param offset the starting offset within the memory.
     * @param length the size of the area being accessed (usually the size of a primitive datatype).
     */
    public void beforeWrite(int offset, int length) {
    }

    /**
     * Access interception hook, invoked by setters following write operations on a part of the memory.
     * This method does nothing, unless overridden by a subclass.
     *
     * @param offset the starting offset within the memory.
     * @param length the size of the area being accessed (usually the size of a primitive datatype).
     */
    public void afterWrite(int offset, int length) {
    }


    // The remaining methods are getter/setter pairs for each supported data type,
    // which just wrap the corresponding memory segment access call with the before/after interceptors.

    public byte getByte(int offset) {
        beforeRead(offset, Byte.BYTES);
        byte value = (byte) byteHandle.get(baseAddress.addOffset(offset));
        afterRead(offset, Byte.BYTES);
        return value;
    }

    public void setByte(int offset, byte value) {
        beforeWrite(offset, Byte.BYTES);
        byteHandle.set(baseAddress.addOffset(offset), value);
        afterWrite(offset, Byte.BYTES);
    }

    public char getChar(int offset) {
        beforeRead(offset, Character.BYTES);
        char value = (char) charHandle.get(baseAddress.addOffset(offset));
        afterRead(offset, Character.BYTES);
        return value;
    }

    public void setChar(int offset, char value) {
        beforeWrite(offset, Character.BYTES);
        charHandle.set(baseAddress.addOffset(offset), value);
        afterWrite(offset, Character.BYTES);
    }

    public short getShort(int offset) {
        beforeRead(offset, Short.BYTES);
        short value = (short) shortHandle.get(baseAddress.addOffset(offset));
        afterRead(offset, Short.BYTES);
        return value;
    }

    public void setShort(int offset, short value) {
        beforeWrite(offset, Short.BYTES);
        shortHandle.set(baseAddress.addOffset(offset), value);
        afterWrite(offset, Short.BYTES);
    }

    public int getInt(int offset) {
        beforeRead(offset, Integer.BYTES);
        int value = (int) intHandle.get(baseAddress.addOffset(offset));
        afterRead(offset, Integer.BYTES);
        return value;
    }

    public void setInt(int offset, int value) {
        beforeWrite(offset, Integer.BYTES);
        intHandle.set(baseAddress.addOffset(offset), value);
        afterWrite(offset, Integer.BYTES);
    }

    public float getFloat(int offset) {
        beforeRead(offset, Float.BYTES);
        float value = (float) floatHandle.get(baseAddress.addOffset(offset));
        afterRead(offset, Float.BYTES);
        return value;
    }

    public void setFloat(int offset, float value) {
        beforeWrite(offset, Float.BYTES);
        floatHandle.set(baseAddress.addOffset(offset), value);
        afterWrite(offset, Float.BYTES);
    }

    public long getLong(int offset) {
        beforeRead(offset, Long.BYTES);
        long value = (long) longHandle.get(baseAddress.addOffset(offset));
        afterRead(offset, Long.BYTES);
        return value;
    }

    public void setLong(int offset, long value) {
        beforeWrite(offset, Long.BYTES);
        longHandle.set(baseAddress.addOffset(offset), value);
        afterWrite(offset, Long.BYTES);
    }

    public double getDouble(int offset) {
        beforeRead(offset, Double.BYTES);
        double value = (double) doubleHandle.get(baseAddress.addOffset(offset));
        afterRead(offset, Double.BYTES);
        return value;
    }

    public void setDouble(int offset, double value) {
        beforeWrite(offset, Double.BYTES);
        doubleHandle.set(baseAddress.addOffset(offset), value);
        afterRead(offset, Double.BYTES);
    }

    ///////////////////////////////////

    public byte[] getByteArray(int offset, int numElements) {
        beforeRead(offset, Byte.BYTES);
        byte[] data = new byte[numElements];
        ByteBuffer dataBuffer = byteBuffer.slice(offset, Byte.BYTES * numElements);
        dataBuffer.get(data);
        afterRead(offset, Byte.BYTES);
        return data;
    }

    public void setByteArray(int offset, int numElements, byte[] value) {
        beforeWrite(offset, Byte.BYTES);
        ByteBuffer dataBuffer = byteBuffer.slice(offset, Byte.BYTES * numElements);
        dataBuffer.put(value);
        afterWrite(offset, Byte.BYTES);
    }

    public byte getByteArrayElement(int offset, int numElements, int index) {
        return getByte(offset + (index * Byte.BYTES));
    }

    public void setByteArrayElement(int offset, int numElements, int index, byte value) {
        setByte(offset + (index * Byte.BYTES), value);
    }

    public char[] getCharArray(int offset, int numElements) {
        beforeRead(offset, Character.BYTES);
        char[] data = new char[numElements];
        CharBuffer dataBuffer = byteBuffer.slice(offset, Character.BYTES * numElements).asCharBuffer();
        dataBuffer.get(data);
        afterRead(offset, Character.BYTES);
        return data;
    }

    public void setCharArray(int offset, int numElements, char[] value) {
        beforeWrite(offset, Character.BYTES);
        CharBuffer dataBuffer = byteBuffer.slice(offset, Character.BYTES * numElements).asCharBuffer();
        dataBuffer.put(value);
        afterWrite(offset, Character.BYTES);
    }

    public char getCharArrayElement(int offset, int numElements, int index) {
        return getChar(offset + (index * Character.BYTES));
    }

    public void setCharArrayElement(int offset, int numElements, int index, char value) {
        setChar(offset + (index * Character.BYTES), value);
    }

    public short[] getShortArray(int offset, int numElements) {
        beforeRead(offset, Short.BYTES);
        short[] data = new short[numElements];
        ShortBuffer dataBuffer = byteBuffer.slice(offset, Short.BYTES * numElements).asShortBuffer();
        dataBuffer.get(data);
        afterRead(offset, Short.BYTES);
        return data;
    }

    public void setShortArray(int offset, int numElements, short[] value) {
        beforeWrite(offset, Short.BYTES);
        ShortBuffer dataBuffer = byteBuffer.slice(offset, Short.BYTES * numElements).asShortBuffer();
        dataBuffer.put(value);
        afterWrite(offset, Short.BYTES);
    }

    public short getShortArrayElement(int offset, int numElements, int index) {
        return getShort(offset + (index * Short.BYTES));
    }

    public void setShortArrayElement(int offset, int numElements, int index, short value) {
        setShort(offset + (index * Short.BYTES), value);
    }

    public int[] getIntArray(int offset, int numElements) {
        beforeRead(offset, Integer.BYTES);
        int[] data = new int[numElements];
        IntBuffer dataBuffer = byteBuffer.slice(offset, Integer.BYTES * numElements).asIntBuffer();
        dataBuffer.get(data);
        afterRead(offset, Integer.BYTES);
        return data;
    }

    public void setIntArray(int offset, int numElements, int[] value) {
        beforeWrite(offset, Integer.BYTES);
        IntBuffer dataBuffer = byteBuffer.slice(offset, Integer.BYTES * numElements).asIntBuffer();
        dataBuffer.put(value);
        afterWrite(offset, Integer.BYTES);
    }

    public int getIntArrayElement(int offset, int numElements, int index) {
        return getInt(offset + (index * Integer.BYTES));
    }

    public void setIntArrayElement(int offset, int numElements, int index, int value) {
        setInt(offset + (index * Integer.BYTES), value);
    }

    public long[] getLongArray(int offset, int numElements) {
        beforeRead(offset, Long.BYTES);
        long[] data = new long[numElements];
        LongBuffer dataBuffer = byteBuffer.slice(offset, Long.BYTES * numElements).asLongBuffer();
        dataBuffer.get(data);
        afterRead(offset, Long.BYTES);
        return data;
    }

    public void setLongArray(int offset, int numElements, long[] value) {
        beforeWrite(offset, Long.BYTES);
        LongBuffer dataBuffer = byteBuffer.slice(offset, Long.BYTES * numElements).asLongBuffer();
        dataBuffer.put(value);
        afterWrite(offset, Long.BYTES);
    }

    public long getLongArrayElement(int offset, int numElements, int index) {
        return getLong(offset + (index * Long.BYTES));
    }

    public void setLongArrayElement(int offset, int numElements, int index, long value) {
        setLong(offset + (index * Long.BYTES), value);
    }

    public float[] getFloatArray(int offset, int numElements) {
        beforeRead(offset, Float.BYTES);
        float[] data = new float[numElements];
        FloatBuffer dataBuffer = byteBuffer.slice(offset, Float.BYTES * numElements).asFloatBuffer();
        dataBuffer.get(data);
        afterRead(offset, Float.BYTES);
        return data;
    }

    public void setFloatArray(int offset, int numElements, float[] value) {
        beforeWrite(offset, Float.BYTES);
        FloatBuffer dataBuffer = byteBuffer.slice(offset, Float.BYTES * numElements).asFloatBuffer();
        dataBuffer.put(value);
        afterWrite(offset, Float.BYTES);
    }

    public float getFloatArrayElement(int offset, int numElements, int index) {
        return getFloat(offset + (index * Float.BYTES));
    }

    public void setFloatArrayElement(int offset, int numElements, int index, float value) {
        setFloat(offset + (index * Float.BYTES), value);
    }

    public double[] getDoubleArray(int offset, int numElements) {
        beforeRead(offset, Double.BYTES);
        double[] data = new double[numElements];
        DoubleBuffer dataBuffer = byteBuffer.slice(offset, Double.BYTES * numElements).asDoubleBuffer();
        dataBuffer.get(data);
        afterRead(offset, Double.BYTES);
        return data;
    }

    public void setDoubleArray(int offset, int numElements, double[] value) {
        beforeWrite(offset, Double.BYTES);
        DoubleBuffer dataBuffer = byteBuffer.slice(offset, Double.BYTES * numElements).asDoubleBuffer();
        dataBuffer.put(value);
        afterWrite(offset, Double.BYTES);
    }

    public double getDoubleArrayElement(int offset, int numElements, int index) {
        return getDouble(offset + (index * Double.BYTES));
    }

    public void setDoubleArrayElement(int offset, int numElements, int index, double value) {
        setDouble(offset + (index * Double.BYTES), value);
    }
}
