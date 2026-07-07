package vehicleRouting.scripts;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import ilog.concert.IloException;
import nodeComparators.BoundComparator;
import optimisation.BAP.BranchAndPrice;
import optimisation.columnGeneration.AbstractSolution;
import optimisation.columnGeneration.ColumnGeneration;
import optimisation.columnGeneration.columnManagement.AllColumnSelector;
import util.Configuration;
import util.Logger;
import util.Util;
import util.Writer;
import vehicleRouting.columnGeneration.CVRPColumn;
import vehicleRouting.columnGeneration.CVRPMasterProblem;
import vehicleRouting.columnGeneration.MaxColumn;
import vehicleRouting.columnGeneration.MinColumn;
import vehicleRouting.columnGeneration.Route;
import vehicleRouting.columnGeneration.RouteColumn;
import vehicleRouting.columnGeneration.branching.BranchingRuleArc;
import vehicleRouting.columnGeneration.branching.BranchingRuleLastCustomer;
import vehicleRouting.columnGeneration.branching.BranchingRuleRange;
import vehicleRouting.columnGeneration.constraints.BudgetConstraint;
import vehicleRouting.columnGeneration.constraints.CustomerPartitionConstraint;
import vehicleRouting.columnGeneration.constraints.LowerBoundMaxConstraint;
import vehicleRouting.columnGeneration.constraints.MaxConstraint;
import vehicleRouting.columnGeneration.constraints.MinConstraint;
import vehicleRouting.columnGeneration.constraints.NumberVehiclesConstraint;
import vehicleRouting.columnGeneration.constraints.RankConstraint;
import vehicleRouting.columnGeneration.cuts.RCCCutSeparator;
import vehicleRouting.columnGeneration.pricing.CVRPPricingProblem;
import vehicleRouting.columnGeneration.pricing.CVRPPricingRoutine;
import vehicleRouting.columnGeneration.pricing.DistanceCompletionBoundTest;
import vehicleRouting.columnGeneration.pricing.RegularCVRPSolver;
import vehicleRouting.instance.CVRPInstance;
import vehicleRouting.tsp.DynamicProgram;
import vehicleRouting.tsp.TSPCutSeparator;
import vehicleRouting.tsp.TSPRestrictedMasterHeuristic;

