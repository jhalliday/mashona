---
layout: post
title: "Mashona 1.0 Released"
date: 2021-09-15
---

Mashona 1.0 GA is now available, providing fast logging to persistent memory for Java applications.

Using Java 17's [MappedByteBuffer](https://mashona.io/blog/2020/03/16/jep-352)
support for DAX operations, mashona needs no native library component and avoids the overhead of JNI.

Providing a variety of log types, it's flexible enough for a many use cases,
integrating with e.g. the [Narayana](https://mashona.io/blog/2020/10/29/narayana) transaction manager
and the [Infinispan](https://mashona.io/blog/2020/11/05/infinispan) data grid.

