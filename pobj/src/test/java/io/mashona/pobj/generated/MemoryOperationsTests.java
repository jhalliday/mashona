package io.mashona.pobj.generated;

import io.mashona.pobj.allocator.CompositeAllocator;
import io.mashona.pobj.allocator.CompositeAllocatorTests;
import io.mashona.pobj.allocator.MemoryHeap;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

// (mostly) automatically generated class.
public class MemoryOperationsTests {

    private static final File TEST_DIR = new File("/mnt/pmem/test"); // TODO  System.getenv("PMEM_TEST_DIR"));
    private static final File heapFile = new File(TEST_DIR, "test.heap");

    private static MemoryHeap memoryHeap;

    private MBOTestEntity instance;

    @BeforeAll
    public static void setUpClass() throws IOException {

        CompositeAllocator compositeAllocator = new CompositeAllocator(0, CompositeAllocatorTests.PAGE_SIZE);

        if (heapFile.exists()) {
            heapFile.delete();
        }

        memoryHeap = new MemoryHeap(heapFile, compositeAllocator.getBackingSize(), compositeAllocator);
    }

    @AfterAll
    public static void tearDownClass() throws IOException {
        memoryHeap.close();
        if (heapFile.exists()) {
            heapFile.delete();
        }
    }

    @BeforeEach
    public void setUp() throws IOException {
        instance = memoryHeap.newInstance(MBOTestEntity.class);
    }

    @AfterEach
    public void tearDown() throws IOException {
        memoryHeap.delete(instance);
    }

    @Test
    public void testBasicOperations() {
        // TODO
    }

    /////////// below here is automatically generated boilerplate

    @Test
    public void myByteSetAndGetTest() {
        instance.setMyByte(Byte.MAX_VALUE);
        assertEquals(Byte.MAX_VALUE, instance.getMyByte());
    }

    @Test
    public void myCharSetAndGetTest() {
        instance.setMyChar('Ø');
        assertEquals('Ø', instance.getMyChar());
    }

    @Test
    public void myShortSetAndGetTest() {
        instance.setMyShort(Short.MAX_VALUE);
        assertEquals(Short.MAX_VALUE, instance.getMyShort());
    }

    @Test
    public void myIntSetAndGetTest() {
        instance.setMyInt(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, instance.getMyInt());
    }

    @Test
    public void myFloatSetAndGetTest() {
        instance.setMyFloat(Float.MAX_VALUE);
        assertEquals(Float.MAX_VALUE, instance.getMyFloat());
    }

    @Test
    public void myLongSetAndGetTest() {
        instance.setMyLong(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, instance.getMyLong());
    }

    @Test
    public void myDoubleSetAndGetTest() {
        instance.setMyDouble(Double.MAX_VALUE);
        assertEquals(Double.MAX_VALUE, instance.getMyDouble());
    }

    @Test
    public void myByteFixArraySetAndGetTest() {
        byte[] testValue = new byte[4];
        Arrays.fill(testValue, Byte.MAX_VALUE);
        instance.setMyByteFixArray(testValue);
        assertArrayEquals(testValue, instance.getMyByteFixArray());
    }

    @Test
    public void myCharFixArraySetAndGetTest() {
        char[] testValue = new char[4];
        Arrays.fill(testValue, 'Ø');
        instance.setMyCharFixArray(testValue);
        assertArrayEquals(testValue, instance.getMyCharFixArray());
    }

    @Test
    public void myShortFixArraySetAndGetTest() {
        short[] testValue = new short[4];
        Arrays.fill(testValue, Short.MAX_VALUE);
        instance.setMyShortFixArray(testValue);
        assertArrayEquals(testValue, instance.getMyShortFixArray());
    }

    @Test
    public void myIntFixArraySetAndGetTest() {
        int[] testValue = new int[4];
        Arrays.fill(testValue, Integer.MAX_VALUE);
        instance.setMyIntFixArray(testValue);
        assertArrayEquals(testValue, instance.getMyIntFixArray());
    }

    @Test
    public void myLongFixArraySetAndGetTest() {
        long[] testValue = new long[4];
        Arrays.fill(testValue, Long.MAX_VALUE);
        instance.setMyLongFixArray(testValue);
        assertArrayEquals(testValue, instance.getMyLongFixArray());
    }

    @Test
    public void myFloatFixArraySetAndGetTest() {
        float[] testValue = new float[4];
        Arrays.fill(testValue, Float.MAX_VALUE);
        instance.setMyFloatFixArray(testValue);
        assertArrayEquals(testValue, instance.getMyFloatFixArray());
    }

