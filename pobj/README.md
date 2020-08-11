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

Meanwhile, practical medium-term efforts in Java focus on providing object persistence support at the library and framework (i.e. middleware) level, with minimal and non-invasive changes to the runtime platform.

A simple solution is just to make the storage layer underlying relational databases aware of persistent memory.
Java applications can continue using the JPA without any changes, whilst the database becomes faster as a result of the hardware improvements.

Such a database-centric has the attraction of leaving in place the mature query language, transaction mechanism, schema management, security and other benefits of the existing software stack.
All those features come with a performance cost though, and for some use cases that's not a desirable tradeoff.


More direct models dispense with the mechanisms for declarative querying, schema management, security configuration and, to some extent, transactions.
In return they provide a programming model closer to natural Java, with objects and familiar data structures such as lists and maps.

A good early example of this genre is [PCJ](https://github.com/pmem/pcj). It is not yet clear if this project's lack of traction stems from providing the wrong thing, or from providing it too early for the market.
It is clear, however, that Intel take an approach to supporting Java which leverages their existing investment in C, via JNI calls.

The same approach manifests in a more streamlined and targeted manner in [LLPL](https://github.com/pmem/llpl), a library which exposes the primitive building blocks of a persistent object system directly from the C library to Java with little additional abstraction along the way.
This is perhaps the most interesting starting point for examining the feasibility of building a persistent object system entirely in Java.

Unpacking LLPL's functionality, we see that the key components are a memory management system and a transaction system, underpinned by a persistent logging mechanism.
Importantly, whilst LLPL gets much of that functionality from C code via JNI, such division is largely a design decision rather than a necessity.

The only functionality that can't be ported from C to Java, is the flush+fence sequence that guarantees data is moved from the volatile CPU cache to the persistent memory, which requires specific CPU instructions.
Fortunately we built that capability into the Java 14 platform as part of [JEP-352](https://openjdk.java.net/jeps/352), so it's now theoretically possible to dispense with C code entirely.

Reuse of C libraries benefits from the considerable development and tuning work they embody, but carries the costs of build/packaging/deployment complexity, and runtime overhead for the JNI calls.
By contrast, a pure Java approach requires more upfront work in redeveloping core functionality, but the eventual benefit of simpler use and possibility of faster execution as the JIT can see and optimise across the entire code path.

Here in mashona's pobj module, we're prototyping a pure Java approach to providing the building blocks for a persistent object framework.

