---
layout: post
title: "Infinispan Data Store, now on Persistent Memory"
date: 2021-04-14
---

With the recent release of [Infinispan 12.1](https://infinispan.org/blog/2021/04/07/infinispan-12-1-0-final)
it's now possible to have [fast](https://jhalliday.github.io/mashona/blog/2020/11/05/infinispan) data storage on persistent memory with just a few simple setup steps.

The release incorporates some enhancements to Infinispan's Single Index File Store (SIFS), including a non-blocking model and support for persistent memory.

## Configuration file

The persistent memory cache [configuration](https://infinispan.org/docs/stable/titles/configuring/configuring.html#sifs_cache_store) looks as it does for use on regular storage devices, such as SSD.
Just add an appropriate `local-cache` to your `cache-container`

```xml
<local-cache name="sifs">
    <persistence>
        <soft-index-file-store xmlns="urn:infinispan:config:store:soft-index:12.1">
            <index path="sifs/index" />
            <data path="sifs/data" />
        </soft-index-file-store>
    </persistence>
</local-cache>
```

To enable the persistent memory code, two additional steps are necessary.

## Library installation

First, add the mashona library to the server classpath

```bash
$ cd lib
# version latest at time of writing. Check for updates!
$ wget https://repo1.maven.org/maven2/io/mashona/mashona-logwriting/1.0.0.Beta1/mashona-logwriting-1.0.0.Beta1.jar
```

Without the pmem library installed in the classpath, the system will default to normal usage:

```log
server.log:2021-04-14 08:14:37,156 DEBUG (main) [org.infinispan.persistence.sifs.FileProvider] Persistent Memory not in classpath, not attempting
```

## Directory selection

Next, you need to ensure the data directory is DAX enabled. Here are two possible approaches

### Option A:

Symlink the directory to one that is on DAX.
This is best where you've mounted `-o dax=always` and would like to leave the rest of the server data directory on a non-DAX fs.

```bash
$ cd server/data/sifs
$ ln -s /mnt/pmem/sifsdata data
```

### Option B:

Selectively enable DAX for the data directory. This is best where you've mounted `-o dax=inode` and default most files to non-DAX mode.

```bash
$ cd server/data/sifs
xfs_io -c 'lsattr -v'  data
[has-xattr] data
$ xfs_io -c 'chattr +x' data
$ xfs_io -c 'lsattr -v'  data
[dax, has-xattr] data
```

## Validation

You can check the directory is DAX enabled by having the pmem library try it out

```bash
$ java -cp "lib/*" io.mashona.logwriting.PmemUtil server/data/sifs/data
server/data/sifs/data: pmem is true
```

When the server starts, you can also look for the .pmem metadata file, which exists only when running in persistent memory mode

```bash
$ ls server/data/sifs/data
ispn12.0  ispn12.0.pmem
```

You can also use mashona's TRACE logging to check the library is installed and operating as expected. Edit `server/conf/log4j2.xml`

```xml
    <Logger name="org.infinispan.persistence.sifs" level="ALL"/>
    <Logger name="io.mashona.logwriting" level="ALL"/>
```

Then look in `server/log/server.log`

```log
server/log/server.log.2021-04-14-2:2021-04-14 09:42:32,994 TRACE (blocking-thread--p3-t2) \
  [io.mashona.logwriting.PmemUtil] pmemChannelFor:153 entry with file=/test/infinispan-server-12.1.0.Final/server/data/sifs/data/ispn12.0, length=16,777,216, create=false, readSharedMetadata=true
server/log/server.log.2021-04-14-2:2021-04-14 09:42:33,000 TRACE (blocking-thread--p3-t2) \]
  [io.mashona.logwriting.PmemUtil] pmemChannelFor:194 exit returning io.mashona.logwriting.MappedFileChannel@4ca2f180
```

## Cautionary Notes

Always configure the system for Persistent Memory before populating the cache.
Moving existing stores between persistent memory and regular storage in either direction is not supported.

When backing up the store from persistent memory, ensure you also copy the .pmem metadata files.