    @Test
    public void myDoubleFixArraySetAndGetTest() {
        double[] testValue = new double[4];
        Arrays.fill(testValue, Double.MAX_VALUE);
        instance.setMyDoubleFixArray(testValue);
        assertArrayEquals(testValue, instance.getMyDoubleFixArray());
    }

    @Test
    public void myByteVarArrayElementCountSetAndGetTest() {
        instance.setMyByteVarArrayElementCount(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, instance.getMyByteVarArrayElementCount());
    }

    @Test
    public void myByteVarArraySetAndGetTest() {
        byte[] testValue = new byte[4];
        Arrays.fill(testValue, Byte.MAX_VALUE);
        instance.setMyByteVarArray(testValue);
        assertArrayEquals(testValue, instance.getMyByteVarArray());
    }

    @Test
    public void myCharVarArrayElementCountSetAndGetTest() {
        instance.setMyCharVarArrayElementCount(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, instance.getMyCharVarArrayElementCount());
    }

    @Test
    public void myCharVarArraySetAndGetTest() {
        char[] testValue = new char[4];
        Arrays.fill(testValue, 'Ø');
        instance.setMyCharVarArray(testValue);
        assertArrayEquals(testValue, instance.getMyCharVarArray());
    }

    @Test
    public void myShortVarArrayElementCountSetAndGetTest() {
        instance.setMyShortVarArrayElementCount(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, instance.getMyShortVarArrayElementCount());
    }

    @Test
    public void myShortVarArraySetAndGetTest() {
        short[] testValue = new short[4];
        Arrays.fill(testValue, Short.MAX_VALUE);
        instance.setMyShortVarArray(testValue);
        assertArrayEquals(testValue, instance.getMyShortVarArray());
    }

    @Test
    public void myIntVarArrayElementCountSetAndGetTest() {
        instance.setMyIntVarArrayElementCount(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, instance.getMyIntVarArrayElementCount());
    }

    @Test
    public void myIntVarArraySetAndGetTest() {
        int[] testValue = new int[4];
        Arrays.fill(testValue, Integer.MAX_VALUE);
        instance.setMyIntVarArray(testValue);
        assertArrayEquals(testValue, instance.getMyIntVarArray());
    }

    @Test
    public void myLongVarArrayElementCountSetAndGetTest() {
        instance.setMyLongVarArrayElementCount(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, instance.getMyLongVarArrayElementCount());
    }

    @Test
    public void myLongVarArraySetAndGetTest() {
        long[] testValue = new long[4];
        Arrays.fill(testValue, Long.MAX_VALUE);
        instance.setMyLongVarArray(testValue);
        assertArrayEquals(testValue, instance.getMyLongVarArray());
    }

    @Test
    public void myFloatVarArrayElementCountSetAndGetTest() {
        instance.setMyFloatVarArrayElementCount(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, instance.getMyFloatVarArrayElementCount());
    }

    @Test
    public void myFloatVarArraySetAndGetTest() {
        float[] testValue = new float[4];
        Arrays.fill(testValue, Float.MAX_VALUE);
        instance.setMyFloatVarArray(testValue);
        assertArrayEquals(testValue, instance.getMyFloatVarArray());
    }

    @Test
    public void myDoubleVarArrayElementCountSetAndGetTest() {
        instance.setMyDoubleVarArrayElementCount(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, instance.getMyDoubleVarArrayElementCount());
    }

    @Test
    public void myDoubleVarArraySetAndGetTest() {
        double[] testValue = new double[4];
        Arrays.fill(testValue, Double.MAX_VALUE);
        instance.setMyDoubleVarArray(testValue);
        assertArrayEquals(testValue, instance.getMyDoubleVarArray());
    }

    @Test
    public void myStringVarArrayElementCountSetAndGetTest() {
        instance.setMyStringVarArrayElementCount(Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, instance.getMyStringVarArrayElementCount());
    }

    @Test
    public void myStringVarArraySetAndGetTest() {
        byte[] testValue = new byte[16];
        Arrays.fill(testValue, Byte.MAX_VALUE);
        instance.setMyStringVarArray(testValue);
        assertArrayEquals(testValue, instance.getMyStringVarArray());
    }

    @Test
    public void myStringSetAndGetTest() {
        String testValue = new String("TTTTTTTTTTTTTTTT");
        instance.setMyString(testValue);
        assertEquals(testValue, instance.getMyString());
    }

}
