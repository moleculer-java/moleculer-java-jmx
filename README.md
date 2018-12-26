[![Build Status](https://travis-ci.org/moleculer-java/moleculer-java-jmx.svg?branch=master)](https://travis-ci.org/moleculer-java/moleculer-java-jmx)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/627f31ac7df448b9a277c7dc4d5c3bc1)](https://www.codacy.com/app/berkesa/moleculer-java-jmx?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=moleculer-java/moleculer-java-jmx&amp;utm_campaign=Badge_Grade)

# [WIP]

JMX service for [Moleculer](https://github.com/berkesa/moleculer-java).

The "jmx" Moleculer Service allows you to easily query the contents stored in a local or a remote JMX Registry. Through the service Java and NodeJS-based Moleculer nodes can easily query java-specific data (eg. JVM's memory usage, number of threads, or various statistical data - for example, JMX Service provides access to low-level statistics to Cassandra, Apache Kafka or Elasticsearch Servers).

The other advantage of the JMXService is that it can monitor any MBean state, and then send an event about the changes. This events can be received by any node subscribed to the event, including NodeJS-based nodes.

# License

moleculer-java-jmx is available under the [MIT license](https://tldrlegal.com/license/mit-license).
