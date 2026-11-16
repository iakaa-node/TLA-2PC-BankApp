package com.twopc.traceSchemaLogger;

import com.twopc.util.GlobalClock;

public class TraceEvents {
	public long time;
	public String nodeName;
	public String action;
	public String txnID;	//transaction ID
	public String state;	//Rms:working, prepared, committed, aborted; and TM: prepare commit, abort
	
	public TraceEvents (long time, String nodeName, String action, String txnID, String state) {
		this.time = GlobalClock.nextTime();
		this.nodeName = nodeName;
		this.action = action;
		this.txnID =txnID;
		this.state = state;

	}
}

