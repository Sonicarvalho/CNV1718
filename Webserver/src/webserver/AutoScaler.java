package webserver;

import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;

import webserver.models.Server;

public class AutoScaler extends Thread {

	private static String ami = LoadBalancer.ImageId;
	private static String keyName = "CNV-AWS";
	private static String securityGroup = "CNV-ssh+http";

	private static int minimumServers = 2;

	// Scale up usage percentage
	private static double scaleUp = 0.8;
	// Scale down usage percentage
	private static double scaleDown = 0.4;

	public void run() {
		System.out.println("AutoScaller Running!");
		// Init Mazerunner Servers
		Init();
		System.out.println("AutoScaller Initialized!");

		// Auto Scaler Life Cycle
		while (true) {
			try {
				// Sleep Auto Scaler for 1 minute
				Thread.sleep(60000);

				double ratio = weightRatio();
				System.out.println("System Overload : " + ratio);

				if (ratio > scaleUp) {
					// Launch Instance
					this.launchInstance(ami, keyName, securityGroup);
				} else if ((ratio < scaleDown) && (LoadBalancer.serverUtil.count() > minimumServers)) {
					// Terminate Instance with less weight
					Server highest = null;

					// Get the server with less weight
					for (Server server : LoadBalancer.serverUtil.getServers()) {
						if ((highest == null) || (server.getWeight() < highest.getWeight())) {
							highest = server;
						}
					}

					// Terminate the Instance
					this.terminateInstance(highest.getInstanceId());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void pressureLaunchInstance() {
		System.out.println("Launching an Instance to backup the system!");
		this.launchInstance(ami, keyName, securityGroup);
		try {
			Thread.sleep(30000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void launchInstance(String ami, String keyName, String securityGroup) {
		// Configure Instance
		RunInstancesRequest runInstancesRequest = new RunInstancesRequest();
		runInstancesRequest.withImageId(ami).withInstanceType("t2.micro").withMinCount(1).withMaxCount(1)
				.withKeyName(keyName).withSecurityGroups(securityGroup);

		// Launch Instance
		RunInstancesResult runInstancesResult = LoadBalancer.ec2.runInstances(runInstancesRequest);

		// Get new InstanceId
		String newInstanceId = runInstancesResult.getReservation().getInstances().get(0).getInstanceId();
		LoadBalancer.serverUtil.add(new Server(newInstanceId));
	}

	private void terminateInstance(String instanceId) {
		// Delete Server from list
		for (Server server : LoadBalancer.serverUtil.getServers()) {
			if (server.getInstanceId().equals(instanceId)) {
				// Change Deletion Flag
				server.terminate();
			}

			if (server.toBeTerminated()) {
				// Wait until all requests have finished
				while (server.getWeight() > 0) {
					try {
						Thread.sleep(10000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}

				// If the Scale Down is still needed
				if (weightRatio() > scaleUp) {
					server.activate();
					continue;
				}

				// Remove instance from server list
				LoadBalancer.serverUtil.remove(server);

				// Terminate Instance
				TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
				termInstanceReq.withInstanceIds(instanceId);
				LoadBalancer.ec2.terminateInstances(termInstanceReq);
			}
		}
	}

	public void forceTerminationInstance(String instanceId) {
		System.out.println("Forcing the deletion of an Instance");
		
		// Delete Server from list
		for (Server server : LoadBalancer.serverUtil.getServers()) {
			if (server.getInstanceId().equals(instanceId)) {
				// Change Deletion Flag
				server.terminate();

				// Wait until all requests have finished
				while (server.getWeight() > 0) {
					try {
						Thread.sleep(10000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}

				// Remove instance from server list
				LoadBalancer.serverUtil.remove(server);

				// Terminate Instance
				TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
				termInstanceReq.withInstanceIds(instanceId);
				LoadBalancer.ec2.terminateInstances(termInstanceReq);

				return;
			}
		}
	}

	private double weightRatio() {
		// Sum the weights of the servers
		int sum = 0;
		int active = 0;
		for (Server server : LoadBalancer.serverUtil.getServers()) {
			sum += server.getWeight();
			active += !server.toBeTerminated() ? 1 : 0;
		}

		// Get the Average of the weight
		double averageWeight = sum / active;

		// Get the weight ratio
		return averageWeight / LoadBalancer.maximumWeight;
	}

	private void Init() {
		for (int i = 0; i < minimumServers; i++)
			this.launchInstance(ami, keyName, securityGroup);
	}
}
