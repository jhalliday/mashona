---
layout: post
title: "Narayana Transaction Log, now on Persistent Memory"
date: 2021-04-06
---

With the recent release of [Narayana 5.11.0.Final](https://jbossts.blogspot.com/2021/03/narayana-5110final-released.html)
it's now possible to have [fast](https://jhalliday.github.io/mashona/blog/2020/10/29/narayana) transaction logging to persistent memory right out of the box.

The new SlotStore code is bundled in the release, making Narayana the first Java transaction system to have pure Java support for Persistent Memory.
It can be enabled easily through Narayana's flexible configuration mechanisms.
You can use the API, set System properties in Java, or pass them in from the command line.

First, add the mashona pmem library to your classpath.

```xml
<!-- persistent memory support. https://mashona.io/ -->
<dependency>
    <groupId>io.mashona</groupId>
    <artifactId>mashona-logwriting</artifactId>
    <version>1.0.0.Beta1</version>
</dependency>
```

Then, choose the configuration style you prefer

Option One, Configuration with the API:
```java
import com.arjuna.ats.arjuna.common.CoreEnvironmentBean;
import com.arjuna.ats.arjuna.common.ObjectStoreEnvironmentBean;
import com.arjuna.ats.internal.arjuna.objectstore.slot.SlotStoreAdaptor;
import com.arjuna.ats.internal.arjuna.objectstore.slot.SlotStoreEnvironmentBean;
import com.arjuna.common.internal.util.propertyservice.BeanPopulator;
...

// first, configure the SlotStore subsystem to use Persistent Memory
SlotStoreEnvironmentBean config = BeanPopulator.getDefaultInstance(SlotStoreEnvironmentBean.class);
config.setBackingSlotsClassName("com.arjuna.ats.internal.arjuna.objectstore.slot.PmemSlots");
// use a directory on a dax aware filesystem:
config.setStoreDir("/mnt/pmem/dir");
// finally, tell the transaction log store to use the slot configuration
BeanPopulator.getDefaultInstance(ObjectStoreEnvironmentBean.class).setObjectStoreType(SlotStoreAdaptor.class.getName());
```

Option Two, Java configuration with system properties. This avoids importing Narayana implementation classes:

```java
System.setProperty("SlotStoreEnvironmentBean.backingSlotsClassName",
        "com.arjuna.ats.internal.arjuna.objectstore.slot.PmemSlots");
System.setProperty("SlotStoreEnvironmentBean.storeDir",
        "/mnt/pmem/test");
System.setProperty("ObjectStoreEnvironmentBean.objectStoreType",
        "com.arjuna.ats.internal.arjuna.objectstore.slot.SlotStoreAdaptor");
```

Option Three, if you prefer to keep the configuration outside the code:

```bash
java -DSlotStoreEnvironmentBean.backingSlotsClassName=com.arjuna.ats.internal.arjuna.objectstore.slot.PmemSlots \
  -DSlotStoreEnvironmentBean.storeDir=/mnt/pmem/test \
  -DObjectStoreEnvironmentBean.objectStoreType=com.arjuna.ats.internal.arjuna.objectstore.slot.SlotStoreAdaptor 
```

That's it! There are additional configuration properties for tuning the space usage and behavior of the store, but the defaults are enough to get going with.

In the unlikely event of problems, it may help to enable more detailed (i.e. debug/trace) output.
Note that whilst the Narayana SlotStore system uses "com.arjuna.ats.arjuna" package logger,
the mashona pmem library uses "io.mashona.logwriting".
Setting one or both of these to a more detailed log level may help gather more information to diagnose any problems.
