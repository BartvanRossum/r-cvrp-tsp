package vehicleRouting.columnGeneration.pricing;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import graph.algorithms.shortestPath.ShortestPath;
import graph.structures.digraph.DirectedGraph;
import vehicleRouting.instance.CVRPInstance;
import vehicleRouting.instance.CustomerLoadNode;

public class DistanceCompletionBoundTest
{
	private final Map<Integer, Map<CustomerLoadNode, Integer>> minimumDistance;
	private final Map<Integer, Map<CustomerLoadNode, Integer>> maximumDistance;

	public DistanceCompletionBoundTest(CVRPInstance instance)
	{
		minimumDistance = new LinkedHashMap<>();
		maximumDistance = new LinkedHashMap<>();
		for (int lastCustomer = 1; lastCustomer <= instance.getNumCustomers(); lastCustomer++)
		{
			minimumDistance.put(lastCustomer, new LinkedHashMap<>());
			maximumDistance.put(lastCustomer, new LinkedHashMap<>());

			DirectedGraph<CustomerLoadNode, Integer> graph = instance.getGraph(lastCustomer);
			CustomerLoadNode source = graph.getNodes().get(0);
			CustomerLoadNode sink = graph.getNodes().get(graph.getNumberOfNodes() - 1);

			CVRPArcCostFunction arcCostFunction = new CVRPArcCostFunction(false);
			CVRPArcCostFunction negativeArcCostFunction = new CVRPArcCostFunction(true);

			// Compute minimum distance bounds.
			ShortestPath<CustomerLoadNode, Integer> minimumDistanceForward = new ShortestPath<>(graph, sink, source,
					false, arcCostFunction);
			ShortestPath<CustomerLoadNode, Integer> minimumDistanceBackward = new ShortestPath<>(graph, source, sink,
					true, arcCostFunction);
			for (CustomerLoadNode node : graph.getNodes())
			{
				if (minimumDistanceForward.containsPath(node) && minimumDistanceBackward.containsPath(node))
				{
					int minDist = (int) Math
							.rint(minimumDistanceForward.getDistance(node) + minimumDistanceBackward.getDistance(node));
					minimumDistance.get(lastCustomer).put(node, minDist);
				}
			}

			// Compute maximum distance bounds.
			ShortestPath<CustomerLoadNode, Integer> maximumDistanceForward = new ShortestPath<>(graph, sink, source,
					false, negativeArcCostFunction);
			ShortestPath<CustomerLoadNode, Integer> maximumDistanceBackward = new ShortestPath<>(graph, source, sink,
					true, negativeArcCostFunction);
			for (CustomerLoadNode node : graph.getNodes())
			{
				if (maximumDistanceForward.containsPath(node) && maximumDistanceBackward.containsPath(node))
				{
					int maxDist = (int) Math.abs(Math.rint(
							maximumDistanceForward.getDistance(node) + maximumDistanceBackward.getDistance(node)));
					maximumDistance.get(lastCustomer).put(node, maxDist);
				}
			}
		}
	}

	public Set<CustomerLoadNode> getPrunedNodes(int lastCustomer, int distanceLB, int distanceUB)
	{
		Set<CustomerLoadNode> prunedNodes = new LinkedHashSet<>();
		for (CustomerLoadNode node : minimumDistance.get(lastCustomer).keySet())
		{
			if (minimumDistance.get(lastCustomer).get(node) > distanceUB)
			{
				prunedNodes.add(node);
			}
			if (maximumDistance.get(lastCustomer).get(node) < distanceLB)
			{
				prunedNodes.add(node);
			}
		}
		return prunedNodes;
	}
}
