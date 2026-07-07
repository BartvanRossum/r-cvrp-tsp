package vehicleRouting.columnGeneration.constraints;

import java.util.Objects;

import optimisation.columnGeneration.AbstractConstraint;
import vehicleRouting.columnGeneration.CVRPColumn;
import vehicleRouting.columnGeneration.MinColumn;
import vehicleRouting.columnGeneration.Route;
import vehicleRouting.columnGeneration.RouteColumn;
import vehicleRouting.columnGeneration.pricing.CVRPPricingProblem;
import vehicleRouting.instance.CVRPInstance;

public class MinConstraint extends AbstractConstraint<CVRPInstance, CVRPColumn, CVRPPricingProblem>
{
	private final int lastCustomer;
	private final double M;

	public MinConstraint(int lastCustomer, double M)
	{
		super(ConstraintType.GREATER, -M);

		this.lastCustomer = lastCustomer;
		this.M = M;
	}

	@Override
	public boolean containsColumn(CVRPColumn column)
	{
		if (column instanceof RouteColumn)
		{
			Route route = ((RouteColumn) column).getRoute();
			return route.getLastCustomer() == lastCustomer;
		}
		return column instanceof MinColumn;
	}

	@Override
	public double getCoefficient(CVRPColumn column)
	{
		if (column instanceof MinColumn)
		{
			return -1.0;
		}
		Route route = ((RouteColumn) column).getRoute();
		double coefficient = -1 * M + route.getDistance();
		return coefficient;
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
			for (int i = 1; i <= pricingProblem.getNumCustomers(); i++)
			{
				pricingProblem.addTransitionDual(0, i, -M * dual);
			}
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
		MinConstraint other = (MinConstraint) obj;
		return lastCustomer == other.lastCustomer;
	}
}
