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

	public String getObjectName() {
		return objectName;
	}

	public void setObjectName(String objectName) {
		String name = Objects.requireNonNull(objectName).trim();
		if (name.isEmpty()) {
			throw new IllegalArgumentException("The value of the \"ObjectName\" parameter cannot be empty!");
		}
		this.objectName = name;
	}

	public String getAttributeName() {
		return attributeName;
	}

	public void setAttributeName(String attributeName) {
		this.attributeName = attributeName == null ? null : attributeName.trim();
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path == null ? null : path.trim();
	}

	public String getEvent() {
		return event;
	}

	public void setEvent(String eventName) {
		String name = Objects.requireNonNull(eventName).trim();
		if (name.isEmpty()) {
			throw new IllegalArgumentException("The value of the \"event\" parameter cannot be empty!");
		}
		this.event = name;
	}

	public boolean isBroadcast() {
		return broadcast;
	}

	public void setBroadcast(boolean broadcast) {
		this.broadcast = broadcast;
	}

	public String getGroups() {
		return groups;
	}

	public void setGroups(String groups) {
		this.groups = groups == null ? null : groups.trim();
	}

}