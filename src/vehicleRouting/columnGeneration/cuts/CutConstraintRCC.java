package vehicleRouting.columnGeneration.cuts;

import java.util.Objects;
import java.util.Set;

import optimisation.columnGeneration.AbstractConstraint;
import util.Pair;
import vehicleRouting.columnGeneration.CVRPColumn;
import vehicleRouting.columnGeneration.Route;
import vehicleRouting.columnGeneration.RouteColumn;
import vehicleRouting.columnGeneration.pricing.CVRPPricingProblem;
import vehicleRouting.instance.CVRPInstance;

/**
 * Rounded Capacity Inequality (RCC) cut: x(delta+(S)) >= ceil(demand(S) /
 * capacity)
 *
 * Coefficient of a column is the number of arcs exiting S in its route for this
 * period.
 */
public class CutConstraintRCC extends AbstractConstraint<CVRPInstance, CVRPColumn, CVRPPricingProblem>
{
	private final Set<Integer> customerSet;

	public CutConstraintRCC(double bound, Set<Integer> customerSet)
	{
		super(ConstraintType.GREATER, bound);

		this.customerSet = customerSet;
	}

	private int countCutArcs(CVRPColumn column)
	{
		if (column instanceof RouteColumn)
		{
			Route route = ((RouteColumn) column).getRoute();
			int count = 0;
			for (Pair<Integer, Integer> arc : route.getArcs())
			{
				if (customerSet.contains(arc.getKey()) && !customerSet.contains(arc.getValue()))
				{
					count++;
				}
			}
			return count;
		}
		return 0;
	}

	@Override
	public boolean containsColumn(CVRPColumn column)
	{
		return countCutArcs(column) > 0;
	}

	@Override
	public double getCoefficient(CVRPColumn column)
	{
		return countCutArcs(column);
	}

	@Override
	public void updateGenericDuals(CVRPInstance instance, double dual)
	{
		// Do nothing.
	}

	@Override
	public void updatePricingProblemDuals(CVRPPricingProblem pricingProblem, double dual)
	{
		// Place dual on arcs leaving cutset.
		for (int from : customerSet)
		{
			for (int i = 0; i <= pricingProblem.getNumCustomers(); i++)
			{
				if (customerSet.contains(i))
				{
					continue;
				}
				pricingProblem.addTransitionDual(from, i, dual);
			}
		}
	}

	public Set<Integer> getCustomerSet()
	{
		return customerSet;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(customerSet);
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
		CutConstraintRCC other = (CutConstraintRCC) obj;
		return Objects.equals(customerSet, other.customerSet);
	}
}
