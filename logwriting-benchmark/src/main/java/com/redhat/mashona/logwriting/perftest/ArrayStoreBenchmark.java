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
package com.redhat.mashona.logwriting.perftest;

import com.redhat.mashona.logwriting.ArrayStore;
import com.redhat.mashona.logwriting.ArrayStoreImpl;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JMH benchmarking code for writing to a MappedFileChannel.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-09
 */
@State(Scope.Benchmark)
public class ArrayStoreBenchmark {

    private static File file = new File("/mnt/pmem/test/ArrayStoreBenchmark");

    private static final byte[] data = new byte[1801];

    private ArrayStore arrayStore;

    private void deleteFile() {
        if (file.exists()) {
            file.delete();
        }
    }

    @State(Scope.Thread)
    public static class ThreadId {
        public static AtomicInteger idAllocator = new AtomicInteger(0);
        public final int slot = idAllocator.getAndAdd(1);
    }

    @Setup(Level.Iteration)
    public void setUp() throws IOException {

        deleteFile();

        arrayStore = new ArrayStoreImpl(file, 100, data.length);

        Arrays.fill(data, (byte)-1);
    }

    @TearDown(Level.Iteration)
    public void tearDown() throws Exception {

        arrayStore.close();

        deleteFile();
    }

    @Benchmark()
    @BenchmarkMode(Mode.Throughput)
    public void writeLog(ThreadId threadId) throws IOException {

        int slotIndex = threadId.slot;
        arrayStore.write(slotIndex, data);
        arrayStore.clear(slotIndex, false);
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(ArrayStoreBenchmark.class.getSimpleName())
                .forks(0) // use 0 for debugging in-process
                .build();
        new Runner(opt).run();
    }
}
