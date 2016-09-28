import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 *	Class Daemon. Manages the entire routing process, including sending and receiving packets and timer management.
 *	@see Main
 *	@see RoutingTableEntry
 */

public class Daemon {
	
	private int router_id;
	private int[][] output_ports;
	private Map<Integer, RoutingTableEntry> routingTable = new HashMap<Integer, RoutingTableEntry>();
	private Selector selector;
	private boolean periodicSend = true;
	private Map<Integer, Timer> timeoutTimers = new HashMap<Integer, Timer>();
	private Map<Integer, Timer> garbageTimers = new HashMap<Integer, Timer>();
	final private int AF_INET = 2;
	final private int PERIODIC_UPDATE_INTERVAL = 5000;
	final private int TIMEOUT_INTERVAL = PERIODIC_UPDATE_INTERVAL * 6;
	final private int GARBAGE_INTERVAL = PERIODIC_UPDATE_INTERVAL * 4;
	final int INFINITY = 16;
	private ByteBuffer buffer = ByteBuffer.allocate(1024);
	
	/**
	 *	Class constructor.
	 *	Creates a channel for each input port, registers it to the Selector and initializes the timers' maps.
	 *	@param router_id int representing the unique identification of the router within which the daemon runs.
	 *	@param input_ports Array of int representing the different input ports of the router.
	 *	@param output_ports Array of array of int representing for each output ports of the router, the link cost and the router located at the other side of the link.
	 *	@throws IOException If an input or output exception occured.
	 *	@see java.nio.channels.DatagramChannel
	 *	@see java.nio.channels.Selector
	 *	@see java.util.Timer
	 */	
	public Daemon(int router_id, int[] input_ports, int[][] output_ports) throws IOException{
		super();
		this.router_id = router_id;
		this.output_ports = output_ports;
		selector = Selector.open();
		
		for(int i = 0; i < input_ports.length; i++){
			DatagramChannel channel = DatagramChannel.open();
			channel.socket().bind(new InetSocketAddress(input_ports[i]));
			channel.configureBlocking(false);
			channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

			timeoutTimers.put(output_ports[i][2], new Timer());
			garbageTimers.put(output_ports[i][2], new Timer());
		}
	}
	
	/**
	 *	Method select. This method is called in the Main class.
	 *	Invokes the selectNow() method of the router's selector.
	 *	@throws IOException If an input or output exception occurred.
	 *	@see java.nio.channels.Selector#selectNow
	 */
	public void select() throws IOException {
		selector.selectNow();
		
	}
	
	/**
	 *	Method isSelected. This method is called in the Main class.
	 *	Processes the selection by looping over the selected keys. Our concerns here are the cases when a selected key is readable (meaning a packet has been received) or writable (meaning the selector's channel is ready for writing, then the sending process is started).
	 *	@throws IOException If an input or output exception occurred.
	 *	@see #receivePacket(DatagramChannel channel)
	 *	@see #sendPackets(boolean triggered)
	 *	@see java.nio.channels.Selector
	 *	@see java.nio.channels.SelectionKey
	 *	@see java.nio.channels.DatagramChannel
	 */
	public void isSelected() throws IOException {
		Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
		while (selectedKeys.hasNext()) {
			SelectionKey key = (SelectionKey) selectedKeys.next();
						
			if(key.isAcceptable()) {
				// a connection was accepted by a ServerSocketChannel.
				System.out.println("A connection was accepted");

			} else if (key.isConnectable()) {
				// a connection was established with a remote server.
				System.out.println("A connection was established");
				
			} else if (key.isReadable()) {
				receivePacket((DatagramChannel) key.channel()); 
				
			} else if (key.isWritable() && periodicSend) {
				sendPackets(false);
				periodicSend = false;
				displayRoutingTable();
			}
			
			selectedKeys.remove();
		}
	}
	
