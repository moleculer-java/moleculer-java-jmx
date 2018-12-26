[![Build Status](https://travis-ci.org/moleculer-java/moleculer-java-jmx.svg?branch=master)](https://travis-ci.org/moleculer-java/moleculer-java-jmx)

# [WIP]

JMX service for [Moleculer](https://github.com/berkesa/moleculer-java).

The "jmx" Moleculer Service allows you to easily query the contents stored in a local or a remote JMX Registry. Through the service Java and NodeJS-based Moleculer nodes can easily query java-specific data (eg. JVM's memory usage, number of threads, or various statistical data - for example, JMX Service provides access to low-level statistics to Cassandra, Apache Kafka or Elasticsearch Servers).

The other advantage of the JMXService is that it can monitor any MBean state, and then send an event about the changes. This events can be received by any node subscribed to the event, including NodeJS-based nodes.

# License

moleculer-java-jmx is available under the [MIT license](https://tldrlegal.com/license/mit-license).
