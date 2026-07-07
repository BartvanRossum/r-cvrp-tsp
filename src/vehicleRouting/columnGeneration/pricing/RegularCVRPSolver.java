package vehicleRouting.columnGeneration.pricing;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import graph.algorithms.spprc.BidirectionalLabelling;
import graph.structures.Path;
import graph.structures.digraph.DirectedGraph;
import graph.structures.digraph.DirectedGraphArc;
import graph.structures.digraph.activation.Activation;
import optimisation.columnGeneration.AbstractMasterProblem;
import optimisation.columnGeneration.pricing.AbstractPricingProblemSolver;
import util.Pair;
import vehicleRouting.columnGeneration.CVRPColumn;
import vehicleRouting.columnGeneration.Route;
import vehicleRouting.columnGeneration.RouteColumn;
import vehicleRouting.columnGeneration.RouteREF;
import vehicleRouting.instance.CVRPInstance;
import vehicleRouting.instance.CustomerLoadNode;

public class RegularCVRPSolver extends AbstractPricingProblemSolver<CVRPInstance, CVRPColumn, CVRPPricingProblem>
{
	private DistanceCompletionBoundTest completionBound = null;

	public void setDistanceCompetionBound(DistanceCompletionBoundTest completionBound)
	{
		this.completionBound = completionBound;
	}

	@Override
	public List<Pair<CVRPColumn, Double>> generateColumns(
			AbstractMasterProblem<CVRPInstance, CVRPColumn, CVRPPricingProblem> masterProblem,
			CVRPPricingProblem pricingProblem, double reducedCostThreshold, boolean enumerateColumns)
	{
		List<Pair<CVRPColumn, Double>> columns = new ArrayList<>();
		DirectedGraph<CustomerLoadNode, Integer> graph = pricingProblem.getGraph();

		// Retrieve source and sink.
		CustomerLoadNode source = graph.getNodes().get(0);
		CustomerLoadNode sink = graph.getNodes().get(graph.getNumberOfNodes() - 1);

		// Activation function.
		Activation.getActivation().setActivationFunction(
				new CVRPActivationFunction(pricingProblem.getForbiddenNodes(), pricingProblem.getForbiddenArcs()));
		if (completionBound != null)
		{
			// TODO: Apply completion bound if available.
			Set<CustomerLoadNode> forbiddenExtendedNodes = completionBound.getPrunedNodes(pricingProblem.getIndex(),
					pricingProblem.getDistanceLB(), pricingProblem.getDistanceUB());
			ExtendedCVRPActivationFunction activationFunction = new ExtendedCVRPActivationFunction(
					pricingProblem.getForbiddenNodes(), forbiddenExtendedNodes, pricingProblem.getForbiddenArcs());
			Activation.getActivation().setActivationFunction(activationFunction);
		}
		BidirectionalLabelling<CustomerLoadNode, Integer> labelling = new BidirectionalLabelling<>();

		// Set weight function.
		CVRPArcWeightFunction arcWeightFunction = new CVRPArcWeightFunction(pricingProblem.getTransitionDuals(),
				pricingProblem.getDistanceDual());
		labelling.setArcWeightFunction(arcWeightFunction);

		// Set resource extension function.
		RouteREF REF = new RouteREF(pricingProblem.getDistanceLB(), pricingProblem.getDistanceUB());

		// Apply labelling.
		labelling.applyLabelling(graph, source, sink, REF, reducedCostThreshold, false, false);
		if (labelling.hasPaths())
		{
			Path<CustomerLoadNode, Integer> path = labelling.getMinimumWeightPath();
			double reducedCost = 0;
			int distance = 0;
			int load = 0;
			List<Integer> customers = new ArrayList<>();
			List<Pair<Integer, Integer>> arcs = new ArrayList<>();
			for (DirectedGraphArc<CustomerLoadNode, Integer> arc : path.getArcs())
			{
				distance += arc.getData();
				if (arc.getTo().getCustomer() > 0)
				{
					load += arc.getTo().getDemand();
					customers.add(arc.getTo().getCustomer());
				}
				arcs.add(new Pair<>(arc.getFrom().getCustomer(), arc.getTo().getCustomer()));
				reducedCost += arcWeightFunction.getArcWeight(arc);
			}
			Route route = new Route(0, distance, load, customers, arcs);

			// Add column.
			columns.add(new Pair<>(new RouteColumn(route), reducedCost));
		}
		return columns;
	}
}
