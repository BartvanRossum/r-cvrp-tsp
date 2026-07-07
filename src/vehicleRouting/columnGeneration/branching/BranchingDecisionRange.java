package vehicleRouting.columnGeneration.branching;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import optimisation.BAP.AbstractBranchingDecision;
import optimisation.columnGeneration.AbstractConstraint;
import optimisation.columnGeneration.AbstractConstraint.ConstraintType;
import vehicleRouting.columnGeneration.CVRPColumn;
import vehicleRouting.columnGeneration.pricing.CVRPPricingProblem;
import vehicleRouting.instance.CVRPInstance;

public class BranchingDecisionRange extends AbstractBranchingDecision<CVRPInstance, CVRPColumn, CVRPPricingProblem>
{
	private final boolean isMinimum;
	private final boolean isLowerBound;
	private final int bound;

	public BranchingDecisionRange(boolean isMinimum, boolean isLowerBound, int bound)
	{
		this.isMinimum = isMinimum;
		this.isLowerBound = isLowerBound;
		this.bound = bound;
	}

	@Override
	public boolean isCompatible(CVRPPricingProblem pricingProblem)
	{
		return true;
	}

	@Override
	public void modifyPricingProblem(CVRPPricingProblem pricingProblem)
	{
		if (isMinimum && isLowerBound)
		{
			pricingProblem.setDistanceLB(bound);
		}

		if (!isMinimum && !isLowerBound)
		{
			pricingProblem.setDistanceUB(bound);
		}
	}

	@Override
	public Set<AbstractConstraint<CVRPInstance, CVRPColumn, CVRPPricingProblem>> getBranchingConstraints()
	{
		Set<AbstractConstraint<CVRPInstance, CVRPColumn, CVRPPricingProblem>> constraints = new LinkedHashSet<>();
		if ((isMinimum && isLowerBound) || (!isMinimum && !isLowerBound))
		{
			constraints.add(new BranchingConstraintRangeRoute(isLowerBound, bound));
		}
		else
		{
			ConstraintType type = isLowerBound ? ConstraintType.GREATER : ConstraintType.LESSER;
			constraints.add(new BranchingConstraintRange(type, bound, isMinimum));
		}
		return constraints;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(bound, isLowerBound, isMinimum);
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
		BranchingDecisionRange other = (BranchingDecisionRange) obj;
		return bound == other.bound && isLowerBound == other.isLowerBound && isMinimum == other.isMinimum;
	}

	@Override
	public String toString()
	{
		return "" + isMinimum + " " + isLowerBound + " " + bound;
	}
}