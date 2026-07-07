package vehicleRouting.columnGeneration.branching;

import java.util.Objects;

import optimisation.columnGeneration.AbstractConstraint;
import vehicleRouting.columnGeneration.CVRPColumn;
import vehicleRouting.columnGeneration.MaxColumn;
import vehicleRouting.columnGeneration.MinColumn;
import vehicleRouting.columnGeneration.pricing.CVRPPricingProblem;
import vehicleRouting.instance.CVRPInstance;

public class BranchingConstraintRange extends AbstractConstraint<CVRPInstance, CVRPColumn, CVRPPricingProblem>
{
	private final boolean isMinimum;
	private final int bound;

	public BranchingConstraintRange(ConstraintType constraintType, int bound, boolean isMinimum)
	{
		super(constraintType, bound);

		this.isMinimum = isMinimum;
		this.bound = bound;
	}

	@Override
	public boolean containsColumn(CVRPColumn column)
	{
		if (isMinimum)
		{
			return column instanceof MinColumn;
		}
		else
		{
			return column instanceof MaxColumn;
		}
	}

	@Override
	public String toString()
	{
		return "BranchingConstraintRange [isMinimum=" + isMinimum + ",type=" + this.constraintType + ", bound=" + bound
				+ "]";
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
		// Do nothing.
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(bound, isMinimum);
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
		BranchingConstraintRange other = (BranchingConstraintRange) obj;
		return bound == other.bound && isMinimum == other.isMinimum;
	}
}