	/**
	 *	Synchronized method sendPackets. This method is called by the method isSelected and by the timeout's run method.
	 *	Fills the buffer and sends it to all the router's output ports via a datagram channel.
	 *	@param triggered boolean only needed for system output.
	 *	@throws IOException If an input or output exception occurred.
	 *	@see #isSelected()
	 *	@see #createBuffer()
	 *	@see TimeoutHandler#run()
	 *	@see java.nio.ByteBuffer
	 *	@see java.nio.channels.DatagramChannel
	 */
	public synchronized void sendPackets(boolean triggered) throws IOException {
		
		buffer.clear();
		String text = "periodic";
		if(triggered) {
			text = "triggered";
			
		}
		System.out.print("Sending " + text + " packets: ");
		String end = ", ";
		
		for(int i = 0; i < output_ports.length; i++) {
			String begin = "";
			createBuffer();
			buffer.flip();
			DatagramChannel sender = DatagramChannel.open();
			int bytesSent = sender.send(buffer, new InetSocketAddress("localhost", output_ports[i][0]));
			if(i == output_ports.length-1){
				end = ".";
			}
			else if(i == 0){
				begin = bytesSent + "Bytes to ports: ";
			}
			System.out.print(begin + output_ports[i][0] + end);
			sender.close();
			buffer.clear();
		}
		System.out.println("\n");
		
	}
	
	/**
	 *	Private method createBuffer. This method is called by the method sendPackets.
	 *	Fills the router's buffer by a RIP formatted packet.
	 *	@see #sendPackets()
	 *	@see java.nio.ByteBuffer
	 */ 
	private void createBuffer() {
	
		byte[] header = new byte[4];
		byte[] entries = new byte[20*routingTable.size()];
		header[0] = new Integer(2).byteValue();
		header[1] = new Integer(2).byteValue();
		header[3] = new Integer(router_id).byteValue();
		buffer.put(header);
		for(int i = 0; i < routingTable.size(); i++){
			entries[20*i+1] = new Integer(AF_INET).byteValue();
		}
		int i = 0;
		for(Integer key: routingTable.keySet()) {
			entries[20*i+7] = key.byteValue();
			entries[20*i+15] = new Integer(routingTable.get(key).getFirst_hop_id()).byteValue();
			entries[20*i+19] = new Integer(routingTable.get(key).getCost()).byteValue();
			i++;
		}
		buffer.put(entries);
	}
	
	/**
	 *	Method receivePacket. This method is called by the method isSelected.
	 *	Processes the arrival of a packet. It stores the packet in the buffer, then reads the content of the buffer and updates the routing table if the packet is consistent (drops it otherwise).
	 *	@param channel DatagramChannel from which the packet is coming.
	 *	@throws IOException If an input or output exception occured.
	 *	@see #isSelected()
	 *	@see java.nio.ByteBuffer
	 *	@see java.nio.channels.DatagramChannel
	 */
	public void receivePacket(DatagramChannel channel) throws IOException{
			
		channel.receive(buffer);
		buffer.flip();
		
		int[] data = readReceivedPackets();
		System.out.println("Received Packet from " + data[3]);
		if(consistentPacket(data)){	
			updateRoutingTable(data);
		}
	}
	
	/**
	 *	Private method readReceivedPackets. This method is called by the method receivePacket.
	 *	Reads the received packets stores in the router's buffer.
	 *	@return The content of the buffer as an array of int.
	 *	@see #receivePacket(DatagramChannel channel)
	 *	@see java.nio.ByteBuffer
	 */
	private int[] readReceivedPackets(){
		
		int[] data = new int[buffer.limit()];
		int i = 0;
	
		while(buffer.hasRemaining()){
			data[i] = (int) buffer.get();
			i++;
		}
		
		buffer.clear();
		
		//Uncomment the paragraph to display the content of the received packet.
		/*
		System.out.println("----------Header----------");
		System.out.print("Command: " + data[0] + "     ");
		System.out.print("Version: " + data[1] + "     ");
		System.out.println("Sender: " + data[3] + "     ");
		i = 0;
		while(20*i+4 < data.length){
			System.out.println("----------Entry " + i + "---------");
			System.out.println("AFI: " + data[20*i+5] + "     ");
			System.out.println("RouterID: " + data[20*i+11] + "     ");
			System.out.println("NextHop: " + data[20*i+19] + "     ");
			System.out.println("Cost: " + data[20*i+23] + "     ");
			i++;
		}
		System.out.println();
		*/
		
		return data;
	}
	
