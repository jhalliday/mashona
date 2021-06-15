---
layout: post
title: "The LTS cometh"
date: 2021-06-14
---

The forthcoming [JDK 17](http://jdk.java.net/17/) release is an important event for support of persistent memory applications on the JVM.
Although the [JEP 352: Non-Volatile Mapped Byte Buffers](https://openjdk.java.net/jeps/352) feature work was complete way back in JDK 14,
the JDK 17 release will be the first LTS version to include it.

Many developers benefit from the biannual release cadence and upgrade every six months to use the most feature-rich version of the platform,
but there are still some environments that are more conservative and move only from one LTS release to the next.
For such users upgrading from JDK 11, this will be the first opportunity to benefit from the new persistent memory support and other features in the platform.

With the JDK release cycle now entering the rampdown phase,
it's time to start testing on JDK 17 early-access builds.
Pre-release testing allows us to identify potential issues early and provide feedback to the JDK developers whilst there is still time for changes to be made.

The testing is not a large task.
The Java platform API remains very stable over time and recompilation is straightforward.

Until the test suite breaks...

There is one change in the Java library internals between Java 16 and 17 that affects the mashona library.
It's actually a feature enhancement, not a regression.
Which is some consolation when staring at a nasty red test failure.

In older Java releases, the MappedByteBuffer API has a potentially problematic quirk -
duplicates and slices from a direct buffer are not themselves direct.
What this mean in practice is that until now calling force() on the original object would do the right thing,
ensuring that changes were flushed to persistent disk or, in the case of mashona, to persistent memory.
But... calling force() on a duplicate or slice would silently fail to flush anything.
Adding to the headaches, it's not easy to determine if you have the original object or a copy, so you can't always spot the problem.

In part to get around this, mashona is designed to flush only through a PersistenceHandle, which wraps the original buffer.
The library uses duplicates and slices freely for data copying, as it makes managing the arithmetic for offsets much easier and more readable, but it never calls flush on any of them.

The library also provides a safety net in the form of the setParanoid() configuration flag,
an option which causes the mashona to use reflection to peer inside the user-provided buffer and determine that it actually is an original and not an inferior copy.
Of course this feature has a test, and it's this that breaks when running on JDK 17.

The new JDK finally has a fix [ [1](https://bugs.openjdk.java.net/browse/JDK-4833719), [2](https://github.com/openjdk/jdk/pull/2902) ] for the longstanding quirk in the standard library,
causing duplicated and sliced buffers to carry over the original file handle and thus flush correctly.
So naturally our test complains it no longer gets an error when doing something that used to be expected to fail, but now doesn't.

I guess we'll call that a Good Thing, though it does require some code changes to the library.

Starting with 1.0.0.Beta2, official releases will be compiled on (and for) JDK 17 or later.
The setParanoid option is gone, since it's no longer useful.
However, the PeristenceHandle abstraction stays, as it's still a handy API design.

In the unlikely event that you need the library to run on the obsolete JDK 14, 15 or 16 platforms after the release of 17, 
it's still possible to build the library from source.
However, if you're building releases later than 1.0.0.Beta1 for those targets,
you'll not have the setParanoid functionality available to you.
Take particular care with the use of force() when developing your own persistent memory applications on JDK 17 but releasing them for use on older JDKs.

+1 for unit testing and +100 for JDK enhancements.
Remember kids, upgrade early, upgrade often, and always run your tests.
