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
import com.twopc.util.DBConnections;

public class Shard2DB implements ResourceManager, Runnable {
	/*responsibilities: lock target account, add amount, log/record transaction, 
	 * emit execution traces
	 */
	private Long time = com.twopc.util.GlobalClock.nextTime();
	private static final String nodeName = "Shard2DB";
	private BlockingQueue<com.twopc.message.Message> shard2Inbox;
	private BlockingQueue<com.twopc.message.Message> tmInbox; 
	private com.twopc.traceSchemaLogger.TraceLogger trace = new com.twopc.traceSchemaLogger.TraceLogger(nodeName);
	
	private ConcurrentHashMap<String, Connection> txnConnections = new ConcurrentHashMap<>();// stores connections
	private ConcurrentHashMap<String,CountDownLatch>txnLatches;		//stores countDownLatsches
	private ConcurrentHashMap<Integer, ReentrantLock>accountLocks = new ConcurrentHashMap<>(); // per account locks
	
	private ConcurrentHashMap<String, ReentrantLock>txnHeldLocks = new ConcurrentHashMap<>();  // store locks
	
	public Shard2DB (BlockingQueue<Message>shard2Inbox, BlockingQueue<Message>tmInbox,ConcurrentHashMap<String,CountDownLatch>txnLatches) {
		this.shard2Inbox = shard2Inbox;
		this.tmInbox = tmInbox;
		this.txnLatches = txnLatches;  
	}
	
	@Override
	public String getNodeName() {
		return nodeName;
	}
	
