package com.redhat.mashona.pobj.generated;

import com.redhat.mashona.pobj.runtime.MemoryOperations;
import com.redhat.mashona.pobj.runtime.MemoryBackedObject;

import java.nio.charset.StandardCharsets;

// automatically generated class.
public class MBOTestEntity implements MemoryBackedObject {

    protected static final int MYBYTE_OFFSET = 0;
    protected static final int MYBYTE_STORESIZE = 1;
    protected static final int MYCHAR_OFFSET = 2;
    protected static final int MYCHAR_STORESIZE = 2;
    protected static final int MYSHORT_OFFSET = 4;
    protected static final int MYSHORT_STORESIZE = 2;
    protected static final int MYINT_OFFSET = 8;
    protected static final int MYINT_STORESIZE = 4;
    protected static final int MYFLOAT_OFFSET = 12;
    protected static final int MYFLOAT_STORESIZE = 4;
    protected static final int MYLONG_OFFSET = 16;
    protected static final int MYLONG_STORESIZE = 8;
    protected static final int MYDOUBLE_OFFSET = 24;
    protected static final int MYDOUBLE_STORESIZE = 8;
    protected static final int MYBYTEFIXARRAY_OFFSET = 32;
    protected static final int MYBYTEFIXARRAY_STORESIZE = 4;
    protected static final int MYBYTEFIXARRAY_MAXELEMENTS = 4;
    protected static final int MYCHARFIXARRAY_OFFSET = 36;
    protected static final int MYCHARFIXARRAY_STORESIZE = 8;
    protected static final int MYCHARFIXARRAY_MAXELEMENTS = 4;
    protected static final int MYSHORTFIXARRAY_OFFSET = 44;
    protected static final int MYSHORTFIXARRAY_STORESIZE = 8;
    protected static final int MYSHORTFIXARRAY_MAXELEMENTS = 4;
    protected static final int MYINTFIXARRAY_OFFSET = 52;
    protected static final int MYINTFIXARRAY_STORESIZE = 16;
    protected static final int MYINTFIXARRAY_MAXELEMENTS = 4;
    protected static final int MYLONGFIXARRAY_OFFSET = 68;
    protected static final int MYLONGFIXARRAY_STORESIZE = 32;
    protected static final int MYLONGFIXARRAY_MAXELEMENTS = 4;
    protected static final int MYFLOATFIXARRAY_OFFSET = 100;
    protected static final int MYFLOATFIXARRAY_STORESIZE = 16;
    protected static final int MYFLOATFIXARRAY_MAXELEMENTS = 4;
    protected static final int MYDOUBLEFIXARRAY_OFFSET = 116;
    protected static final int MYDOUBLEFIXARRAY_STORESIZE = 32;
    protected static final int MYDOUBLEFIXARRAY_MAXELEMENTS = 4;
    protected static final int MYBYTEVARARRAYELEMENTCOUNT_OFFSET = 148;
    protected static final int MYBYTEVARARRAYELEMENTCOUNT_STORESIZE = 4;
    protected static final int MYBYTEVARARRAY_OFFSET = 152;
    protected static final int MYBYTEVARARRAY_STORESIZE = 4;
    protected static final int MYBYTEVARARRAY_MAXELEMENTS = 4;
    protected static final int MYCHARVARARRAYELEMENTCOUNT_OFFSET = 156;
    protected static final int MYCHARVARARRAYELEMENTCOUNT_STORESIZE = 4;
    protected static final int MYCHARVARARRAY_OFFSET = 160;
    protected static final int MYCHARVARARRAY_STORESIZE = 8;
    protected static final int MYCHARVARARRAY_MAXELEMENTS = 4;
    protected static final int MYSHORTVARARRAYELEMENTCOUNT_OFFSET = 168;
    protected static final int MYSHORTVARARRAYELEMENTCOUNT_STORESIZE = 4;
    protected static final int MYSHORTVARARRAY_OFFSET = 172;
    protected static final int MYSHORTVARARRAY_STORESIZE = 8;
    protected static final int MYSHORTVARARRAY_MAXELEMENTS = 4;
    protected static final int MYINTVARARRAYELEMENTCOUNT_OFFSET = 180;
    protected static final int MYINTVARARRAYELEMENTCOUNT_STORESIZE = 4;
    protected static final int MYINTVARARRAY_OFFSET = 184;
    protected static final int MYINTVARARRAY_STORESIZE = 16;
    protected static final int MYINTVARARRAY_MAXELEMENTS = 4;
    protected static final int MYLONGVARARRAYELEMENTCOUNT_OFFSET = 200;
    protected static final int MYLONGVARARRAYELEMENTCOUNT_STORESIZE = 4;
    protected static final int MYLONGVARARRAY_OFFSET = 204;
    protected static final int MYLONGVARARRAY_STORESIZE = 32;
    protected static final int MYLONGVARARRAY_MAXELEMENTS = 4;
    protected static final int MYFLOATVARARRAYELEMENTCOUNT_OFFSET = 236;
    protected static final int MYFLOATVARARRAYELEMENTCOUNT_STORESIZE = 4;
    protected static final int MYFLOATVARARRAY_OFFSET = 240;
    protected static final int MYFLOATVARARRAY_STORESIZE = 16;
    protected static final int MYFLOATVARARRAY_MAXELEMENTS = 4;
    protected static final int MYDOUBLEVARARRAYELEMENTCOUNT_OFFSET = 256;
    protected static final int MYDOUBLEVARARRAYELEMENTCOUNT_STORESIZE = 4;
    protected static final int MYDOUBLEVARARRAY_OFFSET = 260;
    protected static final int MYDOUBLEVARARRAY_STORESIZE = 32;
    protected static final int MYDOUBLEVARARRAY_MAXELEMENTS = 4;
    protected static final int MYSTRINGVARARRAYELEMENTCOUNT_OFFSET = 292;
    protected static final int MYSTRINGVARARRAYELEMENTCOUNT_STORESIZE = 4;
    protected static final int MYSTRINGVARARRAY_OFFSET = 296;
    protected static final int MYSTRINGVARARRAY_STORESIZE = 16;
    protected static final int MYSTRINGVARARRAY_MAXELEMENTS = 16;
    protected static final int TOTAL_STORESIZE = 312;

