package vehicleRouting.tsp;

import java.util.ArrayList;
import java.util.List;

import ilog.concert.IloException;
import ilog.concert.IloNumVar;
import optimisation.BAP.primalHeuristics.RestrictedMasterHeuristic;
import optimisation.columnGeneration.AbstractConstraint;
import optimisation.columnGeneration.AbstractMasterProblem;
import optimisation.columnGeneration.AbstractSolution;
import optimisation.columnGeneration.ColumnGeneration;
import util.Pair;
import vehicleRouting.columnGeneration.CVRPColumn;
import vehicleRouting.columnGeneration.CVRPMasterProblem;
import vehicleRouting.columnGeneration.MaxColumn;
import vehicleRouting.columnGeneration.MinColumn;
import vehicleRouting.columnGeneration.Route;
import vehicleRouting.columnGeneration.RouteColumn;
import vehicleRouting.columnGeneration.branching.BranchingConstraintRange;
import vehicleRouting.columnGeneration.branching.BranchingConstraintRangeRoute;
import vehicleRouting.columnGeneration.pricing.CVRPPricingProblem;
import vehicleRouting.instance.CVRPInstance;

public class TSPRestrictedMasterHeuristic
		extends RestrictedMasterHeuristic<CVRPInstance, CVRPColumn, CVRPPricingProblem>
{
	public TSPRestrictedMasterHeuristic(int timeLimit)
	{
		super(timeLimit);
	}

	@Override
	public AbstractSolution<CVRPInstance, CVRPColumn, CVRPPricingProblem> applyHeuristic(
			AbstractMasterProblem<CVRPInstance, CVRPColumn, CVRPPricingProblem> masterProblem,
			ColumnGeneration<CVRPInstance, CVRPColumn, CVRPPricingProblem> columnGeneration, double initialLowerBound,
			long timeLimit) throws IloException
	{
		// Copy the master problem.
		AbstractMasterProblem<CVRPInstance, CVRPColumn, CVRPPricingProblem> copy = new CVRPMasterProblem(
				masterProblem.getInstance());
		for (AbstractConstraint<CVRPInstance, CVRPColumn, CVRPPricingProblem> constraint : masterProblem
				.getConstraints())
		{
			if (constraint instanceof BranchingConstraintRange || constraint instanceof BranchingConstraintRangeRoute)
			{
				continue;
			}
			copy.addConstraint(constraint);
		}
		copy.addColumn(new MaxColumn());
		copy.addColumn(new MinColumn());

		// Convert to TSP-respecting routes.
		List<CVRPColumn> remove = new ArrayList<>();
		for (Pair<CVRPColumn, IloNumVar> column : masterProblem.getColumns())
		{
			if (column.getKey() instanceof RouteColumn)
			{
				Route route = ((RouteColumn) column.getKey()).getRoute();
				List<Integer> optimalSequence = DynamicProgram.determineOptimalSequence(masterProblem.getInstance(),
						route.getCustomers(), true, 0);
				int optimalDistance = DynamicProgram.sequenceDistance(masterProblem.getInstance(), optimalSequence,
						true, 0);
				if (optimalDistance < route.getDistance())
				{
					List<Pair<Integer, Integer>> arcs = new ArrayList<>();
					arcs.add(new Pair<>(0, optimalSequence.getFirst()));
					for (int i = 0; i < optimalSequence.size() - 1; i++)
					{
						arcs.add(new Pair<>(optimalSequence.get(i), optimalSequence.get(i + 1)));
					}
					arcs.add(new Pair<>(optimalSequence.getLast(), 0));
					Route newRoute = new Route(route.getVehicle(), optimalDistance, route.getLoad(), optimalSequence,
							arcs);
					RouteColumn routeColumn = new RouteColumn(newRoute);
					remove.add(routeColumn);
					copy.addColumn(routeColumn);
				}
				else
				{
					copy.addColumn(column.getKey());
				}
			}
		}

		// Apply a restricted master problem.
		AbstractSolution<CVRPInstance, CVRPColumn, CVRPPricingProblem> solution = copy
				.applyRestrictedMasterHeuristic(TIME_LIMIT_SECONDS);

		// Update the lower bound.
		lowerBound = copy.getLowerBoundMILP();
		if (solution != null)
		{
			lowerBound = Math.min(copy.getLowerBoundMILP(), solution.getObjectiveValue());
		}
		copy.clean();

		// Return solution.
		return solution;
	}
}
