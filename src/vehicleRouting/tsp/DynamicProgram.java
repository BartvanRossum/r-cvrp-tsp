package vehicleRouting.tsp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import vehicleRouting.instance.CVRPInstance;

public class DynamicProgram
{
	public static List<Integer> determineOptimalSequence(CVRPInstance instance, List<Integer> customers,
			boolean returnToDepot, int start)
	{
		int[][] dist = instance.getDistances();
		int n = customers.size();

		// Trivial case.
		if (n == 0)
			return new ArrayList<>();

		// Map subset indices -> actual customer numbers.
		int[] nodes = new int[n];
		for (int i = 0; i < n; i++)
			nodes[i] = customers.get(i);

		// Held-Karp DP.
		// dp[mask][i]     = min cost of a path starting at start, visiting
		//                   exactly the customers in mask, ending at node i.
		// parent[mask][i] = predecessor index of i in that optimal path.
		final int INF = Integer.MAX_VALUE / 2;
		int[][] dp = new int[1 << n][n];
		int[][] parent = new int[1 << n][n];
		for (int[] row : dp)
			Arrays.fill(row, INF);
		for (int[] row : parent)
			Arrays.fill(row, -1);

		// Initialise single-node subsets: cost = start -> node i.
		for (int i = 0; i < n; i++)
			dp[1 << i][i] = dist[start][nodes[i]];

		// Fill the DP table in order of increasing mask popcount.
		for (int mask = 1; mask < (1 << n); mask++)
		{
			for (int i = 0; i < n; i++)
			{
				if ((mask & (1 << i)) == 0 || dp[mask][i] == INF)
					continue;

				for (int j = 0; j < n; j++)
				{
					if ((mask & (1 << j)) != 0)
						continue;

					int newMask = mask | (1 << j);
					int newCost = dp[mask][i] + dist[nodes[i]][nodes[j]];
					if (newCost < dp[newMask][j])
					{
						dp[newMask][j] = newCost;
						parent[newMask][j] = i;
					}
				}
			}
		}

		int fullMask = (1 << n) - 1;

		// Choose the best terminal node.
		int lastNode = -1;
		int minCost = INF;
		for (int i = 0; i < n; i++)
		{
			int cost = dp[fullMask][i];
			if (returnToDepot && cost < INF)
				cost += dist[nodes[i]][0];
			if (cost < minCost)
			{
				minCost = cost;
				lastNode = i;
			}
		}

		// Reconstruct the path by following parent pointers.
		List<Integer> result = new ArrayList<>();
		int mask = fullMask;
		int cur = lastNode;
		while (cur != -1)
		{
			result.add(0, nodes[cur]);
			int prev = parent[mask][cur];
			mask ^= (1 << cur);
			cur = prev;
		}

		return result;
	}

	// Finds the optimal ordering of intermediateCustomers on the path start -> ... -> terminal.
	// Returns the full sequence including the terminal at the end.
	public static List<Integer> determineOptimalSequenceWithTerminal(CVRPInstance instance,
			List<Integer> intermediateCustomers, int terminal, int start)
	{
		int[][] dist = instance.getDistances();
		int n = intermediateCustomers.size();

		// Trivial case: no intermediates, path is just start -> terminal.
		if (n == 0)
		{
			List<Integer> result = new ArrayList<>();
			result.add(terminal);
			return result;
		}

		int[] nodes = new int[n];
		for (int i = 0; i < n; i++)
			nodes[i] = intermediateCustomers.get(i);

		final int INF = Integer.MAX_VALUE / 2;
		int[][] dp = new int[1 << n][n];
		int[][] parent = new int[1 << n][n];
		for (int[] row : dp)
			Arrays.fill(row, INF);
		for (int[] row : parent)
			Arrays.fill(row, -1);

		for (int i = 0; i < n; i++)
			dp[1 << i][i] = dist[start][nodes[i]];

		for (int mask = 1; mask < (1 << n); mask++)
		{
			for (int i = 0; i < n; i++)
			{
				if ((mask & (1 << i)) == 0 || dp[mask][i] == INF)
					continue;

				for (int j = 0; j < n; j++)
				{
					if ((mask & (1 << j)) != 0)
						continue;

					int newMask = mask | (1 << j);
					int newCost = dp[mask][i] + dist[nodes[i]][nodes[j]];
					if (newCost < dp[newMask][j])
					{
						dp[newMask][j] = newCost;
						parent[newMask][j] = i;
					}
				}
			}
		}

		// Choose the intermediate node that minimises cost of reaching terminal.
		int fullMask = (1 << n) - 1;
		int lastNode = -1;
		int minCost = INF;
		for (int i = 0; i < n; i++)
		{
			if (dp[fullMask][i] == INF)
				continue;
			int cost = dp[fullMask][i] + dist[nodes[i]][terminal];
			if (cost < minCost)
			{
				minCost = cost;
				lastNode = i;
			}
		}

		// Reconstruct intermediates, then append terminal.
		List<Integer> result = new ArrayList<>();
		int mask = fullMask;
		int cur = lastNode;
		while (cur != -1)
		{
			result.add(0, nodes[cur]);
			int prev = parent[mask][cur];
			mask ^= (1 << cur);
			cur = prev;
		}
		result.add(terminal);
		return result;
	}

	public static int sequenceDistance(CVRPInstance instance, List<Integer> sequence, boolean returnToDepot, int start)
	{
		int[][] dist = instance.getDistances();
		int total = 0;

		// Sum dist[start -> first] + inter-customer distances.
		if (!sequence.isEmpty())
			total += dist[start][sequence.get(0)];
		for (int i = 0; i < sequence.size() - 1; i++)
			total += dist[sequence.get(i)][sequence.get(i + 1)];

		if (returnToDepot && !sequence.isEmpty())
			total += dist[sequence.get(sequence.size() - 1)][0];

		return total;
	}
}
