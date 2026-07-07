package vehicleRouting.columnGeneration.cuts.capsep;

/**
 * Utility functions for evaluating capacity cuts.
 * Equivalent to CUTBASE.CPP in CVRPSEP.
 *
 * <p>All customer node indices are 1-based (1..noOfCustomers). The depot
 * is node noOfCustomers+1. The flow matrix xMatrix is symmetric and
 * 1-indexed.</p>
 */
public class CapSepCutUtils {

    /**
     * Computes x(S:S) = sum of xMatrix[i][j] for all distinct pairs i&lt;j
     * where both i and j are customer nodes (i.e. ≤ noOfCustomers) and
     * both are in S. This is the "interior edge sum" used in cut form (i).
     * Equivalent to CUTBASE_CompXSumInSet.
     *
     * <p>Either {@code inNodeSet} or {@code nodeList} must be non-null (but
     * not both). When {@code inNodeSet} is null the set is described by
     * {@code nodeList[1..nodeListSize]}; otherwise it is described by
     * {@code inNodeSet[1..noOfCustomers]}.</p>
     *
     * @param supportPtr   undirected support graph (forward adjacency lists)
     * @param noOfCustomers n
     * @param inNodeSet    boolean membership array [1..n], or null
     * @param nodeList     1-indexed customer list, or null
     * @param nodeListSize number of valid entries in nodeList
     * @param xMatrix      symmetric undirected flow matrix [1..n+1][1..n+1]
     * @return x(S:S)
     */
    public static double compXSumInSet(CapSepGraph supportPtr,
                                        int noOfCustomers,
                                        boolean[] inNodeSet,
                                        int[] nodeList,
                                        int nodeListSize,
                                        double[][] xMatrix) {
        boolean[] inSet;
        if (inNodeSet == null) {
            inSet = new boolean[noOfCustomers + 1];
            for (int i = 1; i <= nodeListSize; i++) {
                inSet[nodeList[i]] = true;
            }
        } else {
            inSet = inNodeSet;
        }

        double xSum = 0.0;
        for (int i = 1; i < noOfCustomers; i++) {
            if (!inSet[i]) continue;
            int cfi = supportPtr.forwCount(i);
            for (int k = 1; k <= cfi; k++) {
                int j = supportPtr.forwNeighbor(i, k);
                if (j > i && j <= noOfCustomers && inSet[j]) {
                    xSum += xMatrix[i][j];
                }
            }
        }
        return xSum;
    }

    /**
     * Computes k(S) = ⌈sum_demand(S) / cap⌉ = the minimum number of vehicles
     * required to serve all customers in S.
     * Equivalent to CUTBASE_CompVehiclesForSet.
     *
     * @param noOfCustomers n
     * @param nodeInSet     boolean membership array [1..n], or null
     * @param nodeList      1-indexed customer list, or null
     * @param nodeListSize  number of valid entries in nodeList
     * @param demand        1-indexed demand array [1..n]
     * @param cap           vehicle capacity
     * @return minimum vehicles required, k(S)
     */
    public static int compVehiclesForSet(int noOfCustomers,
                                          boolean[] nodeInSet,
                                          int[] nodeList,
                                          int nodeListSize,
                                          int[] demand,
                                          int cap) {
        int demandSum = 0;
        if (nodeInSet == null) {
            for (int i = 1; i <= nodeListSize; i++) {
                demandSum += demand[nodeList[i]];
            }
        } else {
            for (int i = 1; i <= noOfCustomers; i++) {
                if (nodeInSet[i]) demandSum += demand[i];
            }
        }
        int minV = 1;
        int capSum = cap;
        while (capSum < demandSum) {
            minV++;
            capSum += cap;
        }
        return minV;
    }

    /**
     * Computes the violation of the capacity cut in form (i):
     * violation = x(S:S) − (|S| − k(S)).
     * A positive value means the cut is violated.
     * Equivalent to CUTBASE_CompCapViolation.
     */
    public static double compCapViolation(CapSepGraph supportPtr,
                                           int noOfCustomers,
                                           boolean[] nodeInSet,
                                           int[] nodeList,
                                           int nodeListSize,
                                           int[] demand,
                                           int cap,
                                           double[][] xMatrix) {
        double xSum = compXSumInSet(
                supportPtr, noOfCustomers, nodeInSet, nodeList, nodeListSize, xMatrix);
        int minV = compVehiclesForSet(
                noOfCustomers, nodeInSet, nodeList, nodeListSize, demand, cap);
        int setSize;
        if (nodeInSet != null) {
            setSize = 0;
            for (int i = 1; i <= noOfCustomers; i++) {
                if (nodeInSet[i]) setSize++;
            }
        } else {
            setSize = nodeListSize;
        }
        double rhs = setSize - minV;
        return xSum - rhs;
    }
}
