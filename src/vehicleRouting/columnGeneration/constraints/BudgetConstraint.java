package vehicleRouting.columnGeneration.constraints;

import optimisation.columnGeneration.AbstractConstraint;
import vehicleRouting.columnGeneration.CVRPColumn;
import vehicleRouting.columnGeneration.Route;
import vehicleRouting.columnGeneration.RouteColumn;
import vehicleRouting.columnGeneration.pricing.CVRPPricingProblem;
import vehicleRouting.instance.CVRPInstance;

public class BudgetConstraint extends AbstractConstraint<CVRPInstance, CVRPColumn, CVRPPricingProblem>
{
	public BudgetConstraint(double budget)
	{
		super(ConstraintType.LESSER, budget);
	}

	@Override
	public boolean containsColumn(CVRPColumn column)
	{
		return column instanceof RouteColumn;
	}

	@Override
	public double getCoefficient(CVRPColumn column)
	{
		Route route = ((RouteColumn) column).getRoute();
		return route.getDistance();
	}

	@Override
	public void updateGenericDuals(CVRPInstance instance, double dual)
	{
		// Do nothing.
	}

	@Override
	public void updatePricingProblemDuals(CVRPPricingProblem pricingProblem, double dual)
	{
		pricingProblem.addDistanceDual(dual);
	}

	@Override
	public int hashCode()
	{
		return super.hashCode();
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
		return true;
	}
}
