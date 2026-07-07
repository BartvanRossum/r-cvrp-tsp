package vehicleRouting.tsp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import ilog.concert.IloException;
import optimisation.columnGeneration.AbstractConstraint;
import optimisation.columnGeneration.AbstractSolution;
import optimisation.cuts.AbstractCutSeparator;
import util.Configuration;
import util.Pair;
import vehicleRouting.columnGeneration.CVRPColumn;
import vehicleRouting.columnGeneration.Route;
import vehicleRouting.columnGeneration.RouteColumn;
import vehicleRouting.columnGeneration.pricing.CVRPPricingProblem;
import vehicleRouting.instance.CVRPInstance;

// Separates TSP path cuts for paths starting at the depot (node 0). The depot
// may also appear as the terminal node, but never as an intermediate node.
public class TSPCutSeparator extends AbstractCutSeparator<CVRPInstance, CVRPColumn, CVRPPricingProblem>
{
	private final double PRECISION;
	private final CVRPInstance instance;
	private final boolean liftCuts;

	public TSPCutSeparator(double violationThreshold, CVRPInstance instance, boolean liftCuts)
	{
		super(violationThreshold, false, true);

		this.PRECISION = Configuration.getConfiguration().getDoubleProperty("PRECISION");
		this.instance = instance;
		this.liftCuts = liftCuts;
	}

	public double computeViolation(TSPCut cut, double[][] arcFlow)
	{
		double violation = 0;
		for (Pair<Integer, Integer> arc : cut.getArcs())
		{
			violation += arcFlow[arc.getKey()][arc.getValue()];
		}
		violation -= cut.getBound();
		return violation;
	}

