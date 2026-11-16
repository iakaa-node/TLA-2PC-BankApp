package com.twopc.transactionManager;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.twopc.message.Message;
import com.twopc.rms.ResourceManager;
import com.twopc.traceSchemaLogger.TraceEvents;
import com.twopc.traceSchemaLogger.TraceLogger;
import com.twopc.util.GlobalClock;
import com.twopc.util.TransactionIDgenerator;


public class TransactionManager implements Runnable {
	
	private final String nodeName = "TM";
	private TraceLogger trace = new TraceLogger(nodeName);
	long time = GlobalClock.nextTime();
	private final BlockingQueue<Message> shard1Inbox; // shard1Inbox is object of type BlockingQueue
	private final BlockingQueue<Message> shard2Inbox; // shard2Inbox is object of type BlockingQeue 
	private  BlockingQueue<Message> tmInbox = new LinkedBlockingQueue<>();
	private ConcurrentHashMap<String, ConcurrentHashMap<String, String>> votes = new ConcurrentHashMap<>();	
							
	// Thread Scheduler for timeout handling
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
	
	//for thread synchronization coordination
	private ConcurrentHashMap<String, CountDownLatch> txnLatches;
	
	//Declare interface-based List
	private List<ResourceManager> rms;   //List<> is data type of variable rms
		
	// setter injection
	public void setParticipants(List<ResourceManager> rms) {
		 
		//validation
		if(rms == null || rms.isEmpty()) {
			throw new IllegalArgumentException("Resource manager list cannot be null or empty");
		}
		
		this.rms = rms;
	}
	
	
	//transaction manager constructor
	public TransactionManager(List<ResourceManager> rms, BlockingQueue<Message> tmInbox, BlockingQueue<Message> shard1Inbox, BlockingQueue<Message> shard2Inbox, ConcurrentHashMap<String, ConcurrentHashMap<String, String>> votes, ConcurrentHashMap<String, CountDownLatch>txnLatches) {
		this.rms = rms; 
		this.tmInbox = tmInbox;
		this.shard1Inbox  = shard1Inbox;
		this.shard2Inbox = shard2Inbox;
		this.votes = votes;
		this.txnLatches = txnLatches;
		
	
	} 
	 
	public void startTransaction( int debitAccount, int creditAccount, double amount ) throws InterruptedException {
		
		//generate unique txnIDs for each transaction
		String txnID = TransactionIDgenerator.getTxnId();
		
		//create LATCH for each txnID to ensure each transaction  independence
		txnLatches.put(txnID,new CountDownLatch(1));
		
		System.out.println("Start-TransactionID generated:"+ txnID);
		System.out.println();
		System.out.println("Debit account:"+ " " + debitAccount);
		System.out.println();
		System.out.println("Credit account:"+ " " + creditAccount);
		System.out.println();
		System.out.println("Amount:"+ "" + amount);
		System.out.println();
	
		
		/* this method initiates transaction, generates transaction IDs(txnID),
		 * sends PREPARE messages to RMs, initialize vote tracking
		 */
		 
		setParticipants(rms);
		for (ResourceManager rm:rms) {
			
			Message debitMsg = new Message(txnID, "tm", "PREPARE",  debitAccount, creditAccount, amount);  
			Message creditMsg = new Message(txnID, "tm", "PREPARE", debitAccount, creditAccount, amount);
			
			if (rm.getNodeName().equals("Shard1DB")){
				
				shard1Inbox.put(debitMsg);	// put() is a method of BlockingQueue for inserting messages on Queue	
				
				System.out.println("TM sends PREPARE message to" + " " + rm.getNodeName());
				System.out.println();
		
			}
			if (rm.getNodeName().equals("Shard2DB")){
				shard2Inbox.put(creditMsg);
				System.out.println("TM sends PREPARE message to" + " " + rm.getNodeName());
				System.out.println();
			}
			
		}		trace.emit(new TraceEvents(time, nodeName,"sends-PREPARE", txnID, "init"));
	}
	
	 @Override
	 public void run() {
		 
		 /* run() removes messages (vote-commit or vote-abort) from TM's in-box (Queue) and stores
		  *  in message object, msg 
		  */
		 
		 while (true) {
			 
			
				 try {
					
					 Message msg = tmInbox.take();	// take() is a method of BlockingQueue for removing messages from Queue
					 
					 System.out.println(msg.getTxnID() + " TM has received txnID and msgType");
					 System.out.println();
					 
					 handleVote(msg); 
					 
					
					 trace.emit(new TraceEvents(time, nodeName,"receives-" + msg.getMsgType(), msg.getTxnID(),  "prepare"));
				 
				 }
				 catch (InterruptedException | SQLException e) {
					
					e.printStackTrace();
				 } 
			 }
	}
	 		 

