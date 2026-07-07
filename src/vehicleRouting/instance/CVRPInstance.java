package vehicleRouting.instance;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import graph.structures.digraph.DirectedGraph;
import optimisation.columnGeneration.AbstractInstance;

public class CVRPInstance extends AbstractInstance
{
	private final int numCustomers;
	private final int numVehicles;

	private final int capacity;
	private final int[] demands;
	private final int[][] distances;

	private final Map<Integer, DirectedGraph<CustomerLoadNode, Integer>> graphMap;
	
	public CVRPInstance(int numCustomers, int numVehicles, int capacity, int[] demands, int[][] distances, boolean initialiseGraphs)
	{
		this.numCustomers = numCustomers;
		this.numVehicles = numVehicles;
		
		this.capacity = capacity;
		this.demands = demands;
		this.distances = distances;
		
		if (initialiseGraphs)
		{
			graphMap = computeGraphMap();
		}
		else
		{
			graphMap = new LinkedHashMap<>();
		}
		
	}

	public CVRPInstance(int numCustomers, int numVehicles, String file, boolean initialiseGraphs) throws IOException
	{
		this.numCustomers = numCustomers;
		this.numVehicles = numVehicles;

		demands = new int[numCustomers];
		distances = new int[numCustomers + 1][numCustomers + 1];

		// Initialise reader.
		BufferedReader bufferedReader = new BufferedReader(new FileReader(file));

		// Read Q.
		String line = bufferedReader.readLine();
		line = bufferedReader.readLine();
		capacity = Integer.valueOf(line);

		// Read N.
		line = bufferedReader.readLine();
		line = bufferedReader.readLine();
		int N = Integer.valueOf(line);

		// Read demands.
		line = bufferedReader.readLine();
		for (int i = 0; i < N; i++)
		{
			line = bufferedReader.readLine();
			String[] data = line.split("\\s+");
			demands[i] = Integer.valueOf(data[1]);
		}

		// Read distances.
		line = bufferedReader.readLine();
		for (int i = 0; i <= N; i++)
		{
			line = bufferedReader.readLine();
			String[] data = line.split("\\s+");
			for (int j = 0; j <= N; j++)
			{
				distances[i][j] = (int) Math.rint(Double.valueOf(data[j]));
			}
		}
		bufferedReader.close();
		if (initialiseGraphs)
		{
			graphMap = computeGraphMap();
		}
		else
		{
			graphMap = new LinkedHashMap<>();
		}
	}

