package vehicleRouting.columnGeneration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import ilog.concert.IloColumn;
import ilog.concert.IloException;
import ilog.concert.IloNumVar;
import optimisation.columnGeneration.AbstractConstraint;
import optimisation.columnGeneration.AbstractMasterProblem;
import vehicleRouting.columnGeneration.pricing.CVRPPricingProblem;
import vehicleRouting.instance.CVRPInstance;
import vehicleRouting.tsp.DynamicProgram;

public class CVRPMasterProblem extends AbstractMasterProblem<CVRPInstance, CVRPColumn, CVRPPricingProblem>
{
	public CVRPMasterProblem(CVRPInstance instance) throws IloException
	{
		super(instance);
	}

	public void removeTSPViolatingRoutes() throws IloException
	{
		// Sort columns to be removed by reduced cost.
		List<Entry<CVRPColumn, IloNumVar>> violatingRoutes = new ArrayList<>();
		for (Entry<CVRPColumn, IloNumVar> entry : columnMap.entrySet())
		{
			if (entry.getKey() instanceof RouteColumn)
			{
				Route route = ((RouteColumn) entry.getKey()).getRoute();
				int distance = route.getDistance();
				List<Integer> optimalSquence = DynamicProgram.determineOptimalSequence(instance, route.getCustomers(),
						true, 0);
				int optimalDistance = DynamicProgram.sequenceDistance(instance, optimalSquence, true, 0);
				if (distance > optimalDistance)
				{
					violatingRoutes.add(entry);
				}
			}
		}

		// Remove columns from column map.
		IloNumVar[] removeColumns = new IloNumVar[violatingRoutes.size()];
		for (int i = 0; i < violatingRoutes.size(); i++)
		{
			removeColumns[i] = violatingRoutes.get(i).getValue();
			columnMap.remove(violatingRoutes.get(i).getKey());
		}

		// Remove columns from the model.
		cplex.end(removeColumns);
	}

	@Override
	public void addColumn(CVRPColumn column) throws IloException
	{
		// Initialise a new column.
		IloColumn columnVar = cplex.column(objective, column.getCoefficient());
		if (columnMap.containsKey(column))
		{
			return;
		}

		// Loop over all constraints.
		for (AbstractConstraint<CVRPInstance, CVRPColumn, CVRPPricingProblem> constraint : constraintMap.keySet())
		{
			if (constraint.containsColumn(column))
			{
				columnVar = columnVar
						.and(cplex.column(constraintMap.get(constraint), constraint.getCoefficient(column)));
			}
		}
		columnMap.put(column, cplex.numVar(columnVar, 0, Double.MAX_VALUE));
	}
}