	//get per account lock
	private ReentrantLock getAccountLock(int accountID) {
		return accountLocks.computeIfAbsent(accountID,id -> new ReentrantLock(true));
	}

	
	@Override
	public void run() {
		
		/*this is the starting point of RM Thread. The method continuously waits for
		 *  messages. RM runs as independent thread, awaits TM commands
		 */
		
		while(true) {
			
			
			try {
				Message msg = shard2Inbox.take(); //take() removes messages. other removal queue's methods are:remove(), poll()
				
				System.out.println("shard2DB key contents:");
				System.out.print("txnID:"+ msg.getTxnID() +";" + "" +  "msgType: "+ msg.getMsgType() + "" + "debitAccount: " + msg.getDebitAccount());
				System.out.println();
				
				handleMessage(msg);	
				
				String action =  "receives-" + msg.getMsgType();
				String state = "prepared";
				trace.emit(new TraceEvents(time, nodeName, action, msg.getTxnID(), state));
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		}
	}	
	
 
	public void handleMessage(Message msg) {
		
		System.out.println("shard2DB key contents:");
		System.out.print("txnID:"+ msg.getTxnID() +";" +"" + "Sender:" + msg.getSender() + " " + ";" + "" + "msgType:" + msg.getMsgType());
		System.out.println();
		
		
			/* this is message dispatcher; this method route the 
			 * incoming message,it is central control point. */
			 
			switch (msg.msgType) {
			case "PREPARE":
				boolean votes = prepare(msg);
				if(votes == true) {
					try {
						tmInbox.put(new Message(msg.getTxnID(), "Shard2DB", "VOTE-COMMIT", msg.getDebitAccount(), msg.getCreditAccount(), msg.getAmount()));
						
						System.out.println(msg.getTxnID() + " Shard2DB prepare() returns 'true' after validation");
						System.out.println(msg.getTxnID() + " shard2DB sends VOTE-COMMIT Messge to TM's run()" );
						System.out.println();
						 
						String action = "sends-" + msg.getMsgType();
						String state = "prepared";
						 trace.emit(new TraceEvents(time , nodeName, action, msg.getTxnID(), state));
						
					} 
					catch (InterruptedException e) {
					
						e.printStackTrace();
					}
					String action = "sends-" + msg.getMsgType();
					String state = "prepared";
					 trace.emit(new TraceEvents(time , nodeName, action, msg.getTxnID(), state));
				}
				else {
					try {
						tmInbox.put(new Message(msg.getTxnID()," Shard2DB", "VOTE-ABORT", msg.getDebitAccount(), msg.getCreditAccount(), msg.getAmount()));
					} catch (InterruptedException e) {
						
						e.printStackTrace();
					}
					System.out.println("shard2DB sends VOTE-ABORT Messge to TM with transaction:"+ msg.getTxnID());
					System.out.println();
					
					 trace.emit(new TraceEvents(time, nodeName, "VOTE-ABORT", msg.getTxnID(),"prepared"));
				}
				break;
				
			case "COMMIT":
				
				 sendGlobalCommit(msg);
				break;
				
			case"ABORT":
	
				 sendGlobalAbort(msg);
				break;
			}
		}
	
		public boolean prepare(Message msg) {
			
			/*phase1 in 2PC. Responsibilities: validate transactions, checks
			 * constraints, lock resources, votes either commit or abort.
			 */
			
			String txnID = msg.getTxnID();
			int creditAccount = msg.getCreditAccount();
			double amount = msg.getAmount();
			
			//get lock on creditAccount
			int second = creditAccount;
			
			ReentrantLock secondLock = getAccountLock(second);
			
			Connection conn1 = null;
			boolean acquiredLock = false;
			
			try {
				CountDownLatch shard2Latch = txnLatches.get(msg.getTxnID()); //in k:v, v = CountDownLatch object
				if(shard2Latch != null) {
					shard2Latch.await(); // shard2DB resumes action with that particular txnID
				}
				
				acquiredLock = secondLock.tryLock(5,TimeUnit.SECONDS); // key-point: App lock takes place now
				System.out.println(msg.getTxnID() + " Shard2DB has acquired lock on creditAccount");
				System.out.println();
				 if(!acquiredLock) {
					 return false;
				 }
		
				
				//store locks created using ConcurrentHashMap
				if(secondLock != null) {		 
					txnHeldLocks.put(msg.getTxnID(),secondLock); // stores Reentrant lock
				}
				
				conn1 = DBConnections.getConnection2("Shard2DB");
								
				conn1.setAutoCommit(false);
				conn1.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
		
				txnConnections.put(txnID, conn1); //store the created conn1 using ConcurrentHashMap
				
				String sql = "SELECT accountID  FROM Accounts WHERE accountID = ? for update";			
				try(PreparedStatement ps = conn1.prepareStatement(sql)){
				
						ps.setInt(1, msg.getCreditAccount()); //value of msg.getCreditAccount() is set for ?.
				
						try(ResultSet rs = ps.executeQuery()){ 	//the value of ? is sought for
				
							if (rs.next()) {		// rs.next() is T or F ( YES is found or NO is not found)
								creditAccount = rs.getInt("accountID"); 	// ? = T so assign it to accountID
								System.out.println("Destination account" + " " + creditAccount + " " + "exists" );
								System.out.println();
								
								trace.emit(new TraceEvents(time, nodeName, "confirm-creditAccount", msg.getTxnID(), "prepared"));
							}
							else {
								System.out.println("Destination account not found");
								
								trace.emit(new TraceEvents(time, nodeName, "confirm-creditAccount", msg.getTxnID(), "prepared"));
								return false;	
							}
						}
				}
				
				// carrying out increment update (deposit)
				sql = "UPDATE Accounts SET accountBalance = accountBalance + ? WHERE accountID = ?";
				
				try(PreparedStatement ps = conn1.prepareStatement(sql)){				
				
					ps.setDouble(1,amount);		// set() contains App values  to search for in DB
					ps.setInt(2,creditAccount);
					ps.executeUpdate();		// execute only, no commit() nor rollback()
				
					System.out.println(msg.getTxnID() + " :Shard2DB node carried out increment-update");
					System.out.println();
					
					trace.emit(new TraceEvents(time, nodeName, "deposit-update", msg.getTxnID(), "prepared"));
					
				}
				
				// logging the transactions in the TransactionLogs table
				sql = "INSERT INTO TransactionLogs(transactionID,"
						+ " transactionType, amount, dateTime, Accounts_accountID) VALUES(?, ?, ?, now(), ?)";
				
				
				try(PreparedStatement logStmt = conn1.prepareStatement(sql)){
					logStmt.setString(1, msg.getTxnID());
					logStmt.setString(2, "TRANSFER");
					logStmt.setDouble(3, msg.getAmount());
					logStmt.setInt(4, msg.getCreditAccount());
					logStmt.executeUpdate();
				
					String action = "sends-" + msg.getMsgType();
					String state = "prepared";
					trace.emit(new TraceEvents(time, nodeName, action, msg.getTxnID(), state));
				
				return true;
			}	
				
		}
		catch(Exception e) {
			try {
				txnID = msg.getTxnID();
				conn1 = txnConnections.get(txnID);
				secondLock = txnHeldLocks.get(msg.getTxnID());
				if((conn1 != null) && (secondLock != null)) {
					conn1.rollback();
					conn1.close();
					txnConnections.remove(txnID);
					secondLock.unlock();
				}
			}
			catch(SQLException ex) {
				e.printStackTrace();
			}
				
			return false;		
	}
			
}
		
		
	public void sendGlobalCommit(Message msg) {
			
			try {
										
					if(msg == null) {
			            System.out.println("Abort failed: message is null");
			            return;
			        }

					String txnID = msg.getTxnID();

			        if(txnID == null) {
			            System.out.println("In Shard2DB, COMMIT failed: txnID is null");
			            return;
			        }
					 
					Connection conn1 = txnConnections.get(txnID); // Here in Map < K,V >, K => txnID and V => conn1 
					conn1.commit();
					conn1.close();
					txnConnections.remove(txnID);
					
					 System.out.println(msg.getTxnID() + " :Shard2DB has Committed successfully");
					
					 String action = "COMMITS";
					 String state = "committed";
					 trace.emit(new TraceEvents(time, nodeName, action, msg.getTxnID(), state));
				}
				catch(SQLException e) {
					e.printStackTrace();
				} finally {
					ReentrantLock secondLock = txnHeldLocks.get(msg.getTxnID());
					if(secondLock != null) {
							secondLock.unlock();
							
						}
					}
				
	
		}
		
		public void sendGlobalAbort(Message msg) {
			
			try {
				
				if(msg == null) {
		            System.out.println("In Shard2DB, Abort failed: message is null");
		            return;
		        }

			 String txnID = msg.getTxnID();

		        if(txnID == null) {
		            System.out.println("In Shard2DB, Abort failed: txnID is null");
		            return;
		        }
		        
				Connection conn1 = txnConnections.get(msg.getTxnID());
				if(conn1 != null) {
					conn1.rollback();
					conn1.close();
					txnConnections.remove(txnID);
					
					ReentrantLock secondLock = txnHeldLocks.get(msg.getTxnID());
					secondLock.unlock();
					
				}else {
					 System.out.println(msg.getTxnID() + " : Shard2DB has aborted successfully");
					 System.out.println();
				}
				
				
				 String action = "rollback";
				 String state = "aborted";
				
				 trace.emit(new TraceEvents(time, nodeName, action, msg.getTxnID(), state));
				
				
			}
			catch(SQLException  e) {
				
				e.printStackTrace();
			} 
		}
}
