---
layout: post
title: "Using Persistent Memory in Containers"
date: 2021-04-16
---

With [linux containers](https://opencontainers.org/) a widespread tool for both development use and runtime deployments,
it's interesting to look at how persistent memory can be accessed in container environments.

Let's try building mashona inside a container. The unit tests executed by the build rely on having access to a persistent memory (dax-fs) filesystem,
so having them pass is a good way to validate that the pmem can be used inside the container.

## Bootstrap

First we're going to need a base image. Here we use the Red Hat RHEL8 [UBI](https://developers.redhat.com/blog/2020/03/24/red-hat-universal-base-images-for-docker-users/),
but any similar linux base will do.

The container initialization must expose some of the host's persistent memory filesystem to the container.
Here the host has a pmem DAX enabled filesystem mounted at `/mnt/pmem`,
and we're going to map a `forcontainer` directory on that to the guest as `/hostpmem`

```bash
[host]$ mkdir /mnt/pmem/forcontainer
[host]$ docker run -it --name pmemdemo --mount src=/mnt/pmem/forcontainer,target=/hostpmem,type=bind registry.access.redhat.com/ubi8/ubi:8.1 bash
```
## Tools

Next we need to install the build environment in the container.

The RHEL base has OpenJDK packages only for the LTS Java releases.
Persistent Memory won't be in one of those until Java 17, so for now we need a tarball release instead.

We're also going to need the maven build tool. Although that is available as an .rpm, the package depends on the older java-11-openjdk,
so you'll wind up with two versions of Java taking up space in the container if you go down that route.

You can find current versions on the download pages for [Java](https://jdk.java.net/) and [Apache-Maven](http://maven.apache.org/download.cgi)

```bash
[rhel8-ubi]$ mkdir pmemdemo; cd pmemdemo;
[rhel8-ubi]$ yum install wget git
[rhel8-ubi]$ wget https://download.java.net/java/GA/jdk16/7863447f0ab643c585b9bdebf67c69db/36/GPL/openjdk-16_linux-x64_bin.tar.gz
[rhel8-ubi]$ tar -xf openjdk-16_linux-x64_bin.tar.gz
[rhel8-ubi]$ wget https://downloads.apache.org/maven/maven-3/3.8.1/binaries/apache-maven-3.8.1-bin.tar.gz
[rhel8-ubi]$ tar -xf apache-maven-3.8.1-bin.tar.gz
[rhel8-ubi]$ export PATH=$PATH:/pmemdemo/jdk-16/bin:/pmemdemo/apache-maven-3.8.1/bin
```

Alternatively, with base images such as `fedora:33` it's possible to install the jdk with the package manager instead - `yum install java-latest-openjdk-devel` will currently get you jdk-16.

## Build

Now that we have a build environment, we need something to build.
Clone the mashona repository from github

```bash
[rhel8-ubi]$ git clone https://github.com/jhalliday/mashona.git
[rhel8-ubi]$ cd mashona/logwriting
```

Finally we're ready to run the build and tests

```bash
[rhel8-ubi]$ mvn clean install -Djdk.dir=/pmemdemo/jdk-16 -Dpmem.test.dir=/hostpmem/testdir
```

## Bonus Points: CPU pinning

As persistent memory performance is sensitive to cross-socket access, for production workloads where latency is more critical than memory bandwidth,
it can be desirable to place persistent memory filesystems on the memory attached to a specific socket.

In such cases, apps running on the host OS can use `numactl` to pin the JVM process to the corresponding CPU cores.
For containerised workloads, the container itself can be similarly pinned,
using the [configuration](https://docs.docker.com/config/containers/resource_constraints/#cpu) flag `docker run --cpuset-cpus=[list|range]`
