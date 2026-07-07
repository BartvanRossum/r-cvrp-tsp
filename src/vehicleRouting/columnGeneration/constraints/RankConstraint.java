package vehicleRouting.columnGeneration.constraints;

import optimisation.columnGeneration.AbstractConstraint;
import vehicleRouting.columnGeneration.CVRPColumn;
import vehicleRouting.columnGeneration.MaxColumn;
import vehicleRouting.columnGeneration.MinColumn;
import vehicleRouting.columnGeneration.pricing.CVRPPricingProblem;
import vehicleRouting.instance.CVRPInstance;

public class RankConstraint extends AbstractConstraint<CVRPInstance, CVRPColumn, CVRPPricingProblem>
{
	public RankConstraint()
	{
		super(ConstraintType.GREATER, 0);
	}

	@Override
	public boolean containsColumn(CVRPColumn column)
	{
		return (column instanceof MaxColumn) || (column instanceof MinColumn);
	}

	@Override
	public double getCoefficient(CVRPColumn column)
	{
		if (column instanceof MaxColumn)
		{
			return 1;
		}
		return -1;
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
}
