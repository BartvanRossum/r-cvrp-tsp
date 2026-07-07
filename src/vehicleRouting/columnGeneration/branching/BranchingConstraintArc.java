package vehicleRouting.columnGeneration.branching;

import java.util.Objects;

import optimisation.columnGeneration.AbstractConstraint;
import util.Pair;
import vehicleRouting.columnGeneration.CVRPColumn;
import vehicleRouting.columnGeneration.Route;
import vehicleRouting.columnGeneration.RouteColumn;
import vehicleRouting.columnGeneration.pricing.CVRPPricingProblem;
import vehicleRouting.instance.CVRPInstance;

public class BranchingConstraintArc extends AbstractConstraint<CVRPInstance, CVRPColumn, CVRPPricingProblem>
{
	private final int from;
	private final int to;

	public BranchingConstraintArc(ConstraintType constraintType, double bound, int from, int to)
	{
		super(constraintType, bound);

		this.from = from;
		this.to = to;
	}

	@Override
	public boolean containsColumn(CVRPColumn column)
	{
		if (column instanceof RouteColumn)
		{
			Route route = ((RouteColumn) column).getRoute();
			int numArcs = 0;
			for (Pair<Integer, Integer> arc : route.getArcs())
			{
				if (arc.getKey() == from && arc.getValue() == to)
				{
					numArcs++;
				}
			}
			return numArcs > 0;
		}
		return false;
	}

	@Override
	public double getCoefficient(CVRPColumn column)
	{
		Route route = ((RouteColumn) column).getRoute();
		int numArcs = 0;
		for (Pair<Integer, Integer> arc : route.getArcs())
		{
			if (arc.getKey() == from && arc.getValue() == to)
			{
				numArcs++;
			}
		}
		return numArcs;
	}

	@Override
	public void updateGenericDuals(CVRPInstance instance, double dual)
	{
		// Do nothing.
	}

	@Override
	public void updatePricingProblemDuals(CVRPPricingProblem pricingProblem, double dual)
	{
		// Place dual on arc.
		pricingProblem.addTransitionDual(from, to, dual);
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(from, to);
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		BranchingConstraintArc other = (BranchingConstraintArc) obj;
		return from == other.from && to == other.to;
	}

	@Override
	public String toString()
	{
		return "" + from + " " + to;
	}
}
