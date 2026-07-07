package vehicleRouting.columnGeneration.pricing;

import java.util.ArrayList;
import java.util.List;

import optimisation.columnGeneration.pricing.AbstractPricingRoutine;
import vehicleRouting.columnGeneration.CVRPColumn;
import vehicleRouting.instance.CVRPInstance;

public class CVRPPricingRoutine extends AbstractPricingRoutine<CVRPInstance, CVRPColumn, CVRPPricingProblem>
{
	@Override
	protected void preProcessPricingProblems(CVRPInstance instance)
	{
		// Do nothing.
	}

	@Override
	protected void preProcessPricingProblem(CVRPInstance instance, CVRPPricingProblem pricingProblem)
	{
		// Reset duals.
		pricingProblem.resetDuals();
	}

	@Override
	protected List<CVRPPricingProblem> generatePricingProblems(CVRPInstance instance)
	{
		List<CVRPPricingProblem> pricingProblems = new ArrayList<>();
		for (int customer = 1; customer <= instance.getNumCustomers(); customer++)
		{
			pricingProblems.add(new CVRPPricingProblem(customer, instance));
		}
		return pricingProblems;
	}
}