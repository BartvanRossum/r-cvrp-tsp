package vehicleRouting.tsp;

import java.util.Objects;
import java.util.Set;

import optimisation.columnGeneration.AbstractConstraint;
import util.Pair;
import vehicleRouting.columnGeneration.CVRPColumn;
import vehicleRouting.columnGeneration.Route;
import vehicleRouting.columnGeneration.RouteColumn;
import vehicleRouting.columnGeneration.pricing.CVRPPricingProblem;
import vehicleRouting.instance.CVRPInstance;

public class TSPCut extends AbstractConstraint<CVRPInstance, CVRPColumn, CVRPPricingProblem>
{
	private final Set<Pair<Integer, Integer>> arcs;

	public Set<Pair<Integer, Integer>> getArcs()
	{
		return arcs;
	}

	public TSPCut(Set<Pair<Integer, Integer>> arcs)
	{
		super(ConstraintType.LESSER, arcs.size() - 1);

		this.arcs = arcs;
	}

	public TSPCut(Set<Pair<Integer, Integer>> arcs, int bound)
	{
		super(ConstraintType.LESSER, bound);

		this.arcs = arcs;
	}

	@Override
	public boolean containsColumn(CVRPColumn column)
	{
		if (column instanceof RouteColumn)
		{
			return countArcs(((RouteColumn) column).getRoute()) > 0;
		}
		return false;
	}

	private int countArcs(Route route)
	{
		int count = 0;
		for (Pair<Integer, Integer> arc : route.getArcs())
		{
			if (arcs.contains(arc))
			{
				count++;
			}
		}
		return count;
	}

	@Override
	public double getCoefficient(CVRPColumn column)
	{
		return countArcs(((RouteColumn) column).getRoute());
	}

	@Override
	public void updateGenericDuals(CVRPInstance instance, double dual)
	{
		// Do nothing.
	}

	@Override
	public void updatePricingProblemDuals(CVRPPricingProblem pricingProblem, double dual)
	{
		for (Pair<Integer, Integer> arc : arcs)
		{
			pricingProblem.addTransitionDual(arc.getKey(), arc.getValue(), dual);
		}
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(arcs);
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
		TSPCut other = (TSPCut) obj;
		return Objects.equals(arcs, other.arcs);
	}
}
