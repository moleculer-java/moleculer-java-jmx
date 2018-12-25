/**
 * THIS SOFTWARE IS LICENSED UNDER MIT LICENSE.<br>
 * <br>
 * Copyright 2018 Andras Berkes [andras.berkes@programmer.net]<br>
 * Based on Moleculer Framework for NodeJS [https://moleculer.services].
 * <br><br>
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:<br>
 * <br>
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.<br>
 * <br>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package services.moleculer.jmx;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.datatree.Tree;
import services.moleculer.ServiceBroker;
import services.moleculer.config.ServiceBrokerConfig;
import services.moleculer.context.Context;
import services.moleculer.error.MoleculerServerError;
import services.moleculer.eventbus.Groups;
import services.moleculer.service.Action;
import services.moleculer.service.Name;
import services.moleculer.service.Service;
import services.moleculer.util.CheckedTree;
import services.moleculer.util.FastBuildTree;

/**
 * The "jmx" Moleculer Service allows you to easily query the contents stored in
 * JMX. Through the service Java and NodeJS-based Moleculer nodes can easily
 * query java-specific data (eg. JVM's memory usage, number of threads, or
 * various statistical data). For example, JMX Service provides access to
 * low-level statistics to Cassandra, Apache Kafka or Elasticsearch Servers.<br>
 * <br>
 * The other advantage of the JMXService is that it can monitor any MBean state,
 * and then send an event about the changes. This events can be received by any
 * node subscribed to the event, including NodeJS-based nodes.
 * 
 * <pre>
 * ObjectWatcher watcher = new ObjectWatcher();
 * watcher.setObjectName("java.lang:type=Memory");
 * watcher.setEvent("jmx.memory");
 * 
 * JmxService jmx = new JmxService();
 * jmx.addObjectWatcher(watcher);
 * broker.createService(jmx);
 * </pre>
 */
@Name("jmx")
public class JmxService extends Service {

	// --- LOGGER ---

	protected static final Logger logger = LoggerFactory.getLogger(JmxService.class);

	// --- VARIABLES ---

	/**
	 * Local nodeID of the Moleculer ServiceBroker.
	 */
	protected String localNodeID;

	/**
	 * Local or Remote JMX connection.
	 */
	protected MBeanServerConnection connection;

	/**
	 * Use local/internal (true) or a remote/rmi (false) JMX connection.
	 */
	protected boolean local = true;

	/**
	 * Remote JMX server's URL (eg.
	 * "service:jmx:rmi:///jndi/rmi://127.0.0.1:7199/jmxrmi").
	 */
	protected String url = "service:jmx:rmi:///jndi/rmi://127.0.0.1:7199/jmxrmi";

	/**
	 * The username used for the optional user authentication (null = no
	 * authentication).
	 */
	protected String username;

	/**
	 * The password used for the optional user authentication (null = no
	 * authentication).
	 */
	protected String password;

	/**
	 * Optional Environment Map used for connecting the remote JMX server.
	 */
	protected Map<String, Object> environment;

	// --- VARIABLES OF THE MBEAN WATCHER THREAD ---

	protected Set<ObjectWatcher> objectWatchers = new HashSet<>();

	protected HashMap<ObjectWatcher, String> previousValues = new HashMap<>();

	protected long watchPeriod = 3000;

	protected ScheduledExecutorService scheduler;

	protected ExecutorService executor;

	protected ScheduledFuture<?> timer;

	protected AtomicBoolean running = new AtomicBoolean();

