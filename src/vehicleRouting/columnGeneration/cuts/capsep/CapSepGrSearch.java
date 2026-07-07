package vehicleRouting.columnGeneration.cuts.capsep;

import java.util.Arrays;

/**
 * Greedy capacity-cut separation routines. Equivalent to GRSEARCH.CPP in
 * CVRPSEP.
 *
 * <p>
 * Contains two public entry points:
 * </p>
 * <ol>
 * <li>{@link #capCuts} — greedy construction of violated capacity cuts starting
 * from each supernode as a seed, using antiset pruning to avoid re-exploring
 * previously covered regions.</li>
 * <li>{@link #addDropCapsOnGS} — improves existing cuts from
 * {@code CMPExistingCuts} by mapping them to the shrunken graph and applying
 * add/drop/exchange local search.</li>
 * </ol>
 *
 * <p>
 * All "customer" indices in these routines refer to <em>supernode</em> indices
 * (the shrunken graph). Original-customer expansion happens in
 * {@link CapacityCutSeparation}.
 * </p>
 *
 * <p>
 * All indices are 1-based.
 * </p>
 */
public class CapSepGrSearch
{

	private static final double EPS_VIOLATION = 0.01;

	// =========================================================================
	// Private helpers
	// =========================================================================

	/**
	 * Swaps positions {@code s} and {@code t} in the Node array and keeps the Pos
	 * (inverse-permutation) array consistent. Equivalent to
	 * GRSEARCH_SwapNodesInPos.
	 *
	 * @param node Node[1..n]: node at position i
	 * @param pos  Pos[1..n]: position of node i (Pos[Node[i]] = i)
	 * @param s    position index to swap
	 * @param t    position index to swap
	 */
	private static void swapNodesInPos(int[] node, int[] pos, int s, int t)
	{
		if (s == t)
			return;
		int tmp = node[s];
		node[s] = node[t];
		node[t] = tmp;
		tmp = pos[node[s]];
		pos[node[s]] = pos[node[t]];
		pos[node[t]] = tmp;
	}

	/**
	 * Stores set {@code list[1..setSize]} in {@code rPtr} at index {@code index},
	 * with either a cumulative or a total-sum backward list.
	 *
	 * <p>
	 * When {@code addFullSumList = true}: BAL[i] = sum of List[1..i] (cumulative);
	 * backCount = setSize.
	 * </p>
	 * <p>
	 * When {@code addFullSumList = false}: BAL[1] = total sum of list; backCount =
	 * 1.
	 * </p>
	 *
	 * <p>
	 * Equivalent to GRSEARCH_AddSet.
	 * </p>
	 */
	private static void addSet(CapSepGraph rPtr, int index, int setSize, int[] list, boolean addFullSumList)
	{
		rPtr.setForwList(list, index, setSize);
		int[] sumList = new int[setSize + 1]; // 1-indexed
		sumList[1] = list[1];
		if (addFullSumList)
		{
			for (int i = 2; i <= setSize; i++)
				sumList[i] = sumList[i - 1] + list[i];
			rPtr.setBackwList(sumList, index, setSize);
		}
		else
		{
			for (int i = 2; i <= setSize; i++)
				sumList[1] += list[i];
			rPtr.setBackwList(sumList, index, 1);
		}
	}

