package webserver;

import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;

import webserver.models.Server;

public class AutoScaler extends Thread {

	private static String ami = "";
	private static String keyName = "";
	private static String securityGroup = "";
	
	public void run(){
		System.out.println("AutoScaller Running!");
		//Init Mazerunner Servers
		Init();
		System.out.println("AutoScaller Initialized!");
		
		//Auto Scaler Life Cycle
		while(true){
			try {
				//TODO Your code should go here!


				//Sleep Auto Scaler for 1 minute
				this.sleep(60000);
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
		// TerminateInstance
		TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        termInstanceReq.withInstanceIds(instanceId);
        LoadBalancer.ec2.terminateInstances(termInstanceReq);
        
        // Delete Server from list
        for(Server server: LoadBalancer.servers) {
            LoadBalancer.servers.remove(server);
        }
	}
	
	private void Init() {
		this.launchInstance(ami, keyName, securityGroup);
		this.launchInstance(ami, keyName, securityGroup);
	}
}
