package vehicleRouting.columnGeneration;

import optimisation.columnGeneration.AbstractColumn;
import vehicleRouting.columnGeneration.pricing.CVRPPricingProblem;
import vehicleRouting.instance.CVRPInstance;

public abstract class CVRPColumn extends AbstractColumn<CVRPInstance, CVRPPricingProblem>
{
	public CVRPColumn(double coefficient, boolean isRemovableColumn, boolean isIntegerValued)
	{
		super(coefficient, isRemovableColumn, isIntegerValued);
	}
}
