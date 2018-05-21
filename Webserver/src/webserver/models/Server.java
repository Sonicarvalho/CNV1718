package webserver.models;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.elasticmapreduce.model.InstanceState;
import com.amazonaws.util.IOUtils;

public class Server {	
	// Instance Identifier
	private String InstanceId;
	// Instance Ip address
	private String Ip;
	// Instance Status
	private String state;
	
	// Instance Resolve State;
	private boolean resolved;
	// Instance has tried to Resolve State;
	private boolean tried;
	
	// Instance weight
	private int weight;
	
	// Deletion Flag;
	private boolean terminate;
	
	public Server(String instanceId) {
		this.InstanceId = instanceId;
		this.resolved = false;
		this.tried = false;
		this.terminate = false;
	}
	
	public String getInstanceId() {
		return InstanceId;
	}
	
	public String getIp() {
		return Ip;
	}

	public String getState() {
		return state;
	}
	
	public boolean isResolved() {
		return resolved;
	}
	
	public boolean hasTried() {
		return tried;
	}
	
	public boolean toBeTerminated() {
		return terminate;
	}
	
	public void terminate() {
		terminate = true;
	}

	public void activate() {
		terminate = false;
	}
	
	public int getWeight() {
		return weight;
	}
	
	public void addWeight(int weight) {
		this.weight += weight;
	}
	
	public void subtractWeight(int weight) {
		this.weight -= weight;
	}
	
	
	public boolean ping() {
    	String response = "";
    	URL url;
    	int retryCount = 0;
    	
    	if(this.Ip == null) {
    		System.out.println("The instance hasn't been resolved!");
    		return false;
    	}
    	
        try {
        	System.out.println("Pinging:\nhttp://" + this.Ip + ":8000/ping");
			url = new URL("http://" + this.Ip + ":8000/ping");
			while(retryCount < 5) {
				try {
					//Open Connection to server
					HttpURLConnection connection = (HttpURLConnection) url.openConnection();
					//Send request to server and convert to string- Blocking Function
					response = IOUtils.toString(connection.getInputStream());
					
					break;
				}
				catch(Exception e) {
					retryCount++;
					// Sleep for 10 seconds before retrying ping
					Thread.sleep(10000);
					System.out.println("Retrying...");
				}
			}
			
			return response.equals("Pong");
			
        } catch (Exception e) {
			e.printStackTrace();
		}
        return false;
	}
	
	
	public boolean resolve(AmazonEC2 ec2) {
		if(this.tried) {
			return this.isResolved();
		}
		
		this.tried = true;
		
		DescribeInstancesResult describeInstancesRequest = ec2.describeInstances();
        List<Reservation> reservations = describeInstancesRequest.getReservations();
        Set<Instance> instances = new HashSet<Instance>();

        for (Reservation reservation : reservations) {
            instances.addAll(reservation.getInstances());
        }
        
        for (Instance instance : instances ) {
        	if(instance.getInstanceId().equals(this.InstanceId)) {
        		// Check machine state
        		this.state = instance.getState().getName();
        		System.out.println("AWS Machine state : " + instance.getState().getName());
        		
        		// If the machine is not Running
        		if(this.state.equals("pending")) {
        			try {
        				// Wait 10 seconds until it changes from this state
						Thread.sleep(20000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
        		} else if(!this.state.equals("running")) {
        			System.out.println("This AWS Machine isn't running!");
        			this.resolved = false;
        			return false;
        		}

            	//Get public Ip of this AWS Instance
        		this.Ip = instance.getPublicIpAddress();
        		
        		if(this.Ip == null) {
        			try {
        				// Wait 10 more seconds until it gets its IP
						Thread.sleep(10000);
		        		this.Ip = instance.getPublicIpAddress();
		        		
		        		if(this.Ip == null) {
		        			this.resolved = false;
		        			return false;
		        		}
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
        		}
        		
        		// Try to ping machine
        		if(this.ping()) {
        			this.resolved = true;
        			return true;
        		}
    			System.out.println("Couldn't succesfully ping this AWS Machine!");
    			this.resolved = false;
    			return false;
        	}
        }
        
        System.out.println("No instance has this identifier ("+ this.InstanceId +")");
		this.resolved = false;
        return false;
		
	}
}
