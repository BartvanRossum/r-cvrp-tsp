package vehicleRouting.columnGeneration.cuts.capsep;

import java.util.Arrays;

/**
 * Main orchestrator for the CVRPSEP rounded-capacity-inequality separator.
 * Equivalent to CAPSEP.CPP in CVRPSEP.
 *
 * <p>
 * Pipeline (mirrors CAPSEP_SeparateCapCuts):
 * </p>
 * <ol>
 * <li>Build the undirected support graph and symmetric XMatrix from the
 * directed edge list.</li>
 * <li>{@link CapSepCompCuts#computeCompCuts COMPCUTS}: connected-components
 * heuristic — if any violated cuts are found, return immediately (they are
 * almost certainly the strongest).</li>
 * <li>Check integrality of the current LP solution.</li>
 * <li>Extract V1 (one-vehicle) cuts from {@code cmpExistingCuts} to guide the
 * graph shrinking step.</li>
 * <li>{@link CapSepCompress#shrinkGraph COMPRESS}: iterative graph shrinking —
 * produces supernodes, shrunken adjacency, and the SMatrix.</li>
 * <li>Compute super-node statistics: SuperDemand, SuperNodeSize, XInSuperNode =
 * SMatrix diagonal.</li>
 * <li>{@link CapSepFCApFix#computeCuts FCAPFIX}: max-flow-based cuts on the
 * shrunken graph (up to MaxNoOfCuts/2 cuts, 3 rounds).</li>
 * <li>Set up AntiSets from the FCAPFIX results.</li>
 * <li>{@link CapSepGrSearch#capCuts GRSEARCH_CapCuts}: greedy expansion on the
 * shrunken graph (up to MaxNoOfCuts total).</li>
 * <li>Expand all cuts to original customers; filter by violation; add to
 * output.</li>
 * <li>If quota not yet filled: {@link CapSepGrSearch#addDropCapsOnGS
 * GRSEARCH_AddDropCapsOnGS}: add/ drop/exchange local search seeded by
 * {@code cmpExistingCuts}.</li>
 * <li>Expand and filter the additional cuts.</li>
 * </ol>
 *
 * <p>
 * All indices are 1-based. The depot is node {@code noOfCustomers + 1}.
 * </p>
 */
public class CapacityCutSeparation
{

	private static final double EPS_VIOLATION = 0.01;

	// =========================================================================
	// Private helpers
	// =========================================================================

	/**
	 * Extracts all V1 (one-vehicle) cuts from an existing-cuts store. A cut S is a
	 * V1 cut iff RHS ∈ (|S|−1.01, |S|−0.99), i.e., k(S) = 1.
	 *
	 * <p>
	 * Equivalent to CAPSEP_GetOneVehicleCapCuts.
	 * </p>
	 *
	 * @param cmpExistingCuts source of cuts (0-indexed access)
	 * @param v1CutsOut       output: V1 cuts stored at 1-based indices in
	 *                        v1CutsOut.forwList; v1CutsOut[i].forwArray() =
	 *                        customer list of V1 cut i
	 * @return number of V1 cuts found
	 */
	private static int getOneVehicleCapCuts(CapSepExistingCuts cmpExistingCuts, CapSepGraph v1CutsOut)
	{
		int noOfV1Cuts = 0;
		for (int i = 0; i < cmpExistingCuts.size(); i++)
		{
			double setSize = cmpExistingCuts.getIntListSize(i);
			double rhs = cmpExistingCuts.getRhs(i);
			// V1 cut: RHS ≈ setSize − 1 (k(S) = 1).
			if (rhs >= setSize - 1.01 && rhs <= setSize - 0.99)
			{
				noOfV1Cuts++;
				v1CutsOut.setForwList(cmpExistingCuts.getIntList(i), noOfV1Cuts, cmpExistingCuts.getIntListSize(i));
			}
		}
		return noOfV1Cuts;
	}

	/**
	 * Computes the violation x(S:S) − (|S| − k(S)) for a given customer list using
	 * the original support graph and flow matrix.
	 */
	private static double computeViolation(CapSepGraph supportPtr, int noOfCustomers, int[] demand, int cap,
			double[][] xMatrix, int[] nodeList, int nodeListSize)
	{
		return CapSepCutUtils.compCapViolation(supportPtr, noOfCustomers, null, nodeList, nodeListSize, demand, cap,
				xMatrix);
	}

