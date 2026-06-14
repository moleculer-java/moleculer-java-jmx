# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

This is a single Moleculer service (`JmxService`) that bridges a JVM's JMX registry into the
[Moleculer Java](https://github.com/moleculer-java/moleculer-java) microservices framework. It exposes
JMX MBean data as callable Moleculer actions and turns MBean attribute changes into Moleculer events
that any node in the cluster — including Node.js nodes — can subscribe to.

The published artifact is `com.github.berkesa:moleculer-java-jmx`. There is no application entrypoint;
this is a library meant to be added to a `ServiceBroker` by the host application.

## Build & Test Commands

Uses **Maven** (`pom.xml`). The CI target is `mvn clean verify`.

- `mvn clean install` — compile, test, assemble the jar (`target/moleculer-java-jmx-2.0.0-SNAPSHOT.jar`) and install to the local `~/.m2` repository
- `mvn clean verify` — full build with tests (definition of done)
- `mvn test` — run all JUnit 5 tests
- `mvn test -Dtest=JmxServiceTest#testLocal` — run a single test method
- `mvn javadoc:javadoc` — build Javadoc (release profile uses `<doclint>none</doclint>`)
- `mvn -Prelease ...` — activate the release profile (sources + javadoc + GPG sign + Central Portal publishing)

Targets **Java 21** (`maven.compiler.release = 21`) and compiles with **javac** via `maven-compiler-plugin`.
Tests run on **JUnit 5 (Jupiter)**.

## Architecture

Three source classes, plus two test-only helpers:

- **`JmxService`** (`src/main/java/services/moleculer/jmx/JmxService.java`) — the `@Name("jmx")` Moleculer
  service. Holds an `MBeanServerConnection` (either the local platform MBean server when `local = true`,
  or a remote RMI connection built from `url`/`username`/`password`/`environment`). Exposes four actions
  as public `Action` lambda fields:
  - `jmx.listObjectNames` — list MBean ObjectNames, with optional `query` filter and `sort`
  - `jmx.getObject` — fetch one MBean by `objectName` as JSON
  - `jmx.getAttribute` — fetch one attribute (optionally drill into a CompositeData via `path`)
  - `jmx.findObjects` — text/wildcard search across MBeans (slow; intended for discovery, not production)
- **`ObjectWatcher`** (`src/main/java/.../ObjectWatcher.java`) — a plain config/value object describing
  one MBean (or attribute) to poll and which Moleculer event to fire when its serialized value changes.
  Implements `equals`/`hashCode` over all fields because watchers are stored in a `HashSet`.
- Test helpers (`src/test/java/...`): **`JmxListener`** is a Moleculer `Service` that subscribes to the
  watcher event; **`JmxServiceTest`** is a JUnit 5 (Jupiter) test covering both local and remote (in-process
  RMI registry on port 1234) connections.

### Key behaviors to preserve

- **Everything is converted to `datatree` `Tree`/JSON-compatible structures.** `convertValue()` recursively
  maps JMX types (`CompositeData`, arrays, `Collection`, `Map`, scalars) into `Map`/`List`/scalar. NaN and
  infinite `Double`/`Float` values are deliberately coerced to `null` so the output stays valid JSON. Unknown
  types fall back to `toString()`. This is why Java-only JMX data can be consumed by Node.js nodes.
- **Watcher lifecycle.** Watchers can only be added/removed while the service is stopped — `addObjectWatcher`,
  `removeObjectWatcher`, and `setObjectWatchers` call `checkRunningState()`, which throws
  `IllegalStateException` once `started()` has set `running = true`.
- **Watcher polling.** When started with non-empty watchers, the service schedules `watchObjects()` on the
  broker's `ScheduledExecutorService` every `watchPeriod` ms (min 200, default 3000). It compares each
  MBean's current serialized JSON against the previous value in `previousValues`; on change it `broadcast`s
  (all listeners) or `emit`s (one listener) the configured event, optionally scoped to `Groups`. A watcher
  with a null `objectName`/`event` or malformed name is logged and removed from the rotation.
- **Attribute-name cache.** `objectToJson()` caches the readable attribute-name array per ObjectName (keyed
  with a trailing `>` when sorted) in a 1024-entry `io.datatree.dom.Cache` to avoid repeated `getMBeanInfo`
  calls.
- **Errors are surfaced as `MoleculerServerError`** with stable error codes (`NO_JMX`, `NO_CTX`,
  `NO_OBJECT_NAME`, `NO_ATTRIBUTE_NAME`, `NO_QUERY`) — keep these codes stable for API consumers.

## Conventions

- Every source file carries the MIT license header block; new files should include it.
- Public methods and `Action` fields are documented with Javadoc that includes REPL `call jmx.*` usage
  examples — match this style when adding actions.
- Fields and helper methods are `protected` (not `private`) so the service can be subclassed and extended;
  prefer `protected` for new internals.
- Version lives in two places in `pom.xml`: the project `<version>` and the `moleculer-java`
  dependency `<version>` (currently the `${moleculer.version}` property). Both are `2.0.0`
  (lockstep across the workspace) — update consistently when releasing.
