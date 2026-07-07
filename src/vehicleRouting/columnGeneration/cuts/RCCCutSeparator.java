package vehicleRouting.columnGeneration.cuts;

import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.Set;

import ilog.concert.IloException;
import optimisation.columnGeneration.AbstractConstraint;
import optimisation.columnGeneration.AbstractSolution;
import optimisation.cuts.AbstractCutSeparator;
import vehicleRouting.columnGeneration.CVRPColumn;
import vehicleRouting.columnGeneration.Route;
import vehicleRouting.columnGeneration.RouteColumn;
import vehicleRouting.columnGeneration.cuts.capsep.CapSepExistingCuts;
import vehicleRouting.columnGeneration.cuts.capsep.CapacityCutSeparation;
import vehicleRouting.columnGeneration.pricing.CVRPPricingProblem;
import vehicleRouting.instance.CVRPInstance;

/**
 * Separates Rounded Capacity Inequalities (RCCs): x(delta+(S)) >=
 * ceil(demand(S) / capacity) for violated customer subsets per period.
 *
 * Uses the CVRPSEP-based separation pipeline (COMPCUTS → COMPRESS → FCAPFIX →
 * GRSEARCH). Existing cuts are accumulated across calls to seed the local
 * search in subsequent B&amp;C nodes.
 */
public class RCCCutSeparator extends AbstractCutSeparator<CVRPInstance, CVRPColumn, CVRPPricingProblem>
{
	private static final int MAX_CUTS_PER_CALL = 10;
	private static final double EPS_INTEGRALITY = 0.001;

	private final CVRPInstance instance;
	private final CapSepExistingCuts existingCuts;

	public RCCCutSeparator(double violationThreshold, CVRPInstance instance)
	{
		super(violationThreshold, false, true);

		this.instance = instance;

		existingCuts = new CapSepExistingCuts();
	}

	@Override
	public boolean separate(boolean isRootNode)
	{
		return true;
	}

	@Override
	public Set<AbstractConstraint<CVRPInstance, CVRPColumn, CVRPPricingProblem>> generateCuts(
			AbstractSolution<CVRPInstance, CVRPColumn, CVRPPricingProblem> solution) throws IloException
	{
		Set<AbstractConstraint<CVRPInstance, CVRPColumn, CVRPPricingProblem>> cuts = new LinkedHashSet<>();

		int numCustomers = instance.getNumCustomers();
		int depot = numCustomers + 1; // CVRPSEP depot index

		// ------------------------------------------------------------------
		// Aggregate directed arc flows for this period over all LP columns.
		// arcFlows[i][j]: flow from i to j; depot index = 0.
		// ------------------------------------------------------------------
		double[][] arcFlows = new double[numCustomers + 1][numCustomers + 1];
		for (Entry<CVRPColumn, Double> entry : solution.getColumnMap().entrySet())
		{
			if (entry.getKey() instanceof RouteColumn)
			{
				Route route = ((RouteColumn) entry.getKey()).getRoute();
				double value = entry.getValue();
				for (var arc : route.getArcs())
				{
					arcFlows[arc.getKey()][arc.getValue()] += value;
				}
			}
		}

		// ------------------------------------------------------------------
		// Build 1-indexed demand array for CVRPSEP.
		// ------------------------------------------------------------------
		int[] demandArr = instance.getDemands();
		int[] demand = new int[numCustomers + 1]; // demand[1..n]
		for (int c = 1; c <= numCustomers; c++)
			demand[c] = demandArr[c - 1];

		int cap = instance.getCapacity();

		// ------------------------------------------------------------------
		// Convert directed arc flows to undirected CVRPSEP edge list.
		// Depot: arcFlows index 0 → CVRPSEP index numCustomers+1.
		// Undirected flow for {i,j} = arcFlows[i][j] + arcFlows[j][i].
		// Arrays are 1-indexed; max edges = C(n+1, 2).
		// ------------------------------------------------------------------
		int maxEdges = (numCustomers + 1) * numCustomers / 2;
		int[] edgeTail = new int[maxEdges + 1];
		int[] edgeHead = new int[maxEdges + 1];
		double[] edgeX = new double[maxEdges + 1];
		int noOfEdges = 0;

		for (int i = 0; i <= numCustomers; i++)
		{
			for (int j = i + 1; j <= numCustomers; j++)
			{
				double flow = arcFlows[i][j] + arcFlows[j][i];
				if (flow > 1e-6)
				{
					noOfEdges++;
					edgeTail[noOfEdges] = (i == 0) ? depot : i;
					edgeHead[noOfEdges] = j;
					edgeX[noOfEdges] = flow;
				}
			}
		}

		if (noOfEdges == 0)
		{
			return cuts;
		}

		// ------------------------------------------------------------------
		// Run CVRPSEP separation pipeline.
		// ------------------------------------------------------------------
		boolean[] intAndFeasible =
		{ false };
		double[] maxViolation =
		{ 0.0 };

		CapSepExistingCuts newCuts = CapacityCutSeparation.separate(numCustomers, demand, cap, noOfEdges, edgeTail,
				edgeHead, edgeX, existingCuts, MAX_CUTS_PER_CALL, EPS_INTEGRALITY, intAndFeasible, maxViolation);

		// Accumulate cuts for future B&C nodes.
		existingCuts.appendAll(newCuts);

		// ------------------------------------------------------------------
		// Convert returned cuts to framework RCC constraint objects.
		// ------------------------------------------------------------------
		for (int k = 0; k < newCuts.size(); k++)
		{
			int[] list = newCuts.getIntList(k);
			int setSize = newCuts.getIntListSize(k);

			Set<Integer> S = new LinkedHashSet<>();
			double totalDemand = 0;
			for (int idx = 1; idx <= setSize; idx++)
			{
				int c = list[idx]; // 1-indexed customer
				S.add(c);
				totalDemand += demandArr[c - 1];
			}
			int rhs = (int) Math.ceil(totalDemand / cap);
			cuts.add(new CutConstraintRCC(rhs, S));
		}

		return cuts;
	}
}