    protected MemoryOperations memory;

    public MBOTestEntity() {
    }

    @Override
    public void setMemory(MemoryOperations memory) {
        this.memory = memory;
    }

    @Override
    public int size() {
        return TOTAL_STORESIZE;
    }

    @Override
    public MemoryOperations getMemory() {
        return memory;
    }

    /////////////////////

    public byte getMyByte() {
        return memory.getByte(MYBYTE_OFFSET);
    }

    public void setMyByte(byte myByte) {
        memory.setByte(MYBYTE_OFFSET, myByte);
    }

    public char getMyChar() {
        return memory.getChar(MYCHAR_OFFSET);
    }

    public void setMyChar(char myChar) {
        memory.setChar(MYCHAR_OFFSET, myChar);
    }

    public short getMyShort() {
        return memory.getShort(MYSHORT_OFFSET);
    }

    public void setMyShort(short myShort) {
        memory.setShort(MYSHORT_OFFSET, myShort);
    }

    public int getMyInt() {
        return memory.getInt(MYINT_OFFSET);
    }

    public void setMyInt(int myInt) {
        memory.setInt(MYINT_OFFSET, myInt);
    }

    public float getMyFloat() {
        return memory.getFloat(MYFLOAT_OFFSET);
    }

    public void setMyFloat(float myFloat) {
        memory.setFloat(MYFLOAT_OFFSET, myFloat);
    }

    public long getMyLong() {
        return memory.getLong(MYLONG_OFFSET);
    }

    public void setMyLong(long myLong) {
        memory.setLong(MYLONG_OFFSET, myLong);
    }

    public double getMyDouble() {
        return memory.getDouble(MYDOUBLE_OFFSET);
    }

    public void setMyDouble(double myDouble) {
        memory.setDouble(MYDOUBLE_OFFSET, myDouble);
    }

    public byte[] getMyByteFixArray() {
        return memory.getByteArray(MYBYTEFIXARRAY_OFFSET, MYBYTEFIXARRAY_MAXELEMENTS);
    }

    public byte getMyByteFixArrayElement(int index) {
        return memory.getByteArrayElement(MYBYTEFIXARRAY_OFFSET, MYBYTEFIXARRAY_MAXELEMENTS, index);
    }

