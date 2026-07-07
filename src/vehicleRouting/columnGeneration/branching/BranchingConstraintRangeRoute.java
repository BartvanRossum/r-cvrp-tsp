package vehicleRouting.columnGeneration.branching;

import java.util.Objects;

import optimisation.columnGeneration.AbstractConstraint;
import vehicleRouting.columnGeneration.CVRPColumn;
import vehicleRouting.columnGeneration.RouteColumn;
import vehicleRouting.columnGeneration.pricing.CVRPPricingProblem;
import vehicleRouting.instance.CVRPInstance;

public class BranchingConstraintRangeRoute extends AbstractConstraint<CVRPInstance, CVRPColumn, CVRPPricingProblem>
{
	private final boolean isLowerBound;
	private final int resourceBound;

	public BranchingConstraintRangeRoute(boolean isLowerBound, int resourceBound)
	{
		super(ConstraintType.LESSER, 0);

		this.isLowerBound = isLowerBound;
		this.resourceBound = resourceBound;
	}

	public boolean isLowerBound()
	{
		return isLowerBound;
	}

	@Override
	public boolean containsColumn(CVRPColumn column)
	{
		if (column instanceof RouteColumn)
		{
			RouteColumn routeColumn = (RouteColumn) column;
			int distance = routeColumn.getRoute().getDistance();
			return isLowerBound ? distance < resourceBound : distance > resourceBound;
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
		// Do nothing.
	}

	@Override
	public String toString()
	{
		return "BranchingConstraintRangeRoute [isLowerBound=" + isLowerBound + ", resourceBound=" + resourceBound + "]";
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + Objects.hash(isLowerBound, resourceBound);
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
		BranchingConstraintRangeRoute other = (BranchingConstraintRangeRoute) obj;
		return isLowerBound == other.isLowerBound && resourceBound == other.resourceBound;
	}
}
