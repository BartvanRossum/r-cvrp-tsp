package graph.algorithms.spprc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import graph.algorithms.shortestPath.ShortestPath;
import graph.algorithms.shortestPath.arcWeightFunctions.AbstractArcWeightFunction;
import graph.algorithms.shortestPath.arcWeightFunctions.DefaultArcWeightFunction;
import graph.structures.Path;
import graph.structures.digraph.DirectedGraph;
import graph.structures.digraph.DirectedGraphArc;
import graph.structures.digraph.DirectedGraphNodeIndex;
import graph.structures.digraph.activation.Activation;
import optimisation.columnGeneration.PricingSettings;

public class BidirectionalLabelling<V extends DirectedGraphNodeIndex, A>
{
	private AbstractArcWeightFunction<V, A> arcWeightFunction = new DefaultArcWeightFunction<>();
	private REF<V, A> resourceExtensionFunction;
	private final List<AbstractResourceCompletionTest<V, A>> completionTests;

	private int forwardIndex;
	private int backwardIndex;
	private int numForwardLabels;
	private int numBackwardLabels;

	Map<V, LinkedList<Label<V, A>>> forwardBucketMap;
	Map<V, LinkedList<Label<V, A>>> backwardBucketMap;
	private List<Path<V, A>> paths;
	private Set<DirectedGraphArc<V, A>> arcs;

	public BidirectionalLabelling()
	{
		completionTests = new ArrayList<>();
	}

	public void setArcWeightFunction(AbstractArcWeightFunction<V, A> arcWeightFunction)
	{
		this.arcWeightFunction = arcWeightFunction;
	}

	public void addCompletionBound(AbstractResourceCompletionTest<V, A> completionTest)
	{
		completionTests.add(completionTest);
	}

	public List<Path<V, A>> getPaths()
	{
		return paths;
	}

	public boolean hasPaths()
	{
		return paths.size() > 0;
	}

	public Path<V, A> getMinimumWeightPath()
	{
		paths.sort((a, b) -> Double.compare(a.getWeight(), b.getWeight()));
		return paths.get(0);
	}

	public Set<DirectedGraphArc<V, A>> getArcs()
	{
		return arcs;
	}

	public double getMinimumPathCostThroughNode(V node, DirectedGraph<V, A> graph)
	{
		// Store minimum
		double minCost = Double.MAX_VALUE;

		LinkedList<Label<V, A>> forwardLabels = forwardBucketMap.get(node);
		if (forwardLabels == null || forwardLabels.isEmpty())
		{
			return minCost;
		}

		for (DirectedGraphArc<V, A> arc : graph.getOutArcs(node))
		{
			LinkedList<Label<V, A>> backwardLabels = backwardBucketMap.get(arc.getTo());
			if (backwardLabels == null || backwardLabels.isEmpty())
			{
				continue;
			}

			for (Label<V, A> forwardLabel : forwardLabels)
			{
				double forwardCost = forwardLabel.getCost();
				if (forwardCost + backwardLabels.getFirst().getCost() + arcWeightFunction.getArcWeight(arc) >= minCost)
				{
					break;
				}

				for (Label<V, A> backwardLabel : backwardLabels)
				{
					double cost = forwardCost + backwardLabel.getCost() + arcWeightFunction.getArcWeight(arc);
					if (cost >= minCost)
					{
						break;
					}

					int[] concatenated = resourceExtensionFunction.concatenateResourceVectors(arc,
							forwardLabel.getResourceVector(), backwardLabel.getResourceVector());
					if (resourceExtensionFunction.isFeasibleAtSink(concatenated))
					{
						minCost = cost;
					}
				}
			}
		}
		return minCost;
	}

