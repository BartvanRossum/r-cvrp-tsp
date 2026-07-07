
package vehicleRouting.data;

import java.io.IOException;
import java.util.List;

import ilog.concert.IloException;
import ilog.cplex.IloCplex;
import util.Writer;
import vehicleRouting.columnGeneration.Route;
import vehicleRouting.instance.CVRPInstance;
import vehicleRouting.models.TwoIndexMILP;
import vehicleRouting.models.TwoIndexRoundedCapacityCuts;

public class GenerateEfficientSolutions
{
	public static void main(String[] args) throws IOException, IloException
	{
		// Settings.
		int numPeriods = 20;
		int[] customers =
		{ 30 };
		int numVehicles = 5;

		for (int period = 0; period < numPeriods; period++)
		{
			for (int numCustomers : customers)
			{
				String file = "dataCVRP/instances/n" + numCustomers + "_k" + numVehicles + "_" + period + ".txt";

				// Read in instance.
				CVRPInstance instance = new CVRPInstance(numCustomers, numVehicles, file, false);

				// MILP.
				TwoIndexMILP model = new TwoIndexMILP(instance);
				long contextMask = IloCplex.Callback.Context.Id.Relaxation;
				model.addCallback(new TwoIndexRoundedCapacityCuts(instance, model.getArcVariables()), contextMask);
				model.setOut(System.out);
				model.solve();

				// Write routes to file.
				List<Route> routes = model.getRoutes();
				for (Route route : routes)
				{
					System.out.println(route);
				}
				Writer.write(routes,
						"dataCVRP/solutions/solution_n" + numCustomers + "_k" + numVehicles + "_" + period + ".txt");

				model.clean();
				System.out.println("Solved: " + period + " " + numCustomers);
			}
		}
	}
}
