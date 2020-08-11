# mashona
The first Persistent Memory library for pure Java

## Background

TL;DR: We've added native support for Persistent Memory to the JDK, then built this library on top of it.

Persistent memory brings the ability to use byte addressable persistent storage in our programs.
This means using CPU load/store instructions, not I/O read/write methods, to operate on persistent data.
For background on the new programming model see <https://www.snia.org/PM> or <https://www.usenix.org/system/files/login/articles/login_summer17_07_rudoff.pdf> 

Some building blocks for working with this style of storage are already available.
<https://pmem.io/pmdk/> provides C libraries for using persistent memory operations in your native applications,
whilst <https://developers.redhat.com/blog/2016/12/05/configuring-and-using-persistent-memory-rhel-7-3/> describes how to set up the operating system to run them.

Just one small snag for Java programmers: you can't issue arbitrary CPU instructions directly from Java code.
So to take advantage of this new programming model, you need to call out to C functions. That generally means using JNI.

Some open source Java libraries provide JNI based access to Persistent Memory, with useful high level abstractions layered on top.
These are <https://github.com/pmem/pcj> , <https://github.com/pmem/llpl> and <https://mnemonic.apache.org/>
Like the PMDK they build on, these projects originate from Intel.

These libraries are, roughly speaking, all layered over the same low level model : memory-map a file in DAX mode, allowing persistent writes direct from user space,
thus taking the system call overhead off the write path and making the most of the raw performance available from the new hardware.

The parts that, until JEP-352, required C code are: establishing the memory map in DAX mode and flushing the mapped memory to the persistence domain without requiring a system call.
From Java 14 onwards, those building blocks are provided by MappedByteBuffer. https://openjdk.java.net/jeps/352 has the full story.

However, the support in the JDK itself is very low level - it provides only the fast path (userspace) persistence mechanism.
That's by design, since it's the only part that needs C code and can't be done in Java. By minimising the JDK changes we can get them done faster,
then iterate on higher level support independently of the JDK change control and release cycle.

Programming directly against the low level API is hard work.
There are subtle correctness and performance considerations that are tricky to work with.
We need a library of higher level abstractions.
In terms of PMDK, the functionality of libpmem is now in the JVM, whilst we need libraries to replace libpmemobj and other components that sit above those low level primitives.
e.g. the log, heap and transaction elements from libpmemobj (and llpl/pcj via JNI) currently have no pure Java equivalent.

The log is the first abstraction we add in Java, since it's the most useful for our immediate use cases. Other parts may follow.

Details of the log design and notes on its usage can be found in the [logwriting](logwriting/) module.
Experiments with persistent objects are discussed in the [pobj](pobj/) module.
The docs directory, despite the misleading name, actually contains the project blog. Blame github-pages for that one...

## Try it out

As of Java 14 the changes from JEP-352 are available, so Java versions from this time onward are suitable for experimenting with persistent memory.

Install the JDK, then edit the mashona pom.xml to change the jdk.dir property to point at this JDK.

Then you need some persistent memory, or fake it with mmapped DRAM.
See the 'Experimenting without NVDIMMs' section of <https://developers.redhat.com/blog/2016/12/05/configuring-and-using-persistent-memory-rhel-7-3/>
for how to configure the kernel to mmap a chunk of RAM to simulate pmem.

Edit the pom.xml again, to change the pmem.test.dir property to point at a directory on your pmem mount.

Now you can build and run as usual:

```
mvn clean package
```

## Roadmap

Adoption of Persistent Memory by Java applications is anticipated to proceed in three steps:

Hardware only. A simple upgrade similar to replacing HDD with SSD, which will provide some speedup without requiring software modifications beyond the O/S.
However, this leaves a lot of performance untapped, as the hardware path is now fast enough that the software overhead is significant.

'pmem aware' applications. Software changes that allow for detection and optimized use of pmem hardware for storage when available, whilst remaining able to run on legacy storage.
This is the current sweet spot, requiring only modest code changes to gain further substantial performance benefits.
This is the use case that the AppendOnlyLog and MappedFileChannel target.
By using DAX to remove the kernel msync/fsync call from the critical path, greater performance is achieved for the write path.

'pmem native' applications. With more extensive work, applications can place advanced data structures directly on pmem, gaining even greater performance benefits, particularly to recovery time after startup.
This is an active research area. Java's [Project Panama](https://openjdk.java.net/projects/panama/) is relevant here, as is more experimental Oracle labs work to make Java Objects natively persistent.
Third party efforts in this direction include e.g. [Espresso](https://arxiv.org/abs/1710.09968) and [AutoPersist](https://dl.acm.org/citation.cfm?id=3314221.3314608).

The [PCJ](https://github.com/pmem/pcj) library also aims at this space, providing a high level persistent collections library.
It's layered on a lower level model of persistent heap allocations, provided by [PMDK's](https://github.com/pmem/pmdk) libpmemobj via JNI.
The primitives for memory management and transactions currently provided by libpmemobj would need to be recreated in pure Java,
at which point the JNI layer of PCJ/LLPL could be replaced whilst the Java layer above remained, providing a clean migration path.

However, the programming model proposed by these libraries has shortcomings.

It's tedious and error-prone to hand-code the persistence and memory management code for objects.
This can be addressed to some extent by tooling and frameworks, similarly to e.g. how JPA solutions such as hibernate do persistence for EJBs.

More seriously, memory in the persistent heap is subject to fragmentation over time.
Without managed pointers, it's not possible to address this with heap compaction solutions, as memory is not relocatable.
As this problem currently appears intractable, it is anticipated that the approach may have limited utility.

Solutions in which the memory is more closely managed may have more success.
A Key/Value store, for example, can relocate its own entries if it's the only structure holding direct references to them.

This project therefore has two objectives:

Firstly, to provide stable, performant, production quality library of abstractions for pmem aware applications,
with the initial focus on Java middleware such as databases and message queues.
At present the [logwriting](logwriting/README.md) module addresses this.

Secondly, to provide more experimental high level abstractions for pmem native coding.
At present these are in the [pobj](pobj/README.md) module.
