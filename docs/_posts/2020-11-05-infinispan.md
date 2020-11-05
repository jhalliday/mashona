---
layout: post
title: "Infinispan persistence performance tests"
date: 2020-11-05
---

The pre-release performance testing and tuning of the mashona [logwriting](https://github.com/jhalliday/mashona/tree/main/logwriting) module continues with a look at how it accelerates the Infinispan data grid.

## Background

[Infinispan](https://infinispan.org/) is a distributed in-memory key/value data store written in Java.
For these tests we're running it as an embedded library, though it's also accessible remotely with a variety of language-independent network protocols.

Infinispan can be used as a cache, but does much more than that.
When configured with a persistent store, it is a nosql database.

Whilst reads can be served from memory, writes to a persistent data grid node must be written to disk to ensure recovery in the event of failures.
This can can be a significant bottleneck when the data is changing rapidly, especially where updates must be totally reliable, as this requirement means frequent flushes of modified data from O/S cache to persistent storage devices.

As with the similar [requirement in Narayana](https://jhalliday.github.io/mashona/blog/2020/10/29/narayana), this looks like a good use case for persistent memory, so let's see what we can do to relieve the persistence bottleneck.

## Persistent store integration

Infinispan has several [persistent store options](https://infinispan.org/docs/stable/titles/configuring/configuring.html#cache_store_implementations).
For these experiments we're using the Soft-Index File Store (SIFS), a disk based variant which holds an in-memory index to on-disk values, written using sequential log writes.

The SIFS implementation internally uses [FileChannel](https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/nio/channels/FileChannel.html) as its API to the persistent data files,
employing a `FileChannel.force()` call to invoke the fsync syscall to ensure persistence.
This makes mashona's `MappedFileChannel` a good fit for the integration.
As `MappedFileChannel extends FileChannel`, only the two points in the code where the channel is opened need to be modified. The other methods in the persistence code need not be aware if they are running on persistent memory or conventional storage devices. 

## Test results

For these benchmarks, we use an embedded single-node cache configured with Soft-Index File Store persistence in immediate mode i.e. with `syncWrites(true)`.
We place the persistence store on a variety of devices and measure the update rate that can be achieved.

```
threads     FlashSSD    OptaneSSD
      1         3880         5499
      4        11963        15587
     16        28299        73288
```

As with the Narayana tests, the system is using an Intel [D3-S4610](https://www.intel.co.uk/content/www/uk/en/products/memory-storage/solid-state-drives/data-center-ssds/d3-series/d3-s4610-series/d3-s4610-480gb-2-5inch-3d2.html) flash SSD against an Intel [DC P4800X](https://www.intel.co.uk/content/www/uk/en/products/memory-storage/solid-state-drives/data-center-ssds/optane-dc-ssd-series/optane-dc-p4800x-series/p4800x-750gb-2-5-inch.html) Optane SSD.
The 3D-XPoint technology gives a clear advantage, but it's limited by the complexity of the path between the CPU and the drive.

Let's try putting the stores on a single Optane DC PMM module, with the file system in sector mode.
This configuration offers the same filesystem block level persistence guarantees as regular drives, but moves the storage from the I/O path to the memory bus.

```
threads     FlashSSD    OptaneSSD    OptaneDCPMM
      1         3880         5499           7010
      4        11963        15587          17710
     16        28299        73288          68696
```

Here we see a substantial advantage at first due to the lower latency, but as the load increases the greater buffering and internal striping of the drive gives it an advantage.
To keep the DC PMM option ahead we'd need to interlace (i.e. stripe) multiple modules, as the drive does internally.

So far we're still going via the kernel's fsync syscall to flush O/S caches and guarantee persistence.
However, much of the advantage of persistent memory comes from DAX mode, which is not available to disk drives.

Using DAX mode in conjunction with the [JEP-352](https://jhalliday.github.io/mashona/blog/2020/03/16/jep-352) changes added in JDK 14, we can flush direct from user space without the cost of a system call.
On storage as fast as the Optane modules the syscall cost is significant, so eliminating it from the path should help a lot.
This is what mashona's MappedFileChannel is designed to do.

```
threads     FlashSSD    mashona    mult
      1         3880      16355    4.2x
      4        11963      50991    4.3x
     16        28299     162930    5.8x
```

This approach utilises much more of the hardware's potential!

The legacy assumptions of the store design still result in append-only sequential writes that don't take full advantage of the hardware parallelism.
Even so, it's a big improvement for very little integration work.
A store designed specifically for persistent memory could likely achieve even better results, but at the cost of much greater engineering effort.

## Conclusion

With minimal code change to integrate the mashona logwriting library into Infinispan,
we can substantially relieve the persistence bottleneck and allow the data grid to support use cases that were previously beyond reach.

The **6x** improvement in performance over flash SSD for this use case, whilst very good, is more modest than the **10x** [seen](https://jhalliday.github.io/mashona/blog/2020/10/29/narayana) with the Narayana use case.
A tailored design with more extensive code changes has the potential to raise performance still further.

Even so, Infinispan, and by extension the Wildfly application server and other projects that use it,
are well-prepared to make use of the new hardware, thanks to our JDK contributions and the mashona library.