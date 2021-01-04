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
package io.mashona.logwriting.perftest;

import jdk.nio.mapmode.ExtendedMapMode;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import sun.misc.Unsafe;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMH benchmarking code for writing to a MappedByteBuffer.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-10
 */
@State(Scope.Benchmark)
public class SimpleHardwareBenchmark {

    static Unsafe unsafe;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static File file = new File(System.getenv("PMEM_TEST_DIR"), "SimpleHardwareBenchmark");

    private static final int length = 1024 * 1024 * 100;

    private FileChannel fileChannel;
    private MappedByteBuffer mappedByteBuffer;

    private static final byte[] data = new byte[1024*1024*10];

    private void deleteFile() {
        if (file.exists()) {
            file.delete();
        }
    }

    @Setup(Level.Iteration)
    public void setUp() throws IOException {

        deleteFile();

        fileChannel = (FileChannel) Files
                .newByteChannel(file.toPath(), EnumSet.of(
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE));

        mappedByteBuffer = fileChannel.map(ExtendedMapMode.READ_WRITE_SYNC, 0, length);
        //mappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, length);

        Arrays.fill(data, (byte)-1);
    }

    @TearDown(Level.Iteration)
    public void tearDown() throws Exception {

        // https://bugs.openjdk.java.net/browse/JDK-4724038
        unsafe.invokeCleaner(mappedByteBuffer);

        fileChannel.close();

        deleteFile();
    }

    @State(Scope.Thread)
    public static class ThreadId {
        public static AtomicInteger idAllocator = new AtomicInteger(0);
        public final int offset = idAllocator.getAndAdd(1) * data.length;
    }

    @Benchmark()
    @BenchmarkMode(Mode.Throughput)
    public void writeLog(ThreadId threadId) {
        mappedByteBuffer.put(threadId.offset, data, 0, data.length);
        mappedByteBuffer.force(threadId.offset, data.length);
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(SimpleHardwareBenchmark.class.getSimpleName())
                .forks(0) // use 0 for debugging in-process
                .measurementTime(TimeValue.hours(1))
                .threads(4)
                .build();
        new Runner(opt).run();
    }
}
