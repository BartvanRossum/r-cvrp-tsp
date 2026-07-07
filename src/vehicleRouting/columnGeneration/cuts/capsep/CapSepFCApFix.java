package vehicleRouting.columnGeneration.cuts.capsep;

import java.util.Arrays;

/**
 * Max-flow based capacity-cut separation. Equivalent to FCAPFIX.CPP in CVRPSEP.
 *
 * <p>
 * Operates on the <em>shrunken</em> graph produced by {@link CapSepCompress}:
 * {@code noOfCustomers} here refers to the number of customer supernodes,
 * {@code demand[i]} is the aggregate demand of supernode i, and {@code xMatrix}
 * is the shrunken flow matrix SMatrix (with {@code xMatrix[j][j]} = interior
 * flow of supernode j).
 * </p>
 *
 * <p>
 * Algorithm:
 * </p>
 * <ol>
 * <li>Build a directed max-flow network with {@code noOfCustomers + 2} nodes
 * (supernodes 1..n, source n+1, sink n+2).</li>
 * <li>Solve the initial max-flow and save the residual (warm-start state).</li>
 * <li>For each round and each seed supernode: fix the seed to the sink side,
 * optionally add a second sink-side node, fix history-derived nodes to the
 * source side, solve the max-flow from the warm-start state, and read the
 * sink-side cut set.</li>
 * <li>Optionally expand the cut set with {@code FCAPFIX_CheckExpandSet} to
 * force k(S) to increase.</li>
 * <li>Check violation: LHS = x(S:S) in original graph (adding diagonal terms
 * for multi-node supernodes); RHS = |S|_orig − k(S).</li>
 * </ol>
 *
 * <p>
 * The Edmonds-Karp (BFS augmenting path) algorithm replaces the closed- source
 * MXF library used in the C reference. Warm restart is achieved by saving and
 * restoring the residual-capacity matrix.
 * </p>
 *
 * <p>
 * All indices are 1-based.
 * </p>
 */
public class CapSepFCApFix
{

	private static final double EPS_VIOLATION = 0.01;

	// =========================================================================
	// Edmonds-Karp max-flow helpers
	// =========================================================================

	/**
	 * Runs one BFS augmenting-path step on the residual matrix.
	 *
	 * @param res        residual capacities [1..totalNodes][1..totalNodes]
	 * @param parent     work array [1..totalNodes]; parent[v] = predecessor on path
	 * @param queue      work array [1..totalNodes] for BFS queue
	 * @param source     source node (1-based)
	 * @param sink       sink node (1-based)
	 * @param totalNodes total number of nodes
	 * @return flow value of the augmenting path, or 0 if no path exists
	 */
	private static int augment(int[][] res, int[] parent, int[] queue, int source, int sink, int totalNodes)
	{
		Arrays.fill(parent, 1, totalNodes + 1, -1);
		parent[source] = source;
		int qHead = 0, qTail = 0;
		queue[qTail++] = source;

		bfs:
		while (qHead < qTail)
		{
			int u = queue[qHead++];
			for (int v = 1; v <= totalNodes; v++)
			{
				if (parent[v] < 0 && res[u][v] > 0)
				{
					parent[v] = u;
					if (v == sink)
						break bfs;
					queue[qTail++] = v;
				}
			}
		}
		if (parent[sink] < 0)
			return 0;

		// Bottleneck capacity along the path.
		int flow = Integer.MAX_VALUE;
		for (int v = sink; v != source; v = parent[v])
		{
			flow = Math.min(flow, res[parent[v]][v]);
		}
		// Update residuals.
		for (int v = sink; v != source; v = parent[v])
		{
			res[parent[v]][v] -= flow;
			res[v][parent[v]] += flow;
		}
		return flow;
	}

	/**
	 * Runs Edmonds-Karp until no augmenting path remains (or max-flow is found).
	 * Updates {@code res} in place.
	 */
	private static void runMaxFlow(int[][] res, int source, int sink, int totalNodes, int[] parent, int[] queue)
	{
		while (augment(res, parent, queue, source, sink, totalNodes) > 0)
		{
			/* empty */ }
	}