	// Lifts a TSPCut by adding arcs to its LHS, keeping the RHS unchanged.
	//
	// Forward lifting (forward=true): iterates h from 0 to p-1 (prefix perspective,
	// start = depot). Adds arcs (v_h, j) that are infeasible to use after visiting
	// the prefix path[0..h]: elementarity (j already visited), capacity
	// (overflows),
	// or TSP-optimality (prefix extended to j is TSP-suboptimal).
	//
	// Backward lifting (forward=false): iterates h from p-1 down to 1 (suffix
	// perspective, start = terminal). Adds arcs (v_h, j) that are infeasible to use
	// as the last arc before v_h in a route that still visits the suffix
	// path[h..p]:
	// elementarity (j already in suffix), capacity (suffix load + demand[j] exceeds
	// capacity), or TSP-optimality (suffix extended to j is TSP-suboptimal).
	public TSPCut lift(TSPCut cut, boolean forward)
	{
		Set<Pair<Integer, Integer>> cutArcs = cut.getArcs();

		// Reconstruct the ordered path from the unordered arc set.
		Map<Integer, Integer> next = new LinkedHashMap<>();
		Set<Integer> hasIncoming = new LinkedHashSet<>();
		for (Pair<Integer, Integer> arc : cutArcs)
		{
			next.put(arc.getKey(), arc.getValue());
			hasIncoming.add(arc.getValue());
		}
		int pathStart = 0;
		for (Integer from : next.keySet())
		{
			if (!hasIncoming.contains(from))
			{
				pathStart = from;
				break;
			}
		}
		List<Integer> path = new ArrayList<>();
		int current = pathStart;
		while (next.containsKey(current))
		{
			path.add(current);
			current = next.get(current);
			if (current == 0 && path.contains(current))
			{
				break;
			}
		}
		path.add(current); // terminal node

		int n = instance.getNumCustomers();
		int[][] dist = instance.getDistances();
		int[] demands = instance.getDemands();
		int capacity = instance.getCapacity();

		Set<Pair<Integer, Integer>> liftedArcs = new LinkedHashSet<>(cutArcs);
		if (forward)
		{
			// Precompute cumulative prefix distances along the path.
			int[] prefixDistances = new int[path.size()];
			for (int i = 1; i < path.size(); i++)
			{
				prefixDistances[i] = prefixDistances[i - 1] + dist[path.get(i - 1)][path.get(i)];
			}

			int prefixLoad = 0;
			// h iterates over intermediate nodes path[0..p-1]; path[p] is the terminal.
			for (int h = 0; h < path.size() - 1; h++)
			{
				int vh = path.get(h);
				prefixLoad += (vh == 0) ? 0 : demands[vh - 1];

				// Elementarity: arc (vh, path[j]) for j < h revisits an already-visited
				// node. Depot is skipped since ending at the depot is valid.
				for (int j = 0; j < h; j++)
				{
					int vj = path.get(j);
					if (vj != 0)
					{
						liftedArcs.add(new Pair<>(vh, vj));
					}
				}

				// Excluded for capacity/TSP: nodes already in path[0..h+1].
				Set<Integer> excluded = new LinkedHashSet<>();
				for (int k = 0; k <= h + 1 && k < path.size(); k++)
				{
					excluded.add(path.get(k));
				}

				// Customers in path[1..h] are the intermediates for the TSP-optimality check.
				List<Integer> prefixCustomers = null;
				if (h >= 2)
				{
					prefixCustomers = new ArrayList<>();
					for (int m = 1; m <= h; m++)
					{
						if (path.get(m) != 0)
						{
							prefixCustomers.add(path.get(m));
						}
					}
				}

				for (int j = 1; j <= n; j++)
				{
					if (excluded.contains(j))
					{
						continue;
					}

					// Capacity: arc (vh, j) is infeasible when prefix load + demand[j] > capacity.
					if (prefixLoad + demands[j - 1] > capacity)
					{
						liftedArcs.add(new Pair<>(vh, j));
						continue;
					}

					// TSP-optimality: requires h >= 2 (at least 3 arcs for a TSP-violating path).
					// Arc (vh, j) is added when extending the prefix to j is TSP-suboptimal.
					if (h >= 2)
					{
						int currentDist = prefixDistances[h] + dist[vh][j];
						List<Integer> optSeq = DynamicProgram.determineOptimalSequenceWithTerminal(instance,
								prefixCustomers, j, 0);
						int optDist = DynamicProgram.sequenceDistance(instance, optSeq, false, 0);
						if (currentDist > optDist)
						{
							liftedArcs.add(new Pair<>(vh, j));
						}
					}
				}
			}
		}
		else
		{
			// Backward lifting: iterate h from p-1 down to 1 on the original path.
			// For each intermediate node v_h = path[h], arc (, v_h) is lifted when
			// using arc (j, v_h) is incompatible with still needing to visit the suffix
			// path[h+1..p] = {v_{h+1}, ..., v_p}.
			int p = path.size() - 1; // index of terminal
			int terminalNode = path.get(p);

			// Precompute cumulative prefix distances to compute suffix distances.
			int[] prefixDistances = new int[path.size()];
			for (int i = 1; i < path.size(); i++)
			{
				prefixDistances[i] = prefixDistances[i - 1] + dist[path.get(i - 1)][path.get(i)];
			}
			int totalDist = prefixDistances[p];

			// suffixLoad grows as h decreases: demand(v_{h+1}) + ... + demand(v_p).
			// At start of iteration h it does NOT yet include demand(v_h).
			int suffixLoad = (terminalNode == 0) ? 0 : demands[terminalNode - 1];

			for (int h = p - 1; h >= 1; h--)
			{
				int vh = path.get(h);

				// Elementarity: arc (vh, path[j]) for j > h is already in the suffix,
				// so using it would revisit a node. Depot is skipped.
				for (int j = h + 1; j <= p; j++)
				{
					int vj = path.get(j);
					if (vj != 0)
					{
						liftedArcs.add(new Pair<>(vj, vh));
					}
				}

				// Excluded for capacity/TSP: nodes in path[h-1..p] (the suffix plus the
				// predecessor, since path[h-1]→vh is the arc before vh).
				Set<Integer> excluded = new LinkedHashSet<>();
				for (int k = h - 1; k <= p; k++)
				{
					excluded.add(path.get(k));
				}

				// Customers in path[h+1..p-1] are the intermediates for the TSP check.
				// (Equivalent to suffixCustomers minus the terminal if it is a customer.)
				// We need at least 2 intermediate customers in the suffix for TSP check:
				// extended suffix (j, v_{h+1}, ..., v_{p-1}, terminalNode) has >= 3 arcs
				// when suffixCustomers.size() >= 2 (i.e., p - h >= 2).
				List<Integer> suffixIntermediates = null;
				int numSuffixArcs = p - h; // arcs from vh to terminalNode along the suffix
				if (numSuffixArcs >= 2)
				{
					// Intermediates are path[h..p-1]: vh included, terminal excluded.
					suffixIntermediates = new ArrayList<>();
					for (int m = h; m <= p - 1; m++)
					{
						if (path.get(m) != 0)
						{
							suffixIntermediates.add(path.get(m));
						}
					}
				}

				for (int j = 1; j <= n; j++)
				{
					if (excluded.contains(j))
					{
						continue;
					}

					// Capacity: arc (j, vh) forces the route to carry j + vh + v_{h+1}..v_p.
					if (demands[j - 1] + demands[vh - 1] + suffixLoad > capacity)
					{
						liftedArcs.add(new Pair<>(j, vh));
						continue;
					}

					// TSP-optimality: arc (j, vh) is added when the detour via j costs
					// more than the original suffix. Going j->vh then optimally visiting
					// suffixIntermediates to terminalNode costs dist[j][vh] + optDist(vh,...).
					// If this exceeds suffixDist (the original vh→...→terminalNode distance),
					// arc (j, vh) is TSP-suboptimal. Requires numSuffixArcs >= 2.
					if (numSuffixArcs >= 2)
					{
						int suffixDist = totalDist - prefixDistances[h]; // dist vh->...->terminalNode
						List<Integer> optSeq = DynamicProgram.determineOptimalSequenceWithTerminal(instance,
								suffixIntermediates, terminalNode, j);
						int optDist = DynamicProgram.sequenceDistance(instance, optSeq, false, j);
						if (dist[j][vh] + suffixDist > optDist)
						{
							liftedArcs.add(new Pair<>(j, vh));
						}
					}
				}

				// Update suffixLoad for the next (lower) iteration.
				suffixLoad += demands[vh - 1]; // vh is always a customer (h >= 1, not depot)
			}
		}
		return new TSPCut(liftedArcs, cutArcs.size() - 1);
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

		int n = instance.getNumCustomers();
		double[][] arcFlow = new double[n + 1][n + 1];
		for (Entry<CVRPColumn, Double> entry : solution.getColumnMap().entrySet())
		{
			if (!(entry.getKey() instanceof RouteColumn))
			{
				continue;
			}
			Route route = ((RouteColumn) entry.getKey()).getRoute();
			double value = entry.getValue();
			for (var arc : route.getArcs())
			{
				arcFlow[arc.getKey()][arc.getValue()] += value;
			}
		}

		boolean[] visited = new boolean[n + 1];
		visited[0] = true;
		dfs(new ArrayList<>(), new ArrayList<>(), 0.0, 0, visited, arcFlow, cuts);

		return cuts;
	}