    public void setMyByteFixArray(byte[] myByteFixArray) {
        memory.setByteArray(MYBYTEFIXARRAY_OFFSET, MYBYTEFIXARRAY_MAXELEMENTS, myByteFixArray);
    }

    public void setMyByteFixArrayElement(int index, byte value) {
        memory.setByteArrayElement(MYBYTEFIXARRAY_OFFSET, MYBYTEFIXARRAY_MAXELEMENTS, index, value);
    }

    public char[] getMyCharFixArray() {
        return memory.getCharArray(MYCHARFIXARRAY_OFFSET, MYCHARFIXARRAY_MAXELEMENTS);
    }

    public char getMyCharFixArrayElement(int index) {
        return memory.getCharArrayElement(MYCHARFIXARRAY_OFFSET, MYCHARFIXARRAY_MAXELEMENTS, index);
    }

    public void setMyCharFixArray(char[] myCharFixArray) {
        memory.setCharArray(MYCHARFIXARRAY_OFFSET, MYCHARFIXARRAY_MAXELEMENTS, myCharFixArray);
    }

    public void setMyCharFixArrayElement(int index, char value) {
        memory.setCharArrayElement(MYCHARFIXARRAY_OFFSET, MYCHARFIXARRAY_MAXELEMENTS, index, value);
    }

    public short[] getMyShortFixArray() {
        return memory.getShortArray(MYSHORTFIXARRAY_OFFSET, MYSHORTFIXARRAY_MAXELEMENTS);
    }

    public short getMyShortFixArrayElement(int index) {
        return memory.getShortArrayElement(MYSHORTFIXARRAY_OFFSET, MYSHORTFIXARRAY_MAXELEMENTS, index);
    }

    public void setMyShortFixArray(short[] myShortFixArray) {
        memory.setShortArray(MYSHORTFIXARRAY_OFFSET, MYSHORTFIXARRAY_MAXELEMENTS, myShortFixArray);
    }

    public void setMyShortFixArrayElement(int index, short value) {
        memory.setShortArrayElement(MYSHORTFIXARRAY_OFFSET, MYSHORTFIXARRAY_MAXELEMENTS, index, value);
    }

    public int[] getMyIntFixArray() {
        return memory.getIntArray(MYINTFIXARRAY_OFFSET, MYINTFIXARRAY_MAXELEMENTS);
    }

    public int getMyIntFixArrayElement(int index) {
        return memory.getIntArrayElement(MYINTFIXARRAY_OFFSET, MYINTFIXARRAY_MAXELEMENTS, index);
    }

    public void setMyIntFixArray(int[] myIntFixArray) {
        memory.setIntArray(MYINTFIXARRAY_OFFSET, MYINTFIXARRAY_MAXELEMENTS, myIntFixArray);
    }

    public void setMyIntFixArrayElement(int index, int value) {
        memory.setIntArrayElement(MYINTFIXARRAY_OFFSET, MYINTFIXARRAY_MAXELEMENTS, index, value);
    }

    public long[] getMyLongFixArray() {
        return memory.getLongArray(MYLONGFIXARRAY_OFFSET, MYLONGFIXARRAY_MAXELEMENTS);
    }

    public long getMyLongFixArrayElement(int index) {
        return memory.getLongArrayElement(MYLONGFIXARRAY_OFFSET, MYLONGFIXARRAY_MAXELEMENTS, index);
    }

    public void setMyLongFixArray(long[] myLongFixArray) {
        memory.setLongArray(MYLONGFIXARRAY_OFFSET, MYLONGFIXARRAY_MAXELEMENTS, myLongFixArray);
    }

    public void setMyLongFixArrayElement(int index, long value) {
        memory.setLongArrayElement(MYLONGFIXARRAY_OFFSET, MYLONGFIXARRAY_MAXELEMENTS, index, value);
    }

    public float[] getMyFloatFixArray() {
        return memory.getFloatArray(MYFLOATFIXARRAY_OFFSET, MYFLOATFIXARRAY_MAXELEMENTS);
    }

    public float getMyFloatFixArrayElement(int index) {
        return memory.getFloatArrayElement(MYFLOATFIXARRAY_OFFSET, MYFLOATFIXARRAY_MAXELEMENTS, index);
    }

