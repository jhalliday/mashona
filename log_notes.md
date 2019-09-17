# Append-Only Log notes
The binary append-only log is a key data structure for many use cases, including databases and message queues.
This document covers some of the design decisions applicable to mashona's implementation(s) and highlights key points where caution is necessary for users.

## Building blocks
Like all pmem backed abstractions, the append-only log must be built on MappedByteBuffer, since that's how the Java runtime exposes pmem to the programmer.
MappedByteBuffer's persistence guarantees on pmem are weak. Specifically, arbitrary writes and not persistence atomic.
This necessitates adding metadata such as flags or checksums to make data writes survive power failures cleanly,
a detail which log implementations should mask from their users.

So, how to wrap an unreliable MappedByteBuffer in a reliable log-like interface?

## AppendOnlyLog
The AppendOnlyLog class provides a log abstraction suitable for green-field application implementations that will run only on pmem.
It aims to provide an elegant, minimal API that allows for an efficient implementation.

Abstractly, the conceptual design is a list of records (ByteBuffers), which can be appended to, iterated over, or deleted.

This conspicuously excludes many features. Note there is no record numbering or indexed read access.

The minimalism of the API allows flexibility in the implementation.
For example, the nature and location of metadata is not exposed to the user.
These library implementation details can be changed to allow optimal performance, without requiring changes to user's code.
e.g. In the future, record write-completion flags may be used in place of checksums, as the internal structure of the log is not exposed.
Or, records may be padded to hardware block boundaries, trading space for time.
Though that one actually is user visible, explicitly offered as a configuration tuning parameter.

### Basics of safe usage
Despite the minimal API, the abstraction still requires some care to use correctly.
Most essential, the starting location in memory must be correctly aligned to the underlying hardware.

For performance and simplicity, the implementation relies on 8 byte aligned writes being persisence atomic. When calculating where to put these, it further assumes that the base of the provided buffer is aligned.
Writes to pmem are persistence atomic only at the hardware instruction level - putLong as single 8 byte write instruction is fine, two 4 byte writes are not.
MappedByteBuffer's internal implementation will do the right thing and execute aligned writes as a single instruction, so that just leaves the problem of ensuring the buffer is aligned.

### Partially ordered writes

The user can select the write ordering semantics they require, trading weakened guarantees for greater performance.
An append-only log traditionally provided strict write order as a consequence of an implementation detail:
To minimise expensize HDD head seeks, writes were always strictly serial.
However, this is unnecessarily limiting to modern storage devices (e.g. SSD and pmem) which have considerable internal concurrency.

Therefore, the log makes it possible to only reserve the required space with a sequential (mutexed) operation, then
write the actual record content concurrently. This may be appropriate where writes by a given Thread must be ordered with
respect to one another, but need not be with respect to writes from other Threads. The write API is unchanged,
as only the internal locking is affected.
For reads, the iterator will detect and skip partial or missing records resulting from later records having been completely flushed whilst earlier ones were not.

## MappedFileChannel

When modifying existing applications to use pmem, accomodating the AppendOnlyLog API may require extensive modifications.
These can be particularly painful in cases where the application must also continue to support conventional storage,
as it may necessitate maintaining two code paths for the storage I/O.

To address this use case, the library also offers MappedFileChannel, a partial implementation of Java's FileChannel API over pmem.

For existing applications that currently use FileChannel for their log, a MappedFileChannel can provide a smoother integration path,
allowing for a Factory style arrangement to select between pmem and conventional implementations at runtime, according to configuration or hardware probing.

The FileChannel API is a more leaky abstraction for an append-only log than is the bespoke AppendOnlyLog.
In our implementation, many methods are unimplemented i.e. throw an exception if called, whilst others have
usage restrictions that do not exist in the standard library's FileChannel implementation.

### Location, location, location

The FileChannel API allows for indexed access, i.e. reads or writes at a given offset within the channel.
In order to accomodate this requirement on pmem, two different implementations are possible.

Firstly, to place required metadata in-band in the backing file, with appropriate offset adjustment applied to the
user-visible (API level) index values to translate to internal index values in such a way as to mask the maetadata from the user.
This scheme is somewhat complex, but is rejected largely for another reason: it makes the internal structure of the
backing file differ compared to that of the same file written by the same application code via the regular FileChannel.
This presents substantial operational challenges, for example when migrating or replicating logs in either direction between
conventional file systems and pmem backed DAX file systems.

The chosen implementation instead places metadata in a second file in the same directory, with a naming convention pairing it to the data file.
This approach still leaves some smaller difficulties when moving content between storage devices, but is considerably simpler than the alternative.

### Size matters

A conventional FileChannel starts at zero length and grows as data is added, with capacity limited only by the underlying storage device or quota.
The size as seen by the file system (e.g. shell 'ls' or 'dir' commands), the File.length() method and the FileChannel.size() method have the same value,
being the number of bytes written to the log.
Thus existing code may make use these methods of obtaining the file size interchangeably.

Even with the metadata removed to a separate file, handling this behaviour is still a challenge for adoption of MappedFileChannel.
The underlying MappedByteBuffer has a size fixed at creation, and may at that time cause the backing file to grow or shrink to match.
Thus even without pmem specific considerations, the size of the file no longer reflects the size of the data within when using a memory-mapping approach.
Users adopting MappedFileChannel to replace java.nio.channels.FileChannel must therefore take care to locate and verify the semantics of
file size calculations in their code.

