package vehicleRouting.models;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.Callback.Function;
import util.Pair;
import vehicleRouting.columnGeneration.Route;
import vehicleRouting.instance.CVRPInstance;

public class TwoIndexMILP
{
	private final CVRPInstance instance;
	private final int numVehicles;
	private final int numCustomers;

	private IloCplex cplex;

	private IloNumVar[][] arcVariables;
	private IloNumVar[] loadVariables;

	public TwoIndexMILP(CVRPInstance instance) throws IloException
	{
		// Initialisation.
		this.instance = instance;
		this.numCustomers = instance.getNumCustomers();
		this.numVehicles = instance.getNumVehicles();

		this.cplex = new IloCplex();
		cplex.setOut(null);

		this.arcVariables = new IloNumVar[numCustomers + 1][numCustomers + 1];
		this.loadVariables = new IloNumVar[numCustomers];

		// Add variables.
		addArcVariables();
		addLoadVariables();

		// Add constraints.
		addFlowConstraints();
		addLoadConstraints();
		addNumberVehicleConstraints();

		// Add objective.
		addObjective();
	}

	public void setOut(OutputStream out)
	{
		cplex.setOut(out);
	}

	public void addCallback(Function callback, long contextMask) throws IloException
	{
		cplex.use(callback, contextMask);
	}

	public IloNumVar[][] getArcVariables()
	{
		return arcVariables;
	}

	public void solve() throws IloException
	{
		cplex.solve();
	}

	public void clean() throws IloException
	{
		cplex.clearModel();
		cplex.end();
	}

	public double getObjectiveValue() throws IloException
	{
		return cplex.getObjValue();
	}

	private void addArcVariables() throws IloException
	{
		for (int i = 0; i <= numCustomers; i++)
		{
			for (int j = 0; j <= numCustomers; j++)
			{
				arcVariables[i][j] = cplex.boolVar();
				if (i == j)
				{
					arcVariables[i][j].setUB(0);
				}
			}
		}
	}

	private void addLoadVariables() throws IloException
	{
		for (int i = 0; i < numCustomers; i++)
		{
			loadVariables[i] = cplex.numVar(instance.getDemands()[i], instance.getCapacity());
		}
	}

	private void addFlowConstraints() throws IloException
	{
		for (int i = 1; i <= numCustomers; i++)
		{
			IloNumExpr lhs = cplex.constant(0);
			IloNumExpr rhs = cplex.constant(0);
			for (int j = 0; j <= numCustomers; j++)
			{
				lhs = cplex.sum(lhs, arcVariables[i][j]);
				rhs = cplex.sum(rhs, arcVariables[j][i]);
			}
			cplex.addEq(lhs, 1);
			cplex.addEq(rhs, 1);
		}
	}

	private void addNumberVehicleConstraints() throws IloException
	{
		IloNumExpr flowOut = cplex.constant(0);
		for (int i = 1; i <= numCustomers; i++)
		{
			flowOut = cplex.sum(flowOut, arcVariables[0][i]);
		}
		cplex.addEq(flowOut, numVehicles);
	}

	private void addLoadConstraints() throws IloException
	{
		for (int i = 1; i <= numCustomers; i++)
		{
			for (int j = 1; j <= numCustomers; j++)
			{
				IloNumExpr lhs = cplex.constant(0);
				lhs = cplex.sum(lhs, loadVariables[j - 1]);
				lhs = cplex.sum(lhs, cplex.prod(-1, loadVariables[i - 1]));

				IloNumExpr rhs = cplex.constant(instance.getDemands()[j - 1] - instance.getCapacity());
				rhs = cplex.sum(rhs, cplex.prod(instance.getCapacity(), arcVariables[i][j]));
				cplex.addGe(lhs, rhs);
			}
		}
	}

	private void addObjective() throws IloException
	{
		IloNumExpr obj = cplex.constant(0);
		for (int i = 0; i <= numCustomers; i++)
		{
			for (int j = 0; j <= numCustomers; j++)
			{
				obj = cplex.sum(obj, cplex.prod(instance.getDistances()[i][j], arcVariables[i][j]));
			}
		}
		cplex.addMinimize(obj);
	}

	public List<Route> getRoutes() throws IloException
	{
		List<Route> routes = new ArrayList<>();
		int start = 1;
		int vehicleIndex = 0;
		while (true)
		{
			int currentIndex = 0;
			boolean go = false;

			for (int i = start; i <= numCustomers; i++)
			{
				if (cplex.getValue(arcVariables[0][i]) > 0.5)
				{
					currentIndex = i;
					start = i + 1;
					go = true;
					break;
				}
			}

			if (!go)
			{
				break;
			}

			List<Integer> customers = new ArrayList<>();
			List<Pair<Integer, Integer>> arcs = new ArrayList<>();
			customers.add(currentIndex);
			arcs.add(new Pair<>(0, currentIndex));
			int load = 0;
			int distance = 0;
			distance += instance.getDistances()[0][currentIndex];
			load += instance.getDemands()[currentIndex - 1];
			while (true)
			{
				for (int j = 0; j <= numCustomers; j++)
				{
					if (cplex.getValue(arcVariables[currentIndex][j]) > 0.5)
					{
						if (j != 0)
						{
							customers.add(j);
						}
						arcs.add(new Pair<>(currentIndex, j));
						distance += instance.getDistances()[currentIndex][j];
						if (j > 0)
						{
							load += instance.getDemands()[j - 1];
						}
						currentIndex = j;
						break;
					}
				}
				if (currentIndex == 0)
				{
					break;
				}
			}
			routes.add(new Route(vehicleIndex, distance, load, customers, arcs));
			vehicleIndex++;
		}
		return routes;
	}
}