public class MainTSPCluster
{
	public static void main(String[] args) throws IOException, IloException
	{
		// General settings.
		int numVehicles = 5;
		int[] possibleCustomers =
//			{15, 20, 25, 30}
		{ 30};
		double[] alphas =
		{ 1.01, 1.05, 1.10 };
		int[] sizes =
		{ 1, 3, 20, 3 };
//		{4, 3, 20, 3};
		int argument = Integer.valueOf(args[0]);
		int[] settings = Util.retrieveSettings(sizes, argument);
		int numCustomers = possibleCustomers[settings[0]];
		double alpha = alphas[settings[1]];
		int t = settings[2];
		boolean useCuts = false;
		boolean liftCuts = false;
		switch (settings[3])
		{
		case 0:
			break;
		case 1:
			useCuts = true;
			break;
		case 2:
			useCuts = true;
			liftCuts = true;
			break;
		default:
			break;
		}

		long timeLimit = 3600 * 1000;
		String name = "n_" + numCustomers + "_alpha_" + alpha + "_t_" + t + "_cuts_" + useCuts + "_lifting_" + liftCuts;
		System.out.println(name);

		// Initialise settings file.
		Configuration.initialiseConfiguration("settingsCVRP.properties");

		// Read in instance and optimal routes.
		String instanceFile = "dataCVRP/instances/n" + numCustomers + "_k" + numVehicles + "_" + t + ".txt";
		CVRPInstance instance = new CVRPInstance(numCustomers, numVehicles, instanceFile, true);
		List<Route> routes = new ArrayList<>();
		double budget = 0;
		String solutionFile = "dataCVRP/solutions/solution_n" + numCustomers + "_k" + numVehicles + "_" + t + ".txt";
		double max = 0;
		double min = Integer.MAX_VALUE;
		for (Route route : Route.readRoutes(solutionFile))
		{
			routes.add(new Route(route.getVehicle(), route.getDistance(), route.getLoad(), route.getCustomers(),
					route.getArcs()));
			budget += route.getDistance();
			max = Math.max(max, route.getDistance());
			min = Math.min(min, route.getDistance());
		}
		int alphaBudget = (int) Math.floor(alpha * budget);
		double lowerBoundMax = budget / numVehicles;
		double M = (int) Math.floor((double) alphaBudget / (double) numVehicles);
		System.out.println("Budget: " + budget);

		// Initialise master problem.
		CVRPMasterProblem masterProblem = new CVRPMasterProblem(instance);
		masterProblem.addConstraint(new NumberVehiclesConstraint(numVehicles));
		for (int customer = 1; customer <= numCustomers; customer++)
		{
			masterProblem.addConstraint(new CustomerPartitionConstraint(customer));
			masterProblem.addConstraint(new MaxConstraint(customer));
			masterProblem.addConstraint(new MinConstraint(customer, M));
		}
		masterProblem.addConstraint(new RankConstraint());
		masterProblem.addConstraint(new BudgetConstraint(alphaBudget));
		masterProblem.addConstraint(new LowerBoundMaxConstraint(lowerBoundMax));

		// Add columns.
		masterProblem.addColumn(new MaxColumn());
		masterProblem.addColumn(new MinColumn());

		// Column generation settings.
		CVRPPricingRoutine pricingRoutine = new CVRPPricingRoutine();
		AllColumnSelector<CVRPInstance, CVRPColumn, CVRPPricingProblem> selector = new AllColumnSelector<>();
		RegularCVRPSolver solver = new RegularCVRPSolver();
		solver.setDistanceCompetionBound(new DistanceCompletionBoundTest(instance));
		ColumnGeneration<CVRPInstance, CVRPColumn, CVRPPricingProblem> columnGeneration = new ColumnGeneration<>(
				pricingRoutine, solver, solver, selector);

		// Branch and price settings.
		BranchAndPrice<CVRPInstance, CVRPColumn, CVRPPricingProblem> branchAndPrice = new BranchAndPrice<>(
				new BoundComparator<CVRPInstance, CVRPColumn, CVRPPricingProblem>(), instance, masterProblem,
				columnGeneration);
		branchAndPrice.addBranchingRule(new BranchingRuleRange(0, 0.975));
		branchAndPrice.addBranchingRule(new BranchingRuleRange(0.5, 0.99));
		branchAndPrice.addBranchingRule(new BranchingRuleRange(0.75, 0.995));
		branchAndPrice.addBranchingRule(new BranchingRuleLastCustomer(1));
		branchAndPrice.addBranchingRule(new BranchingRuleArc(2));
		branchAndPrice.setTimeLimit(timeLimit);

		// Cutting planes.
		branchAndPrice.addCutSeparator(new RCCCutSeparator(0.01, instance));
		if (useCuts)
		{
			branchAndPrice.addCutSeparator(new TSPCutSeparator(0.01, instance, liftCuts));
		}

		// Custom RMH.
		branchAndPrice.setRestrictedMasterHeuristic(new TSPRestrictedMasterHeuristic(5));

		// Pass upper bound.
		branchAndPrice.setUpperBound(max - min + 1);

		// Apply branch and price.
		branchAndPrice.applyBranchAndPrice();

		// Write solutions.
		List<Route> optimalRoutes = new ArrayList<>();
		int maximum = 0;
		int minimum = Integer.MAX_VALUE;
		double valueMaximum = 0;
		double valueMinimum = 0;
		AbstractSolution<CVRPInstance, CVRPColumn, CVRPPricingProblem> solution = branchAndPrice.getBestSolution();
		for (Entry<CVRPColumn, Double> entry : solution.getColumnMap().entrySet())
		{
			if (entry.getKey() instanceof MaxColumn)
			{
				valueMaximum = entry.getValue();
			}
			if (entry.getKey() instanceof MinColumn)
			{
				valueMinimum = entry.getValue();
			}
			if (entry.getKey() instanceof RouteColumn && entry.getValue() > 0.5)
			{
				Route route = ((RouteColumn) entry.getKey()).getRoute();
				optimalRoutes.add(route);
				int distance = route.getDistance();
				List<Integer> optimalSeq = DynamicProgram.determineOptimalSequence(instance, route.getCustomers(), true,
						0);
				System.out.println(distance + " vs " + DynamicProgram.sequenceDistance(instance, optimalSeq, true, 0));
				maximum = Math.max(maximum, distance);
				minimum = Math.min(minimum, distance);
			}
		}

		System.out.println("Actual range: " + (maximum - minimum) + " with " + maximum + " and " + minimum
				+ " vs in the model max + " + valueMaximum + " and min " + valueMinimum);
		Writer.write(optimalRoutes, "solution_" + name + ".txt");

		// Write logger.
		String loggerName = "logger_" + name + ".csv";
		Writer.write(Logger.getLogger().getOutput(), loggerName);
	}
}