	public void applyLabelling(DirectedGraph<V, A> graph, V sourceNode, V sinkNode, REF<V, A> extensionFunction,
			double reducedCostThreshold, boolean enumeratePaths, boolean enumerateArcs)
	{
		// Initialise REF.
		this.resourceExtensionFunction = extensionFunction;

		// Initialise parameters.
		forwardIndex = sourceNode.getNodeIndex();
		backwardIndex = sinkNode.getNodeIndex();
		numForwardLabels = 0;
		numBackwardLabels = 0;

		// Initialise reduced cost paths and arcs.
		paths = new ArrayList<>();
		arcs = new LinkedHashSet<>();

		// Solve a shortest path from sink to all other nodes, that can be used as
		// completion bound.
		ShortestPath<V, A> forwardShortestPath = new ShortestPath<>(graph, sinkNode, sourceNode, false,
				arcWeightFunction);
		if (forwardShortestPath.getDistance(sourceNode) > reducedCostThreshold)
		{
			return;
		}
		ShortestPath<V, A> backwardShortestPath = new ShortestPath<>(graph, sourceNode, sinkNode, true,
				arcWeightFunction);

		// Initialise buckets.
		forwardBucketMap = new LinkedHashMap<>();
		backwardBucketMap = new LinkedHashMap<>();
		for (V node : graph.getNodes())
		{
			forwardBucketMap.put(node, new LinkedList<>());
			backwardBucketMap.put(node, new LinkedList<>());
		}

		// Add dummy labels for the source and sink.
		Label<V, A> sourceLabel = new Label<>(null, null, extensionFunction.getEmptyResourceVector(), 0,
				forwardShortestPath.getDistance(sourceNode));
		Label<V, A> sinkLabel = new Label<>(null, null, extensionFunction.getEmptyResourceVector(), 0,
				backwardShortestPath.getDistance(sinkNode));
		addLabel(sourceLabel, forwardBucketMap.get(sourceNode), enumeratePaths);
		addLabel(sinkLabel, backwardBucketMap.get(sinkNode), enumeratePaths);

		// Perform labelling.
		boolean go = true;
		boolean forward = true;
		while (go)
		{
			Map<V, LinkedList<Label<V, A>>> bucketMap = forward ? forwardBucketMap : backwardBucketMap;
			V nextNode = forward ? graph.getNodes().get(forwardIndex) : graph.getNodes().get(backwardIndex);
			ShortestPath<V, A> shortestPath = forward ? forwardShortestPath : backwardShortestPath;
			int increment = 0;

			// Only use active nodes.
			if (!Activation.getActivation().getActivationFunction().isActiveNode(nextNode))
			{
				// Update size of bucket maps and indices.
				forwardIndex += forward ? 1 : 0;
				backwardIndex -= forward ? 0 : 1;
				continue;
			}

			// Fill the bucket of the next node.
			for (DirectedGraphArc<V, A> arc : (forward ? graph.getInArcs(nextNode) : graph.getOutArcs(nextNode)))
			{
				// Only use active arcs.
				if (!Activation.getActivation().getActivationFunction().isActiveArc(arc))
				{
					continue;
				}

				// Expand all labels in the current bucket.
				V previousNode = forward ? arc.getFrom() : arc.getTo();
				for (Label<V, A> label : bucketMap.get(previousNode))
				{
					Label<V, A> expandedLabel = expandLabel(label, arc, forward, reducedCostThreshold, shortestPath,
							nextNode);
					if (expandedLabel == null)
					{
						continue;
					}
					increment += addLabel(expandedLabel, bucketMap.get(nextNode), enumeratePaths);
				}
			}
			// Update size of bucket maps and indices.
			numForwardLabels += forward ? increment : 0;
			numBackwardLabels += forward ? 0 : increment;
			forwardIndex += forward ? 1 : 0;
			backwardIndex -= forward ? 0 : 1;

			// Determine whether to proceed or not.
			if (enumerateArcs)
			{
				go = forwardIndex < sinkNode.getNodeIndex() || backwardIndex > sourceNode.getNodeIndex();
				forward = forwardIndex < sinkNode.getNodeIndex();
			}
			else
			{
				go = forwardIndex <= backwardIndex;
				forward = numForwardLabels < numBackwardLabels;
			}
		}

		// Merge labels by iterating over all arcs that cross the cutoff. We can now
		// exclude source and sink nodes.
		for (int i = 0; i <= Math.min(sinkNode.getNodeIndex(), forwardIndex); i++)
		{
			// We can skip nodes with empty buckets.
			V forwardNode = graph.getNodes().get(i);
			if (forwardBucketMap.get(forwardNode).size() == 0)
			{
				continue;
			}

			// Extend across all arcs.
			arcLoop:
			for (DirectedGraphArc<V, A> arc : graph.getOutArcs(forwardNode))
			{
				V backwardNode = arc.getTo();
				if (backwardBucketMap.get(backwardNode).size() == 0)
				{
					continue;
				}

				// Only use active arcs.
				if (!Activation.getActivation().getActivationFunction().isActiveArc(arc))
				{
					continue;
				}

				for (Label<V, A> forwardLabel : forwardBucketMap.get(forwardNode))
				{
					// TODO: Add a simple extension check here to speed things up! (Copy the one
					// from the regular extension with completion bounds.)
					for (Label<V, A> backwardLabel : backwardBucketMap.get(backwardNode))
					{
						double reducedCost = forwardLabel.getCost() + backwardLabel.getCost()
								+ arcWeightFunction.getArcWeight(arc);
						if (reducedCost > reducedCostThreshold)
						{
							break;
						}

						// Concatenate resource vectors and check feasibility.
						int[] concatenatedVector = extensionFunction.concatenateResourceVectors(arc,
								forwardLabel.getResourceVector(), backwardLabel.getResourceVector());
						if (!extensionFunction.isFeasibleAtSink(concatenatedVector))
						{
							continue;
						}

						// Generate a concatenated column.
						if (enumerateArcs)
						{
							arcs.add(arc);
						}
						else
						{
							List<DirectedGraphArc<V, A>> arcs = new ArrayList<>();
							arcs.addAll(backtrackPath(forwardLabel, true));
							arcs.add(arc);
							arcs.addAll(backtrackPath(backwardLabel, false));
							Path<V, A> path = new Path<>(arcs, reducedCost);
							paths.add(path);
						}

						// Continue the loop if we are not enumerating all labels.
						if (!enumeratePaths)
						{
							continue arcLoop;
						}
					}
				}
			}
		}
	}

