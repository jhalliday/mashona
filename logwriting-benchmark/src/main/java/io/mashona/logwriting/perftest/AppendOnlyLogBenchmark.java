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

import io.mashona.logwriting.AppendOnlyLog;
import io.mashona.logwriting.AppendOnlyLogImpl;
import jdk.nio.mapmode.ExtendedMapMode;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
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

/**
 * JMH benchmarking code for writing to an AppendOnlyLog.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-09
 */
@State(Scope.Benchmark)
public class AppendOnlyLogBenchmark {

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

    private static File file = new File(System.getenv("PMEM_TEST_DIR"), "AppendOnlyLogBenchmark");

    private static final int length = 1024 * 1024 * 512;

    private FileChannel fileChannel;
    private MappedByteBuffer mappedByteBuffer;
    private AppendOnlyLog appendOnlyLog;

    @Param({"1801"})
    public int dataSize;

    @Param({"false", "true"})
    public boolean blockPadding;

    private byte[] data;

    @State(Scope.Thread)
    @AuxCounters(AuxCounters.Type.OPERATIONS)
    public static class OpCounters {
        public long write;
        public long reset;
    }

    private void deleteFile() {
        if (file.exists()) {
            file.delete();
        }
    }

    @Setup(Level.Iteration)
    public void setUp() throws IOException {

        deleteFile();

        data = new byte[dataSize];

        fileChannel = (FileChannel) Files
                .newByteChannel(file.toPath(), EnumSet.of(
                        StandardOpenOption.READ,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE));

        mappedByteBuffer = fileChannel.map(ExtendedMapMode.READ_WRITE_SYNC, 0, length);

        appendOnlyLog = new AppendOnlyLogImpl(mappedByteBuffer, 0, length, blockPadding, false);

        Arrays.fill(data, (byte)-1);
    }

    @TearDown(Level.Iteration)
    public void tearDown() throws Exception {

        // https://bugs.openjdk.java.net/browse/JDK-4724038
        unsafe.invokeCleaner(mappedByteBuffer);

        fileChannel.close();

        deleteFile();
    }

    @Benchmark()
    @BenchmarkMode(Mode.Throughput)
    public void writeLog(OpCounters counters) {

        if(appendOnlyLog.tryPut(data)) {
            counters.write++;
        } else {
            synchronized (this) {
                if(!appendOnlyLog.canAccept(data.length)) {
                    appendOnlyLog.clear();
                    counters.reset++;
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(AppendOnlyLogBenchmark.class.getSimpleName())
                .forks(0) // use 0 for debugging in-process
                .build();
        new Runner(opt).run();
    }
}