	// --- START SERVICE ---

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * services.moleculer.service.MoleculerComponent#started(services.moleculer.
	 * ServiceBroker)
	 */
	@Override
	public void started(ServiceBroker broker) throws Exception {
		super.started(broker);
		running.set(false);

		// Set nodeID
		this.localNodeID = broker.getNodeID();

		// Connect to JMX server
		if (local) {

			// Local JMX connection
			connection = ManagementFactory.getPlatformMBeanServer();

		} else {

			// Remote JMX connection
			JMXServiceURL target = new JMXServiceURL(url);
			if (username != null) {
				username = username.trim();
			}
			if (password != null) {
				password = password.trim();
			}
			if (username != null && password != null && !username.isEmpty() && !password.isEmpty()) {
				if (environment == null) {
					environment = new HashMap<>();
				}
				String[] credentials = new String[2];
				credentials[0] = username;
				credentials[1] = password;
				environment.put(JMXConnector.CREDENTIALS, credentials);
			}
			JMXConnector connector = JMXConnectorFactory.connect(target, environment);
			connection = connector.getMBeanServerConnection();
		}

		// Object watcher
		running.set(true);
		if (objectWatchers != null && !objectWatchers.isEmpty()) {
			ServiceBrokerConfig config = broker.getConfig();
			scheduler = config.getScheduler();
			executor = config.getExecutor();
			timer = scheduler.schedule(this::watchObjects, watchPeriod, TimeUnit.MILLISECONDS);
		}
	}

	@Override
	public void stopped() {
		running.set(false);
		if (timer != null) {
			timer.cancel(false);
			timer = null;
		}
	}

	// --- ACTIONS ---

	/**
	 * Lists all object (MBean) names in the JMX registry.
	 */
	public Action listObjectNames = (ctx) -> {

		// Check connection and context
		checkContext(ctx);

		// Query (eg. "*.*, "java.lang:*"),
		// this parameter is optional.
		String query = ctx.params.get("query", (String) null);
		if (query != null) {
			query = query.trim();
			if (query.isEmpty() || "*.*".equals(query)) {
				query = null;
			}
		}

		// Collect ObjectNames
		Set<ObjectName> objectNames = null;
		try {

			// JMX-based query syntax
			ObjectName objectName = query == null ? null : new ObjectName(query);
			objectNames = connection.queryNames(objectName, null);
		} catch (Exception syntaxError) {
		}
		if ((objectNames == null || objectNames.isEmpty()) && query != null) {

			// Simple "contains" filter
			objectNames = connection.queryNames(null, null);
			Iterator<ObjectName> i = objectNames.iterator();
			String test = query.toLowerCase().replace("*", "");
			while (i.hasNext()) {
				if (!i.next().getCanonicalName().toLowerCase().contains(test)) {
					i.remove();
				}
			}
		}

		// Convert response to Tree (~= JSON)
		FastBuildTree rsp = new FastBuildTree(1);
		ArrayList<String> keys = new ArrayList<>(objectNames.size());
		rsp.putUnsafe("objectNames", keys);
		for (ObjectName name : objectNames) {
			keys.add(name.getCanonicalName());
		}
		return rsp;
	};

	/**
	 * Retrieves one object (MBean) from the JMX registry.
	 */
	public Action getObject = (ctx) -> {

		// Check connection and context
		checkContext(ctx);

		// Get by MBean by the canonical ObjectName
		String objectName = ctx.params.get("objectName", (String) null);
		if (objectName != null) {
			objectName = objectName.trim();
		}
		if (objectName == null || objectName.isEmpty()) {
			throw new MoleculerServerError("The \"objectName\" property is required!", localNodeID, "NO_OBJECT_NAME");
		}
		return new CheckedTree(objectToJson(new ObjectName(objectName)));
	};

	/**
	 * Retrieves one object's attribute from the JMX registry.
	 */
	public Action getAttribute = (ctx) -> {

		// Check connection and context
		checkContext(ctx);

		// MBean's canonical ObjectName
		String objectName = ctx.params.get("objectName", (String) null);
		if (objectName != null) {
			objectName = objectName.trim();
		}
		if (objectName == null || objectName.isEmpty()) {
			throw new MoleculerServerError("The \"objectName\" property is required!", localNodeID, "NO_OBJECT_NAME");
		}

		// Attribute name of the object
		String attributeName = ctx.params.get("attributeName", (String) null);
		if (attributeName != null) {
			attributeName = attributeName.trim();
		}
		if (attributeName == null || attributeName.isEmpty()) {
			throw new MoleculerServerError("The \"attributeName\" property is required!", localNodeID,
					"NO_ATTRIBUTE_NAME");
		}

		// Retrieve attribute's value
		Object value = connection.getAttribute(new ObjectName(objectName), attributeName);

		// Pick one property from a CompositeData
		Tree rsp = new CheckedTree(convertValue(value));
		if (value != null) {
			String path = ctx.params.get("path", (String) null);
			if (path != null) {
				rsp = rsp.get(path.trim());
			}
		}

		// Convert response to Tree (~= JSON)
		return rsp;
	};

	/**
	 * Find all MBeans by a query string (eg. "java.lang:*" or "memoryusage").
	 * This service is not the fastest, but very useful when looking for the
	 * appropriate ObjectName parameter in the JMX registry.
	 */
	public Action findObjects = (ctx) -> {

		// Check connection and context
		checkContext(ctx);

		// Find all MBeans by a "piece of text" (required)
		String query = ctx.params.get("query", (String) null);
		if (query != null) {
			query = query.trim().toLowerCase();
		}
		if (query == null || query.isEmpty()) {
			throw new MoleculerServerError("The \"query\" property is required!", localNodeID, "NO_QUERY");
		}

		// Max number of objects
		int max = ctx.params.get("max", 64);

		// Collect ObjectNames
		Set<ObjectName> objectNames = null;
		try {

			// JMX-based query syntax
			ObjectName objectName = query == null ? null : new ObjectName(query);
			objectNames = connection.queryNames(objectName, null);
		} catch (Exception syntaxError) {
		}
		if ((objectNames == null || objectNames.isEmpty()) && query != null) {

			// Simple "contains" filter
			objectNames = connection.queryNames(null, null);
			Iterator<ObjectName> i = objectNames.iterator();
			String test = query.toLowerCase().replace("*", "");
			while (i.hasNext()) {
				if (!i.next().getCanonicalName().toLowerCase().contains(test)) {
					i.remove();
				}
			}
		}

		// Convert response to Tree (~= JSON)
		FastBuildTree rsp = new FastBuildTree(1);
		LinkedList<Map<String, Object>> list = new LinkedList<>();
		rsp.putUnsafe("objects", list);
		for (ObjectName objectName : objectNames) {
			try {
				Map<String, Object> map = objectToJson(objectName);
				if (map == null) {
					continue;
				}
				Tree objectTree = new Tree(map);
				String txt = objectTree.toString(null, false);
				if (txt.toLowerCase().contains(query)) {
					list.addLast(map);
					if (list.size() >= max) {
						break;
					}
				}
			} catch (Exception unsupported) {
				continue;
			}
		}
		return rsp;
	};

	// --- EVENT HANDLING ---

	public boolean addObjectWatcher(ObjectWatcher watcher) {
		checkRunningState();
		return objectWatchers.add(watcher);
	}

	public boolean removeObjectWatcher(ObjectWatcher watcher) {
		checkRunningState();
		return objectWatchers.remove(watcher);
	}

	protected void checkRunningState() {
		if (running.get()) {
			throw new IllegalStateException(
					"The service is already running, the list of Watchers can no longer be changed!");
		}
	}

	protected void watchObjects() {
		executor.execute(() -> {
			Iterator<ObjectWatcher> watchers = objectWatchers.iterator();
			while (watchers.hasNext()) {
				ObjectWatcher watcher = watchers.next();
				try {

					// Check required parameters
					if (watcher.objectName == null) {
						throw new IllegalArgumentException("The value of the \"ObjectName\" parameter cannot be null!");
					}
					if (watcher.event == null) {
						throw new IllegalArgumentException("The value of the \"event\" parameter cannot be null!");
					}

					// Create ObjectName
					Object value;
					ObjectName objectName;
					try {
						objectName = new ObjectName(watcher.objectName);
					} catch (MalformedObjectNameException malformedName) {
						logger.error("Invalid object name (" + watcher.objectName + ")!", malformedName);
						watchers.remove();
						continue;
					}

					// Get object/attribute from the JMX registry
					if (watcher.attributeName == null || watcher.attributeName.isEmpty()) {

						// Retrieve the entire MBean
						value = objectToJson(objectName);

					} else {

						// Retrieve attribute's value
						value = connection.getAttribute(objectName, watcher.attributeName);

					}

					// Pick one property from a CompositeData
					Tree payload = new CheckedTree(convertValue(value));
					if (value != null && watcher.path != null && !watcher.path.isEmpty()) {
						payload = payload.get(watcher.path);
					}

					// Convert values to unformatted JSON
					String currentText = payload.toString(false);
					String previousText = previousValues.get(watcher);

					// Changed?
					if (previousText != null && previousText.equals(currentText)) {
						continue;
					}

					// Store the current serialized JSON
					previousValues.put(watcher, currentText);

					// Get event group(s)
					Groups groups;
					if (watcher.groups == null || watcher.groups.isEmpty()) {
						groups = null;
					} else {
						groups = Groups.of(watcher.groups);
					}

					// Notify listeners
					if (watcher.broadcast) {

						// Broadcast event (send to all listeners)
						broker.broadcast(watcher.event, payload, groups);
						System.out.println("broadcast " + watcher.event + "\r\n" + payload);

					} else {

						// Emit event (send to one of the listeners)
						broker.broadcast(watcher.event, payload, groups);
						System.out.println("emit " + watcher.event + "\r\n" + payload);

					}
				} catch (Exception cause) {
					logger.error("Unable to get object from JMX registry!", cause);
					try {
						Thread.sleep(5000);
					} catch (InterruptedException interrupt) {
						return;
					}
				}
			}
			if (running.get() && !objectWatchers.isEmpty()) {
				timer = scheduler.schedule(this::watchObjects, watchPeriod, TimeUnit.MILLISECONDS);
			}
		});
	}

	// --- INTERNAL UTILITIES ---

	protected void checkContext(Context ctx) {
		if (connection == null) {
			throw new MoleculerServerError("JMX is not available!", localNodeID, "NO_JMX");
		}
		if (ctx == null || ctx.params == null) {
			throw new MoleculerServerError("There are not input parameters!", localNodeID, "NO_CTX");
		}
	}

	protected Map<String, Object> objectToJson(ObjectName objectName) throws Exception {
		MBeanAttributeInfo[] attrs = connection.getMBeanInfo(objectName).getAttributes();
		LinkedHashMap<String, Object> map = new LinkedHashMap<>((attrs.length + 1) * 2);
		for (MBeanAttributeInfo attr : attrs) {
			if (!attr.isReadable()) {
				continue;
			}
			try {
				Object value = connection.getAttribute(objectName, attr.getName());
				map.put(attr.getName(), convertValue(value));
			} catch (Exception unsupported) {
				map.put(attr.getName(), null);
			}
		}
		map.put("ObjectName", objectName.getCanonicalName());
		return map;
	}

	protected Object convertValue(Object value) {
		try {
			if (value == null) {
				return null;
			}
			if (value instanceof CompositeData) {
				CompositeData data = (CompositeData) value;
				Set<String> keys = data.getCompositeType().keySet();
				LinkedHashMap<String, Object> map = new LinkedHashMap<>(keys.size() + 1);
				for (String key : keys) {
					map.put(key, convertValue(data.get(key)));
				}
				return map;
			}
			if (value.getClass().isArray()) {
				int len = Array.getLength(value);
				ArrayList<Object> list = new ArrayList<>(len);
				for (int i = 0; i < len; i++) {
					list.add(convertValue(Array.get(value, i)));
				}
				return list;
			}
			if (value instanceof Collection) {
				LinkedList<Object> list = new LinkedList<>();
				for (Object val : (Collection<?>) value) {
					list.add(convertValue(val));
				}
				return list;
			}
			if (value instanceof Map) {
				Map<?, ?> from = (Map<?, ?>) value;
				LinkedHashMap<String, Object> map = new LinkedHashMap<>(from.size() * 2);
				for (Map.Entry<?, ?> entry : from.entrySet()) {
					map.put(String.valueOf(entry.getKey()), convertValue(entry.getValue()));
				}
				return map;
			}
			if (value instanceof Double) {
				Double num = (Double) value;
				if (num.isNaN() || num.isInfinite()) {
					return null;
				}
				return num;
			}
			if (value instanceof Float) {
				Float num = (Float) value;
				if (num.isNaN() || num.isInfinite()) {
					return null;
				}
				return num;
			}
			return value;
		} catch (Exception unsupported) {
			return null;
		}
	}

	// --- GETTERS AND SETTERS ---

	public Map<String, ?> getEnvironment() {
		return environment;
	}

	public void setEnvironment(Map<String, Object> environment) {
		this.environment = environment;
	}

	public boolean isLocal() {
		return local;
	}

	public void setLocal(boolean local) {
		this.local = local;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = Objects.requireNonNull(url);
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public Set<ObjectWatcher> getObjectWatchers() {
		return objectWatchers;
	}

	public void setObjectWatchers(Set<ObjectWatcher> objectWatchers) {
		checkRunningState();
		this.objectWatchers = Objects.requireNonNull(objectWatchers);
	}

	public long getWatchPeriod() {
		return watchPeriod;
	}

	public void setWatchPeriod(long watchPeriod) {
		if (watchPeriod < 200) {

			// Too fast polling frequency!
			this.watchPeriod = 200;
			logger.warn("The value of the \"watchPeriod\" parameter must not be less than 200 msec!");

		} else {
			this.watchPeriod = watchPeriod;
		}
	}

}