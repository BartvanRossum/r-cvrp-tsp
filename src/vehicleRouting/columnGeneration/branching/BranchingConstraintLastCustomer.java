package vehicleRouting.columnGeneration.branching;

import optimisation.columnGeneration.AbstractConstraint;
import vehicleRouting.columnGeneration.CVRPColumn;
import vehicleRouting.columnGeneration.RouteColumn;
import vehicleRouting.columnGeneration.pricing.CVRPPricingProblem;
import vehicleRouting.instance.CVRPInstance;

public class BranchingConstraintLastCustomer extends AbstractConstraint<CVRPInstance, CVRPColumn, CVRPPricingProblem>
{
	private final boolean isAllowed;
	private final int customer;

	public BranchingConstraintLastCustomer(boolean isAllowed, int customer)
	{
		super(AbstractConstraint.ConstraintType.EQUALITY, isAllowed ? 1 : 0);

		this.isAllowed = isAllowed;
		this.customer = customer;
	}

	@Override
	public boolean containsColumn(CVRPColumn column)
	{
		if (column instanceof RouteColumn)
		{
			RouteColumn routeColumn = (RouteColumn) column;
			return routeColumn.getRoute().getLastCustomer() == customer;
		}
		return false;
	}

	@Override
	public double getCoefficient(CVRPColumn column)
	{
		return 1;
	}

	@Override
	public void updateGenericDuals(CVRPInstance instance, double dual)
	{
		// Do nothing.
	}

	@Override
	public void updatePricingProblemDuals(CVRPPricingProblem pricingProblem, double dual)
	{
		if (pricingProblem.getIndex() == customer)
		{
			for (int i = 1; i <= pricingProblem.getNumCustomers(); i++)
			{
				pricingProblem.addTransitionDual(0, i, dual);
			}
		}
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + customer;
		result = prime * result + (isAllowed ? 1231 : 1237);
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
		BranchingConstraintLastCustomer other = (BranchingConstraintLastCustomer) obj;
		if (customer != other.customer)
			return false;
		if (isAllowed != other.isAllowed)
			return false;
		return true;
	}
}
