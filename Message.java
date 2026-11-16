package com.twopc.message;

public class Message {
	public final String txnID;
	public final String sender;
	public final String msgType; 	//PREPARE, VOTE-COMMIT, VOTE-ABORT, COMMIT, ABORT
	public final int  debitAccount;
	public final int creditAccount;
	public final double amount;

	// this constructor is used by TM - Shard1DB message
	public Message(String txnID, String sender, String msgType, int debitAccount, int creditAccount, double amount) {
		this.txnID = txnID;
		this.sender = sender;
		this.msgType = msgType;
		this.debitAccount = debitAccount;
		this.creditAccount = creditAccount;
		this.amount = amount;
		
	} 

	public String getTxnID() {
		return txnID;
	}
	
	public String getSender() {
		return sender;
	}
	
	
	public String getMsgType() { // these values are obtained from the communicated TM - RMs messages
		return msgType;
	}
	public int getDebitAccount() {
		return debitAccount;
	}
	public int getCreditAccount(){
		return creditAccount;
	}
	 public double getAmount() {
		 return amount;
	 }
	 
	 

}

