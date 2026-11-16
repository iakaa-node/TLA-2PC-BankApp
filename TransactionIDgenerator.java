package com.twopc.util;

import java.util.concurrent.atomic.AtomicLong;

public class TransactionIDgenerator {
	private static final AtomicLong txnIdGenerator = new AtomicLong(100);
	public static String getTxnId() {
		return ("TXN" + txnIdGenerator.incrementAndGet());
	}

}
 
