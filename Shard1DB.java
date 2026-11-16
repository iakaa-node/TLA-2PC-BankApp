package com.twopc.rms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import com.twopc.message.Message;
import com.twopc.traceSchemaLogger.TraceEvents;
import com.twopc.traceSchemaLogger.TraceLogger;
import com.twopc.util.DBConnections;
import com.twopc.util.GlobalClock;

public class Shard1DB implements ResourceManager, Runnable{
	
	/* responsibilities: locks row, checks: sufficient balance,maximum withdraw,same accountNo,  
	 * account exist; deduct amount, logs/records transactions, emit execution traces, 
	 */
	long time = GlobalClock.nextTime();
	private final String nodeName = "Shard1DB";
	private BlockingQueue<Message> shard1Inbox; //shard1Inbox is object of type BlockingQueue
	private BlockingQueue<Message> tmInbox;  
 	private TraceLogger trace = new TraceLogger(nodeName); 
 	
 	private ConcurrentHashMap<String, Connection> txnConnections = new ConcurrentHashMap<>(); // stores connections
 	private ConcurrentHashMap<String, CountDownLatch>txnLatches; // stores latches
 	private ConcurrentHashMap<Integer, ReentrantLock>accountLocks = new ConcurrentHashMap<>();// for per account lock
 	
 	private ConcurrentHashMap<String, ReentrantLock>txnHeldLocks = new ConcurrentHashMap<>(); // stores locks
 	
 	//get lock per account
 	
 	private ReentrantLock getAccountLock(int accountID) {
 		return accountLocks.computeIfAbsent(accountID, id -> new ReentrantLock(true));
 	}
 	
	private String action;
	private String state;
	