	/**
	 * BFS from {@code source} in the residual graph to determine which nodes are
	 * reachable (i.e., belong to the source side of the min-cut).
	 *
	 * @return boolean array [1..totalNodes]; {@code reached[v]} = true iff v is on
	 *         the source side
	 */
	private static boolean[] sourceReachable(int[][] res, int source, int totalNodes, int[] queue)
	{
		boolean[] reached = new boolean[totalNodes + 1];
		reached[source] = true;
		int qHead = 0, qTail = 0;
		queue[qTail++] = source;
		while (qHead < qTail)
		{
			int u = queue[qHead++];
			for (int v = 1; v <= totalNodes; v++)
			{
				if (!reached[v] && res[u][v] > 0)
				{
					reached[v] = true;
					queue[qTail++] = v;
				}
			}
		}
		return reached;
	}

	// =========================================================================
	// FCAPFIX_CompSourceFixNodes
	// =========================================================================

	/**
	 * Determines which nodes to fix on the source side (outside the cut) for the
	 * given seed, based on previously found cuts stored in the history.
	 *
	 * <p>
	 * Equivalent to FCAPFIX_CompSourceFixNodes.
	 * </p>
	 *
	 * @param historyRPtr     history of found cuts; forwList(i) = supernode list,
	 *                        backNeighbor(i,1) = original seed of cut i
	 * @param historyListSize number of cuts recorded so far (1-based)
	 * @param seedNode        current seed supernode
	 * @param noOfCustomers   number of supernodes
	 * @param list            output: list[1..listSizeOut[0]] = nodes to fix on
	 *                        source side
	 * @param listSizeOut     output: listSizeOut[0] = number of such nodes
	 */
	private static void compSourceFixNodes(CapSepGraph historyRPtr, int historyListSize, int seedNode,
			int noOfCustomers, int[] list, int[] listSizeOut)
	{
		boolean[] fixedOutNode = new boolean[noOfCustomers + 1];

		for (int setNr = historyListSize; setNr >= 1; setNr--)
		{
			int cfn = historyRPtr.forwCount(setNr);
			if (cfn == 1)
				continue; // singleton cuts are skipped

			// Check if seedNode is in this historical cut set.
			boolean seedInSet = false;
			for (int i = 1; i <= cfn; i++)
			{
				if (historyRPtr.forwNeighbor(setNr, i) == seedNode)
				{
					seedInSet = true;
					break;
				}
			}

			boolean coveredSet;
			if (seedInSet)
			{
				coveredSet = false;
				for (int i = 1; i <= cfn; i++)
				{
					int j = historyRPtr.forwNeighbor(setNr, i);
					if (fixedOutNode[j])
					{
						coveredSet = true;
						break;
					}
				}
			}
			else
			{
				coveredSet = true;
			}

			if (!coveredSet)
			{
				// Fix the original seed of this historical cut on the source side.
				int j = historyRPtr.backNeighbor(setNr, 1); // original seed
				if (j != seedNode)
				{
					fixedOutNode[j] = true;
				}
				else
				{
					// Original seed is the current seed: fix any other node in the set.
					for (int i = 1; i <= cfn; i++)
					{
						j = historyRPtr.forwNeighbor(setNr, i);
						if (j != seedNode)
						{
							fixedOutNode[j] = true;
							break;
						}
					}
				}
			}
		}

		listSizeOut[0] = 0;
		for (int i = 1; i <= noOfCustomers; i++)
		{
			if (fixedOutNode[i])
				list[++listSizeOut[0]] = i;
		}
	}

	// =========================================================================
	// FCAPFIX_CompAddSinkNode
	// =========================================================================

