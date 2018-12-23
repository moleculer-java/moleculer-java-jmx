/**
 * THIS SOFTWARE IS LICENSED UNDER MIT LICENSE.<br>
 * <br>
 * Copyright 2017 Andras Berkes [andras.berkes@programmer.net]<br>
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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import io.datatree.Tree;
import services.moleculer.ServiceBroker;
import services.moleculer.context.Context;
import services.moleculer.error.MoleculerServerError;
import services.moleculer.service.Action;
import services.moleculer.service.Name;
import services.moleculer.service.Service;
import services.moleculer.util.CheckedTree;
import services.moleculer.util.FastBuildTree;

/**
 * The "jmx" Moleculer Service allows you to easily query the contents stored in
 * JMX. Through the service Java and NodeJS-based Moleculer nodes can easily
 * query java-specific data (eg. memory usage, number of threads, or various
 * statistical data).
 */
@Name("jmx")
public class JmxService extends Service {

	// --- VARIABLES ---

	/**
	 * Local nodeID.
	 */
	protected String localNodeID;

	/**
	 * Local or Remote JMX connection.
	 */
	protected MBeanServerConnection connection;

	/**
	 * Use local (true) or a remote (false) JMX connection.
	 */
	protected boolean local = true;

	/**
	 * Remote JMX server's URL.
	 */
	protected String url = "service:jmx:rmi:///jndi/rmi://127.0.0.1:7199/jmxrmi";

	/**
	 * The username used for the optional user authentication (null = no authentication).
	 */
	protected String username;

	/**
	 * The password used for the optional user authentication (null = no authentication).
	 */
	protected String password;

	/**
	 * Optional Environment Map used for connecting the remote JMX server.
	 */
	protected Map<String, Object> environment;

	// --- START SERVICE ---

	/* (non-Javadoc)
	 * @see services.moleculer.service.MoleculerComponent#started(services.moleculer.ServiceBroker)
	 */
	@Override
	public void started(ServiceBroker broker) throws Exception {
		super.started(broker);

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
		ObjectName objectName = query == null ? null : new ObjectName(query);
		Set<ObjectName> objectNames = connection.queryNames(objectName, null);
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
		return objectToJson(new ObjectName(objectName));
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
		return new CheckedTree(convertValue(value));
	};

	/**
	 * Find all MBeans by a query string (eg. "memoryusage").
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
		int max = ctx.params.get("max", 100);

		FastBuildTree rsp = new FastBuildTree(1);
		Set<ObjectName> objectNames = connection.queryNames(new ObjectName("*:*"), null);
		LinkedList<Map<String, Object>> list = new LinkedList<>();
		rsp.putUnsafe("objects", list);
		for (ObjectName objectName : objectNames) {
			try {
				Map<String, Object> map = objectToJson(objectName);
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

	// --- UTILITIES ---

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
		this.url = url;
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

}