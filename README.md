# mashona
The first Persistent Memory library for pure Java

## Background

We're adding native support for Persistent Memory to Java.
For the backstory see https://github.com/jhalliday/pmem and https://openjdk.java.net/jeps/352

The support in the JDK itself is very low level - it provides only the fast path (userspace) persistence mechanism.
That's by design, since it's the only part that needs C code and can't be done in Java.

However, programming directly against that low level API is hard work.
There are subtle correctness and performance considerations that are tricky to work with.

That leaves us needing a library of higher level abstractions that are easier to work with, which is where this project comes in.

Existing Java solutions (e.g. https://github.com/pmem/pcj https://github.com/pmem/llpl https://mnemonic.apache.org/ ) work by having Java call down to C ( https://github.com/pmem/pmdk ) via JNI.
With the JEP-352 changes that's no longer necessary or desirable, as it adds complexity and hurts performance. 

So, we need to port the low level abstractions present in the C library to Java,
in order that the higher level abstractions such as those that llpl and pcj already have in Java, can use them in preference to the C version.

The raw persistence mechanism from libpmem is the part that JEP-352 puts in the JDK itself.
The log, heap and transaction elements from libpmemobj currently have no Java equivalent.

The log is the first abstraction we add in Java, since it's the most useful for our immediate use cases. Other parts may follow.

## Try it out

First you need a custom build of jdk13, since the JEP-352 patches are not merged upstream yet.
Follow JDK build instructions as for https://github.com/jhalliday/pmem but using the latest patch from http://cr.openjdk.java.net/~adinn/pmem/webrev.08/

Edit pom.xml to change the compiler path to point at your JDK build output.

Then you need some persistent memory, or fake it with mmapped DRAM. Again, see https://github.com/jhalliday/pmem for setup.

Edit AppendOnlyLogTests.java to change the test File location point at your pmem mount. (TODO read path from env var)

Now you can build and run as usual:

```
mvn clean package
```

