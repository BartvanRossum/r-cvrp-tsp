package vehicleRouting.columnGeneration.pricing;

import java.util.Set;

import graph.structures.digraph.DirectedGraphArc;
import graph.structures.digraph.DirectedGraphNodeIndex;
import graph.structures.digraph.activation.ActivationFunction;
import util.Pair;
import vehicleRouting.instance.CustomerLoadNode;

public class CVRPActivationFunction implements ActivationFunction
{
	private final Set<Integer> forbiddenNodes;
	private final Set<Pair<Integer, Integer>> forbiddenArcs;

	public CVRPActivationFunction(Set<Integer> forbiddenNodes, Set<Pair<Integer, Integer>> forbiddenArcs)
	{
		this.forbiddenNodes = forbiddenNodes;
		this.forbiddenArcs = forbiddenArcs;
	}

	@Override
	public boolean isActiveNode(DirectedGraphNodeIndex node)
	{
		CustomerLoadNode customerNode = (CustomerLoadNode) node;
		if (forbiddenNodes.contains(customerNode.getCustomer()))
		{
			return false;
		}
		return true;
	}

	@Override
	public boolean isActiveArc(DirectedGraphArc<?, ?> arc)
	{
		DirectedGraphArc<CustomerLoadNode, Integer> customerArc = (DirectedGraphArc<CustomerLoadNode, Integer>) arc;
		Pair<Integer, Integer> pair = new Pair<>(customerArc.getFrom().getCustomer(),
				customerArc.getTo().getCustomer());
		if (forbiddenArcs.contains(pair))
		{
			return false;
		}
		return true;
	}
}
