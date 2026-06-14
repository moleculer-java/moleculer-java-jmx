[![codecov](https://codecov.io/gh/moleculer-java/moleculer-java-jmx/branch/master/graph/badge.svg)](https://codecov.io/gh/moleculer-java/moleculer-java-jmx)

## JMX Service for Moleculer

The "jmx" Moleculer Service allows you to easily query the contents stored in a local or a remote JMX Registry.
With this service Java and Node.js-based Moleculer nodes can easily query java-specific data
(eg. JVM's memory usage, number of threads, or various statistical data - for example,
JMX Service provides access to low-level statistics to Cassandra, Apache Kafka or Elasticsearch Servers).
The other advantage of the JMXService is that it can monitor objects in the JMX registry, and sends events about the changes.
This events can be received by any node subscribed to this event, including Node.js-based nodes.

## Documentation

[![Documentation](https://raw.githubusercontent.com/moleculer-java/site/master/docs/docs-button.png)](https://moleculer-java.github.io/site/jmx-service.html)

## License

JMX Service for Moleculer is available under the [MIT license](https://tldrlegal.com/license/mit-license).
