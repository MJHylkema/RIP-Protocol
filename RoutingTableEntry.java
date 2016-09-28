/*
    @author (classes and interfaces only, required)
    @version (classes and interfaces only, required. See footnote 1)
    @param (methods and constructors only)
    @return (methods only)
    @exception (@throws is a synonym added in Javadoc 1.2)
    @see
*/

/**
 *	Class RoutingTableEntry. Holds information about a routing table's entry.
 *	@see Daemon
 */
public class RoutingTableEntry {
	
	private int destination_id;
	private int first_hop_id;
	private int cost;
	private boolean garbage = false;
	
	/**
	 *	Class constructor.
	 *	Creates a new instance of a routing table entry.
	 *	@param destinationId int representing the destination of this entry.
	 *	@param firstHopId int representing the first hop of this entry.
	 *	@param metric int representing the cost of this entry.
	 */
	public RoutingTableEntry(int destinationId, int firstHopId, int metric) {
		this.setDestination_id(destinationId);
		this.setFirst_hop_id(firstHopId);
		this.setCost(metric);
	}

	/**
	 *	Method getDestination_id.
	 *	Getter on the attribute destination_id.
	 *	@return int representing the id of the destination.
	 */
	public int getDestination_id() {
		return destination_id;
	}
	
	/**
	 *	Method setDestination_id.
	 *	Setter of the attribute destination_id.
	 *	@param destination_id int representing the id of the destination.
	 */
	public void setDestination_id(int destination_id) {
		this.destination_id = destination_id;
	}

	/**
	 *	Method getFirst_hop_id.
	 *	Getter on the attribute first_hop_id.
	 *	@return int representing the id of the first hop.
	 */
	public int getFirst_hop_id() {
		return first_hop_id;
	}

	/**
	 *	Method setFirst_hop_id.
	 *	Setter of the attribute destination_id.
	 *	@param first_hop_id int representing the id of the first hop.
	 */
	public void setFirst_hop_id(int first_hop_id) {
		this.first_hop_id = first_hop_id;
	}

	/**
	 *	Method getCost.
	 *	Getter on the attribute cost.
	 *	@return int representing the cost of the route.
	 */
	public int getCost() {
		return cost;
	}

	/**
	 *	Method setCost.
	 *	Setter of the attribute cost.
	 *	@param cost int representing the cost of the route.
	 */
	public void setCost(int cost) {
		this.cost = cost;
	}

	/**
	 *	Method isGarbage.
	 *	Getter on the attribute garbage.
	 *	@return true if the attribute garbage is set, false otherwise.
	 */
	public boolean isGarbage() {
		return garbage;
	}

	/**
	 *	Method setGarbage.
	 *	Setter of the attribute garbage.
	 *	@param garbage boolean representing the value to set to the attribute garbage.
	 */
	public void setGarbage(boolean garbage) {
		this.garbage = garbage;
	}
	
}