    public void setMyFloatFixArray(float[] myFloatFixArray) {
        memory.setFloatArray(MYFLOATFIXARRAY_OFFSET, MYFLOATFIXARRAY_MAXELEMENTS, myFloatFixArray);
    }

    public void setMyFloatFixArrayElement(int index, float value) {
        memory.setFloatArrayElement(MYFLOATFIXARRAY_OFFSET, MYFLOATFIXARRAY_MAXELEMENTS, index, value);
    }

    public double[] getMyDoubleFixArray() {
        return memory.getDoubleArray(MYDOUBLEFIXARRAY_OFFSET, MYDOUBLEFIXARRAY_MAXELEMENTS);
    }

    public double getMyDoubleFixArrayElement(int index) {
        return memory.getDoubleArrayElement(MYDOUBLEFIXARRAY_OFFSET, MYDOUBLEFIXARRAY_MAXELEMENTS, index);
    }

    public void setMyDoubleFixArray(double[] myDoubleFixArray) {
        memory.setDoubleArray(MYDOUBLEFIXARRAY_OFFSET, MYDOUBLEFIXARRAY_MAXELEMENTS, myDoubleFixArray);
    }

    public void setMyDoubleFixArrayElement(int index, double value) {
        memory.setDoubleArrayElement(MYDOUBLEFIXARRAY_OFFSET, MYDOUBLEFIXARRAY_MAXELEMENTS, index, value);
    }

    public int getMyByteVarArrayElementCount() {
        return memory.getInt(MYBYTEVARARRAYELEMENTCOUNT_OFFSET);
    }

    public void setMyByteVarArrayElementCount(int myByteVarArrayElementCount) {
        memory.setInt(MYBYTEVARARRAYELEMENTCOUNT_OFFSET, myByteVarArrayElementCount);
    }

    public byte[] getMyByteVarArray() {
        return memory.getByteArray(MYBYTEVARARRAY_OFFSET, MYBYTEVARARRAY_MAXELEMENTS);
    }

    public void setMyByteVarArray(byte[] myByteVarArray) {
        setMyByteVarArrayElementCount(myByteVarArray.length);
        memory.setByteArray(MYBYTEVARARRAY_OFFSET, MYBYTEVARARRAY_MAXELEMENTS, myByteVarArray);
    }

    public int getMyCharVarArrayElementCount() {
        return memory.getInt(MYCHARVARARRAYELEMENTCOUNT_OFFSET);
    }

    public void setMyCharVarArrayElementCount(int myCharVarArrayElementCount) {
        memory.setInt(MYCHARVARARRAYELEMENTCOUNT_OFFSET, myCharVarArrayElementCount);
    }

    public char[] getMyCharVarArray() {
        return memory.getCharArray(MYCHARVARARRAY_OFFSET, MYCHARVARARRAY_MAXELEMENTS);
    }

    public void setMyCharVarArray(char[] myCharVarArray) {
        setMyCharVarArrayElementCount(myCharVarArray.length);
        memory.setCharArray(MYCHARVARARRAY_OFFSET, MYCHARVARARRAY_MAXELEMENTS, myCharVarArray);
    }

    public int getMyShortVarArrayElementCount() {
        return memory.getInt(MYSHORTVARARRAYELEMENTCOUNT_OFFSET);
    }

    public void setMyShortVarArrayElementCount(int myShortVarArrayElementCount) {
        memory.setInt(MYSHORTVARARRAYELEMENTCOUNT_OFFSET, myShortVarArrayElementCount);
    }

    public short[] getMyShortVarArray() {
        return memory.getShortArray(MYSHORTVARARRAY_OFFSET, MYSHORTVARARRAY_MAXELEMENTS);
    }

    public void setMyShortVarArray(short[] myShortVarArray) {
        setMyShortVarArrayElementCount(myShortVarArray.length);
        memory.setShortArray(MYSHORTVARARRAY_OFFSET, MYSHORTVARARRAY_MAXELEMENTS, myShortVarArray);
    }

    public int getMyIntVarArrayElementCount() {
        return memory.getInt(MYINTVARARRAYELEMENTCOUNT_OFFSET);
    }

    public void setMyIntVarArrayElementCount(int myIntVarArrayElementCount) {
        memory.setInt(MYINTVARARRAYELEMENTCOUNT_OFFSET, myIntVarArrayElementCount);
    }

