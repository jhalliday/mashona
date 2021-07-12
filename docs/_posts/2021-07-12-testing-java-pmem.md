---
layout: post
title: "Testing persistent memory code in Java"
date: 2021-07-12
---

Testing code that interacts with persistent memory requires some careful thought.
For cases such as the [logwriting](https://github.com/jhalliday/mashona/tree/main/logwriting) module of mashona, correct methods can sometimes be distinguishable from incorrect ones only by the presence of force() calls at appropriate points.
A force operation will ensure pending writes are flushed from system caches to persistent memory, thus becoming power-fail tolerant.

The force function call is the path in the Java standard library where control passes from Java code to the JVM,
which uses native code to issue the appropriate CLWB or other hardware instructions necessary to ensure cache lines are passed to the iMC, where they enter the persistence domain of the hardware.

One common mistake in persistent memory programming is missing out some force calls, which can result in code that works perfectly in normal situations and passes all conventional unit tests, but won't actually keep data safe in a crash situation.
A similar, though less critical, mistake is to force cache lines that aren't dirty, which can introduce unnecessary overhead due to the extra memory barrier it inserts.

Since a read will go via the cache hierarchy, simply using asserts against the modified value won't reveal if it's actually reached the persistent storage device, or is merely held in the volatile CPU cache.

Mocking techniques seem like an attractive option at first glance, allowing us to trace usages of the force method.
However, mocking something that's built-in to the Java standard library is not easy, and violates the 'Do not mock types you don’t own' guideline espoused
by frameworks such as [mockito](https://site.mockito.org/).  Nevertheless, it does point us in the direction of a possible solution.

If we can track data writes by the cache lines they modify, and likewise track force calls by the cache lines they make persistent,
then reconciling those views will provide us with a check that we have persisted all, and only, the data we expect.

For applications written in C, this can be accomplished by using a specially modified version of [valgrind](https://www.valgrind.org/),
an instrumentation framework that facilitates dynamic analysis of a program's behaviour by use of binary instrumentation.
Using [pmem-valgrind](https://github.com/pmem/valgrind) in association with tools such as
[pmreorder](https://pmem.io/pmdk/manpages/linux/master/pmreorder/pmreorder.1.html)
can provide validation of the pmem code in C based libraries and is the approach preferred for [PMDK](https://pmem.io/pmdk/),
as described in detail in [Discover Persistent Memory Programming Errors with Pmemcheck](https://software.intel.com/content/www/us/en/develop/articles/discover-persistent-memory-programming-errors-with-pmemcheck.html).

Instrumenting the entire JVM with valgrind is not an attractive option.
It's a large and complex application, most of which we're not interested in.
Furthermore, necessitating use of valgrind in Java unit tests makes the build process complex and extracting the data from valgrind reports so that it can be exposed to unit testing assertions is tedious.

Instead, we need a solution that can instrument deep inside JVM library code in a way that mocking frameworks are poorly suited to,
but not quite so deep that we have to drop down from Java to C code. In short, we need Byteman.

[Byteman](https://byteman.jboss.org/) is a code injection system intended to facilitate tracing Java applications at runtime.
It's somewhat conceptually similar to valgrind, but works at the Java level by using an agent to modify class files on the fly.
It's also more capable in some respects, in that it can be used to introduce side effects for concurrency or fault-injection testing.
Users interact with Byteman by writing event-condition-action rules to specify the required class behaviour changes.
Even better, Byteman's BMUnit module is specifically designed to make it easy to use Byteman in JUnit and TestNG tests.

By constructing Byteman rules that attach instrumentation to the write and force call paths in the standard library,
we can intercept and record the cache line state changes in a way that makes it easy to express appropriate test assertions against the gathered data.

Let's have a look at an example from the mashona test suite:

```java
public class ExecutionTracer {
    
    @BMRule(name = "trace_writebackMemory_0",
            targetClass = "Unsafe", targetMethod = "writebackMemory(long,long)", targetLocation = "ENTRY",
            condition = "io.mashona.logwriting.ExecutionTracer.INSTANCE != null",
            action = "io.mashona.logwriting.ExecutionTracer.INSTANCE.writebackMemory($1,$2);")
    public void writebackMemory(long address, long length) {

        long line = (address & CACHE_LINE_MASK);
        long end = address + length;
        while (line < end) {
            markClean(line); // update our test's state tracking information
            line += CACHE_LINE_FLUSH_SIZE;
        }
    }
    
    ...
```

The @BMRule is an annotation based way of specifying a byteman rule,
which expresses:
- The interception point, in this case the JDK's Unsafe::writebackMemory method, which is the last useful Java frame on the force() path before execution passes into the JVM native code that actually issues the CLWB or other hardware instruction.
- The interception condition, in this case that we have provided a tracer instance that will receive and record the execution event data.
- An action, to be called at the specified point if the condition is met, in this case a call to the method body that we provide right below the annotation, passing through the call arguments.

In other words, we're instructing Byteman to splice a callback to our test utility's writebackMemory method into the JDK's internal writebackMemory call.
Using similar wiring on the data write calls, we can track in our test code the state, clean or dirty, of all the relevant cache lines for our persistent memory.

Connecting this capability into individual tests is as easy as using an annotation to tell the JUnit test framework to call Byteman for the injection work,
then calling the provided assertions to check the recorded state

```java
@WithBytemanFrom(source = ExecutionTracer.class)
public class PersistentMemoryTestsuite {

    @AfterEach
    public static void tearDown() {
        assertFlushingWasEfficient();
        assertFlushingWasSufficient();
    }
    
    ...
```

The tests themselves can then focus on normal unit tests behaviour,
such as writing and reading back values from the log, without further concern about
persistent-memory specific fault-tolerance aspects of the behaviour.

The **ExecutionTracer** extension for monitoring persistent memory use in tests with Byteman
is a modular part of the open-source mashona test suite.
Whilst it's best to use mashona or another library to abstract persistent memory access wherever possible,
if you must write pmem code yourself then it's worth applying the same techniques,
or even reusing the test framework code directly.