## Direct ByteBuffer usage

Ironically, it is applications that already use memory-mapped IO for their logging which face the greatest difficulty in transitioning to MappedByteBuffer.
Since conventional memory-mapped operations are backed by the filesystem's block layer, they are persistence atomic at the filesystem block granularity (i.e. usually 4k).
Whilst MappedByteBuffer exposes the same API and is therefore an easy replacement at the code level, it offers very different persistence guarantees, as noted above.
Therefore, it may be necessary to provide e.g. AtomicMappedByteBuffer as an abstraction over MappedByteBuffer, to add back the expected failure atomicity guarantees.
Such an abstraction is not currently offered by this library.

## Case studies

The initial use cases cases for this library's log abstraction are to support pmem aware storage in existing Java middleware products.
These codebases must continue to support conventional storage devices for the foreseeable future, as widespread adoption
of pmem hardware will take some time. Further, they must continue to support older JDK releases that lack pmem support.
Therefore, MappedFileChannel is preferred to AppendOnlyLog.

### Apache ActiveMQ Artemis

https://github.com/apache/activemq-artemis/tree/master/artemis-journal
https://activemq.apache.org/components/artemis/documentation/latest/persistence.html

This JMS message broker uses the FileChannel API at the heart of its Journal implementation to support persistent messaging logging.
This make it relatively straightforward to substitute MappedFileChannel where the configured storage path is backed by a pmem fs DAX mount point.

Here we hit the first integration hurdles:

Artemis must both build and run on older JDK releases that don't support pmem.
To achieve this, the library bundles a Utility class that is compiled to Java8 bytecode and uses reflection
to access the Java14 classes for the log implementation if the platform supports it. This allows user to to
adopt the following idiom:

```
// first try for pmem optimized
FileChannel log = PmemUtil.pmemChannelFor(file, size);
if (channel == null) {
  // pmem unavailable, fall back to conventional IO:
  log = FileChannel.open(file);
}
```

Note also that, unlike the regular FileChannel, it's necessary to specify the maximum file size when creating
a MappedFileChannel, since the memory map backing space must be reserved. Although the Artemis log does already have a
maximum size config parameter, some additional code changes may be necessary to make this available at point of use.

Whilst the result of the platform version support test is cached, an additional per-invocation check is necessary to
determine if the provided file path is on a mount point that supports DAX mode.

Due to system level API limitations, the only way to know if a given file will support DAX mode IO,
is to try memory mapping it with the appropriate flags and catch the error if it fails.
(specifically, on linux mmap with MAP_SHARED_VALIDATE and MAP_SYNC will return EOPNOTSUPP. At the Java level you get an IOException)

The library does not currently cache the result of this check, nor allow the user to bypass it.
These options may be added in the future.
They are not critical, since log file creation is a relatively rare operation.

When creating a new log file backed by pmem, the library also creates a metadata file in the same directory.
This is used to hold information regarding the furthest extent in the log that is guaranteed to have been persisted.
When performing backup or replication tasks, it's important to copy this file also.

With the code now in place to create the pmem optimized log when possible, few additional code changes are required to complete the integration:

Artemis renames log files, which breaks the association with their corresponding metadata files.
This code now needs to test if the metadata file exists and to rename it also if it does.
There is no library support for this at present, which unfortunately means that the Artemis code needs to
know about what is essentially an implementation detail of the library.

The log files are explicitly pre-filled with zeros by Artemis, which the log library code sees as a write.
Since the log is append-only, it is then impossible to write actual data to it, since it's now considered full.
As the library code implicitly zeros the file anyhow, the fix is for full() to simply test
for pmem use and do nothing if appropriate. Unfortunately it's impossible for the log to distinguish a 'clear'
implemented in this manner from a legitimate write of a record of zeros, so it can't simply ignore the write.

### Infinispan Data Grid

https://github.com/infinispan/infinispan/tree/master/persistence/soft-index
https://infinispan.org/docs/stable/user_guide/user_guide.html#sifs_cache_store

The Infinispan in-memory data grid provides support for a persistent store, which may be used to repopulate memory
after restart, or to provide additional storage for datasets that don't fit entirely in RAM. This is done via
the soft-index file store, sifs.  Sifs uses FileChannel internally in a log arrangement similar to that found in
Artemis and therefore has many of the same integration issues. In addition to those, it introduces a further complication.

Since FileChannel is stateful, having an internal mutable position value that determines the offset for reads and writes,
it's unsafe for multi-threaded use without locking. To address this, Infinispan open multiple FileChannel instances over
the same backing file.

This problem first manifests at file creation, as a race condition exists in the initialization code.
sun.nio.ch.FileChannelImpl.map internally calls truncate(size) to make the file length match the required map size,
prior to attempting the mmap. If the mmap then fails, for example because the filesystem doesn't support DAX,
then the truncate() is not reverted, leaving the file with an unexpected length. Even if the user's exception handler
cleans this up, there exists a timing window in which other threads may attempt to read from the longer file and obtain unexpected results.

To address this, the library uses its pmem support check to validate DAX availability on a temporary test file before
mapping the user's requested file, thus masking the problem.

However, keeping multiple MappedFileChannels over the same file still presents challenges.

TODO finish me