	 private void handleVote(Message msg) throws InterruptedException, SQLException {
		
		 // vote storage takes place here. In K-V, K = msg.getTxnId(), V = ConcHash.put(sender, msgType) 
		    ConcurrentHashMap<String, String> txnVotes = votes.computeIfAbsent(msg.getTxnID(), k -> new ConcurrentHashMap<>());
		    
		    //linearization point!
		    txnVotes.put(msg.getSender(),  msg.getMsgType()); 	// put() is method of ConcurrentHashMap, stores the RMs votes!
		    
		    System.out.println("Vote received from: " + " " + msg.getSender());
		    System.out.println();
		    System.out.println(" Total votes for" + " " + msg.getTxnID() + " " + "=" + txnVotes.size());
		    System.out.println();

		   if (txnVotes.size() == rms.size()) {
	  	    
		    scheduler.schedule(()->{
		    	try {
						evaluateTransaction(msg.getTxnID());
					 }
					 catch (InterruptedException | SQLException e) {
			
						e.printStackTrace();
					 }
					 
		    },5,TimeUnit.SECONDS);
		   
		   
		 }
		
		   trace.emit(new TraceEvents(time, nodeName,"stores-" + msg.getSender()+"-votes", msg.getTxnID(),  "prepare"));
	 } 
	 
	  
	 private  void evaluateTransaction(String txnID) throws InterruptedException, SQLException {
		
		 ConcurrentHashMap<String, String> txnVotes = votes.get(txnID); // K = txnID, V = msgType (return)
		 
		 System.out.println( "Evaluating Transaction votes");
		 System.out.println();
		 
		 if(txnVotes == null) {
			 System.out.println("No votes found for" + " " + txnID);
			 System.out.println();
			 return;
		 }
		 
		 // check if all participants voted
		 if(txnVotes.size() != rms.size()) {
			
			 
			 Message finalMsg = new Message(txnID, "TM", "ABORT", 0, 0, 0.0);
			 sendGlobalAbort(finalMsg);
			
			 votes.remove(txnID );
			 txnLatches.remove(txnID);
			 return;
		 }
		 
		 boolean allCommit = false;
		 if(txnVotes.size() == rms.size()){		//class Map vs class List
			 
			 allCommit = txnVotes.values().stream().allMatch(rm -> rm.equals("VOTE-COMMIT"));
			 
			 System.out.println("In TM's evaluateTransaction():" + " " + "txnVotes.size:" + txnVotes.size() + ";" + " " + "rms.size:"+ rms.size());
			 System.out.println();
		 }
		 
		 if(allCommit == true ) {
			 
			 Message finalMsg = new Message(txnID, "TM", "COMMIT", 0, 0, 0.0);
			 sendGlobalCommit(finalMsg);  // TM calls its method sendGlobalCommit() 
			
		 }
		 else {
			 Message finalMsg = new Message(txnID, "TM", "COMMIT", 0, 0, 0.0);
			 sendGlobalAbort(finalMsg);
			 
		 }
		 
		 
		 votes.remove(txnID);
		 trace.emit(new TraceEvents(time, nodeName,"evaluate-votes-",  txnID,  "prepare"));
	 }
	 
	public void sendGlobalCommit(Message msg) throws InterruptedException {
			 for(ResourceManager rm:rms) {
				 if (rm.getNodeName().equals("Shard1DB")) {
					
					 shard1Inbox.put(new Message(msg.getTxnID(), "tm", "COMMIT",  msg.getDebitAccount(), msg.getCreditAccount(), msg.getAmount()));
					System.out.println(msg.getTxnID() + ":" + " " + "TM sends 'COMMIT' message to" + " " + rm.getNodeName());
					System.out.println();
				 }
				 
				 else if (rm.getNodeName().equals("Shard2DB")){
					 
					 shard2Inbox.put(new Message(msg.getTxnID(), "tm", "COMMIT", msg.getDebitAccount(), msg.getCreditAccount(), msg.getAmount()));
					 System.out.println(msg.getTxnID() + ":" + " " + "TM sends 'COMMIT' message to" + " " + rm.getNodeName());
					 System.out.println();
				 	 
				 trace.emit(new TraceEvents(time, nodeName,"sends-COMMIT", msg.getTxnID(), "commit"));
			 }
		 }
	}
		 
		 public void sendGlobalAbort(Message msg) throws InterruptedException {
			
			 for(ResourceManager rm:rms) {
				 if (rm.getNodeName().equals("Shard1DB")) {
						
					 shard1Inbox.put(new Message(msg.getTxnID(), "tm",  "ABORT", msg.getDebitAccount(), msg.getCreditAccount(), msg.getAmount() ));
					 System.out.println(msg.getTxnID() +":"+ " " +"TM sends 'ABORT' message to" + " " + rm.getNodeName());
					 System.out.println();
				
					 }
					 else if (rm.getNodeName().equals("Shard2DB")){
						 
						 shard2Inbox.put(new Message(msg.getTxnID(), "tm", "ABORT", msg.getDebitAccount(), msg.getCreditAccount(), msg.getAmount()));
						 System.out.println(msg.getTxnID()+ ":"+ " "+ "TM sends 'ABORT' message to" + " " + rm.getNodeName());
						 System.out.println();

				 trace.emit(new TraceEvents(time, nodeName,"sends-ABORT", msg.getTxnID(), "abort"));
			 }
	 }


 }
}
	 

