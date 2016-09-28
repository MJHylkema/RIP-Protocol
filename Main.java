/**
*	This program is an implementation of the RIPv2 protocol.
*	This file is the Main class of the program.
*	@author François Decq
*	@author Matt Hylkema
*	@version Final (1.0).
*	@see Parser
*	@see Daemon
*	@see RoutingTableEntry
*/

import java.util.Map;
import java.io.IOException;

public class Main {

	private final static String ROUTER_ID = "router-id";
	private final static String INPUT_PORTS = "input-ports";
	private final static String OUTPUT_PORTS = "output-ports";
	
	/**
	 *	Program main method. 
	 *	Runs the Parser class to set up the configuration, initializes the Daemon and then starts a continuous loop of incoming event reaction (using the selector abstract class).
	 *	@param args Program input argument.
	 *	@throws IOException If an input or output exception occurred.
	 *	@see Parser
	 *	@see Daemon
	 *	@see Daemon#setupRoutingTable()
	 *	@see Daemon#startPeriodicTimer()
	 *	@see java.nio.channels.Selector
	 */	
	public static void main(String[] args) throws IOException
	{
		Map<String, Object> config = Parser.ParseConfig(args);
		if(config != null) 
		{
			Parser.PrintConfig(config);
			Daemon daemon = new Daemon((int) config.get(ROUTER_ID), (int[]) config.get(INPUT_PORTS), (int[][]) config.get(OUTPUT_PORTS));
			
			daemon.setupRoutingTable();
			daemon.startPeriodicTimer();
			
			while(true) {
				daemon.select();
				daemon.isSelected();
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		else 
		{
			System.out.println("Error parsing config file.");
		}
	}
}
