package com.twopc.util;

import java.util.concurrent.atomic.AtomicLong;


public class GlobalClock {
	private static final AtomicLong timer = new AtomicLong(0);
	
	public static Long nextTime() {
		return timer.incrementAndGet();
		
		/* this ensures deterministic traces and correct ordering 
		 * for TLA+ trace replay */
	}
}

