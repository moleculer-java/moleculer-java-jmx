package services.moleculer.jmx;

import java.lang.management.ManagementFactory;
import java.rmi.registry.LocateRegistry;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.junit.Test;

import io.datatree.Tree;
import junit.framework.TestCase;
import services.moleculer.ServiceBroker;
import services.moleculer.error.MoleculerError;
import services.moleculer.monitor.ConstantMonitor;

public class JmxServiceTest extends TestCase {

	// --- ACTIONS OF THE JMX SERVICE ---

	private static final String LST = "jmx.listObjectNames";
	private static final String OBJ = "jmx.getObject";
	private static final String ATR = "jmx.getAttribute";
	private static final String FND = "jmx.findObjects";

	private static final String URL = "service:jmx:rmi://localhost/jndi/rmi://localhost:1234/jmxrmi";

	// --- SERVICE BROKER ---

	private ServiceBroker br;

	// --- JMX SERVER ---

	private JMXConnectorServer svr;

	// --- TEST METHODS ---

	@Test
	public void testLocal() throws Exception {
		JmxService jmx = new JmxService();
		installWatcher(jmx);
		assertTrue(jmx.isLocal());
		br.createService(jmx);
		br.start();
		doTests();
	}

	@Test
	public void testRemote() throws Exception {
		LocateRegistry.createRegistry(1234);
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
		JMXServiceURL url = new JMXServiceURL(URL);
		svr = JMXConnectorServerFactory.newJMXConnectorServer(url, null, mbs);
		svr.start();

		JmxService jmx = new JmxService();
		installWatcher(jmx);
		jmx.setLocal(false);
		assertFalse(jmx.isLocal());
		jmx.setUrl(URL);
		assertEquals(URL, jmx.getUrl());
		br.createService(jmx);
		br.start();
		doTests();
	}

	private void installWatcher(JmxService jmx) {
		Map<String, Object> environment = new HashMap<>();
		jmx.setEnvironment(environment);
		assertEquals(environment, jmx.getEnvironment());
		jmx.setEnvironment(null);
		assertNull(jmx.getEnvironment());

		jmx.setUsername("a");
		assertEquals("a", jmx.getUsername());
		jmx.setUsername(null);
		assertNull(jmx.getUsername());

		jmx.setPassword("b");
		assertEquals("b", jmx.getPassword());
		jmx.setPassword(null);
		assertNull(jmx.getPassword());

		jmx.setWatchPeriod(100);
		assertEquals(200, jmx.getWatchPeriod());
		jmx.setWatchPeriod(300);
		assertEquals(300, jmx.getWatchPeriod());

		ObjectWatcher watcher = new ObjectWatcher();

		try {

			// Empty "objectName"
			watcher.setObjectName("");
			fail();

		} catch (IllegalArgumentException e) {

			// Ok!
		}
		try {

			// Empty "event"
			watcher.setEvent("");
			fail();

		} catch (IllegalArgumentException e) {

			// Ok!
		}

		// Nulls
		watcher.setGroups(null);
		assertNull(watcher.getGroups());
		watcher.setPath(null);
		assertNull(watcher.getPath());
		watcher.setAttributeName(null);
		assertNull(watcher.getAttributeName());

		watcher.setObjectName("java.lang:type=Memory");
		assertEquals("java.lang:type=Memory", watcher.getObjectName());
		watcher.setAttributeName("HeapMemoryUsage");
		assertEquals("HeapMemoryUsage", watcher.getAttributeName());
		watcher.setPath("used");
		assertEquals("used", watcher.getPath());
		watcher.setEvent("jmx.memory");
		assertEquals("jmx.memory", watcher.getEvent());
		watcher.setGroups("jmx");
		assertEquals("jmx", watcher.getGroups());
		watcher.setBroadcast(false);
		assertFalse(watcher.isBroadcast());
		watcher.setBroadcast(true);
		assertTrue(watcher.isBroadcast());

		assertTrue(jmx.addObjectWatcher(watcher));
		assertFalse(jmx.addObjectWatcher(watcher));
		assertTrue(jmx.removeObjectWatcher(watcher));
		assertFalse(jmx.removeObjectWatcher(watcher));
		assertTrue(jmx.addObjectWatcher(watcher));

		Set<ObjectWatcher> watchers = jmx.getObjectWatchers();
		assertEquals(1, watchers.size());
		jmx.setObjectWatchers(watchers);
		assertEquals(1, jmx.getObjectWatchers().size());
	}

