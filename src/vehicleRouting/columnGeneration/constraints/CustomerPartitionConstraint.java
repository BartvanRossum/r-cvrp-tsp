package vehicleRouting.columnGeneration.constraints;

import java.util.Objects;

import optimisation.columnGeneration.AbstractConstraint;
import vehicleRouting.columnGeneration.CVRPColumn;
import vehicleRouting.columnGeneration.Route;
import vehicleRouting.columnGeneration.RouteColumn;
import vehicleRouting.columnGeneration.pricing.CVRPPricingProblem;
import vehicleRouting.instance.CVRPInstance;

public class CustomerPartitionConstraint extends AbstractConstraint<CVRPInstance, CVRPColumn, CVRPPricingProblem>
{
	private final int customer;

	public CustomerPartitionConstraint(int customer)
	{
		super(ConstraintType.EQUALITY, 1);

		this.customer = customer;
	}

	public int getCustomer()
	{
		return customer;
	}

	@Override
	public boolean containsColumn(CVRPColumn column)
	{
		if (column instanceof RouteColumn)
		{
			Route route = ((RouteColumn) column).getRoute();
			return route.containsCustomer(customer);
		}
		return false;
	}

	@Override
	public double getCoefficient(CVRPColumn column)
	{
		Route route = ((RouteColumn) column).getRoute();
		int coefficient = 0;
		for (Integer node : route.getCustomers())
		{
			if (node == customer)
			{
				coefficient++;
			}
		}
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
		// Place dual on arcs entering customer.
		for (int i = 0; i <= pricingProblem.getNumCustomers(); i++)
		{
			pricingProblem.addTransitionDual(i, customer, dual);
		}
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(customer);
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
		CustomerPartitionConstraint other = (CustomerPartitionConstraint) obj;
		return customer == other.customer;
	}
}
