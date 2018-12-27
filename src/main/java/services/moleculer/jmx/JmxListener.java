package services.moleculer.jmx;

import java.util.LinkedList;

import services.moleculer.eventbus.Group;
import services.moleculer.eventbus.Listener;
import services.moleculer.eventbus.Subscribe;
import services.moleculer.service.Service;

public class JmxListener extends Service {

	public LinkedList<Long> received = new LinkedList<>();
	
	@Group("jmx")
	@Subscribe("jmx.memory")
	public Listener setCounter = data -> {
		synchronized(received) {
			received.addLast(data.asLong());
		}
	};
	
	public LinkedList<Long> getList() {
		LinkedList<Long> copy = new LinkedList<>();
		synchronized(received) {
			copy.addAll(received);
			received.clear();
		}
		return copy;
	}
	
}