	// =========================================================================
	// Public entry point: CAPSEP_SeparateCapCuts
	// =========================================================================

	/**
	 * Separates violated rounded capacity inequalities for the given LP solution.
	 *
	 * <p>
	 * The returned {@link CapSepExistingCuts} contains all violated cuts found (if
	 * any). Each cut is stored as a sorted, 1-indexed list of original customer
	 * indices together with its RHS = |S| − k(S).
	 * </p>
	 *
	 * <p>
	 * Equivalent to CAPSEP_SeparateCapCuts.
	 * </p>
	 *
	 * @param noOfCustomers         n (number of customers)
	 * @param demand                1-indexed demand array [1..n]
	 * @param cap                   vehicle capacity
	 * @param noOfEdges             number of support-graph edges
	 * @param edgeTail              1-indexed edge tail array [1..noOfEdges]; valid
	 *                              node range 1..n+1 (depot = n+1)
	 * @param edgeHead              1-indexed edge head array [1..noOfEdges]
	 * @param edgeX                 1-indexed edge flow array [1..noOfEdges]
	 * @param cmpExistingCuts       previously found cuts (from prior calls), used
	 *                              to seed GRSEARCH_AddDropCapsOnGS and to extract
	 *                              V1 cuts for COMPRESS
	 * @param maxNoOfCuts           maximum number of cuts to generate
	 * @param epsForIntegrality     threshold for "essentially integer" flow values
	 * @param integerAndFeasibleOut out [0]: true if LP solution is integer and
	 *                              feasible (all edge flows ∈ {0,1,2})
	 * @param maxViolationOut       out [0]: largest violation found
	 * @return new violated cuts; empty if none found
	 */
	public static CapSepExistingCuts separate(int noOfCustomers, int[] demand, int cap, int noOfEdges, int[] edgeTail,
			int[] edgeHead, double[] edgeX, CapSepExistingCuts cmpExistingCuts, int maxNoOfCuts,
			double epsForIntegrality, boolean[] integerAndFeasibleOut, double[] maxViolationOut)
	{

		integerAndFeasibleOut[0] = false;
		maxViolationOut[0] = 0.0;

		CapSepExistingCuts cutsCMP = new CapSepExistingCuts();

		// ------------------------------------------------------------------
		// Step 1: Build undirected support graph and symmetric XMatrix.
		// ------------------------------------------------------------------
		CapSepGraph supportPtr = new CapSepGraph(noOfCustomers + 1);
		double[][] xMatrix = new double[noOfCustomers + 2][noOfCustomers + 2];

		for (int i = 1; i <= noOfEdges; i++)
		{
			int tail = edgeTail[i], head = edgeHead[i];
			supportPtr.addForwArc(tail, head);
			supportPtr.addForwArc(head, tail);
			xMatrix[tail][head] = edgeX[i];
			xMatrix[head][tail] = edgeX[i];
		}

		// ------------------------------------------------------------------
		// Step 2: COMPCUTS — connected-components heuristic.
		// ------------------------------------------------------------------
		int generatedCuts = CapSepCompCuts.computeCompCuts(supportPtr, noOfCustomers, demand, cap, xMatrix, cutsCMP);

		if (generatedCuts > 0)
		{
			// Sort each cut's customer list and compute MaxViolation.
			for (int i = 0; i < generatedCuts; i++)
			{
				int[] list = cutsCMP.getIntList(i);
				int sz = cutsCMP.getIntListSize(i);
				Arrays.sort(list, 1, sz + 1);
				double v = computeViolation(supportPtr, noOfCustomers, demand, cap, xMatrix, list, sz);
				if (v > maxViolationOut[0])
					maxViolationOut[0] = v;
			}
			return cutsCMP; // early exit
		}

		// ------------------------------------------------------------------
		// Step 3: Integrality check.
		// ------------------------------------------------------------------
		integerAndFeasibleOut[0] = true;
		outer:
		for (int i = 1; i <= noOfCustomers; i++)
		{
			int cfi = supportPtr.forwCount(i);
			for (int k = 1; k <= cfi; k++)
			{
				int j = supportPtr.forwNeighbor(i, k);
				if (j < i)
					continue; // each edge once
				double x = xMatrix[i][j];
				double eps = epsForIntegrality;
				if ((x >= eps && x <= 1.0 - eps) || (x >= 1.0 + eps && x <= 2.0 - eps))
				{
					integerAndFeasibleOut[0] = false;
					break outer;
				}
			}
		}

		// ------------------------------------------------------------------
		// Step 4: Extract V1 cuts from existing cuts for COMPRESS.
		// ------------------------------------------------------------------
		CapSepGraph v1CutsPtr = new CapSepGraph(50);
		int noOfV1Cuts = getOneVehicleCapCuts(cmpExistingCuts, v1CutsPtr);

		// ------------------------------------------------------------------
		// Step 5: COMPRESS — iterative graph shrinking.
		// ------------------------------------------------------------------
		CapSepGraph sAdjRPtr = new CapSepGraph(noOfCustomers + 1);
		CapSepGraph superNodesRPtr = new CapSepGraph(noOfCustomers + 1);
		double[][] sMatrix = new double[noOfCustomers + 2][noOfCustomers + 2];

		int shrunkGraphCustNodes = CapSepCompress.shrinkGraph(supportPtr, noOfCustomers, xMatrix, sMatrix, noOfV1Cuts,
				v1CutsPtr, sAdjRPtr, superNodesRPtr);

		// ------------------------------------------------------------------
		// Step 6: Compute supernode statistics.
		// ------------------------------------------------------------------
		int[] superDemand = new int[shrunkGraphCustNodes + 1];
		int[] superNodeSize = new int[shrunkGraphCustNodes + 1];
		double[] xInSuperNode = new double[shrunkGraphCustNodes + 1];

		for (int i = 1; i <= shrunkGraphCustNodes; i++)
		{
			superNodeSize[i] = superNodesRPtr.forwCount(i);
			xInSuperNode[i] = sMatrix[i][i];
			int cfni = superNodesRPtr.forwCount(i);
			for (int j = 1; j <= cfni; j++)
			{
				superDemand[i] += demand[superNodesRPtr.forwNeighbor(i, j)];
			}
		}

		// ------------------------------------------------------------------
		// Step 7: FCAPFIX — max-flow-based cuts on the shrunken graph.
		// ------------------------------------------------------------------
		int maxCuts = maxNoOfCuts / 2;
		int fcapRounds = 3;
		CapSepGraph capCutsRPtr = new CapSepGraph(maxNoOfCuts + 1);

		generatedCuts = CapSepFCApFix.computeCuts(sAdjRPtr, shrunkGraphCustNodes, superDemand, cap, superNodeSize,
				sMatrix, maxCuts, fcapRounds, capCutsRPtr);

		// ------------------------------------------------------------------
		// Step 8: Set up antisets from FCAPFIX cuts (backward list = total
		// supernode-index sum; backCount = 1 → only the full set is
		// prohibited in GRSEARCH_GetInfeasExt).
		// ------------------------------------------------------------------
		int[] tmpList = new int[shrunkGraphCustNodes + 2];
		for (int i = 1; i <= generatedCuts; i++)
		{
			int sum = 0;
			int cfi = capCutsRPtr.forwCount(i);
			for (int idx = 1; idx <= cfi; idx++)
			{
				sum += capCutsRPtr.forwNeighbor(i, idx);
			}
			tmpList[1] = sum;
			capCutsRPtr.setBackwList(tmpList, i, 1);
		}

		// ------------------------------------------------------------------
		// Step 9: GRSEARCH_CapCuts — greedy expansion on the shrunken graph.
		// AntiSetsRPtr is a copy of CapCutsRPtr (FCAPFIX results).
		// ------------------------------------------------------------------
		CapSepGraph antiSetsRPtr = capCutsRPtr.copy();
		antiSetsRPtr.expand(capCutsRPtr.getDim() + shrunkGraphCustNodes);
		int generatedAntiSets = generatedCuts; // start = FCAPFIX cut count

		int[] generatedSetsOut =
		{ generatedCuts };
		int[] generatedAntiSetsOut =
		{ generatedAntiSets };

		maxCuts = maxNoOfCuts; // Now allow the full quota.
		CapSepGrSearch.capCuts(sAdjRPtr, shrunkGraphCustNodes, superDemand, cap, superNodeSize, xInSuperNode, sMatrix,
				generatedSetsOut, generatedAntiSetsOut, capCutsRPtr, antiSetsRPtr, maxCuts);

		generatedCuts = generatedSetsOut[0];
		int cutsBeforeLastProc = generatedCuts;

		// ------------------------------------------------------------------
		// Step 10: Expand capCutsRPtr[1..generatedCuts] to original customers;
		// check violation; add violated cuts to cutsCMP.
		// Also store in origCapCutsRPtr for AddDropCapsOnGS.
		// ------------------------------------------------------------------
		CapSepGraph origCapCutsRPtr = new CapSepGraph(maxNoOfCuts + 1);
		int[] nodeList = new int[noOfCustomers + 2];

		for (int cutNr = 1; cutNr <= generatedCuts; cutNr++)
		{
			// Expand supernodes to original customers.
			int nodeListSize = 0;
			int nodeSum = 0;
			int cfCut = capCutsRPtr.forwCount(cutNr);
			for (int idx = 1; idx <= cfCut; idx++)
			{
				int sn = capCutsRPtr.forwNeighbor(cutNr, idx); // supernode
				int cfSN = superNodesRPtr.forwCount(sn);
				for (int j = 1; j <= cfSN; j++)
				{
					int k = superNodesRPtr.forwNeighbor(sn, j); // original customer
					nodeList[++nodeListSize] = k;
					nodeSum += k;
				}
			}

			// Compute violation in the original graph.
			double violation = computeViolation(supportPtr, noOfCustomers, demand, cap, xMatrix, nodeList,
					nodeListSize);
			if (violation > maxViolationOut[0])
				maxViolationOut[0] = violation;

			int minV = CapSepCutUtils.compVehiclesForSet(noOfCustomers, null, nodeList, nodeListSize, demand, cap);
			double rhs = nodeListSize - minV;

			if (violation >= EPS_VIOLATION)
			{
				Arrays.sort(nodeList, 1, nodeListSize + 1);
				cutsCMP.addCut(nodeList, nodeListSize, rhs);
			}

			// Always store in origCapCutsRPtr (for AddDropCapsOnGS dedup).
			origCapCutsRPtr.setForwList(nodeList, cutNr, nodeListSize);
			tmpList[1] = nodeSum;
			origCapCutsRPtr.setBackwList(tmpList, cutNr, 1);
		}

		// ------------------------------------------------------------------
		// Step 11: GRSEARCH_AddDropCapsOnGS — local search from existing cuts
		// (only if quota not yet reached).
		// ------------------------------------------------------------------
		if (generatedCuts < maxNoOfCuts)
		{
			int[] noOfGeneratedSetsOut =
			{ cutsBeforeLastProc };

			CapSepGrSearch.addDropCapsOnGS(sAdjRPtr, noOfCustomers, shrunkGraphCustNodes, superDemand, cap,
					superNodeSize, xInSuperNode, superNodesRPtr, sMatrix, EPS_VIOLATION, cmpExistingCuts,
					noOfGeneratedSetsOut, maxNoOfCuts, origCapCutsRPtr);

			generatedCuts = noOfGeneratedSetsOut[0];

			// Step 12: Expand and filter new cuts (already in original-customer format).
			for (int cutNr = cutsBeforeLastProc + 1; cutNr <= generatedCuts; cutNr++)
			{
				int nodeListSize = origCapCutsRPtr.forwCount(cutNr);
				for (int i = 1; i <= nodeListSize; i++)
				{
					nodeList[i] = origCapCutsRPtr.forwNeighbor(cutNr, i);
				}

				double violation = computeViolation(supportPtr, noOfCustomers, demand, cap, xMatrix, nodeList,
						nodeListSize);
				if (violation > maxViolationOut[0])
					maxViolationOut[0] = violation;

				int minV = CapSepCutUtils.compVehiclesForSet(noOfCustomers, null, nodeList, nodeListSize, demand, cap);
				double rhs = nodeListSize - minV;

				if (violation >= EPS_VIOLATION)
				{
					// nodeList is already sorted (AddDropCapsOnGS iterates i=1..n in order).
					cutsCMP.addCut(nodeList, nodeListSize, rhs);
				}
			}
		}

		return cutsCMP;
	}
}
