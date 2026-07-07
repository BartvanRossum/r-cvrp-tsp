package vehicleRouting.instance;

import graph.structures.digraph.DirectedGraphNodeIndex;

public class CustomerLoadNode extends DirectedGraphNodeIndex
{
	private final int customer;
	private final int demand;
	private final int load;
	private final int bitwiseNeighbours;

	public CustomerLoadNode(int customer, int demand, int load, int bitwiseNeighbours)
	{
		this.customer = customer;
		this.demand = demand;
		this.load = load;
		this.bitwiseNeighbours = bitwiseNeighbours;
	}

	public int getCustomer()
	{
		return customer;
	}

	public int getDemand()
	{
		return demand;
	}

	public int getLoad()
	{
		return load;
	}

	public int getBitwiseNeighbours()
	{
		return bitwiseNeighbours;
	}

	public boolean isDepot()
	{
		return customer == 0;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + customer;
		result = prime * result + demand;
		result = prime * result + load;
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CustomerLoadNode other = (CustomerLoadNode) obj;
		return customer == other.customer && demand == other.demand && load == other.load;
	}
}
