package vehicleRouting.columnGeneration.cuts.capsep;

import java.util.Arrays;

/**
 * Indexed list structure equivalent to ReachPtr / ReachTopRec in CVRPSEP
 * (BASEGRPH module). Each 1-based index i has an independent forward list
 * (FAL, size CFN) and backward list (BAL, size CBN).
 *
 * <p>This structure is used for multiple purposes throughout the capsep
 * package, matching the varied uses of ReachPtr in the C code:</p>
 * <ul>
 *   <li>Undirected customer support graph (SupportPtr, CmprsEdgesRPtr,
 *       SAdjRPtr)</li>
 *   <li>Component/supernode membership lists (CompsRPtr, SuperNodesRPtr)</li>
 *   <li>Cut records with a forward customer list and a backward node-sum
 *       entry (CapCutsRPtr, OrigCapCutsRPtr, AntiSetsRPtr,
 *       HistoryRPtr)</li>
 * </ul>
 *
 * <p>All indices are 1-based, consistent with the C convention.</p>
 */
public class CapSepGraph {

    /** LP[i].FAL: forward adjacency/list for node i (1-indexed elements). */
    private int[][] forw;
    /** LP[i].CFN: number of elements in the forward list of node i. */
    private int[] forwCnt;
    /** Internal capacity of each forward list row. */
    private int[] forwCap;

    /** LP[i].BAL: backward adjacency/list for node i (1-indexed elements). */
    private int[][] back;
    /** LP[i].CBN: number of elements in the backward list of node i. */
    private int[] backCnt;
    /** Internal capacity of each backward list row. */
    private int[] backCap;

    /** Allocated number of node slots (indices 1..dim are valid). */
    private int dim;

    private static final int INIT_ROW_CAP = 4;

    /**
     * Allocates a new CapSepGraph with space for {@code dim} node indices
     * (1-indexed). Equivalent to ReachInitMem(&ptr, dim).
     */
    public CapSepGraph(int dim) {
        this.dim = dim;
        forw    = new int[dim + 1][];
        forwCnt = new int[dim + 1];
        forwCap = new int[dim + 1];
        back    = new int[dim + 1][];
        backCnt = new int[dim + 1];
        backCap = new int[dim + 1];
        for (int i = 0; i <= dim; i++) {
            forw[i] = new int[INIT_ROW_CAP];
            forwCap[i] = INIT_ROW_CAP;
            back[i] = new int[INIT_ROW_CAP];
            backCap[i] = INIT_ROW_CAP;
        }
    }

    // -------------------------------------------------------------------------
    // Forward list operations
    // -------------------------------------------------------------------------

    /**
     * Appends {@code j} to the forward list of node {@code i}.
     * Equivalent to ReachAddForwArc(ptr, i, j).
     */
    public void addForwArc(int i, int j) {
        if (i > dim) expand(Math.max(i, dim * 2));
        if (forwCnt[i] + 1 >= forwCap[i]) growForw(i);
        forw[i][++forwCnt[i]] = j;
    }

    private void growForw(int i) {
        int newCap = forwCap[i] * 2;
        forw[i] = Arrays.copyOf(forw[i], newCap);
        forwCap[i] = newCap;
    }

    /**
     * Sets the forward list of node {@code index} to {@code list[1..size]}.
     * Equivalent to ReachSetForwList(ptr, list, index, size).
     *
     * @param list  1-indexed source array; elements at positions 1..size are copied
     * @param index 1-based node index
     * @param size  number of elements to copy
     */
    public void setForwList(int[] list, int index, int size) {
        if (index > dim) expand(Math.max(index, dim * 2));
        if (forw[index] == null || forw[index].length <= size) {
            forw[index] = new int[size + 1];
            forwCap[index] = size + 1;
        }
        forwCnt[index] = size;
        if (size > 0) System.arraycopy(list, 1, forw[index], 1, size);
    }

    /**
     * Clears all forward lists (sets counts to 0).
     * Equivalent to ReachClearForwLists(ptr).
     */
    public void clearForwLists() {
        Arrays.fill(forwCnt, 0, dim + 1, 0);
    }

    /** Returns LP[i].CFN: number of elements in the forward list of node i. */
    public int forwCount(int i) { return forwCnt[i]; }

    /** Returns LP[i].FAL[k] (k is 1-based): the k-th forward element of node i. */
    public int forwNeighbor(int i, int k) { return forw[i][k]; }

    /** Returns the raw forward array for node i (1-indexed, valid at [1..forwCount(i)]). */
    public int[] forwArray(int i) { return forw[i]; }

    // -------------------------------------------------------------------------
    // Backward list operations
    // -------------------------------------------------------------------------

    /**
     * Sets the backward list of node {@code index} to {@code list[1..size]}.
     * Equivalent to ReachSetBackwList(ptr, list, index, size).
     */
    public void setBackwList(int[] list, int index, int size) {
        if (index > dim) expand(Math.max(index, dim * 2));
        if (back[index] == null || back[index].length <= size) {
            back[index] = new int[size + 1];
            backCap[index] = size + 1;
        }
        backCnt[index] = size;
        if (size > 0) System.arraycopy(list, 1, back[index], 1, size);
    }

    /** Returns LP[i].CBN: number of elements in the backward list of node i. */
    public int backCount(int i) { return backCnt[i]; }

    /** Returns LP[i].BAL[k] (k is 1-based): the k-th backward element of node i. */
    public int backNeighbor(int i, int k) { return back[i][k]; }

    // -------------------------------------------------------------------------
    // Structural operations
    // -------------------------------------------------------------------------

    /**
     * Expands the structure so that at least {@code newDim} node indices are
     * supported. Equivalent to ReachPtrExpandDim(ptr, newDim).
     */
    public void expand(int newDim) {
        if (newDim <= dim) return;
        forw    = Arrays.copyOf(forw,    newDim + 1);
        forwCnt = Arrays.copyOf(forwCnt, newDim + 1);
        forwCap = Arrays.copyOf(forwCap, newDim + 1);
        back    = Arrays.copyOf(back,    newDim + 1);
        backCnt = Arrays.copyOf(backCnt, newDim + 1);
        backCap = Arrays.copyOf(backCap, newDim + 1);
        for (int i = dim + 1; i <= newDim; i++) {
            forw[i]    = new int[INIT_ROW_CAP]; forwCap[i] = INIT_ROW_CAP;
            back[i]    = new int[INIT_ROW_CAP]; backCap[i] = INIT_ROW_CAP;
        }
        dim = newDim;
    }

    /**
     * Returns a deep copy of this graph.
     * Equivalent to CopyReachPtr(src, &dst).
     */
    public CapSepGraph copy() {
        CapSepGraph c = new CapSepGraph(dim);
        for (int i = 0; i <= dim; i++) {
            if (forwCnt[i] > 0) {
                c.forw[i] = Arrays.copyOf(forw[i], forw[i].length);
                c.forwCnt[i] = forwCnt[i];
                c.forwCap[i] = forwCap[i];
            }
            if (backCnt[i] > 0) {
                c.back[i] = Arrays.copyOf(back[i], back[i].length);
                c.backCnt[i] = backCnt[i];
                c.backCap[i] = backCap[i];
            }
        }
        return c;
    }

    /** Returns the current allocated dimension. */
    public int getDim() { return dim; }
}
