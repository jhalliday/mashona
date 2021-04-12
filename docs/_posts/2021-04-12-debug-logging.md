---
layout: post
title: "Debug and trace logging in Mashona"
date: 2021-04-12
---

If you would like to see what Mashona is doing internally at runtime, or you need to supply logs to a support provider for diagnostic purposes, then this post is for you.

The Mashona logwriting library uses the [JBoss Logging](https://github.com/jboss-logging) library for its diagnostic logging statements.

## Embedded use

If you're running it embedded in another JBoss project, such as [Narayana](https://narayana.io/) or [Infinispan](https://infinispan.org/), then you only need to use the project's existing logging configuration mechanism to enable the desired log level (e.g. DEBUG or TRACE) for the "io.mashona.logwriting" logger.

## Custom project setup

If you're running it in your own project, a little more setup may be required.

JBoss Logging is a dependency of Mashona and maven will pull it in automatically, or you can explicitly add it to your project

```xml
<dependency>
    <groupId>org.jboss.logging</groupId>
    <artifactId>jboss-logging</artifactId>
    <!-- Latest at time of writing. Check for updates! -->
    <version>3.4.1.Final</version>
</dependency>
```

JBoss Logging can use a number of backend logging systems, including [Apache Log4j](https://logging.apache.org/log4j/2.x/) and [Slf4j](http://www.slf4j.org/), as well as the JDK's built-in java.util.logging.
Whilst you can select one explicitly (with the 'org.jboss.logging.provider' system property set to jboss | log4j2 | slf4j | jdk) it's usually easier just to let it find one itself by searching along the classpath.

### Option A: Using Log4j

Add the library to your classpath if you don't already have it

```xml
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-core</artifactId>
    <!-- Latest at time of writing. Check for updates! -->
    <version>2.14.1</version>
</dependency>
```

Then add a **log4j2.xml** config file to the classpath or use one of the other [configuration mechanisms](https://logging.apache.org/log4j/2.x/manual/configuration.html).


### Option B: Using Slf4j

Add the API and the [logback](http://logback.qos.ch) backend to the project if you don't already have them

```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <!-- Latest at time of writing. Check for updates! -->
    <version>1.7.30</version>
</dependency>
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <!-- Latest at time of writing. Check for updates! -->
    <version>1.2.3</version>
</dependency>
```

Then add a **logback.xml** config file to the classpath or use one of the other [configuration mechanisms](http://logback.qos.ch/manual/configuration.html).

### Option C: Using JBoss LogManager

JBoss logging has its own backend, which you can add to you project with

```xml
<dependency>
    <groupId>org.jboss.logmanager</groupId>
    <artifactId>jboss-logmanager</artifactId>
    <!-- Latest at time of writing. Check for updates! -->
    <version>2.1.18.Final</version>
</dependency>
```

Then enable it to replace the default log manager by setting the system property

`java.util.logging.manager=org.jboss.logmanager.LogManager`

This LogManager has the same configuration format as Java's native logging system,
so you need a **logging.properties** file on the classpath.
The JBoss LogManager comes with its own Handlers and Formatters though,
so your logging.properties file content may differ from the normal JDK one.
For example
```properties
logger.handlers=CONSOLE

handler.CONSOLE=org.jboss.logmanager.handlers.ConsoleHandler
handler.CONSOLE.level=INFO
handler.CONSOLE.formatter=PATTERN

formatter.PATTERN=org.jboss.logmanager.formatters.PatternFormatter
formatter.PATTERN.properties=pattern
formatter.PATTERN.pattern=%d{HH:mm:ss.SSS} [%t] %-5p %c{36} %M:%L - %msg%n
```

### Option D: Using JDK logging

JBoss logging will delegate to Java's built-in logging framework by default, if it can't find an alternative logging library to use.
Note that, unlike the other options, the JDK won't look for its config on the classpath.
Configure the location of **logging.properties** explicitly with the system property

`java.util.logging.config.file=/path/to/logging.properties`

## Logging pattern configuration

For DEBUG and TRACE level logging, it's helpful to have thread (%t) and code location (%M %L) information in the logs.

The recommended setting for log4j's [PatternLayout](https://logging.apache.org/log4j/2.x/manual/layouts.html)
and slf4j-logback's [PatternLayout](http://logback.qos.ch/manual/layouts.html) is

`%date{yyyy-MM-dd_HH:mm:ss.SSS} [%thread] %-5level %logger{36} %method:%line - %msg%n`

Note that JBoss LogManager uses the same fields, but refers to them only by their
[single character names](https://github.com/jboss-logging/jboss-logmanager/blob/master/core/src/main/java/org/jboss/logmanager/formatters/FormatStringParser.java),
so for it the equivalent pattern must be written as

`%d{yyyy-MM-dd_HH:mm:ss.SSS} [%t] %-5p %c{36} %M:%L - %msg%n`

For JDK native logging, the [format](https://docs.oracle.com/en/java/javase/11/docs/api/java.logging/java/util/logging/SimpleFormatter.html#format(java.util.logging.LogRecord)) is different again.

`java.util.logging.SimpleFormatter.format=%1$s %4$s %2$s %5s%n`

## DEBUG or TRACE ?

Mashona has little DEBUG level logging, but lots of TRACE level logging.
In general, the entry and exit (return or throws) for every public method in the API has TRACE statements,
designed to be read in conjunction with the logger's method and line fields.
Whilst such TRACE logs can be large, this configuration is usually required if you're seeking support or filing issues against the library.
