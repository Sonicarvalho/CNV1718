package webserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.util.IOUtils;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import webserver.models.Server;



public class LoadBalancer {

	private static ExecutorService executor;
	
    /*
     * Before running the code:
     *      Fill in your AWS access credentials in the provided credentials
     *      file template, and be sure to move the file to the default location
     *      (~/.aws/credentials) where the sample code will load the
     *      credentials from.
     *      https://console.aws.amazon.com/iam/home?#security_credential
     *
     * WARNING:
     *      To avoid accidental leakage of your credentials, DO NOT keep
     *      the credentials file in your source directory.
     */

	static int XS = 1;
	static int S = 2;
	static int M = 4;
	static int L = 8;
	static int XL = 12;
	
	static long averageInstructions = 0;
	
	static String ImageId = "";
	static int maximumWeight = 20;
	
	static ArrayList<Server> servers = new ArrayList<>();
    static AmazonEC2 ec2;
	
    public static void main(String[] args) throws Exception {
    	//Init AmazonEC2 connection
    	Init();
    	System.out.println("Amazon EC2 connection initialized!");
    	
    	//Init Auto Scaler Thread
    	AutoScaler autoScaler = new AutoScaler();
    	autoScaler.start();
    	
    	executor = Executors.newFixedThreadPool(10);
    	
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/mzrun.html", new MyHandler());
        server.createContext("/ping", new TestHandler());
        server.setExecutor(executor); // creates a default executor
        server.start();
        
        System.out.println("Web Server initialized!");
    }

    static class MyHandler implements HttpHandler {
    	
        @Override
        public void handle(HttpExchange t) throws IOException {
        	String response = "";
        	URL url;
        	String domain = "";
        	
        	// Get Query from URI
            String query = t.getRequestURI().getQuery();
            if(query == null){
            	//Sets response as BAD_REQUEST(400 http code)
                t.sendResponseHeaders(400, response.length());
                //Get response body
                OutputStream os = t.getResponseBody();
                //Writes the response in the output body
                os.write(response.getBytes());
                //Close connection with Client
                os.close();
            }
            
            //Parse Query
            System.out.println("Query: " + query);
            String [] partes = query.split("[&=]");
            String filename = partes[1];
            String x0 = partes[3];
            String y0= partes[5];
            String x1 = partes[7];
            String y1 = partes[9];
            String vel = partes[11];
            String strat = partes[13];
            
        	// Decide weight for the query
        	int weight = DecideWeight(Integer.parseInt(x0),Integer.parseInt(y0),Integer.parseInt(x1),Integer.parseInt(y1),Integer.parseInt(vel),strat,filename);
            
        	// Get choose server to execute the query
            Server server = ChooseServer(weight);
            
            if(server != null) {
            	//Construct Url => domain + query
            	String finalQuery = x0 + '&' +y0 + '&' +x1 + '&' +y1 + '&'+vel+ '&' +strat+ "&"+filename + ".maze&" + filename + ".html";
                
                System.out.println("Requesting:\nhttp://" + domain + ":8000/test?" + finalQuery);
                server.addWeight(weight);
                url = new URL("http://" + domain + ":8000/test?" + finalQuery);
                //Open Connection to server
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                //Send request to server and convert to string- Blocking Function
                response = IOUtils.toString(connection.getInputStream());
                server.subtractWeight(weight);
                
                //Sets response as OK(200 http code)
                t.sendResponseHeaders(200, response.length());
            }
            else {
            	response = "Couldn't find an AWS Instance";

                //Sets response as BAD REQUEST(400 http code)
                t.sendResponseHeaders(400, response.length());
            }
            //Get response body
            OutputStream os = t.getResponseBody();
            //Writes the response in the output body
            os.write(response.getBytes());
            //Close connection with Client
            os.close();
        }
    }
    

    
    static class TestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
        	Boolean alive = false;
        	int totalPingable = 0;
        	int totalWeight = 0;
        	
        	//Theres any Server available to request
        	for(Server server: servers){
        		if(server.ping()) {
        			totalPingable++;
        			totalWeight += server.getWeight();
        			alive = true;
        		}
        	}
        	String response;
        	if(alive) {
        		response = ((totalWeight/totalPingable) < maximumWeight*0.8)? "Available" : "Overloaded";
        	}
        	else {
        		response = "Unavailable";
        	}
        	
