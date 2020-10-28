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

import com.redhat.mashona.logwriting.MappedFileChannel;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * JMH benchmarking code for writing to a MappedFileChannel.
 *
 * @author Jonathan Halliday (jonathan.halliday@redhat.com)
 * @since 2020-09
 */
@State(Scope.Benchmark)
public class MappedFileChannelBenchmark {

    private static File file = new File(System.getenv("PMEM_TEST_DIR"), "MappedFileChannelBenchmark");

    private static final int length = 1024 * 1024 * 512;

    @Param({"1801"})
    public int dataSize;

    private byte[] data;

    private MappedFileChannel mappedFileChannel;

    @State(Scope.Thread)
    @AuxCounters(AuxCounters.Type.OPERATIONS)
    public static class OpCounters {
        public long write;
        public long reset;
    }

    private void deleteFile() throws IOException {
        if (file.exists()) {
            file.delete();
            MappedFileChannel.getMetadataFile(file).delete();
        }
    }

    @Setup(Level.Iteration)
    public void setUp() throws IOException {

        data = new byte[dataSize];

        deleteFile();

        mappedFileChannel = new MappedFileChannel(file, length);

        Arrays.fill(data, (byte)-1);
    }

    @TearDown(Level.Iteration)
    public void tearDown() throws Exception {

        mappedFileChannel.close();
        mappedFileChannel.deleteMetadata();

        deleteFile();
    }

    @Benchmark()
    @BenchmarkMode(Mode.Throughput)
    public void writeLog(OpCounters counters) throws IOException {

        if(data.length == mappedFileChannel.write(ByteBuffer.wrap(data))) {
            counters.write++;
        } else {
            synchronized (this) {
                if(mappedFileChannel.position() != 0) {
                    mappedFileChannel.clear();
                    counters.reset++;
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Options opt = new OptionsBuilder()
                .include(MappedFileChannelBenchmark.class.getSimpleName())
                .forks(0) // use 0 for debugging in-process
                .build();
        new Runner(opt).run();
    }
}
