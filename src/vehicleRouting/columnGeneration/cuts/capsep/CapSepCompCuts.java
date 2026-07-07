package vehicleRouting.columnGeneration.cuts.capsep;

/**
 * Connected-components separation procedure for rounded capacity cuts.
 * Equivalent to COMPCUTS.CPP in CVRPSEP.
 *
 * <p>
 * Finds connected components of the customer-only support graph (depot
 * excluded), then checks for violated RCCs for:
 * </p>
 * <ol>
 * <li>each individual component;</li>
 * <li>the complement of each component (when ≥ 4 total components);</li>
 * <li>the union of all components that have no edge to the depot (when ≥ 5
 * total components and at least 2 depot-connected and 2 non-depot-connected
 * components exist).</li>
 * </ol>
 *
 * <p>
 * All node indices are 1-based. The depot is node {@code noOfCustomers + 1}.
 * </p>
 */
public class CapSepCompCuts
{

	private static final double EPS_VIOLATION = 0.01;

	/**
	 * Computes violated capacity cuts from connected components of the customer
	 * support graph.
	 *
	 * <p>
	 * Equivalent to COMPCUTS_ComputeCompCuts.
	 * </p>
	 *
	 * @param supportPtr    undirected support graph; depot = noOfCustomers+1
	 * @param noOfCustomers n
	 * @param demand        1-indexed demand array [1..n]
	 * @param cap           vehicle capacity
	 * @param xMatrix       symmetric flow matrix [1..n+1][1..n+1]
	 * @param cutsCMP       output: violated cuts appended here
	 * @return number of violated cuts generated
	 */
	public static int computeCompCuts(CapSepGraph supportPtr, int noOfCustomers, int[] demand, int cap,
			double[][] xMatrix, CapSepExistingCuts cutsCMP)
	{
		int cutNr = 0;
		int totalNodes = noOfCustomers + 1; // depot index

		// -----------------------------------------------------------------
		// Step 1: Find connected components among customers (1..n).
		// The C code does this by temporarily setting depot forward-count to 0
		// before calling ComputeStrongComponents, then restoring it.
		// Since the customer-only graph is undirected, we use BFS here.
		// -----------------------------------------------------------------

		int[] compNr = new int[totalNodes + 1]; // compNr[node] = component number (1-based)
		int[] queue = new int[noOfCustomers + 1];
		// compsRPtr: forward list of component i = customer members of that component
		CapSepGraph compsRPtr = new CapSepGraph(noOfCustomers);
		int[] tempList = new int[noOfCustomers + 1];
		int noOfComponents = 0;
		boolean[] visited = new boolean[noOfCustomers + 1];

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

				int cfu = supportPtr.forwCount(u);
				for (int k = 1; k <= cfu; k++)
				{
					int v = supportPtr.forwNeighbor(u, k);
					if (v <= noOfCustomers && !visited[v])
					{
						visited[v] = true;
						queue[qTail++] = v;
					}
				}
			}
			compsRPtr.setForwList(tempList, noOfComponents, compCount);
		}

		// The depot forms its own virtual component (not stored in compsRPtr).
		// noOfComponents is the count of customer-only components.

		// Equivalent to the C check: if (NoOfComponents == 2) goto EndOfCompCuts
		// which happens when there is exactly 1 customer component + depot.
		if (noOfComponents == 1)
		{
			// Only one customer component: the connected support graph, nothing to do.
			return 0;
		}

		// -----------------------------------------------------------------
		// Step 2: Compute per-component demand sums and interior x-sums.
		// -----------------------------------------------------------------
		int[] compDemandSum = new int[noOfComponents + 1];
		double[] compXSum = new double[noOfComponents + 1];

		for (int i = 1; i <= noOfCustomers; i++)
		{
			compDemandSum[compNr[i]] += demand[i];
		}

		for (int i = 1; i < noOfCustomers; i++)
		{
			int cfi = supportPtr.forwCount(i);
			for (int ki = 1; ki <= cfi; ki++)
			{
				int k = supportPtr.forwNeighbor(i, ki);
				// Only count each undirected edge once (k > i) and only
				// if both endpoints are in the same customer component.
				if (k > i && k <= noOfCustomers && compNr[k] == compNr[i])
				{
					compXSum[compNr[i]] += xMatrix[i][k];
				}
			}
		}

		double sumOfCompXSums = 0.0;
		for (int i = 1; i <= noOfComponents; i++)
			sumOfCompXSums += compXSum[i];

		int totalDemand = 0;
		for (int i = 1; i <= noOfComponents; i++)
			totalDemand += compDemandSum[i];

		int[] nodeList = new int[noOfCustomers + 1];

		// -----------------------------------------------------------------
		// Step 3: Check each customer component and its complement.
		// -----------------------------------------------------------------
		for (int ci = 1; ci <= noOfComponents; ci++)
		{

			int compDemand = compDemandSum[ci];
			int compNodes = compsRPtr.forwCount(ci);

			// --- Component itself ---
			int capSum = cap, minV = 1;
			while (capSum < compDemand)
			{
				capSum += cap;
				minV++;
			}

			double lhs = compXSum[ci];
			double rhs = compNodes - minV;

			if ((lhs - rhs) >= EPS_VIOLATION)
			{
				cutNr++;
				cutsCMP.addCut(compsRPtr.forwArray(ci), compNodes, rhs);
			}

			// --- Complement (only when >= 4 total components;
			// the C code checks NoOfComponents >= 4, which means at
			// least 3 customer components because NoOfComponents counts
			// only customers). In C NoOfComponents includes depot, so
			// "NoOfComponents >= 4" means >= 3 customer components.
			// Here noOfComponents is customer-only, so the threshold is
			// noOfComponents >= 3. ---
			if (noOfComponents >= 3)
			{
				int complementDemand = totalDemand - compDemand;
				double complementXSum = sumOfCompXSums - compXSum[ci];

				capSum = cap;
				minV = 1;
				while (capSum < complementDemand)
				{
					capSum += cap;
					minV++;
				}

				lhs = complementXSum;
				rhs = (noOfCustomers - compNodes) - minV;

				if ((lhs - rhs) >= EPS_VIOLATION)
				{
					cutNr++;
					int nodeListSize = 0;
					for (int j = 1; j <= noOfCustomers; j++)
					{
						if (compNr[j] != ci)
						{
							nodeList[++nodeListSize] = j;
						}
					}
					cutsCMP.addCut(nodeList, nodeListSize, rhs);
				}
			}
		}

		// -----------------------------------------------------------------
		// Step 4: Union of components not adjacent to depot (when >= 5
		// total components in C, which means >= 4 customer
		// components here since C's count includes depot).
		// -----------------------------------------------------------------
		if (noOfComponents >= 4)
		{
			// Mark which customer components are connected to the depot.
			boolean[] connectedToDepot = new boolean[noOfComponents + 1];
			int depotIdx = totalNodes; // = noOfCustomers + 1
			int cfDepot = supportPtr.forwCount(depotIdx);
			for (int ki = 1; ki <= cfDepot; ki++)
			{
				int j = supportPtr.forwNeighbor(depotIdx, ki);
				if (j <= noOfCustomers)
				{
					connectedToDepot[compNr[j]] = true;
				}
			}

			// Count depot-connected (j) and non-depot-connected (k) components.
			int depotConnCount = 0, nonDepotCount = 0;
			for (int ci = 1; ci <= noOfComponents; ci++)
			{
				if (connectedToDepot[ci])
					depotConnCount++;
				else
					nonDepotCount++;
			}

			// Generate cut only when at least 2 depot-connected and
			// at least 2 non-depot-connected components exist.
			if (depotConnCount >= 2 && nonDepotCount >= 2)
			{
				int nodeListSize = 0;
				double lhs = 0.0;
				int demandSum = 0;

				for (int ci = 1; ci <= noOfComponents; ci++)
				{
					if (!connectedToDepot[ci])
					{
						demandSum += compDemandSum[ci];
						lhs += compXSum[ci];
						int cfi = compsRPtr.forwCount(ci);
						for (int j = 1; j <= cfi; j++)
						{
							nodeList[++nodeListSize] = compsRPtr.forwNeighbor(ci, j);
						}
					}
				}

				int capSum = cap, minV = 1;
				while (capSum < demandSum)
				{
					capSum += cap;
					minV++;
				}
				double rhs = nodeListSize - minV;

				if ((lhs - rhs) >= EPS_VIOLATION)
				{
					cutNr++;
					cutsCMP.addCut(nodeList, nodeListSize, rhs);
				}
			}
		}
		return cutNr;
	}
}
