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

import java.util.Objects;

/**
 * This class determines which attribute of the MBean is to be monitored by the
 * JMX Service. The attribute is optional (can be null), in this case the full
 * MBean is monitored by the service. The observed parameters can be further
 * specified by specifying the optional "path" parameter. Only the "ObjectName"
 * and the "Event" parameters are mandatory.
 * 
 * <pre>
 * // Define Watcher
 * ObjectWatcher watcher = new ObjectWatcher();
 * watcher.setObjectName("java.lang:type=Memory");
 * watcher.setAttributeName("HeapMemoryUsage");
 * watcher.setPath("used");
 * watcher.setEvent("jmx.memory");
 * 
 * // Create JMX Service
 * JmxService jmx = new JmxService();
 * jmx.addObjectWatcher(watcher);
 * 
 * // Start Service Broker
 * ServiceBroker broker = ServiceBroker.builder().build();
 * broker.createService(jmx).start();
 * </pre>
 */
public class ObjectWatcher {

	// --- VARIABLES ---

	/**
	 * Full ObjectName of the MBean (eg. "java.lang:type=Memory") - required.
	 */
	protected String objectName;

	/**
	 * Optional attribute name of the MBean (eg. "HeapMemoryUsage").
	 */
	protected String attributeName;

	/**
	 * Optional path of the composite attribute (eg. "used").
	 */
	protected String path;

	/**
	 * Moleculer Event Name - required (eg. "memory.usage").
	 */
	protected String event;

	/**
	 * Broadcast (true = send event to ALL of the listeners), or emit (false =
	 * send event to ONE of the listeners).
	 */
	protected boolean broadcast = true;

	/**
	 * Optional name of the event group (eg. "jmxGroup" or
	 * "group1,group2,group3").
	 */
	protected String groups;

	// --- GETTERS / SETTERS ---

	/**
	 * Returns the current value of the "objectName" property. Can't be null.
	 * It's the full ObjectName of the MBean (eg. "java.lang:type=Memory").
	 * 
	 * @return current value of the "objectName" property
	 */
	public String getObjectName() {
		return objectName;
	}

	/**
	 * Sets the new value of the "objectName" property. Can't be null. It's the
	 * full ObjectName of the MBean (eg. "java.lang:type=Memory").
	 * 
	 * @param objectName
	 *            new value of the "objectName" property
	 */
	public void setObjectName(String objectName) {
		String name = Objects.requireNonNull(objectName).trim();
		if (name.isEmpty()) {
			throw new IllegalArgumentException("The value of the \"ObjectName\" parameter cannot be empty!");
		}
		this.objectName = name;
	}

	/**
	 * Returns the current value of the "attributeName" property. It's an
	 * optional attribute name of the MBean (eg. "HeapMemoryUsage").
	 * 
	 * @return current value of the "attributeName" property
	 */
	public String getAttributeName() {
		return attributeName;
	}

	/**
	 * Sets the new value of the "attributeName" property. It's an optional
	 * attribute name of the MBean (eg. "HeapMemoryUsage").
	 * 
	 * @param attributeName
	 *            new value of the "attributeName" property
	 */
	public void setAttributeName(String attributeName) {
		this.attributeName = attributeName == null ? null : attributeName.trim();
	}

	/**
	 * Returns the current value of the "path" property. It's an optional path
	 * of the composite attribute (eg. "used").
	 * 
	 * @return current value of the "path" property
	 */
	public String getPath() {
		return path;
	}

	/**
	 * Sets the new value of the "path" property. It's an optional path of the
	 * composite attribute (eg. "used").
	 * 
	 * @param path
	 *            new value of the "path" property
	 */
	public void setPath(String path) {
		this.path = path == null ? null : path.trim();
	}

	/**
	 * Returns the current value of the "event" property. Can't be null. It's
	 * the name of the Moleculer Event (eg. "memory.usage").
	 * 
	 * @return current value of the "event" property
	 */
	public String getEvent() {
		return event;
	}

	/**
	 * Sets the new value of the "event" property. Can't be null. It's the name
	 * of the Moleculer Event (eg. "memory.usage").
	 * 
	 * @param eventName
	 *            new value of the "event" property
	 */
	public void setEvent(String eventName) {
		String name = Objects.requireNonNull(eventName).trim();
		if (name.isEmpty()) {
			throw new IllegalArgumentException("The value of the \"event\" parameter cannot be empty!");
		}
		this.event = name;
	}

	/**
	 * Returns the current value of the "broadcast" property. Meaning of this
	 * property: Broadcast (true = send event to ALL of the listeners), or emit
	 * (false = send event to ONE of the listeners).
	 * 
	 * @return current value of the "broadcast" property
	 */
	public boolean isBroadcast() {
		return broadcast;
	}

	/**
	 * Sets the new value of the "broadcast" property. Meaning of this property:
	 * Broadcast (true = send event to ALL of the listeners), or emit (false =
	 * send event to ONE of the listeners).
	 * 
	 * @param broadcast
	 *            new value of the "broadcast" property
	 */
	public void setBroadcast(boolean broadcast) {
		this.broadcast = broadcast;
	}

	/**
	 * Returns the current value of the "groups" property. It's an optional name
	 * of the event group (eg. "jmxGroup" or "group1,group2,group3").
	 * 
	 * @return current value of the "groups" property
	 */
	public String getGroups() {
		return groups;
	}

	/**
	 * Sets the new value of the "groups" property. It's an optional name of the
	 * event group (eg. "jmxGroup" or "group1,group2,group3").
	 * 
	 * @param groups
	 *            new value of the "groups" property
	 */
	public void setGroups(String groups) {
		this.groups = groups == null ? null : groups.trim();
	}

}