	//Shard1DB constructor
	public Shard1DB (BlockingQueue<Message>shard1Inbox, BlockingQueue<Message>tmInbox,ConcurrentHashMap<String, CountDownLatch>txnLatches){
				
		this.shard1Inbox = shard1Inbox;
		this.tmInbox = tmInbox;
		this.txnLatches =txnLatches;
	}

		
	public String getNodeName() {
		return nodeName;
	}
	
	
	@Override
	public void run() {
		while(true) {
			try {
				Message msg = shard1Inbox.take(); //Messages from TM are retrieved and stored/kept in msg
				
				String txnID = msg.getTxnID();
				String msgType = msg.getMsgType();				
				
				System.out.println(" Shard1DB contents @ Run(): " + "txnID:" + txnID + ";" +  "msgType:"+ msgType);
				System.out.println();
			
				action = "receives-"+ msg.getMsgType();
				state = "working";
				txnID = msg.getTxnID();
				/*removes the head of rmInbox queue, wait if necessary until an element becomes 
				 * available.Throws:InterruptedException - if interrupted while waiting */
				
				trace.emit(new TraceEvents(time, nodeName, action, txnID, state));
				
				handleMessage(msg); // Shard1DB node calls its handleMessage() to route retrieved message
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	 public void handleMessage(Message msg) throws SQLException {
		
		 
		String txnID = msg.getTxnID();
		String sender = msg.getSender();
		String msgType = msg.getMsgType();
		
		System.out.println("shard1DB key contents @ handleMessage: " + "txnID:"+ msg.getTxnID() + ";" +" " + "msgType:" + msgType);
		System.out.println();
		
		switch(msg.msgType) { 	
		 case "PREPARE":
			 
			 boolean votes = prepare(msg);  // shard1DB's prepare() method
			 
			 if(votes == true ) {
				 System.out.println("Shard1DB prepare() Returns 'True' value after validations for txnID:" + " " + msg.getTxnID());
				 try {
					tmInbox.put(new Message(msg.getTxnID(),"Shard1DB", "VOTE-COMMIT", msg.getDebitAccount(), msg.getCreditAccount(),msg.getAmount()));
					
					System.out.println("Shar1DB sends VOTE-COMMIT message to TM for txnID:" + " " +  msg.getTxnID());
					System.out.println();
					
				 } catch (InterruptedException e) {
				
					e.printStackTrace();
				 }
				 
				 action = "vote-commit";
				 state = "prepared";
				 trace.emit(new TraceEvents(time, nodeName, action, msg.getTxnID(), state));
			 }
			 else {
				 try {
					
					 tmInbox.put(new Message(msg.getTxnID(),"Shard1DB", "VOTE-ABORT", msg.getDebitAccount(), msg.getCreditAccount(), msg.getAmount()));
					
					System.out.println("shar1DB sends vote-Abort message to TM for txnID: " + " "+ msg.getTxnID());
					System.out.println();
				 } 
				 catch (InterruptedException e) {
					
					e.printStackTrace();
				 }
				 
				 action = "vote-abort";
				 state = "prepared";
				 trace.emit(new TraceEvents(time, nodeName, action, txnID, state));
			 }
			 break;
			 
		 case "COMMIT":
			 
			  sendGlobalCommit( msg);	// Shard1DB calls its commit() method
			 
			 break;
			 
		 case "ABORT":
			 
			 sendGlobalAbort(msg ); 		
			 
			 break;
		 }
	 }
	 	 
	 
	 public boolean prepare( Message msg) {
		
		 String txnID = msg.getTxnID();
		 int debitAccount = msg.getDebitAccount(); 
		 int creditAccount = msg.getCreditAccount();
		 double amount = msg.getAmount();
		
		 
		 //global ordering
		 int first = msg.getDebitAccount();
	 
		/*acquire lock on debitAccount. A lock is a tool for controlling access to a shared resource by multiple threads.
		* Commonly, a lock provides exclusive access to a shared resource: only one thread at a time can acquire the lock 
		* and all access to the shared resource requires that the lock be acquired first. However, some locks may
		*  allow concurrent access to a shared resource, such as the read lock of a ReadWriteLock */
		
		 ReentrantLock firstLock = getAccountLock(first); 	//returns K in K:V from (accuntID, ReentrantLock)
		 
		 
		 Connection conn1 = null;
		 boolean acquiredLock = false;
		 
		 
		 try {
			
			acquiredLock = firstLock.tryLock(5,TimeUnit.SECONDS); // prime-point: lock takes place now
			
			if(!acquiredLock) {
				System.out.println(msg.getTxnID() + " " + " No lock acquired on  " + msg.getDebitAccount());
				 System.out.println(); 
				return false;
				
			}
			else {
				System.out.println(msg.getTxnID() + " lock acquired on " + msg.getDebitAccount());
				 System.out.println(); 
			}
			 
			
			 
			 // stores locks
			 if(firstLock != null) { 			 
				 txnHeldLocks.put(msg.getTxnID(),firstLock); // stores the locked debitAccount
			 }
			  
			 //Store the conn1 connection
			 conn1 = DBConnections.getConnection1("Shard1DB");
			 conn1.setAutoCommit(false);
			 conn1.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
			
			 txnConnections.put(txnID, conn1); // put() is an INSERTION method in ConcurrentHashMap
			 
			 String sql = "SELECT accountID as debitAccount From Accounts WHERE accountID = ?" ; 
			 try(PreparedStatement ps = conn1.prepareStatement(sql)){
				
				 ps.setInt(1,  msg.getDebitAccount()); //the msg.getDebitAccount() value is put in ? placeholder
		
				 	try(ResultSet  rs = ps.executeQuery()){	 //the value accountID is sought for in shard1DB database	
						
				 		if(!rs.next()) {			//if the value of searching is TRUE or false
							System.out.println("The Debit account " + " "+ msg.getDebitAccount() + " "+ " does not exist");
				 			System.out.println();
							
				 			 action = "checks-account exist";
				 			 state ="prepare"; 
							
							trace.emit(new TraceEvents(time, nodeName, action, msg.getTxnID(), state ));
							
				 			return false;
						}	
					
				 
						// check same-account transfer constraint
						if ( msg.getDebitAccount() == msg.getCreditAccount()) {
					
							System.out.println("Same-Account transfer is not allowed ");
							System.out.println();
						
							action = "checks-sameAccount";
							state ="prepare"; 
						
							trace.emit(new TraceEvents(time, nodeName, action, msg.getTxnID(), state ));
						
						return false;
					}
				}
		}
			
				
			
				 
			//Checking daily withdrawal limit constraint
		 sql = "SELECT SUM(amount) FROM TransactionLogs WHERE Accounts_accountID = ? AND transactionType = "
							+ " 'withdraw' AND DATE(dateTime) = CURRENT_DATE";
					
			conn1 = txnConnections.get(msg.getTxnID());
			
			try(PreparedStatement ps = conn1.prepareStatement(sql)){
					
					ps.setInt(1, debitAccount); //debitAccount comes from msg.getDebitAccount() using shard1DBInbox.take()
					
					try(ResultSet rs = ps.executeQuery()){
					
							double totalToday = 0;
					
							if(rs.next()) {
					 
								totalToday = totalToday + rs.getDouble(1); //add result of query to totalToday
								totalToday = 2500;
							}
							
							final double dailyLimit = 5000;
							
							if((totalToday + msg.getAmount()) > dailyLimit) {
						
									action ="checks-withdrawLimit";
									state =  "prpeared";
									trace.emit(new TraceEvents(time, nodeName, action, msg.getTxnID(), state));
						
									return false;
							}
					}
			}
			
			// check insufficient funds constraint 
						sql = "SELECT accountBalance from Accounts where accountID = ? for update" ;
						conn1 = txnConnections.get(msg.getTxnID());
						
						try(PreparedStatement ps = conn1.prepareStatement(sql)){
							ps.setInt(1, debitAccount); 
							
								try(ResultSet rs = ps.executeQuery()){
							 
									if(rs.next()) {
										double balance = rs.getDouble("accountBalance");
										
										if( (balance < msg.getAmount())) {	//value of amount is obtained from msg.getAmount()
											
											System.out.println("insufficient funds." + " " + "AccountBalance is: " + balance);
											System.out.println();
											
											action = "checks-insufficient fund";
											state ="prepare"; 
											
											trace.emit(new TraceEvents(time, nodeName, action, msg.getTxnID(), state ));
										
											return false;
										}
										
									}
							}
						}
			 
			
			
			// Update by decrement(withdraw). Do not commit. Return to prepare().		
			 sql = "UPDATE Accounts  SET accountBalance = accountBalance - ? WHERE accountID = ?";		
			conn1 = txnConnections.get(msg.getTxnID());
			
			try(PreparedStatement ps = conn1.prepareStatement(sql)){
					 
					ps.setDouble(1, msg.getAmount()); // amount comes from msg.getAmount() 
					ps.setInt(2, msg.getDebitAccount()); //debitAccount is the value to search for
					ps.executeUpdate(); 	// withdraw operation is done but NOT committed
					
					action ="withdraw-update";
					state =  "prepared";
					trace.emit(new TraceEvents(time, nodeName, action, txnID, state));
			}	
			
			// log transaction into Shard1DB DataBase
			sql = "INSERT INTO TransactionLogs(transactionID, "
			 	+ " transactionType, amount, dateTime, Accounts_accountID) VALUES(?,?,?,now(), ? )";
			
			try(PreparedStatement logStmt = conn1.prepareStatement(sql)){
						 
					logStmt.setString(1, msg.getTxnID());		//setDataType() are Application(external) values
					 logStmt.setString(2, "withdraw"); 
					 logStmt.setDouble(3, msg.getAmount());
					 logStmt.setInt(4,msg.getDebitAccount());
					logStmt.executeUpdate();
						
					System.out.println(msg.getTxnID()+ " " + " is  recorded on TransactionLog of "+ getNodeName()+ " "+ "database");
					
					action ="updates-withdraw";
					state =  "prpeared";
					trace.emit(new TraceEvents(time, nodeName, action, msg.getTxnID(), "prepared"));	
			}
			return true;
			
	 }
	 catch(Exception e) {
		 try {
			 e.printStackTrace();
			txnID = msg.getTxnID();
			conn1 = txnConnections.get(txnID);
			
				if((conn1 != null)) {     // rollback(), close transaction & remove() stored connection
					conn1.rollback();
					conn1.close();
					txnConnections.remove(txnID);
				}
				
				firstLock = txnHeldLocks.get(msg.getTxnID());
					
				if(firstLock != null) {
						txnHeldLocks.remove(msg.getTxnID());	// remove & unlock stored Reentrant locks
						firstLock.unlock();
				 }
				
			}catch(SQLException ex) {
				 ex.printStackTrace();
			 }
			 
			return false;
		 }finally {
			 	System.out.println("Transaction:" + msg.getTxnID() + " " + "UPDATED by 'withdrawal' operation in Shard1DB ");
		
			 	//signal shard2DB that validations, withdraw are done using countDown()
			 	CountDownLatch shard1Latch = txnLatches.get(msg.getTxnID());
			 	if(shard1Latch != null) {
			 		shard1Latch.countDown();
			 	}
		 	}
		 
	 }
	 
	 public void sendGlobalCommit(Message msg) {
		 	
		 Connection conn1 = null;
		 try {
			 conn1 = txnConnections.get(msg.getTxnID());
			ReentrantLock firstLock = txnHeldLocks.get(msg.getTxnID());
			
			 if(conn1 != null) {
				 conn1.commit();
				 conn1.close();
				 txnConnections.remove(msg.getTxnID());
			 }
				if(firstLock != null) {
				txnHeldLocks.remove(msg.getTxnID());
				firstLock.unlock();
			}
				 
				 action = "COMMITS";
				 state = "committed";
				 
				 trace.emit(new TraceEvents(time, nodeName, action, msg.getTxnID(), state));
			 
		 }
		 catch(Exception e) {
			 e.printStackTrace();
		 }
		 
	 }       
		        
	 public void sendGlobalAbort(Message msg) {
		Connection conn1 = null;
		ReentrantLock firstLock = txnHeldLocks.get(msg.getTxnID());
		 try {
			 conn1 = txnConnections.get(msg.getTxnID());
		 
			 if(conn1 != null) {
				 conn1.rollback();
				 conn1.close();
				 txnConnections.remove(msg.getTxnID());
			 }
			if(firstLock !=null) {
				txnHeldLocks.remove(msg.getTxnID());
				firstLock.unlock();
			}
				 action = "ROLLBACK";
				 state = "aborted";
				 trace.emit(new TraceEvents(time, nodeName, action, msg.getTxnID(), state));
			 
		}
		 catch(Exception e) {
				 e.printStackTrace();
		 }
			
	 }

}
		 