	/**
	 * For a seed node whose last solve yielded only a one-node cut, finds the
	 * highest-flow neighbor to add to the sink side to break the tie.
	 *
	 * <p>
	 * Equivalent to FCAPFIX_CompAddSinkNode.
	 * </p>
	 *
	 * @return the supernode to add to the sink side, or 0 if none
	 */
	private static int compAddSinkNode(CapSepGraph supportPtr, int noOfCustomers, double[][] xMatrix, int seedNode,
			int[] sourceList, int sourceListSize)
	{
		boolean[] onSourceSide = new boolean[noOfCustomers + 1];
		for (int i = 1; i <= sourceListSize; i++)
			onSourceSide[sourceList[i]] = true;

		int best = 0;
		double bestScore = 0.0;
		int cfn = supportPtr.forwCount(seedNode);
		for (int i = 1; i <= cfn; i++)
		{
			int j = supportPtr.forwNeighbor(seedNode, i);
			if (j > noOfCustomers)
				continue;
			if (!onSourceSide[j])
			{
				double score = xMatrix[seedNode][j];
				if (best == 0 || score > bestScore)
				{
					best = j;
					bestScore = score;
				}
			}
		}
		return best;
	}

	// =========================================================================
	// FCAPFIX_CheckExpandSet
	// =========================================================================

	/**
	 * Finds the best out-of-set supernode to add to the current cut set such that
	 * k(S) increases (i.e., the added node's demand pushes demand past the current
	 * capacity threshold).
	 *
	 * <p>
	 * Equivalent to FCAPFIX_CheckExpandSet. Note: {@code AddSecondNode} is always 0
	 * in the C reference; this method returns only {@code AddNode}.
	 * </p>
	 *
	 * @param nodeInSet [1..n] membership indicator
	 * @param fixedOut  [1..n] true = node fixed on source side (not addable)
	 * @return best node to add, or 0 if none
	 */
	private static int checkExpandSet(CapSepGraph supportPtr, int noOfCustomers, int[] demand, int cap,
			double[][] xMatrix, boolean[] nodeInSet, boolean[] fixedOut)
	{
		// XNodeSum[j] = sum of xMatrix[i][j] for in-set i (connectivity score).
		double[] xNodeSum = new double[noOfCustomers + 1];

		int demandSum = 0;
		for (int i = 1; i <= noOfCustomers; i++)
		{
			if (nodeInSet[i])
				demandSum += demand[i];
		}

		// Accumulate xNodeSum using undirected edges (iterate each pair once via j >
		// i).
		for (int i = 1; i <= noOfCustomers; i++)
		{
			int cfi = supportPtr.forwCount(i);
			for (int k = 1; k <= cfi; k++)
			{
				int j = supportPtr.forwNeighbor(i, k);
				if (j <= noOfCustomers && j > i)
				{
					double xVal = xMatrix[i][j];
					if (nodeInSet[i])
						xNodeSum[j] += xVal;
					if (nodeInSet[j])
						xNodeSum[i] += xVal;
				}
			}
		}

		// Capacity threshold for current set.
		int capSum = cap;
		int minV = 1;
		while (capSum < demandSum)
		{
			capSum += cap;
			minV++;
		}

		// Find best neighbor of any in-set node that pushes demand over capSum.
		int bestAddNode = 0;
		double bestXScore = 0.0;

		for (int i = 1; i <= noOfCustomers; i++)
		{
			if (!nodeInSet[i])
				continue;
			int cfi = supportPtr.forwCount(i);
			for (int k = 1; k <= cfi; k++)
			{
				int j = supportPtr.forwNeighbor(i, k);
				if (j <= noOfCustomers && !nodeInSet[j] && !fixedOut[j] && (demand[j] + demandSum) > capSum)
				{
					if (bestAddNode == 0 || xNodeSum[j] > bestXScore)
					{
						bestAddNode = j;
						bestXScore = xNodeSum[j];
					}
				}
			}
		}
		return bestAddNode;
	}

	// =========================================================================
	// FCAPFIX_ComputeCuts (public entry point)
	// =========================================================================

