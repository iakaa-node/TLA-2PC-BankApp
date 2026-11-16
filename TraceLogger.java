package com.twopc.traceSchemaLogger;

import java.io.FileWriter;
import java.io.IOException;


import com.fasterxml.jackson.databind.ObjectMapper;
 
public class TraceLogger {
	private String fileName;
	public static final ObjectMapper mapper = new ObjectMapper();
	 
	public TraceLogger(String nodename) {
		this.fileName = nodename.toLowerCase() + ".ndjson";
	}
	public synchronized void emit(TraceEvents event) {
		try {
			FileWriter fw = new FileWriter(fileName, true); //fileName => nodeName.toLowerCase() + ".ndjason"
			String json = mapper.writeValueAsString(event); 
			fw.write(json + "\n");
			fw.flush();
			fw.close();
		}
		catch(IOException e) {
			e.printStackTrace();
		}
	}
}


