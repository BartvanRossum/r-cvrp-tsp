package vehicleRouting.models;

import java.util.Set;

import ilog.concert.IloException;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplexModeler;
import vehicleRouting.instance.CVRPInstance;

public class TwoIndexRoundedCapacityCuts implements IloCplex.Callback.Function
{
	private final static double EPSILON = 0.1;

	private final CVRPInstance instance;
	private final IloNumVar[][] arcVariables;

	public TwoIndexRoundedCapacityCuts(CVRPInstance instance, IloNumVar[][] arcVariables) throws IloException
	{
		this.instance = instance;
		this.arcVariables = arcVariables;
	}

	private void separateRoundedCapacityCuts(IloCplex.Callback.Context context) throws IloException
	{
		IloCplexModeler cplex = context.getCplex();
		int numCustomers = instance.getNumCustomers();
		double[][] arcValues = new double[numCustomers + 1][numCustomers + 1];
		for (int i = 0; i <= numCustomers; i++)
		{
			for (int j = 0; j <= numCustomers; j++)
			{
				arcValues[i][j] = context.getRelaxationPoint(arcVariables[i][j]);
			}
		}

		// Solve separation model.
		SeparationMILP model = new SeparationMILP(instance, arcValues);
		model.solve();
		if (model.getObjectiveValue() < -EPSILON)
		{
			// Add a cut.
			Set<Integer> S = model.getSolution();
			IloNumExpr lhs = cplex.constant(0);

			for (int i = 0; i <= numCustomers; i++)
			{
				for (int j = 0; j <= numCustomers; j++)
				{
					if (!S.contains(i))
					{
						continue;
					}
					if (S.contains(j))
					{
						continue;
					}
					lhs = cplex.sum(lhs, arcVariables[i][j]);
				}
			}

			// Compute minimum number of required trucks.
			double value = 0;
			for (int i = 1; i <= numCustomers; i++)
			{
				if (S.contains(i))
				{
					value += instance.getDemands()[i - 1];
				}
			}
			value = Math.ceil(value / instance.getCapacity());

			// Add violated cut.
			context.addUserCut(cplex.ge(lhs, value), IloCplex.CutManagement.UseCutFilter, false);
		}
		model.clean();
	}

	@Override
	public void invoke(IloCplex.Callback.Context context) throws IloException
	{
		// Separate cuts at root node only.
		if (context.inRelaxation() && context.getIntInfo(IloCplex.Callback.Context.Info.NodeDepth) == 0)
		{
			separateRoundedCapacityCuts(context);
		}
	}
}
