# JMX Service for Moleculer

A Moleculer `Service` that bridges a local or remote **JMX registry** into the
[Moleculer for Java](https://moleculer-java.github.io/site/) framework. With it, any node in the
cluster — **including Node.js nodes** — can query Java-specific runtime data (JVM memory usage,
thread counts, and the low-level statistics exposed by servers such as Cassandra, Apache Kafka,
or Elasticsearch). The service can also **watch** MBean attributes and broadcast a Moleculer
event whenever a value changes, so any subscribed node is notified.

## Documentation

[![Documentation](https://raw.githubusercontent.com/moleculer-java/site/master/docs/docs-button.png)](https://moleculer-java.github.io/site/jmx-service.html)

## Download

```xml
<dependency>
    <groupId>com.github.berkesa</groupId>
    <artifactId>moleculer-java-jmx</artifactId>
    <version>2.0.0</version>
</dependency>
```

## Quick start

Add the service to a broker and call its actions (here over the local platform MBean server):

```java
ServiceBroker broker = new ServiceBroker("node-1");
broker.createService(new JmxService());   // exposes jmx.* actions
broker.start();

// e.g. list MBean ObjectNames, or read one attribute
broker.call("jmx.listObjectNames");
broker.call("jmx.getAttribute",
    new Tree().put("objectName", "java.lang:type=Memory").put("attribute", "HeapMemoryUsage"));
```

The same `jmx.*` actions are callable remotely from any other node in the cluster.

## Requirements

Java 21 or newer.

## License

JMX Service for Moleculer is available under the [MIT license](https://tldrlegal.com/license/mit-license).