	public void enumerateNonDominatedPaths(DirectedGraph<V, A> graph, V sourceNode, V sinkNode,
			REF<V, A> extensionFunction, double reducedCostThreshold)
	{
		// Initialise REF.
		this.resourceExtensionFunction = extensionFunction;

		// Initialise parameters.
		forwardIndex = sourceNode.getNodeIndex();
		backwardIndex = sinkNode.getNodeIndex();
		numForwardLabels = 0;
		numBackwardLabels = 0;

		// Initialise reduced cost paths and arcs.
		paths = new ArrayList<>();
		arcs = new LinkedHashSet<>();

		// Solve a shortest path from sink to all other nodes, that can be used as
		// completion bound.
		ShortestPath<V, A> forwardShortestPath = new ShortestPath<>(graph, sinkNode, sourceNode, false,
				arcWeightFunction);
		if (forwardShortestPath.getDistance(sourceNode) > reducedCostThreshold)
		{
			return;
		}
		ShortestPath<V, A> backwardShortestPath = new ShortestPath<>(graph, sourceNode, sinkNode, true,
				arcWeightFunction);

		// Initialise buckets.
		forwardBucketMap = new LinkedHashMap<>();
		backwardBucketMap = new LinkedHashMap<>();
		for (V node : graph.getNodes())
		{
			forwardBucketMap.put(node, new LinkedList<>());
			backwardBucketMap.put(node, new LinkedList<>());
		}

		// Add dummy labels for the source and sink.
		Label<V, A> sourceLabel = new Label<>(null, null, extensionFunction.getEmptyResourceVector(), 0,
				forwardShortestPath.getDistance(sourceNode));
		Label<V, A> sinkLabel = new Label<>(null, null, extensionFunction.getEmptyResourceVector(), 0,
				backwardShortestPath.getDistance(sinkNode));
		addLabel(sourceLabel, forwardBucketMap.get(sourceNode), false);
		addLabel(sinkLabel, backwardBucketMap.get(sinkNode), false);

		// Perform labelling.
		boolean go = true;
		boolean forward = true;
		while (go)
		{
			Map<V, LinkedList<Label<V, A>>> bucketMap = forward ? forwardBucketMap : backwardBucketMap;
			V nextNode = forward ? graph.getNodes().get(forwardIndex) : graph.getNodes().get(backwardIndex);
			ShortestPath<V, A> shortestPath = forward ? forwardShortestPath : backwardShortestPath;
			int increment = 0;

			// Only use active nodes.
			if (!Activation.getActivation().getActivationFunction().isActiveNode(nextNode))
			{
				// Update size of bucket maps and indices.
				forwardIndex += forward ? 1 : 0;
				backwardIndex -= forward ? 0 : 1;
				continue;
			}

			// Fill the bucket of the next node.
			for (DirectedGraphArc<V, A> arc : (forward ? graph.getInArcs(nextNode) : graph.getOutArcs(nextNode)))
			{
				// Only use active arcs.
				if (!Activation.getActivation().getActivationFunction().isActiveArc(arc))
				{
					continue;
				}

				// Expand all labels in the current bucket.
				V previousNode = forward ? arc.getFrom() : arc.getTo();
				for (Label<V, A> label : bucketMap.get(previousNode))
				{
					Label<V, A> expandedLabel = expandLabel(label, arc, forward, reducedCostThreshold, shortestPath,
							nextNode);
					if (expandedLabel == null)
					{
						continue;
					}
					increment += addLabel(expandedLabel, bucketMap.get(nextNode), false);
				}
			}
			// Update size of bucket maps and indices.
			numForwardLabels += forward ? increment : 0;
			numBackwardLabels += forward ? 0 : increment;
			forwardIndex += forward ? 1 : 0;
			backwardIndex -= forward ? 0 : 1;

			// Determine whether to proceed or not.
			go = forwardIndex <= backwardIndex;
			forward = numForwardLabels < numBackwardLabels;
		}

		// Merge labels by iterating over all arcs that cross the cutoff. We can now
		// exclude source and sink nodes.
		for (int i = 0; i <= Math.min(sinkNode.getNodeIndex(), forwardIndex); i++)
		{
			// We can skip nodes with empty buckets.
			V forwardNode = graph.getNodes().get(i);
			if (forwardBucketMap.get(forwardNode).size() == 0)
			{
				continue;
			}

			// Extend across all arcs.
			for (DirectedGraphArc<V, A> arc : graph.getOutArcs(forwardNode))
			{
				V backwardNode = arc.getTo();
				if (backwardBucketMap.get(backwardNode).size() == 0)
				{
					continue;
				}

				// Only use active arcs.
				if (!Activation.getActivation().getActivationFunction().isActiveArc(arc))
				{
					continue;
				}

				for (Label<V, A> forwardLabel : forwardBucketMap.get(forwardNode))
				{
					// TODO: Add a simple extension check here to speed things up! (Copy the one
					// from the regular extension with completion bounds.)
					for (Label<V, A> backwardLabel : backwardBucketMap.get(backwardNode))
					{
						double reducedCost = forwardLabel.getCost() + backwardLabel.getCost()
								+ arcWeightFunction.getArcWeight(arc);
						if (reducedCost > reducedCostThreshold)
						{
							break;
						}

						// Concatenate resource vectors and check feasibility.
						int[] concatenatedVector = extensionFunction.concatenateResourceVectors(arc,
								forwardLabel.getResourceVector(), backwardLabel.getResourceVector());
						if (!extensionFunction.isFeasibleAtSink(concatenatedVector))
						{
							continue;
						}

						// Generate a concatenated column.
						List<DirectedGraphArc<V, A>> arcs = new ArrayList<>();
						arcs.addAll(backtrackPath(forwardLabel, true));
						arcs.add(arc);
						arcs.addAll(backtrackPath(backwardLabel, false));
						Path<V, A> path = new Path<>(arcs, reducedCost);
						paths.add(path);
					}
				}
			}
		}
	}