	public void doTests() throws Exception {

		// --- LIST OBJECT NAMES ---

		assertNotNull(br.getAction(LST));
		Tree rsp = br.call(LST).waitFor();
		Tree array = rsp.get("objectNames");
		assertTrue(array.isEnumeration());
		assertNotNull(array);
		assertTrue(array.size() > 0);
		assertEquals(array.size(), br.call(LST, "query", "*.*").waitFor().get("objectNames").size());
		assertEquals(array.size(), br.call(LST, "query", "").waitFor().get("objectNames").size());

		rsp = br.call(LST, "query", "java.lang:*").waitFor();
		array = rsp.get("objectNames");
		assertTrue(array.size() > 0);
		for (Tree test : array) {
			assertTrue(test.asString().startsWith("java.lang:"));
		}

		rsp = br.call(LST, "query", "java.*").waitFor();
		array = rsp.get("objectNames");
		assertTrue(array.size() > 0);
		assertTrue(array.isEnumeration());
		for (Tree test : array) {
			assertTrue(test.asString().startsWith("java."));
		}

		rsp = br.call(LST, "query", ".lang:").waitFor();
		array = rsp.get("objectNames");
		assertTrue(array.size() > 0);
		for (Tree test : array) {
			assertTrue(test.asString().toLowerCase().contains(".lang:"));
		}
		rsp = br.call(LST, "query", ".lang:", "sort", true).waitFor();
		Tree sortedArray = rsp.get("objectNames");
		assertTrue(sortedArray.size() > 0);
		for (Tree test : sortedArray) {
			assertTrue(test.asString().toLowerCase().contains(".lang:"));
		}
		String txt1 = sortedArray.toString();
		sortedArray.sort();
		String txt2 = sortedArray.toString();
		assertEquals(txt1, txt2);
		assertNotSame(txt1, array.toString());

		rsp = br.call(LST, "query", "Code").waitFor();
		array = rsp.get("objectNames");
		assertTrue(array.size() > 0);
		for (Tree test : array) {
			assertTrue(test.asString().toLowerCase().contains("code"));
		}

		rsp = br.call(LST, "query", "AAABBBCCC").waitFor();
		array = rsp.get("objectNames");
		assertTrue(array.isEnumeration());
		assertEquals(0, array.size());

		// --- GET OBJECT ---

		try {

			// Missing "objectName"
			rsp = br.call(OBJ).waitFor();
			fail();

		} catch (MoleculerError e) {

			// Ok!
		}

		try {

			// Empty "objectName"
			rsp = br.call(OBJ, "objectName", "").waitFor();
			fail();

		} catch (MoleculerError e) {

			// Ok!
		}

		try {

			// Invalid syntax
			rsp = br.call(OBJ, "objectName", "AAABBBCCC").waitFor();
			fail();

		} catch (MalformedObjectNameException e) {

			// Ok!
		}

		try {

			// Correct syntax, invalid objectName
			rsp = br.call(OBJ, "objectName", "a:b=c").waitFor();
			fail();

		} catch (InstanceNotFoundException e) {

			// Ok!
		}

		// Get object
		rsp = br.call(OBJ, "objectName", "java.lang:name=Metaspace,type=MemoryPool").waitFor();
		assertTrue(rsp.get("Usage.committed", 0L) > 0L);
		assertTrue(rsp.size() > 10);

		rsp = br.call(OBJ, "objectName", "java.lang:type=OperatingSystem").waitFor();
		assertTrue(rsp.get("AvailableProcessors", 0L) > 0L);
		assertTrue(rsp.size() > 10);

		// --- GET ATTRIBUTE ---

		try {

			// Missing "objectName"
			rsp = br.call(ATR).waitFor();
			fail();

		} catch (MoleculerError e) {

			// Ok!
		}

		try {

			// Empty "objectName"
			rsp = br.call(ATR, "objectName", "").waitFor();
			fail();

		} catch (MoleculerError e) {

			// Ok!
		}

		try {

			// Missing "attributeName"
			rsp = br.call(ATR, "objectName", "java.lang:name=Metaspace,type=MemoryPool").waitFor();
			fail();

		} catch (MoleculerError e) {

			// Ok!
		}

		try {

			// Empty "attributeName"
			rsp = br.call(ATR, "objectName", "java.lang:name=Metaspace,type=MemoryPool", "attributeName", "").waitFor();
			fail();

		} catch (MoleculerError e) {

			// Ok!
		}

		rsp = br.call(ATR, "objectName", "java.lang:name=Metaspace,type=MemoryPool", "attributeName", "Usage")
				.waitFor();
		assertEquals(4, rsp.size());
		assertTrue(rsp.isMap());
		assertTrue(rsp.get("committed", 0L) > 0L);

		rsp = br.call(ATR, "objectName", "java.lang:name=Metaspace,type=MemoryPool", "attributeName", "Usage", "path",
				"committed").waitFor();
		assertEquals(1, rsp.size());
		assertTrue(rsp.isPrimitive());
		assertTrue(rsp.asLong() > 0L);

		// --- FIND OBJECTS ---

		try {

			// Missing "query"
			rsp = br.call(FND).waitFor();
			fail();

		} catch (MoleculerError e) {

			// Ok!
		}

		try {

			// Empty "query"
			rsp = br.call(FND, "query", "").waitFor();
			fail();

		} catch (MoleculerError e) {

			// Ok!
		}

		rsp = br.call(FND, "query", "java.lang:name=Metaspace,type=MemoryPool").waitFor();
		array = rsp.get("objects");
		assertEquals(1, array.size());
		assertTrue(array.isEnumeration());
		assertEquals("java.lang:name=Metaspace,type=MemoryPool", array.get("[0].ObjectName", ""));

		rsp = br.call(FND, "query", ".lang:name=").waitFor();
		array = rsp.get("objects");
		for (Tree test : array) {
			assertTrue(test.toString(false).toLowerCase().contains(".lang:name="));
		}

		rsp = br.call(FND, "query", "java.nio:*").waitFor();
		array = rsp.get("objects");
		for (Tree test : array) {
			assertTrue(test.get("ObjectName", "").contains("java.nio:"));
		}

		// Limit result size
		rsp = br.call(FND, "query", "java", "max", 12).waitFor();
		assertEquals(12, rsp.get("objects").size());

		rsp = br.call(FND, "query", "object", "max", 7).waitFor();
		assertEquals(7, rsp.get("objects").size());

		rsp = br.call(FND, "query", "lang", "max", 3).waitFor();
		assertEquals(3, rsp.get("objects").size());

		// --- TEST WATCHER ---

		JmxListener l = (JmxListener) br.getLocalService("jmxListener");
		LinkedList<Long> list = l.getList();
		assertFalse(list.isEmpty());
		for (Long v : list) {
			assertTrue(v > 0);
		}

		try {

			// Unable to modify
			JmxService jmx = (JmxService) br.getLocalService("jmx");
			jmx.addObjectWatcher(new ObjectWatcher());
			fail();

		} catch (IllegalStateException e) {

			// Ok!
		}
	}

	// --- SET UP ---

	@Override
	protected void setUp() throws Exception {
		br = ServiceBroker.builder().monitor(new ConstantMonitor()).build();
		br.createService(new JmxListener());
	}

	// --- TEAR DOWN ---

	@Override
	protected void tearDown() throws Exception {
		if (br != null) {
			br.stop();
			br = null;
		}
		if (svr != null) {
			svr.stop();
			svr = null;
		}
	}

}