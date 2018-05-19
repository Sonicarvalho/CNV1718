package webserver;

import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;

import webserver.models.Server;

public class AutoScaler extends Thread {

	private static String ami = LoadBalancer.ImageId;
	private static String keyName = "";
	private static String securityGroup = "";

	//TODO change this percentage
	private static double scaleUp = 0.8;
	private static double scaleDown = 0.2;
	
	public void run(){
		System.out.println("AutoScaller Running!");
		//Init Mazerunner Servers
		Init();
		System.out.println("AutoScaller Initialized!");
		
		//Auto Scaler Life Cycle
		while(true){
			try {
				//Sleep Auto Scaler for 1 minute
				this.sleep(60000);
				
				//TODO Your code should go here!
				int sum = 0;
				for(Server server: LoadBalancer.servers) {
					sum += server.getWeight();
				}
				
				double averageWeight = sum/(LoadBalancer.servers.size());
				double average = averageWeight/LoadBalancer.maximumWeight;
				
				if(average > scaleUp) {
					//Launch Instance
				}
				else if(average < scaleDown) {
					//Terminate Instance with most weight
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void launchInstance(String ami, String keyName, String securityGroup) {
		// Configure Instance 
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
		runInstancesRequest.withImageId(ami)
		        .withInstanceType("t2.micro")
		        .withMinCount(1)
		        .withMaxCount(1)
		        .withKeyName(keyName)
		        .withSecurityGroups(securityGroup);
		
		// Launch Instance
		RunInstancesResult runInstancesResult = LoadBalancer.ec2.runInstances(runInstancesRequest);
		
		// Get new InstanceId
		String newInstanceId = runInstancesResult.getReservation().getInstances().get(0).getInstanceId();
		LoadBalancer.servers.add(new Server(newInstanceId));
	}
	
	private void terminateInstance(String instanceId) {
		// Delete Server from list
        for(Server server: LoadBalancer.servers) {
        	if(server.getInstanceId().equals(instanceId)) {
        		// Change Deletion Flag
    			server.terminate();
        		
    			// Wait until all requests have finished
    			while(server.getWeight() > 0) {
    				try {
						Thread.sleep(10000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
    			}
    			
    			// Remove instance from server list
            	LoadBalancer.servers.remove(server);	
        	}
        }
		
		// Terminate Instance
		TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        termInstanceReq.withInstanceIds(instanceId);
        LoadBalancer.ec2.terminateInstances(termInstanceReq);
        
        
	}
	
	private void Init() {
		this.launchInstance(ami, keyName, securityGroup);
		this.launchInstance(ami, keyName, securityGroup);
	}
}