	/**
	 * Generates violated capacity cuts using the max-flow fixing heuristic.
	 *
	 * <p>
	 * Equivalent to FCAPFIX_ComputeCuts. Operates on the shrunken graph; all
	 * "customer" indices here are supernode indices.
	 * </p>
	 *
	 * @param supportPtr    shrunken adjacency graph (SAdjRPtr); the depot supernode
	 *                      has index noOfCustomers+1
	 * @param noOfCustomers number of customer supernodes (ShrunkGraphCustNodes)
	 * @param demand        1-indexed aggregate demand per supernode [1..n]
	 * @param cap           vehicle capacity (original, not scaled)
	 * @param superNodeSize 1-indexed number of original customers in supernode
	 *                      [1..n]; superNodeSize[j] > 1 means xMatrix[j][j] > 0
	 * @param xMatrix       shrunken flow matrix (SMatrix) [1..n+1][1..n+1];
	 *                      diagonal xMatrix[j][j] = interior flow of supernode j
	 * @param maxCuts       maximum number of cuts to generate
	 * @param maxRounds     number of rounds (typically 3)
	 * @param resultRPtr    output: resultRPtr.forwArray(i) / forwCount(i) =
	 *                      supernode list of cut i (1-based cut index)
	 * @return number of cuts generated
	 */
	public static int computeCuts(CapSepGraph supportPtr, int noOfCustomers, int[] demand, int cap, int[] superNodeSize,
			double[][] xMatrix, int maxCuts, int maxRounds, CapSepGraph resultRPtr)
	{

		int noOfGeneratedCuts = 0;

		// Determine VCAP (= cap scaled up to >= 1000) and FlowScale.
		int vCap = cap;
		int flowScale = 1;
		while (vCap < 1000)
		{
			vCap *= 10;
			flowScale *= 10;
		}

		// Depot index in the shrunken graph.
		int depotIdx = noOfCustomers + 1;
		// Max-flow network nodes: supernodes 1..n, source n+1, sink n+2.
		int source = noOfCustomers + 1;
		int sink = noOfCustomers + 2;
		int totalNodes = noOfCustomers + 2;

		// ------------------------------------------------------------------
		// Arc capacities for the max-flow network.
		// ------------------------------------------------------------------
		int[] arcCapFromSource = new int[noOfCustomers + 1]; // [1..n]
		int[] arcCapToSink = new int[noOfCustomers + 1]; // [1..n]

		// Depot-edge flows for each supernode k (from the shrunken adjacency).
		double[] depotEdgeXVal = new double[noOfCustomers + 1];
		int cfDepot = supportPtr.forwCount(depotIdx);
		for (int j = 1; j <= cfDepot; j++)
		{
			int k = supportPtr.forwNeighbor(depotIdx, j);
			if (k <= noOfCustomers)
				depotEdgeXVal[k] = xMatrix[depotIdx][k];
		}

		int infCap = 0;

		// Build initial residual capacity matrix.
		int[][] res = new int[totalNodes + 1][totalNodes + 1];

		// Customer-customer arcs (undirected, scaled and rounded up).
		for (int i = 1; i <= noOfCustomers; i++)
		{
			int cfi = supportPtr.forwCount(i);
			for (int j = 1; j <= cfi; j++)
			{
				int k = supportPtr.forwNeighbor(i, j);
				if (k <= noOfCustomers && k > i)
				{
					int arcCap = (int) (xMatrix[i][k] * vCap + 1); // round up
					res[i][k] += arcCap;
					res[k][i] += arcCap;
				}
			}
		}

		// Source-to-customer and customer-to-sink arcs.
		for (int k = 1; k <= noOfCustomers; k++)
		{
			double xVal = depotEdgeXVal[k] * vCap + 1.0 - 2.0 * flowScale * demand[k];
			if (xVal > 0)
			{
				int arcCap = (int) xVal;
				res[source][k] += arcCap;
				// res[k][sink] stays 0 (initialized to 0)
				arcCapFromSource[k] = arcCap;
				arcCapToSink[k] = 0;
				infCap += arcCap;
			}
			else
			{
				int arcCap = (int) (-xVal);
				res[k][sink] += arcCap;
				// res[source][k] stays 0
				arcCapToSink[k] = arcCap;
				arcCapFromSource[k] = 0;
			}
		}
		infCap += (2 * vCap);

		// Working arrays for Edmonds-Karp.
		int[] parent = new int[totalNodes + 1];
		int[] queue = new int[totalNodes + 1];

		// ------------------------------------------------------------------
		// Initial max-flow solve (mode = 1: from scratch).
		// ------------------------------------------------------------------
		runMaxFlow(res, source, sink, totalNodes, parent, queue);

		// Save the initial max-flow residual (warm-start base state).
		int[][] baseRes = new int[totalNodes + 1][totalNodes + 1];
		for (int i = 1; i <= totalNodes; i++)
		{
			System.arraycopy(res[i], 1, baseRes[i], 1, totalNodes);
		}

		// ------------------------------------------------------------------
		// Per-seed working arrays.
		// ------------------------------------------------------------------
		int maxHistoryListSize = maxRounds * noOfCustomers;
		CapSepGraph historyRPtr = new CapSepGraph(maxHistoryListSize);
		int historyListSize = 0;

		boolean[] useSeed = new boolean[noOfCustomers + 1];
		boolean[] oneNodeCut = new boolean[noOfCustomers + 1];
		Arrays.fill(useSeed, 1, noOfCustomers + 1, true);

		boolean[] nodeInSet = new boolean[noOfCustomers + 1];
		boolean[] fixedOut = new boolean[noOfCustomers + 1];

		int[] fixedToSourceList = new int[noOfCustomers + 1];
		int[] fixedToSinkList = new int[noOfCustomers + 2]; // up to 2 entries
		int[] nodeList = new int[noOfCustomers + 2]; // +1 for sink in flow

		int[] listSizeOut = new int[1];

		// ------------------------------------------------------------------
		// Main loop: rounds × seeds.
		// ------------------------------------------------------------------
		outer:
		for (int roundNr = 1; roundNr <= maxRounds; roundNr++)
		{
			if (noOfGeneratedCuts >= maxCuts && roundNr > 1)
				break;

			for (int seedNode = 1; seedNode <= noOfCustomers; seedNode++)
			{
				if (!useSeed[seedNode])
					continue;

				// Determine source-side fixed nodes from history.
				compSourceFixNodes(historyRPtr, historyListSize, seedNode, noOfCustomers, fixedToSourceList,
						listSizeOut);
				int fixedToSourceListSize = listSizeOut[0];

				// Fix seed to sink side (and possibly a second node).
				fixedToSinkList[1] = seedNode;
				int fixedToSinkListSize = 1;

				if (oneNodeCut[seedNode])
				{
					int addSeedNode = compAddSinkNode(supportPtr, noOfCustomers, xMatrix, seedNode, fixedToSourceList,
							fixedToSourceListSize);
					if (addSeedNode > 0)
					{
						fixedToSinkList[2] = addSeedNode;
						fixedToSinkListSize = 2;
					}
					else
					{
						useSeed[seedNode] = false;
						continue;
					}
				}

				// ----------------------------------------------------------
				// FCAPFIX_SolveMaxFlow: restore base flow, fix nodes, solve.
				// ----------------------------------------------------------

				// Restore residual to base state.
				for (int i = 1; i <= totalNodes; i++)
				{
					System.arraycopy(baseRes[i], 1, res[i], 1, totalNodes);
				}

				// Fix source-side nodes: increase cap(source → k) to infCap.
				for (int i = 1; i <= fixedToSourceListSize; i++)
				{
					int k = fixedToSourceList[i];
					res[source][k] += infCap - arcCapFromSource[k];
				}
				// Fix sink-side nodes: increase cap(k → sink) to infCap.
				for (int i = 1; i <= fixedToSinkListSize; i++)
				{
					int k = fixedToSinkList[i];
					res[k][sink] += infCap - arcCapToSink[k];
				}

				// Warm-restart max-flow (augment from current state).
				runMaxFlow(res, source, sink, totalNodes, parent, queue);

				// Read sink side (nodes not reachable from source in residual).
				boolean[] onSourceSide = sourceReachable(res, source, totalNodes, queue);
				int nodeListSize = 0;
				for (int k = 1; k <= noOfCustomers; k++)
				{
					if (!onSourceSide[k])
					{ // on sink side = in cut set S
						nodeList[++nodeListSize] = k;
					}
				}

				// Record history.
				Arrays.fill(nodeInSet, 1, noOfCustomers + 1, false);
				for (int i = 1; i <= nodeListSize; i++)
					nodeInSet[nodeList[i]] = true;
				Arrays.fill(fixedOut, 1, noOfCustomers + 1, false);
				for (int i = 1; i <= fixedToSourceListSize; i++)
				{
					fixedOut[fixedToSourceList[i]] = true;
				}

				int demandSum = 0;
				for (int i = 1; i <= nodeListSize; i++)
					demandSum += demand[nodeList[i]];

				int capSum = cap, minV = 1;
				while (capSum < demandSum)
				{
					capSum += cap;
					minV++;
				}

				int maxDemand = 0;
				for (int i = 1; i <= noOfCustomers; i++)
				{
					if (!nodeInSet[i] && demand[i] > maxDemand)
						maxDemand = demand[i];
				}

				// ----------------------------------------------------------
				// FCAPFIX_CheckExpandSet loop: try to push k(S) higher.
				// ----------------------------------------------------------
				if ((maxDemand + demandSum) > capSum)
				{
					int addNode;
					do
					{
						addNode = checkExpandSet(supportPtr, noOfCustomers, demand, cap, xMatrix, nodeInSet, fixedOut);
						if (addNode > 0)
						{
							nodeList[++nodeListSize] = addNode;
							nodeInSet[addNode] = true;
						}
					}
					while (addNode > 0);
				}

				// One-node cut flag.
				if (nodeListSize == 1)
					oneNodeCut[seedNode] = true;

				// Store in history.
				historyListSize++;
				historyRPtr.setForwList(nodeList, historyListSize, nodeListSize);
				// BAL[1] = original seed of this cut (used by CompSourceFixNodes).
				int[] seedListTmp = new int[]
				{ 0, fixedToSinkList[1] }; // 1-indexed
				historyRPtr.setBackwList(seedListTmp, historyListSize, 1);

				// ----------------------------------------------------------
				// Check violation: compute x(S:S) in the original graph.
				// x(S:S) = inter-supernode flows within S
				// + interior flows of multi-node supernodes in S.
				// ----------------------------------------------------------
				double xInSet = CapSepCutUtils.compXSumInSet(supportPtr, noOfCustomers, null, nodeList, nodeListSize,
						xMatrix);

				int origNodes = 0;
				demandSum = 0;
				for (int i = 1; i <= nodeListSize; i++)
				{
					int j = nodeList[i];
					origNodes += superNodeSize[j];
					demandSum += demand[j];
					if (superNodeSize[j] > 1)
						xInSet += xMatrix[j][j]; // interior flow
				}

				capSum = cap;
				minV = 1;
				while (capSum < demandSum)
				{
					capSum += cap;
					minV++;
				}

				double lhs = xInSet;
				double rhs = origNodes - minV;
				double violation = lhs - rhs;

				if (violation >= EPS_VIOLATION)
				{
					noOfGeneratedCuts++;
					resultRPtr.setForwList(nodeList, noOfGeneratedCuts, nodeListSize);
					if (noOfGeneratedCuts >= maxCuts)
						break outer;
				}
			}
		}
		return noOfGeneratedCuts;
	}
}
