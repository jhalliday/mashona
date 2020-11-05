---
layout: post
title: "Hello, Persistent World!"
date: 2020-03-15
---

This month's launch of [JDK 14](http://jdk.java.net/14/) marks the general availability of native Persistent Memory support for JVM users.
Although a significant foundational milestone, it's not the opening chapter in the story of this new approach to programming. In this first Mashona project blog, we look at some parts of the backstory.

Persistent Memory hardware has been around for some years, often in the form of DIMMs combining DRAM, Flash memory and capacitors to provide fault (i.e. power loss) tolerance by copying the contents of the volatile DRAM to the persistent Flash using power from the capacitors in the case of an unexpected loss of power to the motherbaord.

This style of Persistent Memory, known as NVDIMM-N, benefits from the performance characteristics of DRAM, but at the cost of being limited by DRAM's relatively small size.

More recently, Intel introduced Optane branded DCPMMs. These use the same 3D XPoint chips as Optane SSDs for persistent storage, but interface to the platform memory controller rather than the storage bus. They offer larger capacities than DRAM whilst still having better performance than Flash.

Alongside the new DCPMMs (like DIMMs, but not strictly spec compatible), the Intel platform has processor instructions (CLFLUSH, CLFLUSHOPT and CLWB) for managing flushing volatile cache lines to the non-volatile memory controller.

Careful use of these instructions in conjunction with memory fences is at the core of Persistent Memory programming techniques.
As the instructions are non-privileged, it's even possible to use new O/S features to bypass the kernel when doing memory mapped I/O to files backed by Persistent Memory.
With the storage bus and the kernel out of the picture, user code can exploit the full performance of the new hardware to perform persistence operations with unprecedented speed.

Here lies the problem for programmers working in managed languages on the JVM (Java, Scala, Kotlin, Clojure, ...), for the nature of the managed runtime affords no way to inline assembly code or macros as is possible with C/C++

JNI provides a loophole, allowing function calls out to processor native libraries built on the low level cache management instructions.
But, whilst this approach is functional, it's not fast. JNI plumbing adds considerable overhead to small function calls, preventing programs from exploiting the full performance capability of the hardware.

For traditional disk I/O, this can reasonably be overlooked. Even with SSD, the hardware path cost still dominates any inefficiencies in the software path, to the extent that there is no great benefit from optimizing out the kernel or JNI calls.

With Persistent Memory, programs seeking the best possible I/O paths must look for ways to reduce these software costs, as they are now a more significant part of the total overhead.

So we come to the point in the story where OpenJDK engineering makes its entrance. For the JVM to be a competitive platform on which to run applications that take advantage of Persistent Memory, it needs to provide built-in support for efficient calls to the cache management instructions.

In the [next blog](https://jhalliday.github.io/mashona/blog/2020/03/16/jep-352), we look at the API and implementation of OpenJDK 14's new support for Persistent Memory, 
[JEP 352: Non-Volatile Mapped Byte Buffers](https://openjdk.java.net/jeps/352)
