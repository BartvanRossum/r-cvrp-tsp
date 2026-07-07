package vehicleRouting.columnGeneration;

import java.util.Objects;

public class RouteColumn extends CVRPColumn
{
	private final Route route;

	public RouteColumn(Route route)
	{
		super(0, true, true);

		this.route = route;
	}

	public Route getRoute()
	{
		return route;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(route);
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
		RouteColumn other = (RouteColumn) obj;
		return Objects.equals(route, other.route);
	}

	@Override
	public String toString()
	{
		return "RouteColumn [route=" + route + "]";
	}
}
