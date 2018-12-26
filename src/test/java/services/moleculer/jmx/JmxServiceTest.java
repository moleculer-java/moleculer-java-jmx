package services.moleculer.jmx;

import javax.management.InstanceNotFoundException;
import javax.management.MalformedObjectNameException;

import org.junit.Test;

import io.datatree.Tree;
import junit.framework.TestCase;
import services.moleculer.ServiceBroker;
import services.moleculer.monitor.ConstantMonitor;

public class JmxServiceTest extends TestCase {

	// --- ACTIONS OF THE JMX SERVICE ---

	private static final String LST = "jmx.listObjectNames";
	private static final String OBJ = "jmx.getObject";
	private static final String ATR = "jmx.getAttribute";
	private static final String FND = "jmx.findObjects";

	// --- SERVICE BROKER ---

	private ServiceBroker br;

	// --- TEST METHODS ---

	@Test
	public void testLocal() throws Exception {

		// --- LIST OBJECT NAMES ---

		assertNotNull(br.getAction(LST));
		Tree rsp = br.call(LST).waitFor();
		Tree array = rsp.get("objectNames");
		assertTrue(array.isEnumeration());
		assertNotNull(array);
		assertTrue(array.size() > 0);

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
		
		rsp = br.call(ATR, "objectName", "java.lang:name=Metaspace,type=MemoryPool", "attributeName", "Usage").waitFor();
		assertEquals(4, rsp.size());
		assertTrue(rsp.isMap());
		assertTrue(rsp.get("committed", 0L) > 0L);

		rsp = br.call(ATR, "objectName", "java.lang:name=Metaspace,type=MemoryPool", "attributeName", "Usage", "path", "committed").waitFor();
		assertEquals(1, rsp.size());
		assertTrue(rsp.isPrimitive());
		assertTrue(rsp.asLong() > 0L);

		// --- FIND OBJECTS ---
		
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

	}

	// --- SET UP ---

	@Override
	protected void setUp() throws Exception {
		br = ServiceBroker.builder().monitor(new ConstantMonitor()).build();
		br.createService(new JmxService());
		br.start();
	}

	// --- TEAR DOWN ---

	@Override
	protected void tearDown() throws Exception {
		if (br != null) {
			br.stop();
			br = null;
		}
	}

}