	/**
	 * Labels candidate nodes as infeasible if including them would necessarily
	 * produce a set that is a sub-/superset of a known antiset.
	 *
	 * <p>
	 * The current partial set is {@code Node[1..minCandIdx-1]} with node sum
	 * {@code nodeSum}. Candidates are at positions {@code minCandIdx..maxCandIdx}.
	 * </p>
	 *
	 * <p>
	 * Equivalent to GRSEARCH_GetInfeasExt.
	 * </p>
	 *
	 * @param pos           Pos[1..n]: position of node i
	 * @param minCandIdx    first candidate position (= current set size + 1)
	 * @param maxCandIdx    last candidate position
	 * @param noOfCustomers number of supernodes n
	 * @param nodeSum       sum of node indices in the current partial set
	 * @param rPtr          antiset graph
	 * @param rPtrSize      number of antisets
	 * @param nodeLabel     label array; nodeLabel[v] = label means v is infeasible
	 * @param label         current label value
	 * @param callBack      callBack[0]: set to true if re-checking may be needed
	 */
	private static void getInfeasExt(int[] pos, int minCandIdx, int maxCandIdx, int noOfCustomers, int nodeSum,
			CapSepGraph rPtr, int rPtrSize, int[] nodeLabel, int label, boolean[] callBack)
	{
		callBack[0] = false;

		for (int setNr = 1; setNr <= rPtrSize; setNr++)
		{
			int cfn = rPtr.forwCount(setNr);
			int cbn = rPtr.backCount(setNr);

			if (cfn < minCandIdx)
				continue; // antiset too small

			int sum;
			if (cbn < cfn)
			{
				// Only the full set (of size cfn) is prohibited; BAL[1] = total sum.
				if (cfn != minCandIdx)
				{
					if (cfn > minCandIdx)
						callBack[0] = true;
					continue;
				}
				sum = rPtr.backNeighbor(setNr, 1);
			}
			else
			{
				// Cumulative sums stored; BAL[minCandIdx] = sum of first minCandIdx elements.
				sum = rPtr.backNeighbor(setNr, minCandIdx);
				// If total sum > nodeSum, may need to re-check later.
				if (rPtr.backNeighbor(setNr, cbn) > nodeSum)
					callBack[0] = true;
			}

			int diffNode = sum - nodeSum;
			if (diffNode < 1 || diffNode > noOfCustomers)
				continue;
			if (pos[diffNode] < minCandIdx || pos[diffNode] > maxCandIdx)
				continue;

			// Check that all first minCandIdx antiset members are in the partial
			// set OR are exactly diffNode (= the lone candidate not yet in the set).
			int notMatchingNode = 0;
			boolean invalid = false;
			for (int i = 1; i <= minCandIdx; i++)
			{
				int j = rPtr.forwNeighbor(setNr, i);
				if (pos[j] > maxCandIdx)
				{
					// j is neither in the partial set nor a candidate — antiset cannot apply.
					notMatchingNode = 0;
					invalid = true;
					break;
				}
				else
					if (pos[j] >= minCandIdx)
					{
						// j is a candidate (not yet included).
						if (notMatchingNode == 0)
						{
							notMatchingNode = j;
						}
						else
						{
							// Two candidates are not in the partial set — antiset cannot apply.
							notMatchingNode = 0;
							invalid = true;
							break;
						}
					}
				// pos[j] < minCandIdx → j is already in the partial set (ok).
			}
			if (!invalid && notMatchingNode > 0)
			{
				nodeLabel[notMatchingNode] = label;
			}
		}
	}

	/**
	 * Marks nodes that appear as singleton antisets as invalid source nodes.
	 * Equivalent to GRSEARCH_GetNotOKSources.
	 */
	private static void getNotOKSources(CapSepGraph rPtr, int rPtrSize, boolean[] okSource)
	{
		for (int setNr = 1; setNr <= rPtrSize; setNr++)
		{
			if (rPtr.forwCount(setNr) == 1)
			{
				int j = rPtr.forwNeighbor(setNr, 1);
				okSource[j] = false;
			}
		}
	}

	/**
	 * Checks whether the set identified by the given label, size, and node-sum
	 * already exists in {@code rPtr[1..rPtrSize]}.
	 *
	 * <p>
	 * Uses BAL[1] (total node sum) as a fast pre-filter; confirms with full
	 * membership check via node labels.
	 * </p>
	 *
	 * <p>
	 * Equivalent to GRSEARCH_CheckForExistingSet.
	 * </p>
	 */
	private static boolean checkForExistingSet(CapSepGraph rPtr, int rPtrSize, int[] nodeLabel, int label, int nodeSum,
			int nodeSetSize)
	{
		for (int i = 1; i <= rPtrSize; i++)
		{
			if (rPtr.forwCount(i) != nodeSetSize)
				continue;
			if (rPtr.backNeighbor(i, 1) != nodeSum)
				continue; // BAL[1] = total sum
			boolean match = true;
			for (int j = 1; j <= nodeSetSize; j++)
			{
				if (nodeLabel[rPtr.forwNeighbor(i, j)] != label)
				{
					match = false;
					break;
				}
			}
			if (match)
				return true;
		}
		return false;
	}