	/**
	 *	Private method consistentPacket. This method is called by the method receivePacket.
	 *	Processes all the necessary check on a received packet (length, fixed values, AFI, metric range).
	 *	@param data Array of int representing the content of the received packet.
	 *	@return true if the packet is consistent, false otherwise.
	 *	@see #receivePacket(DatagramChannel channel)
	 *	@see #checkAFI(int[] data)
	 *	@see #checkMetricRange(int[] data)
	 */
	private boolean consistentPacket(int[] data){
	
		if(data.length % 20 != 4){
			System.out.println("Wrong packet length: " + data.length);
			return false;
		}
		if(data[0] != 2 || data[1] != 2){
			System.out.println("Wrong fixed value: ");
			System.out.println("Command: " + data[0]);
			System.out.println("Version: " + data[1]);
			return false;
		}
		if(!checkAFI(data)){
			System.out.println("Wrong AFI: ");
			for(int i = 0; i < (data.length-4)/20; i++)
				System.out.println(data[20*i+5]);
			return false;
		}
		if(!checkMetricRange(data)){
			System.out.println("Wrong metric range: ");
			for(int i = 0; i < (data.length-4)/20; i++)
				System.out.println(data[20*i+19]);
			return false;
		}
		return true;
	}
	
	/**
	 *	Private method checkAFI. This method is called by the method consistentPacket.
	 *	Checks if the AFI of the received packet is correct or not.
	 *	@param data Array of int representing the content of the received packet.
	 *	@return true if the AFI of the received packet is correct, false otherwise.
	 *	@see #consistentPacket(int[] data)
	 */
	private boolean checkAFI(int[] data){
	
		for(int i = 0; i < (data.length-4)/20; i++)
			if(data[20*i+5] != AF_INET)
				return false;
		return true;
	}
	
	/**
	 *	Private method checkMetricRange. This method is called by the method consistentPacket.
	 *	Checks if the metric range of the received packet is correct or not.
	 *	@param data Array of int representing the content of the received packet.
	 *	@return true if the metric range of the received packet is correct, false otherwise.
	 *	@see #consistentPacket(int[] data)
	 */
	private boolean checkMetricRange(int[] data){
	
		for(int i = 0; i < (data.length-4)/20; i++)
			if(data[20*i+19] < 0 || data[20*i+19] > INFINITY)
				return false;
		return true;
	}
	
	/**
	 *	Private method updateRoutingTable. This method is called by the method receivePacket.
	 *	Updates the router's routing table after each consistent received packed.
	 *	@param data Array of int representing the content of the received packet.
	 *	@see #receivePacket(DatagramChannel channel)
	 *	@see #updateLine(int id, int[] line)
	 */
	private void updateRoutingTable(int[] data){
		
		int[] line = new int[3];
		for(int i = 0; i < (data.length-4)/20; i++){
			line[0] = data[20*i+11];
			line[1] = data[20*i+19];
			line[2] = data[20*i+23];
			updateLine(data[3], line);
		}
		
		//displayRoutingTable();
	}
	
	/**
	 *	Private method updateLine. This method is called by the method updateRoutingTable.
	 *	Updates one line of the router's routing table. 
	 *	Also handles the timers process: reseting of the timeout timer and starting the garbage timer depending on the line's content.
	 *	@param id int representing the route destination.
	 *	@param line Array of int representing, in order, the destination, the first hop and the cost of the route.
	 *	@see #updateRoutingTable(int[] data)
	 *	@see #resetTimeoutTimer(int route_id)
	 *	@see #startGarbageTimer(int route_id)
	 *	@see RoutingTableEntry
	 *	@see java.util.Timer
	 */
	private void updateLine(int id, int[] line){	
		int destination = line[0];
		int cost = line[2];
		int index_port = -1;
		
		for(int i = 0; i < output_ports.length; i++){
			if(output_ports[i][2] == id){
				index_port = i;
				break;
			}
		}
		
		int metric = Math.min(cost + output_ports[index_port][1], INFINITY);
		
		if(routingTable.containsKey(destination)) {
			
			RoutingTableEntry route = routingTable.get(destination);
			
			if(route.getFirst_hop_id() == id) {
				resetTimeoutTimer(destination);
				//System.out.println("Reseting timer for: " + destination);
			}
			if((route.getFirst_hop_id() == id && metric != route.getCost()) || metric < route.getCost()) {
				route.setCost(metric);
				route.setFirst_hop_id(id);
				
				if(metric == INFINITY) {
					timeoutTimers.get(destination).cancel();
					startGarbageTimer(destination);
				} else {
					garbageTimers.get(destination).cancel();
					resetTimeoutTimer(destination);
					route.setGarbage(false);
					//System.out.println("Reseting timer for: " + destination + " (Metric changed)");
				}
			}
		} else {
			
			if(metric != INFINITY) {
				routingTable.put(destination, new RoutingTableEntry(destination, id, metric));
				garbageTimers.put(destination, new Timer());
				timeoutTimers.put(destination, new Timer());
				resetTimeoutTimer(destination);
			}
		}
	}
	