            //Sets response as OK(200 http code)
            t.sendResponseHeaders(200, response.length());
            //Get response body
            OutputStream os = t.getResponseBody();
            //Writes the response in the output body
            os.write(response.getBytes());
            //Close connection with Client
            os.close();
    	}
    	
    }
    
    private static void Init() throws Exception {

        /*
         * The ProfileCredentialsProvider will return your [default]
         * credential profile by reading from the credentials file located at
         * (~/.aws/credentials).
         */
        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                    "Please make sure that your credentials file is at the correct " +
                    "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
      ec2 = AmazonEC2ClientBuilder.standard().withRegion("us-east-1").withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
    }
    
    // In -> Weight to be added
    // Out -> Server
    private static Server ChooseServer(int weight) 
    {   
    	Server best = null;
    	
    	//Choose server to run the query
    	for(Server server: servers) {
    		if(!server.toBeTerminated()) {
	    		// If server is well known
	    		if(server.isResolved()) {
	    			// If already resolved ping
					if(server.ping()) {
						// If server can handle more weight
						if((weight + server.getWeight()) <= maximumWeight) {
							if((best == null) || best.getWeight() > server.getWeight()) {
								best = server;
							}
						}
					}
				}
				else {
	    			// Else Resolve and assume this is the best server to execute
					if(server.resolve(ec2)) {
	        			best = server;
	        			break;
					}
				}
    		}
    	}	
    	
//    	  DescribeInstancesResult describeInstancesRequest = ec2.describeInstances();
//        List<Reservation> reservations = describeInstancesRequest.getReservations();
//        Set<Instance> instances = new HashSet<Instance>();
//
//        for (Reservation reservation : reservations) {
//            instances.addAll(reservation.getInstances());
//        }
//        
//        for (Instance instance : instances ) {
//        	// TODO insert image of mazerunner servers
//        	if(instance.getImageId().equals(ImageId)) {
//        		//Choose instance to redirect the request
//
//            	//Get public Ip of the choosen AWS Instance
//        		return instance.getPublicIpAddress();
//        	}
//        }
    	
    	// Return the server
    	return best;
    }
    
    private static int DecideWeight(int xStart, int yStart, int xEnd, int yEnd, int velocity, String strategy, String filename) {
    	// Default weight
    	int weight = 0;
    	
    	// If there's a valid average instruction
    	if(true) {
	    	// Compute distance between points
	    	double distance = Math.sqrt(Math.pow(xStart - xEnd, 2) + Math.pow(yStart - yEnd, 2));
	    	// Get maze maximum possible distance 
	    	int size = Integer.parseInt(filename.split("\\d+")[0]);
	    	double diagonal = Math.sqrt(2*Math.pow(size, 2));
	    	// Compute distance ratio
	    	double distanceRatio = distance/diagonal;
	    	
	    	// Compute velocity ratio
	    	double velocityRatio = (1 - (velocity/100));
	    	
	    	// Get Multiplier
	    	double multiplier = distanceRatio + velocityRatio;
	    	// Estimate number of instructions
	    	long estimation = (long)(averageInstructions/2 * multiplier);
    	
    	
	    	// Categorize the request
	    	if(estimation <= averageInstructions * (1/5)) {
	    		//XS Category
	    		weight = XS;
	    	}
	    	else if((estimation > averageInstructions * (1/5)) && (estimation <= averageInstructions * (2/5))) {
	    		//S Category
	    		weight = S;
	    	}
	    	else if((estimation > averageInstructions * (2/5)) && (estimation <= averageInstructions * (3/5))) {
	    		//M Category
	    		weight = M;
	    	}
	    	else if((estimation > averageInstructions * (3/5)) && (estimation <= averageInstructions * (4/5))) {
	    		//L Category
	    		weight = L;
	    	}
	    	else if((estimation > averageInstructions * (4/5))) {
	    		//XL Category
	    		weight = XL;
	    	}
    	}
    	else {
    		weight = (2/3) * maximumWeight;
    	}
    	
    	return weight;
    }
}
