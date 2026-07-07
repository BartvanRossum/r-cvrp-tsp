package vehicleRouting.columnGeneration.pricing;

import java.util.LinkedHashSet;
import java.util.Set;

import graph.structures.digraph.DirectedGraph;
import optimisation.columnGeneration.pricing.AbstractPricingProblem;
import util.Pair;
import vehicleRouting.instance.CVRPInstance;
import vehicleRouting.instance.CustomerLoadNode;

public class CVRPPricingProblem extends AbstractPricingProblem<CVRPInstance>
{
	private final DirectedGraph<CustomerLoadNode, Integer> graph;
	private final int numCustomers;

	private double[][] transitionDuals;
	private double distanceDual;

	private int distanceLB = 0;
	private int distanceUB = Integer.MAX_VALUE;

	private Set<Integer> forbiddenNodes;
	private Set<Pair<Integer, Integer>> forbiddenArcs;

	public CVRPPricingProblem(int index, CVRPInstance instance)
	{
		super(index);

		this.graph = instance.getGraph(index);
		this.numCustomers = instance.getNumCustomers();

		resetDuals();

		this.forbiddenNodes = new LinkedHashSet<>();
		this.forbiddenArcs = new LinkedHashSet<>();
	}

	public int getNumCustomers()
	{
		return numCustomers;
	}

	public void addForbiddenNode(int customer)
	{
		forbiddenNodes.add(customer);
	}

	public int getDistanceLB()
	{
		return distanceLB;
	}

	public void setDistanceLB(int value)
	{
		this.distanceLB = Math.max(distanceLB, value);
	}

	public int getDistanceUB()
	{
		return distanceUB;
	}

	public void setDistanceUB(int value)
	{
		this.distanceUB = Math.min(distanceUB, value);
	}

	public Set<Integer> getForbiddenNodes()
	{
		return forbiddenNodes;
	}

	public Set<Pair<Integer, Integer>> getForbiddenArcs()
	{
		return forbiddenArcs;
	}

	public void addForbiddenArc(Pair<Integer, Integer> arc)
	{
		forbiddenArcs.add(arc);
	}

	public DirectedGraph<CustomerLoadNode, Integer> getGraph()
	{
		return graph;
	}

	public void resetDuals()
	{
		resetFixedDual();
		transitionDuals = new double[numCustomers + 1][numCustomers + 1];
		distanceDual = 0;
	}

	public double getDistanceDual()
	{
		return distanceDual;
	}

	public void addDistanceDual(double dual)
	{
		distanceDual += dual;
	}

	public void addTransitionDual(int from, int to, double dual)
	{
		transitionDuals[from][to] += dual;
	}

	public double[][] getTransitionDuals()
	{
		return transitionDuals;
	}
}
