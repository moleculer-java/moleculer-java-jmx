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
import java.util.Collections;
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

	/**
	 * Logger of the service.
	 */
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

	/**
	 * Registered MBean "watchers".
	 */
	protected Set<ObjectWatcher> objectWatchers = new HashSet<>();

	/**
	 * Previous JSON strings of the watched MBeans (can be used for status
	 * comparison what has changed).
	 */
	protected HashMap<ObjectWatcher, String> previousValues = new HashMap<>();

	/**
	 * Time between the comparations in MILLISECONDS.
	 */
	protected long watchPeriod = 3000;

	/**
	 * ServiceBroker's task scheduler.
	 */
	protected ScheduledExecutorService scheduler;

	/**
	 * ServiceBroker's task executor.
	 */
	protected ExecutorService executor;

	/**
	 * Cancelable timer of the MBean "watcher".
	 */
	protected ScheduledFuture<?> timer;

	/**
	 * Marker to determine whether the service is running.
	 */
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
	 * Lists all object (MBean) names in the JMX registry. Object names
	 * appearing in the list can be used in getObject action. There is an
	 * optional "query" parameter to refine the search. The "query" parameter
	 * may contain a text fragment (eg "memory") or a standard JMX query (eg.
	 * "*.*, "java.lang:*"). Example, how to call it from a REPL console:
	 * 
	 * <pre>
	 * call jmx.listObjectNames --query memory
	 * or
	 * call jmx.listObjectNames --sort true
	 * </pre>
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
		
		// Sort names
		if (keys.size() > 1 && ctx.params.get("sort", false)) {
			Collections.sort(keys, String.CASE_INSENSITIVE_ORDER);
		}
		
		// Return response
		return rsp;
	};

	/**
	 * Retrieves one object (MBean) from the JMX registry. Example, how to call
	 * it from a REPL console:
	 * 
	 * <pre>
	 * call jmx.getObject --objectName "java.lang:type=Runtime"
	 * </pre>
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
	 * Retrieves one object's attribute from the JMX registry. Example, how to
	 * call it from a REPL console:
	 * 
	 * <pre>
	 * call jmx.getAttribute --objectName "java.lang:type=Runtime" --attributeName HeapMemoryUsage
	 * or
	 * call jmx.getAttribute --objectName java.lang:type=Runtime
	 *                       --attributeName HeapMemoryUsage
	 *                       --path used
	 * </pre>
	 */
	public Action getAttribute = (ctx) -> {

		// Check connection and context
		checkContext(ctx);

		// MBean's canonical ObjectName (eg. "java.lang:type=Memory")
		String objectName = ctx.params.get("objectName", (String) null);
		if (objectName != null) {
			objectName = objectName.trim();
		}
		if (objectName == null || objectName.isEmpty()) {
			throw new MoleculerServerError("The \"objectName\" property is required!", localNodeID, "NO_OBJECT_NAME");
		}

		// Attribute name of the object (eg. "HeapMemoryUsage")
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

		// Pick one property from a CompositeData (eg. "used")
		Tree rsp = new CheckedTree(convertValue(value));
		if (value != null) {

			// The "path" parameter is optional!
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
	 * appropriate ObjectName parameter in the JMX registry. Example, how to
	 * call it from a REPL console:
	 * 
	 * <pre>
	 * call jmx.findObjects --query memory --max 5
	 * or
	 * call jmx.findObjects --query "java.lang:*"
	 * </pre>
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

	/**
	 * Defines a new ObjectWatcher instance. The service monitors the MBean
	 * specified in the ObjectWatcher periodically and sends a notification (~=
	 * a Moleculer Event) if its value changes. No new ObjectWatcher instance
	 * can be added during run, only if the service is not started yet.
	 * 
	 * @param watcher
	 *            new ObjectWatcher instance
	 * 
	 * @return <code>true</code> if the set of watchers did not already contain
	 *         the specified ObjectWatcher
	 */
	public boolean addObjectWatcher(ObjectWatcher watcher) {
		checkRunningState();
		return objectWatchers.add(watcher);
	}

	/**
	 * Removes the specified ObjectWatcher from the set of watchers. Can only be
	 * called for a stopped JMX Service.
	 * 
	 * @param watcher
	 *            the ObjectWatcher to be removed from the set of watchers, if
	 *            present
	 * 
	 * @return <code>true</code> if this set contained the specified
	 *         ObjectWatcher
	 */
	public boolean removeObjectWatcher(ObjectWatcher watcher) {
		checkRunningState();
		return objectWatchers.remove(watcher);
	}

	/**
	 * Checks the running state.
	 */
	protected void checkRunningState() {
		if (running.get()) {
			throw new IllegalStateException(
					"The service is already running, the list of Watchers can no longer be changed!");
		}
	}

	/**
	 * Compares the status of observed MBeans to their previous status. Will
	 * notify the listeners when a value has changed.
	 */
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

						// Broadcast event (send to ALL listeners)
						broker.broadcast(watcher.event, payload, groups);

					} else {

						// Emit event (send to ONE of the listeners)
						broker.emit(watcher.event, payload, groups);

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

	/**
	 * Verifies the input context.
	 * 
	 * @param ctx
	 *            input context
	 */
	protected void checkContext(Context ctx) {
		if (connection == null) {
			throw new MoleculerServerError("JMX is not available!", localNodeID, "NO_JMX");
		}
		if (ctx == null || ctx.params == null) {
			throw new MoleculerServerError("There are not input parameters!", localNodeID, "NO_CTX");
		}
	}

	/**
	 * Converts an input (JMX) object into Map.
	 * 
	 * @param objectName
	 *            input object
	 * 
	 * @return output Map
	 * 
	 * @throws Exception
	 *             any conversion exception
	 */
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

	/**
	 * Recursively converts an input (JMX) object into Map, List, Set, or a
	 * scalar value.
	 * 
	 * @param value
	 *            input object
	 * 
	 * @return output Map, List, Set, or a scalar value
	 */
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

	/**
	 * Returns the value of the "environment" property. This property is an
	 * optional Environment Map used for connecting the remote JMX server.
	 * 
	 * @return the current value of the property
	 */
	public Map<String, ?> getEnvironment() {
		return environment;
	}

	/**
	 * Sets the new Environment Map. This property is an optional Environment
	 * Map used for connecting the remote JMX server.
	 * 
	 * @param environment
	 *            new Environment Map (can be null)
	 */
	public void setEnvironment(Map<String, Object> environment) {
		this.environment = environment;
	}

	/**
	 * Returns the value of the "local" property. Meaning of this property: Use
	 * local/internal (true) or a remote/rmi (false) JMX connection.
	 * 
	 * @return the current value of the property
	 */
	public boolean isLocal() {
		return local;
	}

	/**
	 * Sets the new value of the "local" property. Meaning of this property: Use
	 * local/internal (true) or a remote/rmi (false) JMX connection.
	 * 
	 * @param local
	 *            use local/internal (true) or a remote/rmi (false) connection
	 */
	public void setLocal(boolean local) {
		this.local = local;
	}

	/**
	 * Returns the value of the JMX "url" property. This property is the remote
	 * JMX server's URL (eg.
	 * "service:jmx:rmi:///jndi/rmi://127.0.0.1:7199/jmxrmi").
	 * 
	 * @return the current value of the property
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * Sets the new value of the "url" property. This property is the remote JMX
	 * server's URL (eg. "service:jmx:rmi:///jndi/rmi://127.0.0.1:7199/jmxrmi").
	 * 
	 * @param url
	 *            RMI URL
	 */
	public void setUrl(String url) {
		this.url = Objects.requireNonNull(url);
	}

	/**
	 * Returns the value of the "username" property. The "username" used for the
	 * optional user authentication (null = no authentication).
	 * 
	 * @return the current value of the username
	 */
	public String getUsername() {
		return username;
	}

	/**
	 * Sets the new value of the "username" property. The "username" used for
	 * the optional user authentication (null = no authentication).
	 * 
	 * @param username
	 *            username for authentication (can be null)
	 */
	public void setUsername(String username) {
		this.username = username;
	}

	/**
	 * Returns the value of the "password" property. The "password" used for the
	 * optional user authentication (null = no authentication).
	 * 
	 * @return the current value of the password
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * Sets the new value of the "password" property. The "password" used for
	 * the optional user authentication (null = no authentication).
	 * 
	 * @param password
	 *            password for authentication (can be null)
	 */
	public void setPassword(String password) {
		this.password = password;
	}

	/**
	 * Returns the entire "objectWatchers" Map. This map contains the registered
	 * MBean "watchers".
	 * 
	 * @return the current value of the property
	 */
	public Set<ObjectWatcher> getObjectWatchers() {
		return objectWatchers;
	}

	/**
	 * Sets the new value of the "objectWatchers" Map. This map contains the
	 * registered MBean "watchers".
	 * 
	 * @param objectWatchers
	 *            set of watchers
	 */
	public void setObjectWatchers(Set<ObjectWatcher> objectWatchers) {
		checkRunningState();
		this.objectWatchers = Objects.requireNonNull(objectWatchers);
	}

	/**
	 * Returns the value of the "watchPeriod" time in MILLISECONDS (it's the
	 * delay between the MBean comparations).
	 * 
	 * @return the current value of the property in MILLISECONDS
	 */
	public long getWatchPeriod() {
		return watchPeriod;
	}

	/**
	 * Sets the new value of the "watchPeriod" property (it's the delay between
	 * the MBean comparations). Its possible minimum value is 200, the ideal
	 * value is about 5000-10000. Too low delay can generate high network
	 * traffic.
	 * 
	 * @param watchPeriod
	 *            the new delay in MILLISECONDS
	 */
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