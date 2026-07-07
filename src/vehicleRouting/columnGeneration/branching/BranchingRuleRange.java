package vehicleRouting.columnGeneration.branching;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import optimisation.BAP.AbstractBranchingDecision;
import optimisation.BAP.AbstractBranchingRule;
import optimisation.BAP.BAPNode;
import optimisation.BAP.BranchingCandidate;
import vehicleRouting.columnGeneration.CVRPColumn;
import vehicleRouting.columnGeneration.MaxColumn;
import vehicleRouting.columnGeneration.MinColumn;
import vehicleRouting.columnGeneration.RouteColumn;
import vehicleRouting.columnGeneration.pricing.CVRPPricingProblem;
import vehicleRouting.instance.CVRPInstance;

public class BranchingRuleRange extends AbstractBranchingRule<CVRPInstance, CVRPColumn, CVRPPricingProblem>
{
	private final double cutOff;
	private final static double EPSILON = 0.025;

	public BranchingRuleRange(double priority, double cutOff)
	{
		super(priority);

		this.cutOff = cutOff;
	}

	public AbstractBranchingDecision<CVRPInstance, CVRPColumn, CVRPPricingProblem> createBranchingDecision(
			boolean isMinimum, boolean isLowerBound, int bound)
	{
		int integerBound;
		if (isMinimum)
		{
			integerBound = isLowerBound ? bound : bound - 1;
		}
		else
		{
			integerBound = isLowerBound ? bound + 1 : bound;
		}
		return new BranchingDecisionRange(isMinimum, isLowerBound, integerBound);
	}

	@Override
	public List<BranchingCandidate<CVRPInstance, CVRPColumn, CVRPPricingProblem>> getBranchingCandidates(
			BAPNode<CVRPInstance, CVRPColumn, CVRPPricingProblem> parent)
	{
		// Retrieve value of minimum and maximum.
		double minimum = 0;
		double maximum = 0;
		for (Entry<CVRPColumn, Double> entry : parent.getSolution().getColumnMap().entrySet())
		{
			if (entry.getKey() instanceof RouteColumn)
			{
				continue;
			}
			if (entry.getKey() instanceof MaxColumn)
			{
				maximum = entry.getValue();
			}
			if (entry.getKey() instanceof MinColumn)
			{
				minimum = entry.getValue();
			}
		}

		// Check if any routes are used that are below percentage of the minimum.
		int minimumCutoff = (int) Math.floor(minimum * cutOff);
		int maximumCutoff = (int) Math.ceil(maximum * (2.0 - cutOff));
		double fractionalValueMinimum = 0;
		double fractionalValueMaximum = 0;
		for (Entry<CVRPColumn, Double> entry : parent.getSolution().getColumnMap().entrySet())
		{
			if (!(entry.getKey() instanceof RouteColumn))
			{
				continue;
			}
			RouteColumn routeColumn = (RouteColumn) entry.getKey();
			int distance = routeColumn.getRoute().getDistance();
			if (distance < minimumCutoff)
			{
				fractionalValueMinimum += entry.getValue();
			}
			if (distance > maximumCutoff)
			{
				fractionalValueMaximum += entry.getValue();
			}
		}

		// Generate branching candidates.
		List<BranchingCandidate<CVRPInstance, CVRPColumn, CVRPPricingProblem>> candidates = new ArrayList<>();
		if (fractionalValueMinimum > EPSILON)
		{
			BranchingCandidate<CVRPInstance, CVRPColumn, CVRPPricingProblem> candidate = new BranchingCandidate<>(
					fractionalValueMinimum);
			candidate.addBranchingDecision(createBranchingDecision(true, true, minimumCutoff));
			candidate.addBranchingDecision(createBranchingDecision(true, false, minimumCutoff));
			candidates.add(candidate);
		}
		if (fractionalValueMaximum > EPSILON)
		{
			BranchingCandidate<CVRPInstance, CVRPColumn, CVRPPricingProblem> candidate = new BranchingCandidate<>(
					fractionalValueMaximum);
			candidate.addBranchingDecision(createBranchingDecision(false, true, maximumCutoff));
			candidate.addBranchingDecision(createBranchingDecision(false, false, maximumCutoff));
			candidates.add(candidate);
		}
		return candidates;
	}
}
