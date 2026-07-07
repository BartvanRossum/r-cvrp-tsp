package vehicleRouting.columnGeneration.branching;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import optimisation.BAP.AbstractBranchingRule;
import optimisation.BAP.BAPNode;
import optimisation.BAP.BranchingCandidate;
import util.Configuration;
import util.Pair;
import vehicleRouting.columnGeneration.CVRPColumn;
import vehicleRouting.columnGeneration.Route;
import vehicleRouting.columnGeneration.RouteColumn;
import vehicleRouting.columnGeneration.pricing.CVRPPricingProblem;
import vehicleRouting.instance.CVRPInstance;

public class BranchingRuleArc extends AbstractBranchingRule<CVRPInstance, CVRPColumn, CVRPPricingProblem>
{
	public BranchingRuleArc(double priority)
	{
		super(priority);
	}

	@Override
	public List<BranchingCandidate<CVRPInstance, CVRPColumn, CVRPPricingProblem>> getBranchingCandidates(
			BAPNode<CVRPInstance, CVRPColumn, CVRPPricingProblem> parent)
	{
		// Compute fractional value for each arc in each period.
		Map<Pair<Integer, Integer>, Double> arcMap = new LinkedHashMap<>();
		for (Entry<CVRPColumn, Double> entry : parent.getSolution().getColumnMap().entrySet())
		{
			double value = entry.getValue();
			if (entry.getKey() instanceof RouteColumn)
			{
				Route route = ((RouteColumn) entry.getKey()).getRoute();
				for (Pair<Integer, Integer> arc : route.getArcs())
				{
					if (!arcMap.containsKey(arc))
					{
						arcMap.put(arc, 0.0);
					}
					arcMap.put(arc, arcMap.get(arc) + value);
				}
			}
		}

		// Construct branching candidates.
		double PRECISION = Configuration.getConfiguration().getDoubleProperty("PRECISION");
		List<BranchingCandidate<CVRPInstance, CVRPColumn, CVRPPricingProblem>> candidates = new ArrayList<>();
		for (Entry<Pair<Integer, Integer>, Double> entry : arcMap.entrySet())
		{
			double value = entry.getValue();
			Pair<Integer, Integer> arc = entry.getKey();
			double fractionalValue = Math.abs(Math.rint(value) - value);
			if (fractionalValue > PRECISION)
			{
				BranchingCandidate<CVRPInstance, CVRPColumn, CVRPPricingProblem> candidate = new BranchingCandidate<>(
						fractionalValue);
				candidate.addBranchingDecision(new BranchingDecisionArc(false, arc.getKey(), arc.getValue()));
				candidate.addBranchingDecision(new BranchingDecisionArc(true, arc.getKey(), arc.getValue()));
				candidates.add(candidate);
			}
		}
		return candidates;
	}
}
