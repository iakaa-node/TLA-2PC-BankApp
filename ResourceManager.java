package com.twopc.rms;

import java.sql.SQLException;

import com.twopc.message.Message;

public interface  ResourceManager extends Runnable {
	public String getNodeName();
		
	public void run(); 		
	//Retrieves messages from rmInbox queue (take(), remove(), poll())
	
	public void handleMessage(Message msg) throws SQLException;
	//public void handleMessage(); 	
	// accept/route messages base on type(PREPARE, VOTE-COMMIT, VOTE-ABORT, COMMIT, ABORT)
		
	
	public boolean prepare(Message msg) throws Exception, Throwable;
	// each node carries out validation and sends vote-commit/vote-abort message to TM	
	
	public void sendGlobalCommit(Message msg);
	// executes commit command sent by TM through COMMIT message
	
	public void sendGlobalAbort(Message msg);
	// executes abort command sent by TM through ABORT message

}

