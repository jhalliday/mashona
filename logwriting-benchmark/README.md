# Mashona logwriting benchmarks module.

This module contains performance testing code for the logwriting module.

For a top-level overview, start with the project's [README](../README.md) and then review the [logwriting module](../logwriting) before continuing here.

## Testing Approach

The benchmarking framework [JMH](https://github.com/openjdk/jmh) is used exercise each of the three binary log implementations provided by the logwriting module.
The benchmarks test only write performance, as this is more important than read performance for typical use cases.

The tests create and then repeatedly fill and clear a log, using a data buffer of configurable size.

In addition, a test is provided to exercise raw device write performance using the JDK standard library but not the log implementations. This provides an expected upper bound for comparison.

## Running the tests

Edit the pom's dependency section to reference the logwriting module version you wish to test. Then
```
mvn clean package
```
to build the tests. Available tests can be listed:
```
java -jar target/benchmarks.jar -l
Benchmarks:
AppendOnlyLogBenchmark.writeLog
ArrayStoreBenchmark.writeLog
MappedFileChannelBenchmark.writeLog
SimpleHardwareBenchmark.writeLog
```
#### Framework configuration

Individual tests can selected by name pattern.

Number of threads can be specified with `-t N`

For other generic test harness config options, run
```
java -jar target/benchmarks.jar -h
```

#### Test specific configuration

You must specify a DAX enabled location in which to write the data files using an environment variable.

The size in bytes of the log entry to write can be given as a parameter.

#### Example

```
PMEM_TEST_DIR=/mnt/pmem/test java -jar target/benchmarks.jar -p dataSize=1024 ArrayStoreBenchmark
```

## Expected Results

YMMV depending on hardware, O/S, JVM version and other factors. Here are some general points to consider.

#### Hardware capability

Intel don't publish official performance specifications for their Optane DCPMM hardware.
However, there are 3rd party studies including
- [Basic Performance Measurements of the Intel Optane DC Persistent Memory Module (NVSL)](https://arxiv.org/pdf/1903.05714.pdf)
- [System Evaluation of the Intel Optane Byte-addressable NVM (Lawrence Livermore National Laboratory)](https://arxiv.org/pdf/1908.06503.pdf)
- [An Empirical Guide to the Behavior and Use of Scalable PersistentMemory (NVSL)](https://arxiv.org/pdf/1908.03583.pdf), which also appeared in [FAST20](https://www.usenix.org/system/files/fast20-yang.pdf)

From these we can see the expected write bandwidth of a single module is around `2.3 GB/s`, though achieving that performance requires using multiple threads to exploit the module's internal concurrency.
For a single thread the best that can be expected is around `1.5 GB/s`. For interlaced (similar to RAID-0 striping) aggregates of multiple modules, these numbers increase according to the number of modules.

Unfortunately the studies don't publish their test harness, so reproducing these results independently is challenging.
However, a crude approximation is possible with basic tools such as `dd` or `fio`.
For a single (non-interlaced) module, fio with multiple threads shows `WRITE: bw=1642MiB/s (1722MB/s)`, which is in the right ballpark given that fio is not DAX aware.

Note that on multi-socket systems, NVM devices that don't have a cross-socket interlacing are particularly sensitive to NUMA effects. For example, we can see that cross-socket traffic costs roughly 2x the local cost:
```
numactl -H
...
node distances:
node   0   1
  0:  10  21
  1:  21  10

numactl -N 1 -m 1 dd if=/dev/zero of=/mnt/pmem1/test oflag=direct bs=64M count=1600
107374182400 bytes (107 GB, 100 GiB) copied, 123.529 s, 869 MB/s

numactl -N 0 -m 0 dd if=/dev/zero of=/mnt/pmem1/test oflag=direct bs=64M count=1600
107374182400 bytes (107 GB, 100 GiB) copied, 251.8 s, 426 MB/s

```
It is therefore worthwhile running the benchmarks with appropriate numactl settings to ensure consistent, comparable results between runs.

The DAX support in the JDK can be exercised with `SimpleHardwareBenchmark`
Using multiple (socket-local) threads to write 1MB chunks (so 'Score' is 'MB/s') on a non-interlaced device with dax mode file mapped with `FileChannel.MapMode.READ_WRITE` (i.e. via msync syscalls)
```
Benchmark                           Mode   Cnt      Score    Error  Units
SimpleHardwareBenchmark.writeLog   thrpt     5   1298.167 ± 81.731  ops/s
```
Compared to the `ExtendedMapMode.READ_WRITE_SYNC` (user space writes with CLWB)
```
Benchmark                           Mode   Cnt      Score    Error  Units
SimpleHardwareBenchmark.writeLog   thrpt     5   1514.310 ±  8.343  ops/s
```

These numbers provide an indicative upper limit on the performance that can be expected from the log implementations.

#### Log implementations

There are three different binary log implementations in the mashona library.
These exhibit different performance characteristics.

AppendOnlyLog and MappedFileChannel are both traditional linear logs and as such are essentially single-threaded.
These implementations are not expected to exhibit scaling with number of threads in microbenchmarks.
Whilst broadly similar in functionality, AppendOnlyLog is somewhat more efficient.
By storing integrity metadata in-band with the data entries rather than in a separate file as MappedFileChannel does,
AppendOnlyLog is able to operate with fewer force (i.e. flush+fence) calls.

ArrayStore is a more specialised log, intended for cases where ordering of entries is not critical.
The store pre-allocates a number of spaces of configurable size, which may then be concurrently written to.
This allows for shared use by multiple threads which care about write ordering of their own log entries, but not the ordering relative to other threads.
ArrayStore therefore scales somewhat with number of concurrent threads, at the cost of requiring maximum size and maximum number of log entries to be specified in advance.
However, in the case of a non-interlaced namespace, only a modest number of threads is required to approach the write capacity of the hardware.

Sample Results for ArrayStore in 1 thread and 4 thread tests. Writing 1 MB sized entries means the 'Score' value can be read as 'MB/s', making it comparable to the raw SimpleHardwareBenchmark numbers seen above.
```
Benchmark                     (dataSize)   Mode  Cnt     Score     Error  Units
ArrayStoreBenchmark.writeLog     1048576  thrpt    5  1129.749 ±  47.386  ops/s # threads=1
ArrayStoreBenchmark.writeLog     1048576  thrpt    5  1367.269 ± 164.587  ops/s # threads=4
```

### Summary

The benchmarks in this module allow us to build a picture of how log writing performs on persistent memory devices.
For the single (non-interlaced) DCPMM case, where the threads are running on the same socket as the storage:

- Reported write bandwidth, not reproducible in testing: 2.3 GB/s.
- Observed write bandwidth using system tools (not DAX aware): 1.7 GB/s.
- Observed write bandwidth, non-DAX Java: 1.3 GB/s.
- Observed write bandwidth, DAX Java: 1.5 GB/s.
- ArrayStore log write bandwidth: 1.37 GB/s
