package vehicleRouting.columnGeneration;

import graph.algorithms.spprc.Label;
import graph.algorithms.spprc.REF;
import graph.structures.digraph.DirectedGraphArc;
import vehicleRouting.instance.BitwiseNG;
import vehicleRouting.instance.CustomerLoadNode;

public class RouteREF extends REF<CustomerLoadNode, Integer>
{
	public static final int INDEX_NG = 0;
	public static final int INDEX_INFEASIBLE = 1;
	public static final int INDEX_DISTANCE = 2;

	public final int distanceLB;
	public final int distanceUB;

	public RouteREF(int distanceLB, int distanceUB)
	{
		this.distanceLB = distanceLB;
		this.distanceUB = distanceUB;
	}

	@Override
	public int[] extendResourceVector(Label<CustomerLoadNode, Integer> label,
			DirectedGraphArc<CustomerLoadNode, Integer> arc, boolean forward)
	{
		int[] resources = label.getResourceVector().clone();
		CustomerLoadNode nextNode = forward ? arc.getTo() : arc.getFrom();

		// Check NG memory.
		int memory = resources[INDEX_NG];
		int customer = nextNode.getCustomer();
		int bitwiseNeighbours = nextNode.getBitwiseNeighbours();
		if (BitwiseNG.contains(memory, customer))
		{
			resources[INDEX_INFEASIBLE] = 1;
		}

		// Update NG memory.
		resources[INDEX_NG] = BitwiseNG.extend(memory, customer, bitwiseNeighbours);

		// Update distance.
		resources[INDEX_DISTANCE] += arc.getData();

		// Return resource vector.
		return resources;
	}

	@Override
	public boolean isFeasible(int[] resourceVector)
	{
		return resourceVector[INDEX_INFEASIBLE] == 0 && resourceVector[INDEX_DISTANCE] <= distanceUB;
	}

	@Override
	public boolean isFeasibleAtSink(int[] resourceVector)
	{
		return resourceVector[INDEX_INFEASIBLE] == 0 && resourceVector[INDEX_DISTANCE] >= distanceLB
				&& resourceVector[INDEX_DISTANCE] <= distanceUB;

	}

	@Override
	public int[] getEmptyResourceVector()
	{
		return new int[3];
	}

	@Override
	public int[] concatenateResourceVectors(DirectedGraphArc<CustomerLoadNode, Integer> arc, int[] forwardVector,
			int[] backwardVector)
	{
		int[] resources = forwardVector.clone();

		// Update distance.
		resources[INDEX_DISTANCE] += arc.getData() + backwardVector[INDEX_DISTANCE];

		// Check if NG memories intersect.
		int forwardMemory = resources[INDEX_NG];
		int backwardMemory = backwardVector[INDEX_NG];
		if (BitwiseNG.interSects(forwardMemory, backwardMemory))
		{
			resources[INDEX_INFEASIBLE] = 1;
		}
		return resources;
	}

	@Override
	public boolean dominates(int[] first, int[] second)
	{
		// Check NG-memory.
		if (!BitwiseNG.isSubset(first[INDEX_NG], second[INDEX_NG]))
		{
			return false;
		}

		// Check distance.
		if (first[INDEX_DISTANCE] >= distanceLB)
		{
			if (distanceUB == Integer.MAX_VALUE)
			{
				return true;
			}
			return first[INDEX_DISTANCE] <= second[INDEX_DISTANCE];
		}
		else
		{
			if (distanceUB == Integer.MAX_VALUE)
			{
				return first[INDEX_DISTANCE] >= second[INDEX_DISTANCE];
			}
			return first[INDEX_DISTANCE] == second[INDEX_DISTANCE];
		}
	}
}