	public void applyLabellingFixedArcs(DirectedGraph<V, A> graph, V sourceNode, V sinkNode,
			REF<V, A> extensionFunction, double reducedCostThreshold, boolean enumeratePaths, boolean enumerateArcs,
			Set<DirectedGraphArc<V, A>> fixedArcs, int maxNumberPaths)
	{
		// Initialise REF.
		this.resourceExtensionFunction = extensionFunction;

		// Initialise parameters.
		forwardIndex = sourceNode.getNodeIndex();
		backwardIndex = sinkNode.getNodeIndex();
		numForwardLabels = 0;
		numBackwardLabels = 0;

		// Initialise reduced cost paths and arcs.
		paths = new ArrayList<>();
		arcs = new LinkedHashSet<>();

		// Solve a shortest path from sink to all other nodes, that can be used as
		// completion bound.
		ShortestPath<V, A> forwardShortestPath = new ShortestPath<>(graph, sinkNode, sourceNode, false,
				arcWeightFunction);
		if (forwardShortestPath.getDistance(sourceNode) > reducedCostThreshold)
		{
			return;
		}
		ShortestPath<V, A> backwardShortestPath = new ShortestPath<>(graph, sourceNode, sinkNode, true,
				arcWeightFunction);

		// Initialise buckets.
		forwardBucketMap = new LinkedHashMap<>();
		backwardBucketMap = new LinkedHashMap<>();
		for (V node : graph.getNodes())
		{
			forwardBucketMap.put(node, new LinkedList<>());
			backwardBucketMap.put(node, new LinkedList<>());
		}

		// Add dummy labels for the source and sink.
		Label<V, A> sourceLabel = new Label<>(null, null, extensionFunction.getEmptyResourceVector(), 0,
				forwardShortestPath.getDistance(sourceNode));
		Label<V, A> sinkLabel = new Label<>(null, null, extensionFunction.getEmptyResourceVector(), 0,
				backwardShortestPath.getDistance(sinkNode));
		addLabel(sourceLabel, forwardBucketMap.get(sourceNode), enumeratePaths);
		addLabel(sinkLabel, backwardBucketMap.get(sinkNode), enumeratePaths);

		// Perform labelling.
		boolean go = true;
		boolean forward = true;
		while (go)
		{
			Map<V, LinkedList<Label<V, A>>> bucketMap = forward ? forwardBucketMap : backwardBucketMap;
			V nextNode = forward ? graph.getNodes().get(forwardIndex) : graph.getNodes().get(backwardIndex);
			ShortestPath<V, A> shortestPath = forward ? forwardShortestPath : backwardShortestPath;
			int increment = 0;

			// Only use active nodes.
			if (!Activation.getActivation().getActivationFunction().isActiveNode(nextNode))
			{
				// Update size of bucket maps and indices.
				forwardIndex += forward ? 1 : 0;
				backwardIndex -= forward ? 0 : 1;
				continue;
			}

			// Fill the bucket of the next node.
			for (DirectedGraphArc<V, A> arc : (forward ? graph.getInArcs(nextNode) : graph.getOutArcs(nextNode)))
			{
				// Only use active arcs.
				if (!Activation.getActivation().getActivationFunction().isActiveArc(arc))
				{
					continue;
				}
				if (!fixedArcs.contains(arc))
				{
					continue;
				}

				// Expand all labels in the current bucket.
				V previousNode = forward ? arc.getFrom() : arc.getTo();
				for (Label<V, A> label : bucketMap.get(previousNode))
				{
					Label<V, A> expandedLabel = expandLabel(label, arc, forward, reducedCostThreshold, shortestPath,
							nextNode);
					if (expandedLabel == null)
					{
						continue;
					}
					increment += addLabel(expandedLabel, bucketMap.get(nextNode), enumeratePaths);
				}
			}
			// Update size of bucket maps and indices.
			numForwardLabels += forward ? increment : 0;
			numBackwardLabels += forward ? 0 : increment;
			forwardIndex += forward ? 1 : 0;
			backwardIndex -= forward ? 0 : 1;

			// Determine whether to proceed or not.
			if (enumerateArcs)
			{
				go = forwardIndex < sinkNode.getNodeIndex() || backwardIndex > sourceNode.getNodeIndex();
				forward = forwardIndex < sinkNode.getNodeIndex();
			}
			else
			{
				go = forwardIndex <= backwardIndex;
				forward = numForwardLabels < numBackwardLabels;
			}
		}

		// Merge labels by iterating over all arcs that cross the cutoff. We can now
		// exclude source and sink nodes.
		for (int i = 0; i <= Math.min(sinkNode.getNodeIndex(), forwardIndex); i++)
		{
			// We can skip nodes with empty buckets.
			V forwardNode = graph.getNodes().get(i);
			if (forwardBucketMap.get(forwardNode).size() == 0)
			{
				continue;
			}

			// Extend across all arcs.
			arcLoop:
			for (DirectedGraphArc<V, A> arc : graph.getOutArcs(forwardNode))
			{
				V backwardNode = arc.getTo();
				if (backwardBucketMap.get(backwardNode).size() == 0)
				{
					continue;
				}

				// Only use active arcs.
				if (!Activation.getActivation().getActivationFunction().isActiveArc(arc))
				{
					continue;
				}
				if (!fixedArcs.contains(arc))
				{
					continue;
				}

				for (Label<V, A> forwardLabel : forwardBucketMap.get(forwardNode))
				{
					// TODO: Add a simple extension check here to speed things up! (Copy the one
					// from the regular extension with completion bounds.)
					for (Label<V, A> backwardLabel : backwardBucketMap.get(backwardNode))
					{
						double reducedCost = forwardLabel.getCost() + backwardLabel.getCost()
								+ arcWeightFunction.getArcWeight(arc);
						if (reducedCost > reducedCostThreshold)
						{
							break;
						}

						// Concatenate resource vectors and check feasibility.
						int[] concatenatedVector = extensionFunction.concatenateResourceVectors(arc,
								forwardLabel.getResourceVector(), backwardLabel.getResourceVector());
						if (!extensionFunction.isFeasibleAtSink(concatenatedVector))
						{
							continue;
						}

						// Generate a concatenated column.
						if (enumerateArcs)
						{
							arcs.add(arc);
						}
						else
						{
							List<DirectedGraphArc<V, A>> arcs = new ArrayList<>();
							arcs.addAll(backtrackPath(forwardLabel, true));
							arcs.add(arc);
							arcs.addAll(backtrackPath(backwardLabel, false));
							Path<V, A> path = new Path<>(arcs, reducedCost);
							paths.add(path);

							// Terminate if we have found too many paths already.
							if (paths.size() > maxNumberPaths)
							{
								return;
							}
						}

						// Continue the loop if we are not enumerating all labels.
						if (!enumeratePaths)
						{
							continue arcLoop;
						}
					}
				}
			}
		}
	}

