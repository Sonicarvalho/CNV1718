import BIT.highBIT.*;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static java.nio.file.StandardOpenOption.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;


public class InstrumentationTool {
	private static ConcurrentHashMap<Long, Metrics> metricsPerThread = new ConcurrentHashMap();
	private static ConcurrentHashMap<Long, String> Parameters = new ConcurrentHashMap();
	

	public static void main(String argv[]) throws Exception {
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

				for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
					Routine routine = (Routine) e.nextElement();
					//routine.addBefore("InstrumentationTool", "mcount", new Integer(1));
					
					/*for(Enumeration blocks = routine.getBasicBlocks().elements(); blocks.hasMoreElements(); ) {
						basicBlock = (BasicBlock) blocks.nextElement();
						basicBlock.addBefore("InstrumentationTool", "count", new Integer(basicBlock.size()));
					}*/
					
					for(Enumeration instr = routine.getInstructionArray().elements(); instr.hasMoreElements(); ) {
						instruc = (Instruction) instr.nextElement();
						int opcode = instruc.getOpcode();
						short instr_type = InstructionTable.InstructionTypeTable[opcode];
						
						//instruc.addBefore("InstrumentationTool", "stInstTypes", new String(InstructionTable.InstructionTypeName[instr_type]));


						if(InstructionTable.InstructionTypeName[instr_type].equals("CLASS_INSTRUCTION")) {
							instruc.addBefore("InstrumentationTool", "stInstTypes", new String(InstructionTable.InstructionTypeName[instr_type]));
						}else if(InstructionTable.InstructionTypeName[instr_type].equals("INSTRUCTIONCHECK_INSTRUCTION")) {
							instruc.addBefore("InstrumentationTool", "stInstTypes", new String(InstructionTable.InstructionTypeName[instr_type]));
						}else if(InstructionTable.InstructionTypeName[instr_type].equals("COMPARISON_INSTRUCTION")) {
							instruc.addBefore("InstrumentationTool", "stInstTypes", new String(InstructionTable.InstructionTypeName[instr_type]));
						}/**/

					}

				}


				ci.addAfter("InstrumentationTool", "printInstrumentationTool", ci.getClassName());
				ci.write(argv[1] + System.getProperty("file.separator") + infilename);
			}
		}
	}

	// Outputs the metrics to a log file!
	// logfile->  Thread: # | Instructions: # | Blocks: # | Methods: #
	public static synchronized void printInstrumentationTool(String foo) throws Exception {
		Charset utf8 = StandardCharsets.UTF_8;
		List<String> loggerAux = new ArrayList<String>();

		System.out.println("Instrumentation");
		long threadId = Thread.currentThread().getId();

		System.out.println("Instrumentation ID - " + threadId + " --- entriers count : " + metricsPerThread.entrySet().size());
		for(Map.Entry<Long,Metrics> entries : metricsPerThread.entrySet()) {
			System.out.println("Instrumentation0.5");
			if (threadId != entries.getKey())
				continue;
			System.out.println("Instrumentation1");
			Metrics stuff = entries.getValue(); 
			long in_count = stuff.i_count;
			long bb_count = stuff.b_count;
			System.out.println("Instrumentation2");
			HashMap<String,Long> m = stuff.instrucTypes;
			String aux = Parameters.get(threadId) + "Thread: " + (threadId) + " | Instructions: " + (in_count) + 
					" | BasicBlocks: " +(bb_count)  + " | " /*Methods: " + (me_count) + " */;

			System.out.println("Instrumentation3");
			//loggerAux.add(aux);
			//aux = "";

			System.out.println("Instrumentation4");
			for (Map.Entry<String, Long> e: m.entrySet()) {
				aux += e.getKey() + ": " +  Long.toString(e.getValue()) + " |\t";
			}
			

			System.out.println("Instrumentation5");
			String params[] = Parameters.get(threadId).substring(1, Parameters.get(threadId).length()-1).split(",");
			
			AmazonDynamoDBSample.init();
			AmazonDynamoDBSample.createMetricsTable();
			
			System.out.println("Escrever na BD");
			Map<String, AttributeValue> item = AmazonDynamoDBSample.newItemMetrics(Integer.parseInt(params[0]), 
												Integer.parseInt(params[1].trim()), 
												Integer.parseInt(params[2].trim()),
												Integer.parseInt(params[3].trim()), 
												Integer.parseInt(params[4].trim()), 
												params[5], 
												Integer.parseInt(params[6].split("\\D+")[1]),
												m.get("CLASS_INSTRUCTION"),
												m.get("INSTRUCTIONCHECK_INSTRUCTION"), 
												m.get("COMPARISON_INSTRUCTION"));
			System.out.println(item.toString());
			AmazonDynamoDBSample.addMetricsItem(item);
			System.out.println("Chegou depois das métricas");
			metricsPerThread.remove(entries.getKey());
			loggerAux.add(aux);
		}

		System.out.println("Instrumentation - over 9000");
		
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


	public static synchronized void stInstTypes(String iType) {
		long threadId = Thread.currentThread().getId();
		Metrics metric;
		if(!metricsPerThread.containsKey(threadId)) {
			metric = new Metrics(0,0,0,0,0);
			metricsPerThread.put(threadId, metric);
		}

		metric = metricsPerThread.get(threadId);
		long num = -1;
		if(metric.instrucTypes.get(iType) != null) {
			num = metric.instrucTypes.get(iType);
		}
		metric.instrucTypes.put(iType, num+1);
		//metric.i_count++;
		metricsPerThread.put(threadId, metric);
	}

	public static synchronized void metricinit(String empty) {
		Metrics metric = new Metrics(1,0,0,0,0);
		long threadId = Thread.currentThread().getId();
		metricsPerThread.put(threadId, metric);
	}

	// Extra function to receive the query parameters and save them as the key on the table
	public static void setValues(String queryParam, long threadId) {
		Parameters.put(threadId, queryParam);
	}

}
