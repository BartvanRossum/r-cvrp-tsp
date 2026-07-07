package vehicleRouting.columnGeneration.cuts.capsep;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores a list of rounded capacity cut records (CMGR_CT_CAP type only). Serves
 * as a lightweight replacement for CnstrMgrPointer in the capsep package.
 *
 * <p>
 * Each cut record stores:
 * </p>
 * <ul>
 * <li>{@code IntList[1..IntListSize]}: 1-indexed customer indices in set S</li>
 * <li>{@code rhs}: right-hand side in form (i), i.e. |S| - k(S)</li>
 * </ul>
 *
 * <p>
 * Cuts are 0-indexed when accessed (consistent with the
 * {@code for (CutNr=0; CutNr<CMP->Size; CutNr++)} loops in GRSEARCH).
 * </p>
 *
 * <p>
 * Used for both the "new cuts" output (CutsCMP) and the persistent "previously
 * found cuts" input (CMPExistingCuts) that is accumulated across separation
 * calls in RCCCutSeparator.
 * </p>
 */
public class CapSepExistingCuts
{

	/** IntList per cut: each entry is a 1-indexed array of customer indices. */
	private final List<int[]> intLists = new ArrayList<>();
	/** IntListSize per cut. */
	private final List<Integer> intListSizes = new ArrayList<>();
	/** RHS per cut: |S| - k(S) in cut form x(S:S) <= |S| - k(S). */
	private final List<Double> rhsValues = new ArrayList<>();

	/** Returns the number of stored cuts (CMP->Size). */
	public int size()
	{
		return intLists.size();
	}

	/**
	 * Appends a new cut.
	 *
	 * @param list     1-indexed array; elements at list[1..listSize] are the
	 *                 customer indices in S
	 * @param listSize number of customers in S (IntListSize)
	 * @param rhs      right-hand side = |S| - k(S)
	 */
	public void addCut(int[] list, int listSize, double rhs)
	{
		int[] copy = new int[listSize + 1]; // index 0 unused, 1..listSize valid
		System.arraycopy(list, 1, copy, 1, listSize);
		intLists.add(copy);
		intListSizes.add(listSize);
		rhsValues.add(rhs);
	}

	/**
	 * Returns the 1-indexed customer array for cut {@code i} (0-based i). Valid
	 * positions: [1..getIntListSize(i)].
	 */
	public int[] getIntList(int i)
	{
		return intLists.get(i);
	}

	/** Returns the number of customers in cut {@code i} (0-based i). */
	public int getIntListSize(int i)
	{
		return intListSizes.get(i);
	}

	/** Returns the RHS of cut {@code i} (0-based i). */
	public double getRhs(int i)
	{
		return rhsValues.get(i);
	}

	/** Appends all cuts from {@code other} into this container. */
	public void appendAll(CapSepExistingCuts other)
	{
		intLists.addAll(other.intLists);
		intListSizes.addAll(other.intListSizes);
		rhsValues.addAll(other.rhsValues);
	}
}
