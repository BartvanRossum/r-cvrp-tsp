package vehicleRouting.data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import vehicleRouting.instance.CVRPInstance;

public class LibReader
{
	public static CVRPInstance readInstance(int K, String file) throws IOException
	{
		BufferedReader bufferedReader = new BufferedReader(new FileReader(file));

		// Skip lines.
		String line = bufferedReader.readLine();
		bufferedReader.readLine();
		bufferedReader.readLine();

		// Read N.
		line = bufferedReader.readLine();
		String[] data = line.split("\\s+");
		int N = Integer.valueOf(data[2]) - 1;

		// Read Q.
		bufferedReader.readLine();
		line = bufferedReader.readLine();
		data = line.split("\\s+");
		int Q = Integer.valueOf(data[2]);

		// Read coordinates.
		bufferedReader.readLine();
		int[] x = new int[N + 1];
		int[] y = new int[N + 1];
		for (int i = 0; i <= N; i++)
		{
			line = bufferedReader.readLine();
			data = line.split("\\s+");
			x[i] = Integer.valueOf(data[1]);
			y[i] = Integer.valueOf(data[2]);
		}

		// Read demands.
		bufferedReader.readLine();
		bufferedReader.readLine();
		int[] demands = new int[N];
		for (int i = 0; i < N; i++)
		{
			line = bufferedReader.readLine();
			data = line.split("\\s+");
			demands[i] = Integer.valueOf(data[1]);
		}
		bufferedReader.close();

		// Compute distances.
		int[][] distances = new int[N + 1][N + 1];
		for (int i = 0; i <= N; i++)
		{
			for (int j = 0; j <= N; j++)
			{
				double distance = Math.sqrt(Math.pow(x[i] - x[j], 2) + Math.pow(y[i] - y[j], 2));
				distances[i][j] = (int) Math.ceil(distance);
			}
		}
		return new CVRPInstance(N, K, Q, demands, distances, false);
	}
}
