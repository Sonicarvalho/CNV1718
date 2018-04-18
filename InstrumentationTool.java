import BIT.highBIT.*;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static java.nio.file.StandardOpenOption.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class InstrumentationTool {
	private static ConcurrentHashMap<Long, Metrics> metricsPerThread = new ConcurrentHashMap();
	private static String Parameters;

	static class Metrics {
		public long i_count;
		public long b_count;
		public int m_count;
		public int fieldacc_count;
		public int memacc_count;
		public long bbRobserve;
		public long bbRrun;

		public Metrics(int i_count, int b_count, int m_count,int facc_count,int memacc_count){
			this.i_count = i_count;
			this.b_count = b_count;
			this.m_count = m_count;
			this.fieldacc_count = facc_count;
			this.memacc_count = memacc_count;
			this.bbRobserve = 0;
			this.bbRrun = 0;
		}
	}

	public static void main(String argv[]) {
		File file_in = new File(argv[0]);
		String infilenames[] = file_in.list();


		BasicBlock basicBlock;
		Instruction instruc;

		for (int i = 0; i < infilenames.length; i++) {
			String infilename = infilenames[i];
			if (infilename.endsWith(".class")) {
				// create class info object
				ClassInfo ci = new ClassInfo(argv[0] + System.getProperty("file.separator") + infilename);
				System.out.println((ci.getClassName()));
				// loop through all the routines
				// see java.util.Enumeration for more information on Enumeration class
				for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
					Routine routine = (Routine) e.nextElement();
					routine.addBefore("InstrumentationTool", "mcount", new Integer(1));
					
					if(ci.getClassName().equals("pt/ulisboa/tecnico/meic/cnv/mazerunner/maze/RobotController")){	
						if(routine.getMethodName().equals("observe"))
							for(Enumeration blocks = routine.getBasicBlocks().elements(); blocks.hasMoreElements(); ) {
								basicBlock = (BasicBlock) blocks.nextElement();
								basicBlock.addBefore("InstrumentationTool", "bbRobserve", new Integer(basicBlock.size()));
							}
						
						if(routine.getMethodName().equals("run"))
							for(Enumeration blocks = routine.getBasicBlocks().elements(); blocks.hasMoreElements(); ) {
									basicBlock = (BasicBlock) blocks.nextElement();
									basicBlock.addBefore("InstrumentationTool", "bbRrun", new Integer(basicBlock.size()));
								}
					}else{
						for(Enumeration blocks = routine.getBasicBlocks().elements(); blocks.hasMoreElements(); ) {
								basicBlock = (BasicBlock) blocks.nextElement();
								basicBlock.addBefore("InstrumentationTool", "bcount", new Integer(basicBlock.size()));
							}
					}
				}


				ci.addAfter("InstrumentationTool", "printInstrumentationTool", ci.getClassName());
				ci.write(argv[1] + System.getProperty("file.separator") + infilename);
			}
		}
	}

	// Outputs the metrics to a log file!
	// logfile->  Thread: # | Instructions: # | Blocks: # | Methods: #
	public static synchronized void printInstrumentationTool(String foo) {
		Charset utf8 = StandardCharsets.UTF_8;
		List<String> loggerAux = new ArrayList<String>();

		for(Map.Entry<Long,Metrics> entries : metricsPerThread.entrySet()) {
			long threadId = entries.getKey();
			Metrics stuff = entries.getValue();
			long in_count = stuff.i_count;
			long bb_count = stuff.b_count;
			int me_count = stuff.m_count;
			int fieldacc_count = stuff.fieldacc_count;
			int memacc_count =  stuff.fieldacc_count;
			long bbRobserve = stuff.bbRobserve;
			long bbRrun = stuff.bbRrun;
			String aux = Parameters + "Thread: " + (threadId) + " | Instructions: " + (in_count) + 
					" | Blocks: " +(bb_count) + " | Methods: " + (me_count) + " | Field Accesses: "+ (fieldacc_count) +
					" | Memory Accesses: " + (memacc_count) + " | RobotObserve BB: " + (bbRobserve) + " | RobotRun BB: " + (bbRrun);
			loggerAux.add(aux);
		}
		try {
			Files.write(Paths.get("log.txt"), loggerAux, utf8, APPEND);
		} catch (IOException e) {
			System.out.println("Something went wrong with the logger!!!");
		}

	}

	// Updates metrics for each threadId (instruction and bblock count)
	public static synchronized void count(int incr) {
		long threadId = Thread.currentThread().getId();
		Metrics metric;

		if(!metricsPerThread.containsKey(threadId))
			metric = new Metrics(incr,1,0,0,0);
		else {
			metric = metricsPerThread.get(threadId);
			metric.i_count += incr;
			metric.b_count++;
		}

		metricsPerThread.put(threadId, metric);
	}

	// Updates metrics for each threadId (method count)
	public static synchronized void mcount(int incr) {
		long threadId = Thread.currentThread().getId();
		Metrics metric = null;

		if(!metricsPerThread.containsKey(threadId))
			metric = 	new Metrics(0,0,1,0,0);
		else {
			metric = metricsPerThread.get(threadId);
			metric.m_count++;
		}

		metricsPerThread.put(threadId, metric);
	}

	public static synchronized void bcount(int incr) {
		long threadId = Thread.currentThread().getId();
		Metrics metric = metricsPerThread.get(threadId);
		metric.i_count += incr;
		metric.b_count++;
		metricsPerThread.put(threadId, metric);
	}

	public static synchronized void facount(int incr) {
		long threadId = Thread.currentThread().getId();
		Metrics metric = metricsPerThread.get(threadId);
		metric.fieldacc_count++;
		metricsPerThread.put(threadId, metric);
	}
	
	public static synchronized void bbRobserve(int incr) {
		long threadId = Thread.currentThread().getId();
		Metrics metric = metricsPerThread.get(threadId);
		metric.bbRobserve++;
		metricsPerThread.put(threadId, metric);
	}
	public static synchronized void bbRrun(int incr) {
		long threadId = Thread.currentThread().getId();
		Metrics metric = metricsPerThread.get(threadId);
		metric.bbRrun++;
		metricsPerThread.put(threadId, metric);
	}
	// Updates metrics for each threadId (in case of load or store)
	public static synchronized void macount(int incr) {
		long threadId = Thread.currentThread().getId();
		Metrics metric = metricsPerThread.get(threadId);
		metric.memacc_count++;
		metricsPerThread.put(threadId, metric);
	}

	public static synchronized void metricinit(String empty) {
		Metrics metric = new Metrics(1,0,0,0,0);
		long threadId = Thread.currentThread().getId();
		metricsPerThread.put(threadId, metric);
	}

	// Extra function to receive the query parameters and save them as the key on the table
	public static void setValues(String queryParam) {
		Parameters = queryParam;
	}

}
