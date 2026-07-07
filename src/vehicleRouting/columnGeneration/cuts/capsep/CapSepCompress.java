package vehicleRouting.columnGeneration.cuts.capsep;

import java.util.Arrays;

/**
 * Graph shrinking for the CVRPSEP capacity-cut separator. Equivalent to
 * COMPRESS.CPP in CVRPSEP.
 *
 * <p>
 * Iteratively shrinks the customer support graph by merging groups of customers
 * that are "tightly connected" into supernodes. The merging is driven by four
 * heuristics applied in order until one fires:
 * </p>
 * <ol>
 * <li>Pairs: two shrinkable supernodes with shrunken edge ≥ EdgeEps (0.999);
 * all qualifying pairs are added in one pass.</li>
 * <li>Triples: three mutually connected shrinkable supernodes whose total
 * pairwise flow ≥ TripleEps (1.999); first qualifying triple only.</li>
 * <li>Tolerant shrinking: highest remaining inter-supernode flow, if (1 −
 * max_edge) ≤ remaining tolerance (dead code: disabled in the reference
 * implementation).</li>
 * <li>V1-cut guided: any tight V1 cut (slack ≤ 0.01) whose supernodes are all
 * shrinkable; first qualifying cut only.</li>
 * </ol>
 *
 * <p>
 * Strongly connected components of the symmetric compression-edge graph equal
 * BFS connected components, so BFS replaces Tarjan's SCC (STRNGCMP module) for
 * simplicity.
 * </p>
 *
 * <p>
 * All node indices are 1-based. The depot supernode has index
 * {@code ShrunkGraphCustNodes + 1}.
 * </p>
 */
public class CapSepCompress
{

	/** Compress any inter-supernode edge whose shrunken value ≥ EdgeEps. */
	private static final double EDGE_EPS = 0.999;
	/**
	 * Compress a triple of supernodes when the sum of pairwise flows ≥ TripleEps.
	 */
	private static final double TRIPLE_EPS = 1.999;
	/** Maximum cumulative tolerance for tolerant shrinking. */
	private static final double MAX_SHRINK_TOLERANCE = 0.01;

	// -------------------------------------------------------------------------
	// Private helper: COMPRESS_CheckV1Set
	// -------------------------------------------------------------------------

	/**
	 * Computes the slack of a single V1 cut mapped to the current supernodes.
	 *
	 * <p>
	 * For a V1 cut S (k(S)=1) in form (i): x(S:S) ≤ |S|−1. Slack = |S| − x(S:S) −
	 * 1. Shrinking is triggered when slack ≤ 0.01.
	 * </p>
	 *
	 * <p>
	 * Equivalent to COMPRESS_CheckV1Set.
	 * </p>
	 *
	 * @param supportPtr      undirected support graph (original customers, 1..n)
	 * @param noOfCustomers   n
	 * @param compNr          compNr[j] = current supernode index of customer j
	 * @param xMatrix         symmetric flow matrix [1..n+1][1..n+1]
	 * @param slackOut        out: slackOut[0] = |S| − x(S:S) − 1
	 * @param compListSizeOut out: number of distinct supernodes touched by cut
	 * @param compList        out: compList[1..compListSizeOut[0]] = supernode
	 *                        indices
	 * @param cutNr           1-based index into v1CutsPtr
	 * @param v1CutsPtr       forward list i = customer members of V1 cut i
	 *                        (1-based)
	 */
	private static void checkV1Set(CapSepGraph supportPtr, int noOfCustomers, int[] compNr, double[][] xMatrix,
			double[] slackOut, int[] compListSizeOut, int[] compList, int cutNr, CapSepGraph v1CutsPtr)
	{
		boolean[] inNodeSet = new boolean[noOfCustomers + 1];
		boolean[] compInSet = new boolean[noOfCustomers + 1]; // indexed by supernode nr

		compListSizeOut[0] = 0;
		int cfn = v1CutsPtr.forwCount(cutNr);
		for (int k = 1; k <= cfn; k++)
		{
			int j = v1CutsPtr.forwNeighbor(cutNr, k);
			inNodeSet[j] = true;
			if (!compInSet[compNr[j]])
			{
				compList[++compListSizeOut[0]] = compNr[j];
				compInSet[compNr[j]] = true;
			}
		}

		double xSumInSet = CapSepCutUtils.compXSumInSet(supportPtr, noOfCustomers, inNodeSet, null, 0, xMatrix);
		slackOut[0] = (double) cfn - xSumInSet - 1.0;
	}

