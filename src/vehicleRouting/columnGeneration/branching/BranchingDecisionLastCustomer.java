package vehicleRouting.columnGeneration.branching;

import java.util.LinkedHashSet;
import java.util.Set;

import optimisation.BAP.AbstractBranchingDecision;
import optimisation.columnGeneration.AbstractConstraint;
import vehicleRouting.columnGeneration.CVRPColumn;
import vehicleRouting.columnGeneration.pricing.CVRPPricingProblem;
import vehicleRouting.instance.CVRPInstance;

public class BranchingDecisionLastCustomer
		extends AbstractBranchingDecision<CVRPInstance, CVRPColumn, CVRPPricingProblem>
{
	private final boolean isAllowed;
	private final int customer;

	public BranchingDecisionLastCustomer(boolean isAllowed, int customer)
	{
		this.isAllowed = isAllowed;
		this.customer = customer;
	}

	@Override
	public boolean isCompatible(CVRPPricingProblem pricingProblem)
	{
		if (!isAllowed && pricingProblem.getIndex() == customer)
		{
			return false;
		}
		return true;
	}

	@Override
	public void modifyPricingProblem(CVRPPricingProblem pricingProblem)
	{
		// Do nothing.
	}

	@Override
	public Set<AbstractConstraint<CVRPInstance, CVRPColumn, CVRPPricingProblem>> getBranchingConstraints()
	{
		Set<AbstractConstraint<CVRPInstance, CVRPColumn, CVRPPricingProblem>> constraints = new LinkedHashSet<>();
		constraints.add(new BranchingConstraintLastCustomer(isAllowed, customer));
		return constraints;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + customer;
		result = prime * result + (isAllowed ? 1231 : 1237);
		return result;
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
		BranchingDecisionLastCustomer other = (BranchingDecisionLastCustomer) obj;
		if (customer != other.customer)
			return false;
		if (isAllowed != other.isAllowed)
			return false;
		return true;
	}
}