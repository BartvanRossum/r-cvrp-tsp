package vehicleRouting.instance;

import java.util.Set;

public class BitwiseNG
{
	public static int getBitwiseRepresentation(Set<Integer> neighbours)
	{
		int bitwiseNeighbours = 0;
		for (int neighbour : neighbours)
		{
			bitwiseNeighbours += (1 << neighbour);
		}
		return bitwiseNeighbours;
	}

	public static boolean contains(int memory, int customer)
	{
		return (memory & (1 << customer)) != 0;
	}

	public static boolean interSects(int firstMemory, int secondMemory)
	{
		return (firstMemory & secondMemory) != 0;
	}

	public static int extend(int memory, int customer, int bitwiseNeighbours)
	{
		int result = (memory & bitwiseNeighbours);
		result |= (1 << customer);
		return result;
	}

	public static boolean isSubset(int firstMemory, int secondMemory)
	{
		return (firstMemory & secondMemory) == firstMemory;
	}
}
