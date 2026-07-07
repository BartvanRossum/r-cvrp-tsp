package vehicleRouting.columnGeneration.branching;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import graph.structures.digraph.DirectedGraph;
import optimisation.BAP.AbstractBranchingDecision;
import optimisation.columnGeneration.AbstractConstraint;
import optimisation.columnGeneration.AbstractConstraint.ConstraintType;
import util.Pair;
import vehicleRouting.columnGeneration.CVRPColumn;
import vehicleRouting.columnGeneration.pricing.CVRPPricingProblem;
import vehicleRouting.instance.CVRPInstance;
import vehicleRouting.instance.CustomerLoadNode;

public class BranchingDecisionArc extends AbstractBranchingDecision<CVRPInstance, CVRPColumn, CVRPPricingProblem>
{
	private final boolean allowed;
	private final int from;
	private final int to;

	public BranchingDecisionArc(boolean allowed, int from, int to)
	{
		this.allowed = allowed;
		this.from = from;
		this.to = to;
	}

	@Override
	public Set<AbstractConstraint<CVRPInstance, CVRPColumn, CVRPPricingProblem>> getBranchingConstraints()
	{
		Set<AbstractConstraint<CVRPInstance, CVRPColumn, CVRPPricingProblem>> constraints = new LinkedHashSet<>();
		int bound = allowed ? 1 : 0;
		ConstraintType constraintType = allowed ? ConstraintType.GREATER : ConstraintType.LESSER;
		constraints.add(new BranchingConstraintArc(constraintType, bound, from, to));
		return constraints;
	}

	@Override
	public boolean isCompatible(CVRPPricingProblem pricingProblem)
	{
		return true;
	}

	@Override
	public void modifyPricingProblem(CVRPPricingProblem pricingProblem)
	{
		if (allowed)
		{
			// Forbid all other arcs out of from, unless from is the depCustomerLoadNode !=
			// 0)
			if (from != 0)
			{
				DirectedGraph<CustomerLoadNode, Integer> graph = pricingProblem.getGraph();
				for (CustomerLoadNode node : graph.getNodes())
				{
					if (node.getCustomer() != to)
					{
						pricingProblem.addForbiddenArc(new Pair<Integer, Integer>(from, node.getCustomer()));
					}
				}
			}

			// Forbid all other arcs coming into to, unless to is the depot.
			if (to != 0)
			{
				DirectedGraph<CustomerLoadNode, Integer> graph = pricingProblem.getGraph();
				for (CustomerLoadNode node : graph.getNodes())
				{
					if (node.getCustomer() != from)
					{
						pricingProblem.addForbiddenArc(new Pair<Integer, Integer>(node.getCustomer(), to));
					}
				}
			}
		}
		else
		{
			// Forbid this particular arc.
			pricingProblem.addForbiddenArc(new Pair<>(from, to));
		}
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(allowed, from, to);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BranchingDecisionArc other = (BranchingDecisionArc) obj;
		return allowed == other.allowed && from == other.from && to == other.to;
	}
}