	private Map<Integer, DirectedGraph<CustomerLoadNode, Integer>> computeGraphMap()
	{
		// Construct one graph per last customer.
		Map<Integer, DirectedGraph<CustomerLoadNode, Integer>> graphMap = new LinkedHashMap<>();
		for (int lastCustomer = 1; lastCustomer <= numCustomers; lastCustomer++)
		{
			DirectedGraph<CustomerLoadNode, Integer> graph = new DirectedGraph<>();

			// Compute ng-neighbourhoods once.
			Map<Integer, Integer> neighbourhoodMap = new LinkedHashMap<>();
			for (int i = 0; i <= numCustomers; i++)
			{
				neighbourhoodMap.put(i, computeBitwiseNeighbours(i));
			}

			// Initialise a single source, depot with load 0.
			CustomerLoadNode source = new CustomerLoadNode(0, 0, 0, neighbourhoodMap.get(0));
			graph.addNode(source);

			// Initialise a node for each combination of customer and load.
			// Also build lookup table: nodeByCustomerLoad[customer][load] for O(1) access.
			CustomerLoadNode[][] nodeByCustomerLoad = new CustomerLoadNode[numCustomers + 1][capacity + 1];
			for (int i = 1; i <= numCustomers; i++)
			{
				int demand = demands[i - 1];
				for (int load = demand; load <= capacity; load++)
				{
					CustomerLoadNode customerNode = new CustomerLoadNode(i, demand, load, neighbourhoodMap.get(i));
					graph.addNode(customerNode);
					nodeByCustomerLoad[i][load] = customerNode;
				}
			}

			// Initialise a single sink, with artificial load equal to the capacity + 1.
			CustomerLoadNode sink = new CustomerLoadNode(0, 0, capacity + 1, neighbourhoodMap.get(0));
			graph.addNode(sink);

			// SOURCE arcs: source -> first customer (load must equal demand, customer <=
			// lastCustomer).
			for (int i = 1; i <= lastCustomer; i++)
			{
				CustomerLoadNode to = nodeByCustomerLoad[i][demands[i - 1]];
				graph.addArc(source, to, distances[0][i], distances[0][i]);
			}

			// SINK arcs: lastCustomer -> sink (any load).
			for (int load = demands[lastCustomer - 1]; load <= capacity; load++)
			{
				CustomerLoadNode from = nodeByCustomerLoad[lastCustomer][load];
				graph.addArc(from, sink, distances[lastCustomer][0], distances[lastCustomer][0]);
			}

			// CUSTOMER-CUSTOMER arcs: load of to must equal load of from plus demand of to.
			for (int i = 1; i <= numCustomers; i++)
			{
				// No arc leaves the last customer to another customer.
				if (i == lastCustomer)
				{
					continue;
				}
				for (int loadFrom = demands[i - 1]; loadFrom <= capacity; loadFrom++)
				{
					CustomerLoadNode from = nodeByCustomerLoad[i][loadFrom];
					for (int j = 1; j <= numCustomers; j++)
					{
						if (j == i)
						{
							continue;
						}
						int requiredLoad = loadFrom + demands[j - 1];
						if (requiredLoad > capacity)
						{
							continue;
						}
						CustomerLoadNode to = nodeByCustomerLoad[j][requiredLoad];
						graph.addArc(from, to, distances[i][j], distances[i][j]);
					}
				}
			}

			// Remove all customer nodes that are not connected.
			while (true)
			{
				Set<CustomerLoadNode> remove = new LinkedHashSet<>();
				for (CustomerLoadNode node : graph.getNodes())
				{
					if (node.equals(source) || node.equals(sink))
					{
						continue;
					}
					if (graph.getInDegree(node) == 0 || graph.getOutDegree(node) == 0)
					{
						remove.add(node);
					}
				}
				if (remove.size() > 0)
				{
					for (CustomerLoadNode node : remove)
					{
						graph.removeNode(node);
					}
				}
				else
				{
					break;
				}
			}

			// Sort nodes and set indices.
			graph.sortNodes(new NodeComparator());
			graph.setNodeIndices();

			// Store in graph map.
			graphMap.put(lastCustomer, graph);
		}
		return graphMap;
	}

	private int computeBitwiseNeighbours(int customer)
	{
		Set<Integer> neighbours = new LinkedHashSet<>();
		if (customer == 0)
		{
			neighbours.add(customer);
		}
		else
		{
			int[] intArray = IntStream.range(1, numCustomers + 1).boxed()
					.sorted(Comparator.comparing(i -> distances[customer][i])).mapToInt(Integer::intValue).toArray();
			for (int i = 0; i <= CVRPConstants.NG_SIZE; i++)
			{
				neighbours.add(intArray[i]);
			}
		}
		return BitwiseNG.getBitwiseRepresentation(neighbours);
	}

	public DirectedGraph<CustomerLoadNode, Integer> getGraph(int lastCustomer)
	{
		return graphMap.get(lastCustomer);
	}

	public int getNumCustomers()
	{
		return numCustomers;
	}

	public int getNumVehicles()
	{
		return numVehicles;
	}

	public int getCapacity()
	{
		return capacity;
	}

	@Override
	public String toString()
	{
		return "instance";
	}

	public int[] getDemands()
	{
		return demands;
	}

	public int[][] getDistances()
	{
		return distances;
	}

	private class NodeComparator implements Comparator<CustomerLoadNode>
	{
		@Override
		public int compare(CustomerLoadNode o1, CustomerLoadNode o2)
		{
			if (o1.getLoad() == o2.getLoad())
			{
				return Integer.compare(o1.getCustomer(), o2.getCustomer());
			}
			return Integer.compare(o1.getLoad(), o2.getLoad());
		}
	}
}
