package com.twopc.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnections {
	
				
		static final String urlBase =  "jdbc:mysql://localhost:3306/";
		static final  String PARAMS ="?serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true";
		
		//static String user = "root";
		//static String password = " ";
		
		
		public static Connection getConnection1(String dbName) throws Exception{
			String url = urlBase + dbName + PARAMS;
			String user = "bankapp1";
			String password = "pass1";
			Class.forName("com.mysql.cj.jdbc.Driver");
			return DriverManager.getConnection(url, user, password);
		}
		
		public static Connection getConnection2(String dbName) throws SQLException, ClassNotFoundException {
			String url = urlBase + dbName + PARAMS;
			String user = "bankapp2";
			String password = "pass2";
			Class.forName("com.mysql.cj.jdbc.Driver");
			return DriverManager.getConnection(url, user, password);
		}
	/* usage in shard1DB and shard2DB:
	 * Connection conn1 = DBConnection.getConnection("Shard1DB")
	 * Connection conn2 = DBConnection.getConnetion("Shard2DB)
		*/

}


