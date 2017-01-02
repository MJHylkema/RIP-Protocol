# RIP-Protocol


Class name | Description
--- | --- 
Main.java | Runs the Parser class to set up the configuration, initialises the Daemon and then starts a continuous loop of incoming event reaction (using the selector abstract class). 
Parser.java | Parses router configuration file into a key-value Map.
Daemon.java | Manages the entire routing process, including sending and receiving packets and timer management.
RoutingTableEntry.java | Holds information about a routing table’s entry.
