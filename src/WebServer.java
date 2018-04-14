import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.Main;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.CantGenerateOutputFileException;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.CantReadMazeInputFileException;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.InvalidCoordinatesException;
import pt.ulisboa.tecnico.meic.cnv.mazerunner.maze.exceptions.InvalidMazeRunningStrategyException;

public class WebServer {

	private static ExecutorService executor;
	
	private static long previous = 0;

	public static void main(String[] args) throws Exception {
		
		Process p = Runtime.getRuntime().exec("pwd");
		p.waitFor();

		BufferedReader reader = 
		  new BufferedReader(new InputStreamReader(p.getInputStream()));

		String line = "";           
		while ((line = reader.readLine())!= null) {
		   System.out.println(line);
		}
		
		
		HttpServer server = HttpServer.create(new InetSocketAddress(8070), 0);

		executor = Executors.newFixedThreadPool(10);

		server.createContext("/test", new MyHandler());
		server.setExecutor(executor); // creates a default executor
		server.start();




	}

	static class MyHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {

			String query = t.getRequestURI().getQuery();

			String response = "This was the query:" + query 
								+ "##";

			String [] parts;
			
			if (query == null || (parts = query.split("&")).length < 8){
				response = "InsuficientArguments - The maze runners do not have enough information to solve the maze";
				System.out.println("Bad Query");
				t.sendResponseHeaders(200, response.length());
				OutputStream os = t.getResponseBody();
				os.write(response.getBytes());
				os.close();
				return;
			}
			
			parts[6] = "mazes/"+parts[6];
			parts[7] = "mazes/"+parts[7];

			try {
				Runtime runtime = Runtime.getRuntime();

				runtime.gc();

				long startTime = System.currentTimeMillis();
				Main.main(parts);
				long estimatedTime = System.currentTimeMillis() - startTime;

				long memory = runtime.totalMemory() - runtime.freeMemory();

				response = query.replaceAll("&", " ") + " | Time : "+estimatedTime+"ms | Memory : "+(memory/(1024L * 1024L))+"mb";

				System.out.println(response);
				
				
				//				System.out.println("Estimated time to run Maze" + parts[6] + "Start Pos: " + parts[0]+","+ parts[1] +
				//																			 "End Pos: " + parts[2]+","+ parts[3] + " : "+ estimatedTime+"ms");
			} catch (InvalidMazeRunningStrategyException | CantGenerateOutputFileException
					| CantReadMazeInputFileException e) {
				e.printStackTrace();
			}catch (InvalidCoordinatesException e) {
				System.out.println("Invalid Input : " + query);
			}

			t.sendResponseHeaders(200, response.length());
			OutputStream os = t.getResponseBody();
			os.write(response.getBytes());
			os.close();


		}
	}

}