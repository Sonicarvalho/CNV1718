import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
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



public class WebServer {

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

    static AmazonEC2      ec2;
	
    public static void main(String[] args) throws Exception {
    	//Init AmazonEC2 connection
    	Init();
    	
    	executor = Executors.newFixedThreadPool(10);
    	
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("", new MyHandler());
        server.createContext("/test", new TestHandler());
        server.setExecutor(executor); // creates a default executor
        server.start();
    }

    static class MyHandler implements HttpHandler {
    	
        @Override
        public void handle(HttpExchange t) throws IOException {
        	String response;
        	URL url;
        	//TODO Choose Server Needs Update - Final project
            Instance instance = ChooseServer();
            
            if(instance != null) {
            	//Get public Ip of the choosen AWS Instance
                String domain = instance.getPublicIpAddress();
                
                //Construct Url => domain + query
                url = new URL("http://" + domain + ":8000?" + t.getRequestURI().getQuery());
                //Open Connection to server
        		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        		//Send request to server and convert to string- Blocking Function
        		response = IOUtils.toString(connection.getInputStream());
            }
            else {
            	response = "Couldn't find an AWS Instance";
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
    

    
    static class TestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
        	String response = "I'm Alive";

            //Sets response as OK(200 http code)
            t.sendResponseHeaders(200, response.length());
            //Get response body
            OutputStream os = t.getResponseBody();
            //Writes the response in the output body
            os.write(response.getBytes());
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
    
    private static Instance ChooseServer() 
    {
    	DescribeInstancesResult describeInstancesRequest = ec2.describeInstances();
        List<Reservation> reservations = describeInstancesRequest.getReservations();
        Set<Instance> instances = new HashSet<Instance>();

        for (Reservation reservation : reservations) {
            instances.addAll(reservation.getInstances());
        }
        
        for (Instance instance : instances ) {
        	if(instance.getImageId().equals("ami-f9d67a84")) {
        		return instance;
        	}
        }
        
        return null;
    }

}