	// =========================================================================
	// GRSEARCH_CapCuts (public entry point)
	// =========================================================================

	/**
	 * Greedy construction of violated capacity cuts on the shrunken graph.
	 *
	 * <p>
	 * For each source supernode (from n down to 1), expands a set greedily by
	 * repeatedly adding the neighbor that maximises the cut violation, using
	 * antiset pruning to skip extensions that would reproduce a previously explored
	 * set. Stores violated cuts (and the full explored set as an antiset) in the
	 * output graphs.
	 * </p>
	 *
	 * <p>
	 * Equivalent to GRSEARCH_CapCuts.
	 * </p>
	 *
	 * @param supportPtr            shrunken adjacency (SAdjRPtr)
	 * @param noOfCustomers         number of customer supernodes
	 *                              (ShrunkGraphCustNodes)
	 * @param demand                1-indexed SuperDemand [1..n]
	 * @param cap                   vehicle capacity
	 * @param superNodeSize         1-indexed supernode sizes [1..n]
	 * @param xInSuperNode          1-indexed interior flow per supernode [1..n]
	 * @param xMatrix               shrunken flow matrix SMatrix [1..n+1][1..n+1]
	 * @param generatedSetsOut      in/out [0]: current count of identified cuts
	 * @param generatedAntiSetsOut  in/out [0]: current count of antisets
	 * @param setsRPtr              output: sets (cuts) are appended here
	 * @param antiSetsRPtr          output: antisets are appended here
	 * @param maxTotalGeneratedSets maximum number of cuts before stopping
	 */
	public static void capCuts(CapSepGraph supportPtr, int noOfCustomers, int[] demand, int cap, int[] superNodeSize,
			double[] xInSuperNode, double[][] xMatrix, int[] generatedSetsOut, int[] generatedAntiSetsOut,
			CapSepGraph setsRPtr, CapSepGraph antiSetsRPtr, int maxTotalGeneratedSets)
	{

		boolean[] okSource = new boolean[noOfCustomers + 1];
		int[] node = new int[noOfCustomers + 1]; // node[i] = node at position i
		int[] pos = new int[noOfCustomers + 1]; // pos[v] = position of node v
		int[] nodeLabel = new int[noOfCustomers + 1];
		double[] xVal = new double[noOfCustomers + 1]; // xVal[v] = flow from set to v

		for (int i = 1; i <= noOfCustomers; i++)
		{
			node[i] = i;
			pos[i] = i;
			nodeLabel[i] = 0;
			okSource[i] = true;
		}
		int label = 0;

		getNotOKSources(antiSetsRPtr, generatedAntiSetsOut[0], okSource);

		for (int source = noOfCustomers; source >= 1; source--)
		{
			if (!okSource[source])
				continue;
			if (generatedSetsOut[0] >= maxTotalGeneratedSets)
				break;

			int prevSize = -1;
			int prevVehicles = -1;
			int cutsFromSource = 0;

			// Put source in position 1.
			swapNodesInPos(node, pos, 1, pos[source]);

			// Initialise set with {source}.
			int origNodesInSet = superNodeSize[source];
			int demandSum = demand[source];
			double xInSet = xInSuperNode[source];
			int capSum = cap, minV = 1;
			while (capSum < demandSum)
			{
				capSum += cap;
				minV++;
			}

			// Check if the singleton {source} is already violated.
			if (xInSet - (origNodesInSet - minV) >= EPS_VIOLATION)
			{
				generatedSetsOut[0]++;
				addSet(setsRPtr, generatedSetsOut[0], 1, node, true);
				// Add antiset and continue to next source.
				generatedAntiSetsOut[0]++;
				addSet(antiSetsRPtr, generatedAntiSetsOut[0], 1, node, true);
				continue;
			}

			int nextVMinDemand = capSum + 1 - demandSum;

			int minCandIdx = 2;
			int maxCandIdx = 1; // max < min → no candidates initially

			// Build candidate list from source's neighbors.
			for (int j = 1; j <= supportPtr.forwCount(source); j++)
			{
				int k = supportPtr.forwNeighbor(source, j);
				if (k <= noOfCustomers)
				{
					maxCandIdx++;
					swapNodesInPos(node, pos, maxCandIdx, pos[k]);
					xVal[k] = xMatrix[source][k];
				}
			}

			int nodeSum = source;
			boolean[] callBackAntiSets =
			{ true };
			int bestNode;

			do
			{
				label++;
				if (callBackAntiSets[0])
				{
					getInfeasExt(pos, minCandIdx, maxCandIdx, noOfCustomers, nodeSum, antiSetsRPtr,
							generatedAntiSetsOut[0], nodeLabel, label, callBackAntiSets);
				}

				bestNode = 0;
				double bestXVal = 0.0;

				for (int i = minCandIdx; i <= maxCandIdx; i++)
				{
					int v = node[i];
					if (nodeLabel[v] == label)
						continue; // infeasible

					// Tentative LHS and RHS if v is included.
					double tentLHS = xInSet + xVal[v] + xInSuperNode[v];
					int tentMinV = minV;
					int tentRHSNodes = origNodesInSet + superNodeSize[v];
					if (demand[v] >= nextVMinDemand)
					{
						int rCapSum = capSum;
						while (rCapSum < demandSum + demand[v])
						{
							rCapSum += cap;
							tentMinV++;
						}
					}
					double tentRHS = tentRHSNodes - tentMinV;
					double score = tentLHS - tentRHS;

					// Prefer singleton supernodes (SuperNodeSize==1) with score >= 0.01.
					if (score >= 0.01 && superNodeSize[v] == 1)
					{
						if (bestNode == 0 || superNodeSize[bestNode] > 1 || score > bestXVal)
						{
							bestNode = v;
							bestXVal = score;
						}
					}
					else
						if (bestNode == 0 || score > bestXVal)
						{
							bestNode = v;
							bestXVal = score;
						}
				}

				if (bestNode > 0)
				{
					// Include bestNode.
					swapNodesInPos(node, pos, minCandIdx, pos[bestNode]);
					minCandIdx++;
					nodeSum += bestNode;
					origNodesInSet += superNodeSize[bestNode];
					demandSum += demand[bestNode];
					xInSet += xVal[bestNode] + xInSuperNode[bestNode];
					while (capSum < demandSum)
					{
						capSum += cap;
						minV++;
					}
					nextVMinDemand = capSum + 1 - demandSum;

					double lhs = xInSet;
					double rhs = origNodesInSet - minV;

					if (lhs - rhs >= EPS_VIOLATION)
					{
						// Dominance: replace previous cut if same source added exactly
						// one more singleton node and exactly one more vehicle.
						if (superNodeSize[bestNode] == 1 && (minCandIdx - 1 == prevSize + 1)
								&& (minV == prevVehicles + 1))
						{
							generatedSetsOut[0]--;
							cutsFromSource--;
						}
						generatedSetsOut[0]++;
						addSet(setsRPtr, generatedSetsOut[0], minCandIdx - 1, node, true);
						cutsFromSource++;
						prevSize = minCandIdx - 1;
						prevVehicles = minV;
					}

					// Expand candidate set with bestNode's neighbors.
					for (int j = 1; j <= supportPtr.forwCount(bestNode); j++)
					{
						int k = supportPtr.forwNeighbor(bestNode, j);
						if (k > noOfCustomers)
							continue;
						if (pos[k] > maxCandIdx)
						{
							// New candidate.
							xVal[k] = xMatrix[bestNode][k];
							maxCandIdx++;
							swapNodesInPos(node, pos, maxCandIdx, pos[k]);
						}
						else
							if (pos[k] >= minCandIdx)
							{
								// Existing candidate: accumulate flow.
								xVal[k] += xMatrix[bestNode][k];
							}
					}
				}
			}
			while (minCandIdx <= maxCandIdx && bestNode > 0 && generatedSetsOut[0] < maxTotalGeneratedSets);

			// Record final explored set as antiset.
			generatedAntiSetsOut[0]++;
			addSet(antiSetsRPtr, generatedAntiSetsOut[0], minCandIdx - 1, node, true);
		}
	}

