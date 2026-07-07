package vehicleRouting.columnGeneration;

public class MinColumn extends CVRPColumn
{
	public MinColumn()
	{
		super(-1, false, false);
	}

	@Override
	public int hashCode()
	{
		return 0;
	}

	@Override
	public String toString()
	{
		return "MinColumn";
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
		return true;
	}
}
