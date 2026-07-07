package vehicleRouting.columnGeneration.pricing;

import graph.algorithms.shortestPath.arcWeightFunctions.AbstractArcWeightFunction;
import graph.structures.digraph.DirectedGraphArc;
import vehicleRouting.instance.CustomerLoadNode;

public class CVRPArcCostFunction extends AbstractArcWeightFunction<CustomerLoadNode, Integer>
{
	private final boolean reverse;
	
	public CVRPArcCostFunction(boolean reverse)
	{
		this.reverse = reverse;
		
	}
	@Override
	public double getArcWeight(DirectedGraphArc<CustomerLoadNode, Integer> arc)
	{
		if (reverse)
		{
			return -1 * arc.getData();
		}
		return arc.getData();
	}
}
