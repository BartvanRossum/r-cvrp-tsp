package vehicleRouting.columnGeneration;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import util.Pair;

public class Route
{
	private final int vehicle;
	private final int distance;
	private final int load;
	private final List<Integer> customers;
	private final List<Pair<Integer, Integer>> arcs;

	public Route(int vehicle, int distance, int load, List<Integer> customers, List<Pair<Integer, Integer>> arcs)
	{
		this.vehicle = vehicle;
		this.distance = distance;
		this.load = load;
		this.customers = customers;
		this.arcs = arcs;
	}

	public int getVehicle()
	{
		return vehicle;
	}

	public int getDistance()
	{
		return distance;
	}

	public int getLoad()
	{
		return load;
	}

	public boolean containsCustomer(int customer)
	{
		return customers.contains(customer);
	}

	public List<Integer> getCustomers()
	{
		return customers;
	}

	public int getLastCustomer()
	{
		return customers.get(customers.size() - 1);
	}

	public List<Pair<Integer, Integer>> getArcs()
	{
		return arcs;
	}

	public static List<Route> readRoutes(String file) throws IOException
	{
		List<Route> routes = new ArrayList<>();
		BufferedReader bufferedReader = new BufferedReader(new FileReader(file));
		String line = bufferedReader.readLine();
		while (line != null)
		{
			routes.add(readRoute(line));
			line = bufferedReader.readLine();
		}
		bufferedReader.close();
		return routes;
	}

	public static Route readRoute(String line)
	{
		String[] data = line.split(" ");
		int vehicle = Integer.valueOf(data[0]);
		int distance = Integer.valueOf(data[1]);
		int load = Integer.valueOf(data[2]);
		List<Integer> customers = new ArrayList<>();
		List<Pair<Integer, Integer>> arcs = new ArrayList<>();
		int currentCustomer = 0;
		for (int i = 3; i < data.length; i++)
		{
			int customer = Integer.valueOf(data[i]);
			customers.add(customer);
			arcs.add(new Pair<>(currentCustomer, customer));
			currentCustomer = customer;
		}
		arcs.add(new Pair<>(currentCustomer, 0));
		return new Route(vehicle, distance, load, customers, arcs);
	}

	@Override
	public String toString()
	{
		String result = "" + vehicle + " " + distance + " " + load;
		for (int customer : customers)
		{
			result += " " + customer;
		}
		return result;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(arcs, customers, distance, load, vehicle);
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Route other = (Route) obj;
		return Objects.equals(arcs, other.arcs) && Objects.equals(customers, other.customers)
				&& distance == other.distance && load == other.load && vehicle == other.vehicle;
	}
}
