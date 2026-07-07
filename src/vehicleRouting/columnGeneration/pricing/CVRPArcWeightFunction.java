package vehicleRouting.columnGeneration.pricing;

import graph.algorithms.shortestPath.arcWeightFunctions.AbstractArcWeightFunction;
import graph.structures.digraph.DirectedGraphArc;
import vehicleRouting.instance.CustomerLoadNode;

public class CVRPArcWeightFunction extends AbstractArcWeightFunction<CustomerLoadNode, Integer>
{
	private final double[][] transitionDuals;
	private final double distanceDual;

	public CVRPArcWeightFunction(double[][] transitionDuals, double distanceDual)
	{
		this.transitionDuals = transitionDuals;
		this.distanceDual = distanceDual;
	}

	@Override
	public double getArcWeight(DirectedGraphArc<CustomerLoadNode, Integer> arc)
	{
		double weight = -1 * transitionDuals[arc.getFrom().getCustomer()][arc.getTo().getCustomer()]
				- arc.getData() * distanceDual;
		return weight;
	}
}