    public int[] getMyIntVarArray() {
        return memory.getIntArray(MYINTVARARRAY_OFFSET, MYINTVARARRAY_MAXELEMENTS);
    }

    public void setMyIntVarArray(int[] myIntVarArray) {
        setMyIntVarArrayElementCount(myIntVarArray.length);
        memory.setIntArray(MYINTVARARRAY_OFFSET, MYINTVARARRAY_MAXELEMENTS, myIntVarArray);
    }

    public int getMyLongVarArrayElementCount() {
        return memory.getInt(MYLONGVARARRAYELEMENTCOUNT_OFFSET);
    }

    public void setMyLongVarArrayElementCount(int myLongVarArrayElementCount) {
        memory.setInt(MYLONGVARARRAYELEMENTCOUNT_OFFSET, myLongVarArrayElementCount);
    }

    public long[] getMyLongVarArray() {
        return memory.getLongArray(MYLONGVARARRAY_OFFSET, MYLONGVARARRAY_MAXELEMENTS);
    }

    public void setMyLongVarArray(long[] myLongVarArray) {
        setMyLongVarArrayElementCount(myLongVarArray.length);
        memory.setLongArray(MYLONGVARARRAY_OFFSET, MYLONGVARARRAY_MAXELEMENTS, myLongVarArray);
    }

    public int getMyFloatVarArrayElementCount() {
        return memory.getInt(MYFLOATVARARRAYELEMENTCOUNT_OFFSET);
    }

    public void setMyFloatVarArrayElementCount(int myFloatVarArrayElementCount) {
        memory.setInt(MYFLOATVARARRAYELEMENTCOUNT_OFFSET, myFloatVarArrayElementCount);
    }

    public float[] getMyFloatVarArray() {
        return memory.getFloatArray(MYFLOATVARARRAY_OFFSET, MYFLOATVARARRAY_MAXELEMENTS);
    }

    public void setMyFloatVarArray(float[] myFloatVarArray) {
        setMyFloatVarArrayElementCount(myFloatVarArray.length);
        memory.setFloatArray(MYFLOATVARARRAY_OFFSET, MYFLOATVARARRAY_MAXELEMENTS, myFloatVarArray);
    }

    public int getMyDoubleVarArrayElementCount() {
        return memory.getInt(MYDOUBLEVARARRAYELEMENTCOUNT_OFFSET);
    }

    public void setMyDoubleVarArrayElementCount(int myDoubleVarArrayElementCount) {
        memory.setInt(MYDOUBLEVARARRAYELEMENTCOUNT_OFFSET, myDoubleVarArrayElementCount);
    }

    public double[] getMyDoubleVarArray() {
        return memory.getDoubleArray(MYDOUBLEVARARRAY_OFFSET, MYDOUBLEVARARRAY_MAXELEMENTS);
    }

    public void setMyDoubleVarArray(double[] myDoubleVarArray) {
        setMyDoubleVarArrayElementCount(myDoubleVarArray.length);
        memory.setDoubleArray(MYDOUBLEVARARRAY_OFFSET, MYDOUBLEVARARRAY_MAXELEMENTS, myDoubleVarArray);
    }

    public int getMyStringVarArrayElementCount() {
        return memory.getInt(MYSTRINGVARARRAYELEMENTCOUNT_OFFSET);
    }

    public void setMyStringVarArrayElementCount(int myStringVarArrayElementCount) {
        memory.setInt(MYSTRINGVARARRAYELEMENTCOUNT_OFFSET, myStringVarArrayElementCount);
    }

    public byte[] getMyStringVarArray() {
        return memory.getByteArray(MYSTRINGVARARRAY_OFFSET, MYSTRINGVARARRAY_MAXELEMENTS);
    }

    public void setMyStringVarArray(byte[] myStringVarArray) {
        setMyStringVarArrayElementCount(myStringVarArray.length);
        memory.setByteArray(MYSTRINGVARARRAY_OFFSET, MYSTRINGVARARRAY_MAXELEMENTS, myStringVarArray);
    }

    public String getMyString() {
        byte[] data = getMyStringVarArray();
        return new String(data, StandardCharsets.UTF_8);
    }

    public void setMyString(String myString) {
        byte[] data = myString.getBytes(StandardCharsets.UTF_8);
        setMyStringVarArray(data);
    }
}