	private List<DirectedGraphArc<V, A>> backtrackPath(Label<V, A> label, boolean forward)
	{
		List<DirectedGraphArc<V, A>> path = new ArrayList<>();
		Label<V, A> currentLabel = label;
		while (true)
		{
			if (currentLabel.getArc() == null)
			{
				break;
			}
			if (forward)
			{
				path.add(0, currentLabel.getArc());
			}
			else
			{
				path.add(currentLabel.getArc());
			}
			currentLabel = currentLabel.getPreviousLabel();
		}
		return path;
	}

	private int addLabel(Label<V, A> label, LinkedList<Label<V, A>> bucket, boolean enumeratePaths)
	{
		// Dominance checks.
		double cost = label.getCost();
		boolean inserted = false;
		ListIterator<Label<V, A>> iterator = bucket.listIterator();
		int remove = 0;

		while (iterator.hasNext())
		{
			// Retrieve label.
			Label<V, A> otherLabel = iterator.next();
			double otherCost = otherLabel.getCost();

			// First check if a label is dominated by another label with lower or equal
			// reduced cost.
			if (cost >= otherCost)
			{
				// Perform dominance check, except for when we are in enumeration mode.
				if (!enumeratePaths && dominates(otherLabel, label))
				{
					return 0;
				}
			}

			// The label is not yet inserted and not dominated, so we add it to the bucket.
			if (cost < otherCost && !inserted)
			{
				inserted = true;
				iterator.previous();
				iterator.add(label);
				otherLabel = iterator.next();
			}

			// We check if the new label dominates any labels in the current bucket, except
			// for when we are in enumeration mode.
			if (cost <= otherCost)
			{
				// Perform dominance check.
				if (!enumeratePaths && dominates(label, otherLabel))
				{
					remove++;
					iterator.remove();
				}
			}
		}

		// Add to the bucket if not yet added.
		if (!inserted)
		{
			bucket.add(label);
		}
		return 1 - remove;
	}