	// -------------------------------------------------------------------------
	// Public API: COMPRESS_ShrinkGraph
	// -------------------------------------------------------------------------

	/**
	 * Shrinks the customer support graph into supernodes.
	 *
	 * <p>
	 * On return:
	 * </p>
	 * <ul>
	 * <li>{@code sMatrix[i][i]} = interior flow of supernode i
	 * (XInSuperNode[i])</li>
	 * <li>{@code sMatrix[i][j]} for i≠j = flow between supernodes i and j</li>
	 * <li>{@code sAdjRPtr.forwCount(i)} / {@code forwNeighbor(i,k)} = shrunken
	 * adjacency (supernodes adjacent to i with flow ≥ 0.0001)</li>
	 * <li>{@code superNodesRPtr.forwCount(i)} / {@code forwArray(i)} = original
	 * customers in supernode i (1..ShrunkGraphCustNodes); supernode
	 * ShrunkGraphCustNodes+1 contains only the depot</li>
	 * </ul>
	 *
	 * <p>
	 * Equivalent to COMPRESS_ShrinkGraph.
	 * </p>
	 *
	 * @param supportPtr     undirected support graph [1..n+1]
	 * @param noOfCustomers  n (original customer count)
	 * @param xMatrix        symmetric flow matrix [1..n+1][1..n+1]
	 * @param sMatrix        work and output array [0..n+1][0..n+1]; caller must
	 *                       allocate as {@code new double[n+2][n+2]}
	 * @param noOfV1Cuts     number of V1 cuts in v1CutsPtr
	 * @param v1CutsPtr      forward list i = customer members of V1 cut i (1-based)
	 * @param sAdjRPtr       output: shrunken graph adjacency (caller provides an
	 *                       empty CapSepGraph)
	 * @param superNodesRPtr output: supernode-to-customer mapping (caller provides
	 *                       an empty CapSepGraph)
	 * @return ShrunkGraphCustNodes = number of customer supernodes K; the depot
	 *         supernode has index K+1
	 */
	public static int shrinkGraph(CapSepGraph supportPtr, int noOfCustomers, double[][] xMatrix, double[][] sMatrix,
			int noOfV1Cuts, CapSepGraph v1CutsPtr, CapSepGraph sAdjRPtr, CapSepGraph superNodesRPtr)
	{

		// Tolerant shrinking is disabled in the C reference (TolerantShrinking=0).
		final boolean tolerantShrinking = false;
		double usedTolerance = 0.0;

		// Reusable working arrays.
		int[] compNr = new int[noOfCustomers + 2]; // compNr[j] = supernode of j
		boolean[] visited = new boolean[noOfCustomers + 1];
		boolean[] shrinkable = new boolean[noOfCustomers + 2]; // [1..noOfComponents]
		int[] tempList = new int[noOfCustomers + 1];
		int[] queue = new int[noOfCustomers + 1];
		int[] nodeList = new int[noOfCustomers + 2];
		int[] compList = new int[noOfCustomers + 1];
		double[] slackOut = new double[1];
		int[] compListSizeOut = new int[1];

		// -----------------------------------------------------------------
		// Step 1: Build CmprsEdgesRPtr — undirected edges with x >= EdgeEps.
		// -----------------------------------------------------------------
		CapSepGraph cmprsEdgesRPtr = new CapSepGraph(noOfCustomers + 1);
		for (int i = 1; i < noOfCustomers; i++)
		{
			int cfi = supportPtr.forwCount(i);
			for (int ki = 1; ki <= cfi; ki++)
			{
				int j = supportPtr.forwNeighbor(i, ki);
				if (j <= noOfCustomers && j > i && xMatrix[i][j] >= EDGE_EPS)
				{
					cmprsEdgesRPtr.addForwArc(i, j);
					cmprsEdgesRPtr.addForwArc(j, i);
				}
			}
		}

		// -----------------------------------------------------------------
		// Step 2: Iterative shrinking loop.
		// -----------------------------------------------------------------
		CapSepGraph compsRPtr = new CapSepGraph(noOfCustomers + 1);
		int noOfComponents;

		do
		{
			// --- 2a: Recompute connected components via BFS on cmprsEdgesRPtr ---
			compsRPtr.clearForwLists();
			Arrays.fill(visited, 1, noOfCustomers + 1, false);
			noOfComponents = 0;

			for (int start = 1; start <= noOfCustomers; start++)
			{
				if (visited[start])
					continue;
				noOfComponents++;
				int qHead = 0, qTail = 0;
				queue[qTail++] = start;
				visited[start] = true;
				int compCount = 0;

				while (qHead < qTail)
				{
					int u = queue[qHead++];
					compNr[u] = noOfComponents;
					tempList[++compCount] = u;

					int cfu = cmprsEdgesRPtr.forwCount(u);
					for (int k = 1; k <= cfu; k++)
					{
						int v = cmprsEdgesRPtr.forwNeighbor(u, k);
						if (v <= noOfCustomers && !visited[v])
						{
							visited[v] = true;
							queue[qTail++] = v;
						}
					}
				}
				compsRPtr.setForwList(tempList, noOfComponents, compCount);
			}
			// Depot is the last supernode (index noOfComponents+1).
			compNr[noOfCustomers + 1] = noOfComponents + 1;

			// --- 2b: Build shrunken flow matrix SMatrix ---
			// Zero only the rows/columns that will be used.
			for (int i = 1; i <= noOfComponents + 1; i++)
			{
				Arrays.fill(sMatrix[i], 1, noOfComponents + 2, 0.0);
			}
			// C loop: i <= NoOfCustomers (not i < NoOfCustomers) because
			// j may be the depot (j = noOfCustomers+1 > i = noOfCustomers).
			for (int i = 1; i <= noOfCustomers; i++)
			{
				int cfi = supportPtr.forwCount(i);
				for (int ki = 1; ki <= cfi; ki++)
				{
					int j = supportPtr.forwNeighbor(i, ki);
					if (j > i)
					{ // count each undirected edge once
						double xVal = xMatrix[i][j];
						int ci = compNr[i];
						int cj = compNr[j]; // compNr[depot] = noOfComponents+1
						sMatrix[ci][cj] += xVal;
						if (ci != cj)
							sMatrix[cj][ci] += xVal;
					}
				}
			}

			// --- 2c: Mark shrinkable supernodes ---
			// Shrinkable iff interior flow sum < (size − 1 + 0.01).
			for (int i = 1; i <= noOfComponents; i++)
			{
				shrinkable[i] = (sMatrix[i][i] < (compsRPtr.forwCount(i) - 1 + 0.01));
			}

			// --- 2d: Try new compression edges (in priority order) ---
			boolean newLinks = false;

			// Heuristic 1: Pairs — add ALL qualifying pairs in one pass.
			for (int i = 1; i < noOfComponents; i++)
			{
				if (!shrinkable[i])
					continue;
				for (int j = i + 1; j <= noOfComponents; j++)
				{
					if (!shrinkable[j])
						continue;
					if (sMatrix[i][j] >= EDGE_EPS)
					{
						int tail = compsRPtr.forwNeighbor(i, 1);
						int head = compsRPtr.forwNeighbor(j, 1);
						cmprsEdgesRPtr.addForwArc(tail, head);
						cmprsEdgesRPtr.addForwArc(head, tail);
						newLinks = true;
					}
				}
			}

			// Heuristic 2: Triples — first qualifying triple only (goto in C).
			if (!newLinks)
			{
				outer:
				for (int i = 1; i < noOfComponents; i++)
				{
					if (!shrinkable[i])
						continue;
					for (int j = i + 1; j < noOfComponents; j++)
					{
						if (!shrinkable[j])
							continue;
						if (sMatrix[i][j] <= 0.01)
							continue;
						for (int k = j + 1; k <= noOfComponents; k++)
						{
							if (!shrinkable[k])
								continue;
							if (sMatrix[i][k] <= 0.01)
								continue;
							if (sMatrix[j][k] <= 0.01)
								continue;
							double xVal = sMatrix[i][j] + sMatrix[i][k] + sMatrix[j][k];
							if (xVal >= TRIPLE_EPS)
							{
								int tail = compsRPtr.forwNeighbor(i, 1);
								int headJ = compsRPtr.forwNeighbor(j, 1);
								int headK = compsRPtr.forwNeighbor(k, 1);
								cmprsEdgesRPtr.addForwArc(tail, headJ);
								cmprsEdgesRPtr.addForwArc(headJ, tail);
								cmprsEdgesRPtr.addForwArc(tail, headK);
								cmprsEdgesRPtr.addForwArc(headK, tail);
								newLinks = true;
								break outer;
							}
						}
					}
				}
			}

			// Heuristic 3: Tolerant shrinking (disabled: TolerantShrinking=false).
			if (!newLinks && tolerantShrinking && usedTolerance < MAX_SHRINK_TOLERANCE)
			{
				int bestI = 0, bestJ = 0;
				double maxEdge = 0.0;
				for (int i = 1; i < noOfComponents; i++)
				{
					for (int j = i + 1; j <= noOfComponents; j++)
					{
						if (sMatrix[i][j] > maxEdge)
						{
							maxEdge = sMatrix[i][j];
							bestI = i;
							bestJ = j;
						}
					}
				}
				if (bestI > 0 && (1.0 - maxEdge) <= (MAX_SHRINK_TOLERANCE - usedTolerance))
				{
					int tail = compsRPtr.forwNeighbor(bestI, 1);
					int head = compsRPtr.forwNeighbor(bestJ, 1);
					cmprsEdgesRPtr.addForwArc(tail, head);
					cmprsEdgesRPtr.addForwArc(head, tail);
					usedTolerance += (1.0 - maxEdge);
					newLinks = true;
				}
			}

			// Heuristic 4: V1-cut guided — first qualifying cut only (goto in C).
			if (!newLinks)
			{
				for (int ci = 1; ci <= noOfV1Cuts && !newLinks; ci++)
				{
					checkV1Set(supportPtr, noOfCustomers, compNr, xMatrix, slackOut, compListSizeOut, compList, ci,
							v1CutsPtr);
					int compListSize = compListSizeOut[0];

					boolean shrinkableSet = true;
					for (int j = 1; j <= compListSize; j++)
					{
						if (!shrinkable[compList[j]])
						{
							shrinkableSet = false;
							break;
						}
					}
					if (slackOut[0] <= 0.01 && compListSize > 1 && shrinkableSet)
					{
						int tail = compsRPtr.forwNeighbor(compList[1], 1);
						for (int j = 2; j <= compListSize; j++)
						{
							int head = compsRPtr.forwNeighbor(compList[j], 1);
							cmprsEdgesRPtr.addForwArc(tail, head);
							cmprsEdgesRPtr.addForwArc(head, tail);
						}
						newLinks = true;
					}
				}
			}

			if (!newLinks)
				break;

		}
		while (true);

		// -----------------------------------------------------------------
		// Step 3: Build SAdjRPtr — shrunken graph adjacency (threshold 0.0001).
		// -----------------------------------------------------------------
		for (int i = 1; i <= noOfComponents + 1; i++)
		{
			int nodeListSize = 0;
			for (int j = 1; j <= noOfComponents + 1; j++)
			{
				if (j != i && sMatrix[i][j] >= 0.0001)
				{
					nodeList[++nodeListSize] = j;
				}
			}
			sAdjRPtr.setForwList(nodeList, i, nodeListSize);
		}

		// -----------------------------------------------------------------
		// Step 4: Build SuperNodesRPtr — supernode-to-customer mapping.
		// -----------------------------------------------------------------
		for (int i = 1; i <= noOfComponents; i++)
		{
			superNodesRPtr.setForwList(compsRPtr.forwArray(i), i, compsRPtr.forwCount(i));
		}
		// Depot supernode: contains only the depot (original node noOfCustomers+1).
		nodeList[1] = noOfCustomers + 1;
		superNodesRPtr.setForwList(nodeList, noOfComponents + 1, 1);

		return noOfComponents; // ShrunkGraphCustNodes
	}
}