	// Performs DFS from the depot, extending the current path with one arc at a
	// time. 'customers' accumulates the customer nodes visited so far (depot is
	// never added). The path is evaluated as a cut candidate at every customer
	// endpoint (>= 2 intermediaries) and at the depot endpoint (>= 3 arcs).
	private void dfs(List<Integer> customers, List<Pair<Integer, Integer>> arcs, double flow, int distance,
			boolean[] visited, double[][] arcFlow,
			Set<AbstractConstraint<CVRPInstance, CVRPColumn, CVRPPricingProblem>> cuts)
	{
		int current = arcs.isEmpty() ? 0 : arcs.get(arcs.size() - 1).getValue();

		if (!arcs.isEmpty() && arcs.size() >= 3)
		{
			double violation = flow - (arcs.size() - 1);
			if (violation > violationThreshold)
			{
				// When the endpoint is the depot all customers are intermediaries;
				// when it is a customer the last entry in the list is the terminal.
				List<Integer> intermediates;
				int terminal;
				if (current == 0)
				{
					intermediates = customers;
					terminal = 0;
				}
				else
				{
					intermediates = customers.subList(0, customers.size() - 1);
					terminal = current;
				}

				List<Integer> optimalSequence = DynamicProgram.determineOptimalSequenceWithTerminal(instance,
						intermediates, terminal, 0);
				int optimalDistance = DynamicProgram.sequenceDistance(instance, optimalSequence, false, 0);

				if (distance > optimalDistance)
				{
					TSPCut cut = new TSPCut(new LinkedHashSet<>(arcs));
					if (computeViolation(cut, arcFlow) > violationThreshold)
					{
						if (liftCuts)
						{
							cuts.add(lift(cut, true));
							cuts.add(lift(cut, false));
						}
						else
						{
							cuts.add(cut);
						}
					}
				}
			}
		}

		// Stop extending once the depot has been reached as an endpoint.
		if (current == 0 && !arcs.isEmpty())
		{
			return;
		}

		int n = instance.getNumCustomers();
		int[][] dist = instance.getDistances();
		for (int j = 0; j <= n; j++)
		{
			if (arcFlow[current][j] < PRECISION)
			{
				continue;
			}
			if (j != 0 && visited[j])
			{
				continue;
			}
			double newFlow = flow + arcFlow[current][j];
			int newNumArcs = arcs.size() + 1;
			if (newFlow <= newNumArcs - 1)
			{
				continue;
			}
			if (j != 0)
			{
				customers.add(j);
				visited[j] = true;
			}
			arcs.add(new Pair<>(current, j));

			dfs(customers, arcs, newFlow, distance + dist[current][j], visited, arcFlow, cuts);

			arcs.remove(arcs.size() - 1);
			if (j != 0)
			{
				customers.remove(customers.size() - 1);
				visited[j] = false;
			}
		}
	}
}
