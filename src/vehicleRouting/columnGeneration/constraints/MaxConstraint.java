package vehicleRouting.columnGeneration.constraints;

import java.util.Objects;

import optimisation.columnGeneration.AbstractConstraint;
import vehicleRouting.columnGeneration.CVRPColumn;
import vehicleRouting.columnGeneration.MaxColumn;
import vehicleRouting.columnGeneration.Route;
import vehicleRouting.columnGeneration.RouteColumn;
import vehicleRouting.columnGeneration.pricing.CVRPPricingProblem;
import vehicleRouting.instance.CVRPInstance;

public class MaxConstraint extends AbstractConstraint<CVRPInstance, CVRPColumn, CVRPPricingProblem>
{
	private final int lastCustomer;

	public MaxConstraint(int lastCustomer)
	{
		super(ConstraintType.LESSER, 0);

		this.lastCustomer = lastCustomer;
	}

	@Override
	public boolean containsColumn(CVRPColumn column)
	{
		if (column instanceof RouteColumn)
		{
			Route route = ((RouteColumn) column).getRoute();
			return route.getLastCustomer() == lastCustomer;
		}
		return column instanceof MaxColumn;
	}

	@Override
	public double getCoefficient(CVRPColumn column)
	{
		if (column instanceof MaxColumn)
		{
			return -1.0;
		}
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
		if (pricingProblem.getIndex() == lastCustomer)
		{
			pricingProblem.addDistanceDual(dual);
		}
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(lastCustomer);
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
		MaxConstraint other = (MaxConstraint) obj;
		return lastCustomer == other.lastCustomer;
	}
}
