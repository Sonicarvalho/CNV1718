package webserver.utils;

import java.util.ArrayList;

import webserver.models.Server;

public class ServerManagementUtil {

	private ArrayList<Server> servers = new ArrayList<>();

	public synchronized ArrayList<Server> getServers(){
		return servers;
	}
	
	public synchronized int count(){
		return servers.size();
	}
	
	public synchronized void add(Server server) {
		servers.add(server);
	}
	
	public synchronized void set(Server server) {
		for(int i = 0; i < servers.size(); i++) {
			if(server.getInstanceId().equals(servers.get(i).getInstanceId())) {
				servers.set(i, server);
			}
		}
	}
	
	public synchronized void remove(Server server) {
		servers.remove(server);
	}
	
}