	private Label<V, A> expandLabel(Label<V, A> label, DirectedGraphArc<V, A> arc, boolean forward,
			double reducedCostThreshold, ShortestPath<V, A> shortestPath, V nextNode)
	{
		// Check completion bound before any allocation.
		double expandedLabelCost = label.getCost() + arcWeightFunction.getArcWeight(arc);
		double completionBound = expandedLabelCost + shortestPath.getDistance(nextNode);
		if (completionBound > reducedCostThreshold)
		{
			return null;
		}

		// Extend resource vector and check feasibility.
		int[] extendedResources = resourceExtensionFunction.extendResourceVector(label, arc, forward);
		if (!resourceExtensionFunction.isFeasible(extendedResources))
		{
			return null;
		}

		// Create label and check resource completion tests.
		Label<V, A> expandedLabel = new Label<>(label, arc, extendedResources, expandedLabelCost, completionBound);
		for (AbstractResourceCompletionTest<V, A> completionTest : completionTests)
		{
			if (!completionTest.isFeasible(expandedLabel, forward))
			{
				return null;
			}
		}
		return expandedLabel;
	}

	private boolean dominates(Label<V, A> label, Label<V, A> otherLabel)
	{
		if (PricingSettings.EXACT_PRICING)
		{
			return resourceExtensionFunction.dominates(label.getResourceVector(), otherLabel.getResourceVector());
		}
		return resourceExtensionFunction.dominatesHeuristically(label.getResourceVector(),
				otherLabel.getResourceVector());
	}
}