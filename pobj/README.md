# Mashona persistent object module.

For a top-level overview, start with the project's [README](../README.md) before continuing here.

This pobj module provides pure Java library support for working with persistent objects.
It is broadly modelled on PMDK's libpmemobj.

Note that, unlike the more mature logwriting module, this part of mashona is at an early experimental stage and not yet feature rich or stable enough for use in applications. 

## Persistent Object overview

There have been many different approaches to persisting Java objects over the years, from simple serialization to [PJama](https://www.cs.purdue.edu/homes/hosking/pjama.html) orthogonal persistence, object database systems and, most popularly, object-relational persistence abstraction via JPA implementations such as Hibernate ORM.

Recently the availability of persistent memory hardware is adding a new twist, by allowing for the possibility that objects can be made persistent with even less storage abstraction.

Making Java a true persistent object language is an interesting research field, as seen in [Espresso](https://arxiv.org/abs/1710.09968), [AutoPersist](http://jianh.web.engr.illinois.edu/papers/autopersist-pldi2019.pdf) and [Oracle Labs](https://pirl.nvsl.io/2019/10/22/adding-persistence-to-java/), but the JVM is as much a runtime as a JIT compiler and OpenJDK's update policy favours stability and backwards compatibility over features, so we're unlikely to see major moves in this direction unless persistent memory hardware becomes ubiquitous.
Footloose programmers seeking a modified language may be better off migrating to go-lang's experimental variant, [go-pmem](https://www.snia.org/educational-library/providing-native-support-byte-addressable-persistent-memory-golang-2020) ([code](https://github.com/jerrinsg/go-pmem), [paper](https://www.usenix.org/conference/atc20/presentation/george)).

Meanwhile, practical medium-term efforts in Java focus on providing object persistence support at the library and framework (i.e. middleware) level, with minimal and largely non-invasive changes to the runtime platform.

A simple solution is just to make the storage layer underlying relational databases aware of persistent memory.
Java applications can continue using the JPA without any changes, whilst the database becomes faster as a result of the hardware improvements.

Such a database-centric has the attraction of leaving in place the mature query language, transaction mechanism, schema management, security and other benefits of the existing software stack.
All those features come with a performance cost though, and for some use cases that's not a desirable tradeoff.


More direct models dispense with the mechanisms for declarative querying, schema management, security configuration and, to some extent, transactions.
In return, they provide a programming model closer to natural Java, with objects and familiar data structures such as lists and maps.

A good early example of this genre is [PCJ](https://github.com/pmem/pcj). It is not yet clear if this project's lack of traction stems from providing the wrong thing, or from providing it too early for the market.
It is clear, however, that Intel take an approach to supporting Java which leverages their existing investment in C, via JNI calls.

The same approach manifests in a more streamlined and targeted manner in [LLPL](https://github.com/pmem/llpl), a library which exposes the primitive building blocks of a persistent object system directly from the C library to Java with little additional abstraction along the way.
This is perhaps the most interesting starting point for examining the feasibility of building a persistent object system entirely in Java.

Unpacking LLPL's functionality, we see that the key components are a memory management system and a transaction system, underpinned by a persistent logging mechanism.
Importantly, whilst LLPL gets much of that functionality from C code via JNI, such division is largely a design decision rather than a necessity.

The only functionality that can't be ported from C to Java, is the flush+fence sequence that guarantees data is moved from the volatile CPU cache to the persistent memory, which requires specific CPU instructions.
Fortunately we built that capability into the Java 14 platform as part of [JEP-352](https://openjdk.java.net/jeps/352), so it's now theoretically possible to dispense with C code entirely.

Reuse of PMDK's C libraries benefits from the considerable development and tuning work they embody, but carries the costs of build/packaging/deployment complexity, and runtime overhead for the JNI calls.
By contrast, a pure Java approach requires more upfront work in redeveloping core functionality, but the eventual benefit of simpler use and possibility of faster execution as the JIT can see and optimise across the entire code path.

Here in mashona's pobj module, we're prototyping a pure Java approach to providing the building blocks for a persistent object framework.

Persistent object systems share many requirements with database internals and thus have similar solutions.
They require a storage engine to manage the space used by the object state, and a transaction system to coordinate reliable updates to that state.

## Memory Abstraction

The Java language, unlike its lower level ancestors C and C++, does not have direct object pointers. It has managed references that abstract the object location from the programmer.
This allows the runtime to manage memory differently. It can also provide garbage collection, since it knows where all the references are.
GC not only leaves the programmer with one less thing to worry about, it also helps address one of the most critical problems in storage management: space fragmentation.
Because the runtime can move objects around without the programmer noticing or caring, it can compact the heap to decrease fragmentation.

So at first glance it would seem beneficial if the persistent object system used location abstraction.
Space fragmentation is a particular problem for long-lived storage, and the persistent objects may have a considerably longer lifetime than those on volatile RAM.
A compacting GC would help address that.
It would also furnish Java programmers with a system for working with persistent objects which is close to that used for volatile objects, instead of requiring them to explicitly track and free object storage.
Whilst C/C++ programmers are accustomed to that burden, it's likely to come as a nasty shock to Java programmers.

Unfortunate then that LLPL is built on a persistence management library designed for C programmers.

Here we see an issue with the approach that's more limiting than the packaging inconvenience or performance overhead.
A Java solution that reuses a library designed for non-Java use, must also reuse the design decisions baked into that library, no matter how suitable they may be for the new use context.

However, building a heap GC for persistent memory is a non-trivial task. There are several mature and high performance GC implementations in the Java platform, but none written as a Java library that can be accessed by users.
That may change if Java-on-Java efforts such as GraalVM move more of the Java runtime internals into Java code, but for now it's an obstacle.
Furthermore, despite its ease of use, heap GC may not be the optimal approach anyhow.
A GC pass requires cycling much of the heap through CPU cache, which would be expensive for persistent memory hardware which is bigger, higher latency and more bandwidth constrained than DRAM.

## Change Management

Persistent state changes in the application require transaction support. The hardware provides write failure atomicity only for 8 byte aligned writes, which is inadequate for most use cases.
Furthermore, the hardware doesn't provide any isolation guarantees.
This leaves the average enterprise Java programmer a long way from the ease of use they are accustomed to with JTA for database transactions with full ACID properties.

Not only is it tricky to get the hardware to reliably persist a group of changes, it's also, somewhat counter-intuitively, difficult to stop it persisting partial changes.
Because cache eviction is under hardware rather than application control, it's possible for state to become persistent at any time.
This is an unwelcome surprise for programmers accustomed to explicit transaction commit or file write behavior, where changes are first made to a volatile copy of the data before being written out.
It's the nasty sting in the tail of the performance that comes from DAX persistent memory access, which doesn't copy the data to O/S block cache in DRAM.

The problem of persisting a group of changes as a failure-atomic unit can be addressed with a logging mechanism, at the cost of write amplification.
The problem of partial writes can be solved by flags or checksums to indicate incomplete changes, coupled to a logging mechanism for rollback.

The problem of isolation can be tackled by the usual language level mechanism of locking ('synchronized' in Java),
though without due care it's still possible for a change to become visible to other actors before it's persisted.
See Bill Bridge's [description](https://medium.com/@mwolczko/non-volatile-memory-and-java-part-2-c15954c04e11) of the problem at the end of Mario's blog. 

Whilst transaction systems need considerable engineering effort, perhaps the biggest issue in change management is outside their scope.
The problem of schema evolution is a critical consideration for persistent object systems and a key consideration for anyone stepping away from database systems that have support for it.

Where an object's persistent state is in a different form from the one accessed by the user, the copying step provides a point at which schema changes can be accommodated.
Where the persistent layout is accessed more directly, that abstraction is no longer available to help mask changes.

## Storage Alternatives

Storage systems can work in one of three ways: in-place update, copy-on-write, and log-structured.

In-place updates use a heap space to allocate mutable objects, in conjunction with a log to record changes to that state.
Modifications snapshot the existing state to the log before overwriting the mutable object, so that the original state can be restored if needed.
Obsolete data may be in the log, which is periodically truncated to reclaim space, or on the heap, from which it can be released back to the allocator for reuse.

Copy-on-write similarly uses heap allocation, but instead of logging the value to be changed, it copies the object state to a new heap location and modifies the copy, relying on the immutable original for restoration if required.
Obsolete data takes the form of old object states on the heap, which are released back to the memory manager for reuse.

Finally, log-structured storage similarly treats existing copies as immutable, but uses linear (append-only log) storage allocation instead of heap allocation to record new states.
A log compaction process periodically rewrites logs to reclaim space, optionally using an indexed file format for the rewritten data rather than keeping it in write order.

Most traditional relational databases have block-oriented storage engines that use one of the first two systems, whilst some more recent systems such as Cassandra or Kafka use a log-structured approach.

Given the more limited bandwidth of current persistent memory hardware, write amplification is also an important consideration.
However, spatial locality of data is less of an issue than it is with traditional block storage devices, with the emphasis now being on the cache line more than the page. 

Data on the tradeoffs between the three models is hard to come by. Much of the early academic work in the field pre-dates hardware availability, so is based on inaccurate software simulations.
This requires it to read with some caution.

The use of direct pointers by the application code provides best performance for state access, but limits the runtime's ability to manage space with compaction, as it can't relocate live objects.
For limited use cases that have uniform object size so the heap doesn't fragment, or which can tolerate unavailability whilst offline compaction is performed, this may be acceptable.
Recovery time is also an important consideration, as algorithms that require extensive index rebuilding or data scanning at startup time can also decrease overall availability time.

To provide a programming experience that's closest to Java, some form of indirection through managed pointers would seem to be the way to go.

Writing persistent object code requires a lot of boilerplate for e.g. snapshotting or copying state. Missing it out leads to problems that can be hard to spot because they only manifest in crash or rollback situations, whilst putting it in clusters the application logic and leads to copy/paste errors.
Some form of code generation tooling to move those tedious tasks away from the programmer would seem to be a good place to start. The JPA or jextract approaches are good examples.