	/**
	 *	Method setupRoutingTable. This method is called in the Main class.
	 *	Set up the router's routing table by entering the first entry (route to himself).
	 *	@see Main
	 *	@see RoutingTableEntry
	 */
	public void setupRoutingTable() {
		System.out.println("Setting routing table.");
		routingTable.put(router_id, new RoutingTableEntry(router_id, router_id, 0));
		displayRoutingTable();
		
	}
	
	/**
	 *	Method startPeriodicTimer. This method is called in the Main class.
	 *	Starts the "scheduled at fixed rate" timer.
	 *	@see Main
	 *	@see PeriodicHandler
	 *	@see java.util.Timer#scheduleAtFixedRate(TimerTask task, long delay, long period)
	 */
	public void startPeriodicTimer() {
		Timer t = new Timer();
		PeriodicHandler task = new PeriodicHandler(this);
		// We want a random element to the timer interval, in the range
		// [0.8*interval, 1.2*interval]
		t.scheduleAtFixedRate(task, 0, (long) (PERIODIC_UPDATE_INTERVAL * (0.8 + Math.random() * 0.4)));
	}
	
	/**
	 *	Method startGarbageTimer. This method is called by the method updateLine and by the TimeoutHandler's run method.
	 *	Starts the garbage timer for the given route.
	 *	@param route_id int representing the destination about which the garbage timer runs.
	 *	@see #updateLine(int id, int[] line)
	 *	@see GarbageHandler
	 *	@see java.util.Timer#schedule(TimerTask task, long delay)
	 *	@see TimeoutHandler#run()
	 */
	public void startGarbageTimer(int route_id) {
		//System.out.println("----- Garbage timer started, Route: " + route_id + " -----");
		routingTable.get(route_id).setGarbage(true);
		garbageTimers.get(route_id).cancel();
		garbageTimers.replace(route_id, new Timer());
		GarbageHandler task = new GarbageHandler(this, route_id);
		garbageTimers.get(route_id).schedule(task, GARBAGE_INTERVAL);
	}
	
	/**
	 *	Method resetTimeoutTimer. This method is called by the method updateLine.
	 *	Resets the timeout timer for the given route.
	 *	@param route_id int representing the destination about which the timeout timer runs.
	 *	@see #updateLine(int id, int[] line)
	 *	@see TimeoutHandler
	 *	@see java.util.Timer#schedule(TimerTask task, long delay)
	 */
	public void resetTimeoutTimer(int route_id) {
		timeoutTimers.get(route_id).cancel();
		timeoutTimers.replace(route_id, new Timer());
		TimeoutHandler task = new TimeoutHandler(this, route_id);
		timeoutTimers.get(route_id).schedule(task, TIMEOUT_INTERVAL);
	}
	
	/**
	 *	Internal class PeriodicHandler extending TimerTask.
	 *	Handles the periodic timer process.
	 *	@see java.util.TimerTask
	 */
	class PeriodicHandler extends TimerTask {

		private Daemon daemon;
		