	// =========================================================================
	// GRSEARCH_AddDropCapsOnGS (public entry point)
	// =========================================================================

	/**
	 * Add/drop/exchange local search starting from existing cuts.
	 *
	 * <p>
	 * For each cut in {@code cmpSourceCutList}, maps its original customers to
	 * supernodes, removes weakly connected supernodes (XNodeSum ≤ 0.99) without
	 * changing MinV, then iterates add/drop/exchange operations until no further
	 * improvement. If the resulting set is violated and not already in
	 * {@code setsRPtr}, appends it.
	 * </p>
	 *
	 * <p>
	 * Equivalent to GRSEARCH_AddDropCapsOnGS.
	 * </p>
	 *
	 * @param supportPtr            shrunken adjacency (SAdjRPtr)
	 * @param noOfCustomers         original customer count (for OrigCustLabel
	 *                              array)
	 * @param shrunkGraphCustNodes  number of customer supernodes
	 * @param superDemand           1-indexed aggregate demand per supernode
	 * @param cap                   vehicle capacity
	 * @param superNodeSize         1-indexed sizes of supernodes
	 * @param xInSuperNode          1-indexed interior flow per supernode
	 * @param superNodesRPtr        supernode-to-customer mapping
	 * @param sMatrix               full shrunken flow matrix [1..K+1][1..K+1]
	 * @param eps                   violation threshold (EpsViolation = 0.01)
	 * @param cmpSourceCutList      existing cuts in original-customer format
	 * @param noOfGeneratedSetsOut  in/out [0]: current count in setsRPtr; caller
	 *                              initialises to CutsBeforeLastProc
	 * @param maxTotalGeneratedSets upper limit on total cuts
	 * @param setsRPtr              in/out: existing cuts plus new ones appended
	 */
	public static void addDropCapsOnGS(CapSepGraph supportPtr, int noOfCustomers, int shrunkGraphCustNodes,
			int[] superDemand, int cap, int[] superNodeSize, double[] xInSuperNode, CapSepGraph superNodesRPtr,
			double[][] sMatrix, double eps, CapSepExistingCuts cmpSourceCutList, int[] noOfGeneratedSetsOut,
			int maxTotalGeneratedSets, CapSepGraph setsRPtr)
	{

		// Early exit if any supernode exceeds capacity (can't split demand further).
		for (int i = 1; i <= shrunkGraphCustNodes; i++)
		{
			if (superDemand[i] > cap)
				return;
		}

		int[] nodeLabel = new int[shrunkGraphCustNodes + 1];
		int[] sListBuf = new int[noOfCustomers + 1];
		int[] sumVector = new int[shrunkGraphCustNodes + 1];
		double[] xNodeSum = new double[shrunkGraphCustNodes + 1];
		int[] origCustLabel = new int[noOfCustomers + 1];
		int[] superNodeForCust = new int[noOfCustomers + 1];

		// Map each original customer to its supernode.
		for (int i = 1; i <= shrunkGraphCustNodes; i++)
		{
			int cfi = superNodesRPtr.forwCount(i);
			for (int j = 1; j <= cfi; j++)
			{
				superNodeForCust[superNodesRPtr.forwNeighbor(i, j)] = i;
			}
		}

		// Sort supernode indices by increasing demand (for the removal phase).
		// Use insertion sort (n is small after COMPRESS).
		int[] sortIdx = new int[shrunkGraphCustNodes + 1];
		for (int i = 1; i <= shrunkGraphCustNodes; i++)
			sortIdx[i] = i;
		for (int i = 2; i <= shrunkGraphCustNodes; i++)
		{
			int key = sortIdx[i];
			int keyDemand = superDemand[key];
			int j = i - 1;
			while (j >= 1 && superDemand[sortIdx[j]] > keyDemand)
			{
				sortIdx[j + 1] = sortIdx[j];
				j--;
			}
			sortIdx[j + 1] = key;
		}

		Arrays.fill(nodeLabel, 1, shrunkGraphCustNodes + 1, -1);

		for (int cutNr = 0; cutNr < cmpSourceCutList.size(); cutNr++)
		{
			int label = cutNr; // unique label for this cut's supernodes

			int demandSum = 0, nodeSum = 0;
			double xSumInSet = 0.0;
			int initListSize = 0;

			// Map cut's original customers to supernodes.
			int[] intList = cmpSourceCutList.getIntList(cutNr);
			int listSz = cmpSourceCutList.getIntListSize(cutNr);
			for (int i = 1; i <= listSz; i++)
			{
				int j = intList[i]; // original customer
				int k = superNodeForCust[j];
				if (nodeLabel[k] != label)
				{
					nodeLabel[k] = label;
					demandSum += superDemand[k];
					nodeSum += k;
					xSumInSet += xInSuperNode[k];
					initListSize += superNodeSize[k];
				}
			}

			int capSum = cap, minV = 1;
			while (capSum < demandSum)
			{
				capSum += cap;
				minV++;
			}
			int capSumMinusCap = capSum - cap;
			int capSlack = demandSum - capSumMinusCap - 1;

			// Compute XNodeSum for all supernodes (flow between each supernode and the
			// set).
			Arrays.fill(xNodeSum, 1, shrunkGraphCustNodes + 1, 0.0);
			for (int i = 1; i <= shrunkGraphCustNodes; i++)
			{
				int cfi = supportPtr.forwCount(i);
				for (int k = 1; k <= cfi; k++)
				{
					int j = supportPtr.forwNeighbor(i, k);
					if (j <= shrunkGraphCustNodes && j > i)
					{
						double xVal = sMatrix[i][j];
						if (nodeLabel[i] == label)
							xNodeSum[j] += xVal;
						if (nodeLabel[j] == label)
							xNodeSum[i] += xVal;
						if (nodeLabel[i] == label && nodeLabel[j] == label)
							xSumInSet += xVal;
					}
				}
			}

			// ----------------------------------------------------------------
			// Removal phase: remove weakly connected supernodes in order of
			// increasing demand, as long as MinV is preserved.
			// ----------------------------------------------------------------
			int removedDemand = 0, removedNodes = 0, addedNodes = 0;
			int remainingCapSlack = capSlack;
			int lastRemoved = 0;

			for (int nodeIdx = 1; nodeIdx <= shrunkGraphCustNodes; nodeIdx++)
			{
				int custNr = sortIdx[nodeIdx];
				if (nodeLabel[custNr] != label)
					continue;
				if (superDemand[custNr] > remainingCapSlack)
					break;
				if (xNodeSum[custNr] <= 0.99)
				{
					lastRemoved = custNr;
					xSumInSet -= (xNodeSum[custNr] + xInSuperNode[custNr]);
					removedNodes += superNodeSize[custNr];
					remainingCapSlack -= superDemand[custNr];
					demandSum -= superDemand[custNr];
					nodeLabel[custNr]--;
					nodeSum -= custNr;
					int cfi = supportPtr.forwCount(custNr);
					for (int k = 1; k <= cfi; k++)
					{
						int j = supportPtr.forwNeighbor(custNr, k);
						if (j <= shrunkGraphCustNodes)
							xNodeSum[j] -= sMatrix[j][custNr];
					}
				}
			}

			// Re-add the last removed node, then try to replace it.
			if (lastRemoved > 0)
			{
				xSumInSet += (xNodeSum[lastRemoved] + xInSuperNode[lastRemoved]);
				removedNodes -= superNodeSize[lastRemoved];
				remainingCapSlack += superDemand[lastRemoved];
				demandSum += superDemand[lastRemoved];
				nodeLabel[lastRemoved]++;
				nodeSum += lastRemoved;
				int cfi = supportPtr.forwCount(lastRemoved);
				for (int k = 1; k <= cfi; k++)
				{
					int j = supportPtr.forwNeighbor(lastRemoved, k);
					if (j <= shrunkGraphCustNodes)
						xNodeSum[j] += sMatrix[j][lastRemoved];
				}

				// Look for the weakest node among the original cut's supernodes.
				int minXNode = 0;
				double minX = Double.MAX_VALUE;
				for (int k = 1; k <= listSz; k++)
				{
					int j2 = intList[k];
					int custNr = superNodeForCust[j2];
					if (nodeLabel[custNr] == label && superDemand[custNr] <= remainingCapSlack)
					{
						if (minXNode == 0 || xNodeSum[custNr] < minX)
						{
							minXNode = custNr;
							minX = xNodeSum[custNr];
						}
					}
				}
				if (minXNode > 0 && minX <= 0.99)
				{
					xSumInSet -= (xNodeSum[minXNode] + xInSuperNode[minXNode]);
					removedNodes += superNodeSize[minXNode];
					remainingCapSlack -= superDemand[minXNode];
					demandSum -= superDemand[minXNode];
					nodeLabel[minXNode]--;
					nodeSum -= minXNode;
					int cfi2 = supportPtr.forwCount(minXNode);
					for (int k2 = 1; k2 <= cfi2; k2++)
					{
						int j3 = supportPtr.forwNeighbor(minXNode, k2);
						if (j3 <= shrunkGraphCustNodes)
							xNodeSum[j3] -= sMatrix[j3][minXNode];
					}
				}
			}

			// ----------------------------------------------------------------
			// Main add/drop/exchange loop.
			// ----------------------------------------------------------------
			int custNr;
			do
			{
				custNr = 0;

				// Find best candidate to add (BestNewNode) and best to drop (MinXNode).
				int bestNewNode = 0, minXNode = 0;
				double maxX = -0.1, minX = 2.1;

				for (int i = 1; i <= shrunkGraphCustNodes; i++)
				{
					if (nodeLabel[i] != label)
					{
						// i is outside S: compute add score.
						double xScore = xNodeSum[i];
						if (demandSum + superDemand[i] > capSum)
							xScore += 1.0;
						if (xScore > maxX)
						{
							maxX = xScore;
							bestNewNode = i;
						}
					}
					else
					{
						// i is inside S: check if removable without changing MinV.
						if (superDemand[i] <= remainingCapSlack)
						{
							if (xNodeSum[i] < minX)
							{
								minX = xNodeSum[i];
								minXNode = i;
							}
						}
					}
				}

				if (minXNode > 0 && minX <= 0.99)
				{
					// Drop MinXNode.
					custNr = minXNode;
					xSumInSet -= (xNodeSum[custNr] + xInSuperNode[custNr]);
					removedNodes += superNodeSize[custNr];
					remainingCapSlack -= superDemand[custNr];
					demandSum -= superDemand[custNr];
					nodeLabel[custNr]--;
					nodeSum -= custNr;
					int cfi = supportPtr.forwCount(custNr);
					for (int k = 1; k <= cfi; k++)
					{
						int j = supportPtr.forwNeighbor(custNr, k);
						if (j <= shrunkGraphCustNodes)
							xNodeSum[j] -= sMatrix[j][custNr];
					}
				}
				else
					if (maxX >= 1.01)
					{
						// Add BestNewNode.
						custNr = bestNewNode;
						xSumInSet += (xNodeSum[custNr] + xInSuperNode[custNr]);
						addedNodes += superNodeSize[custNr];
						demandSum += superDemand[custNr];
						while (capSum < demandSum)
						{
							capSum += cap;
							minV++;
						}
						capSumMinusCap = capSum - cap;
						capSlack = demandSum - capSumMinusCap - 1;
						remainingCapSlack = capSlack;
						nodeLabel[custNr] = label;
						nodeSum += custNr;
						int cfi = supportPtr.forwCount(custNr);
						for (int k = 1; k <= cfi; k++)
						{
							int j = supportPtr.forwNeighbor(custNr, k);
							if (j <= shrunkGraphCustNodes)
								xNodeSum[j] += sMatrix[j][custNr];
						}
					}
					else
						if (maxX >= 0.01 && demandSum + superDemand[bestNewNode] <= capSum)
						{
							// Attempt exchange: add BestNewNode, remove MinXNode with lowest
							// effective connectivity (XNodeSum[i] + SMatrix[BestNewNode][i]).
							int exchMinXNode = 0;
							double exchMinX = 2.1;
							for (int i = 1; i <= shrunkGraphCustNodes; i++)
							{
								if (nodeLabel[i] == label
										&& (superDemand[i] - superDemand[bestNewNode]) <= remainingCapSlack)
								{
									double score = xNodeSum[i] + sMatrix[bestNewNode][i];
									if (score < exchMinX)
									{
										exchMinX = score;
										exchMinXNode = i;
									}
								}
							}
							if (exchMinXNode > 0 && maxX >= exchMinX + 0.01)
							{
								// Add BestNewNode.
								xSumInSet += (xNodeSum[bestNewNode] + xInSuperNode[bestNewNode]);
								addedNodes += superNodeSize[bestNewNode];
								demandSum += superDemand[bestNewNode];
								while (capSum < demandSum)
								{
									capSum += cap;
									minV++;
								}
								capSumMinusCap = capSum - cap;
								capSlack = demandSum - capSumMinusCap - 1;
								remainingCapSlack = capSlack;
								nodeLabel[bestNewNode] = label;
								nodeSum += bestNewNode;
								int cfi = supportPtr.forwCount(bestNewNode);
								for (int k = 1; k <= cfi; k++)
								{
									int j = supportPtr.forwNeighbor(bestNewNode, k);
									if (j <= shrunkGraphCustNodes)
										xNodeSum[j] += sMatrix[j][bestNewNode];
								}
								// Drop exchMinXNode.
								custNr = exchMinXNode;
								xSumInSet -= (xNodeSum[custNr] + xInSuperNode[custNr]);
								removedNodes += superNodeSize[custNr];
								remainingCapSlack -= superDemand[custNr];
								demandSum -= superDemand[custNr];
								nodeLabel[custNr]--;
								nodeSum -= custNr;
								int cfi2 = supportPtr.forwCount(custNr);
								for (int k = 1; k <= cfi2; k++)
								{
									int j = supportPtr.forwNeighbor(custNr, k);
									if (j <= shrunkGraphCustNodes)
										xNodeSum[j] -= sMatrix[j][custNr];
								}
							}
						}
			}
			while (custNr > 0);

			// ----------------------------------------------------------------
			// Violation check: add to setsRPtr if violated and not a duplicate.
			// ----------------------------------------------------------------
			double rhs = initListSize - removedNodes + addedNodes - minV;
			double violation = xSumInSet - rhs;

			if (violation >= eps)
			{
				int sListSize = initListSize - removedNodes + addedNodes;

				// Expand supernodes back to original customers.
				Arrays.fill(origCustLabel, 1, noOfCustomers + 1, -1);
				nodeSum = 0;
				for (int i = 1; i <= shrunkGraphCustNodes; i++)
				{
					if (nodeLabel[i] == label)
					{
						int cfi = superNodesRPtr.forwCount(i);
						for (int j = 1; j <= cfi; j++)
						{
							int k = superNodesRPtr.forwNeighbor(i, j);
							origCustLabel[k] = label;
							nodeSum += k;
						}
					}
				}

				if (!checkForExistingSet(setsRPtr, noOfGeneratedSetsOut[0], origCustLabel, label, nodeSum, sListSize))
				{
					// Build the sorted customer list.
					int j = 0;
					for (int i = 1; i <= noOfCustomers; i++)
					{
						if (origCustLabel[i] == label)
							sListBuf[++j] = i;
					}
					noOfGeneratedSetsOut[0]++;
					setsRPtr.setForwList(sListBuf, noOfGeneratedSetsOut[0], j);
					sumVector[1] = nodeSum;
					setsRPtr.setBackwList(sumVector, noOfGeneratedSetsOut[0], 1);

					if (noOfGeneratedSetsOut[0] >= maxTotalGeneratedSets)
						return;
				}
			}
		}
	}
}