		/**
		 *	Class constructor.
	 	 *	Creates the periodic handler.
	 	 *	@param daemon The daemon on which runs the timeout (this).
		 */
		public PeriodicHandler(Daemon daemon) {
			this.daemon = daemon;
		}
		
		/**
		 *	Overridden method run (from TimerTask). This method is automatically invoked when the timer has expired.
		 *	Sets the boolean periodicSend to true so that the next writable selector key is used to send a packet.
		 *	@see java.util.TimerTask#run()
		 */
		@Override
		public void run() {
			daemon.periodicSend = true; 
			//System.out.println("Reseting periodicSend.");
			
		}
	}
	
	/**
	 *	Internal class TimeoutHandler extending TimerTask.
	 *	Handles the timeout timer process.
	 *	@see java.util.TimerTask
	 */
	class TimeoutHandler extends TimerTask {

		private Daemon daemon;
		private int route_id;
		
		/**
		 *	Class constructor.
	 	 *	Creates the timeout handler.
	 	 *	@param daemon The daemon on which runs the timeout (this).
	 	 *	@param route_id int representing the route on which the timeout runs.
		 */
		public TimeoutHandler(Daemon daemon, int route_id) {
			this.daemon = daemon;
			this.route_id = route_id;
		}

		/**
		 *	Overridden synchronized method run (from TimerTask). This method is automatically invoked when the timer has expired.
		 *	Starts the garbage timer, sets the route's cost to INFINITY and sends a triggered packet.
		 *	@see #startGarbageTimer(int route_id)
		 *	@see #sendPackets(boolean triggered)
		 *	@see RoutingTableEntry
		 *	@see java.util.TimerTask#run()
		 */
		@Override
		public synchronized void run() {
			
			System.out.println("----- Timeout activated for: " + route_id + ", garbage timer started. -----");
			startGarbageTimer(route_id);
			daemon.routingTable.get(route_id).setCost(daemon.INFINITY);
			try {
				synchronized(System.out) {
					daemon.sendPackets(true);
					daemon.displayRoutingTable();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			//daemon.displayRoutingTable();
			
		}
	}
	
	/**
	 *	Internal class GarbageHandler extending TimerTask.
	 *	Handles the garbage timer process.
	 *	@see java.util.TimerTask
	 */
	class GarbageHandler extends TimerTask {

		private Daemon daemon;
		private int route_id;

		/**
		 *	Class constructor.
	 	 *	Creates the garbage handler.
	 	 *	@param daemon The daemon on which runs the timeout (this).
	 	 *	@param route_id int representing the route on which the timeout runs.
		 */
		public GarbageHandler(Daemon daemon, int route_id) {
			this.daemon = daemon;
			this.route_id = route_id;
		}

		/**
		 *	Overridden method run (from TimerTask). This method is automatically invoked when the timer has expired.
		 *	Removes the entry from the routing table.
		 *	@see RoutingTableEntry
		 *	@see java.util.TimerTask#run()
		 */
		@Override
		public void run() {
			synchronized(routingTable) {
				System.out.println("----- Garbage collection for: " + route_id + " -----");
				routingTable.remove(route_id);
				daemon.timeoutTimers.get(route_id).cancel();
				//daemon.displayRoutingTable();
			}
		}
	}
	
	/**
	 *	Private method displayRoutingTable. This method is called all along the daemon class in several methods (could be added anywhere if needed)
	 *	Displays the routing table of this daemon. One entry is composed of a destination, a first hop, a cost and a flag set if the garbage process has been initiated for this entry.
	 *	@see RoutingTableEntry
	 */
	private void displayRoutingTable() {
		synchronized(routingTable) {
			
			System.out.println("----- Routing Table of " + router_id + " -----");
			for(Integer key: routingTable.keySet()) {
				String flag = "Inactive";
				String space = " ";
				RoutingTableEntry entry = routingTable.get(key);
				if(entry.isGarbage()) {
					flag = "Active";
				} 
				if(entry.getCost() >= 10) {
					space = "";
				}
				System.out.println("Dest: " + entry.getDestination_id() + ", First Hop: " + entry.getFirst_hop_id() + ", Cost: " + space + entry.getCost() +", Garbage: " +  flag);
			}
			System.out.println("");
		}
	}
}
