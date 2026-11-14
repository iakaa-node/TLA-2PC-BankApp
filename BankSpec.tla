------------------------------ MODULE BankSpec ------------------------------
EXTENDS Naturals, Sequences, TLC
CONSTANTS Accounts, Txns, DailyLimit
VARIABLES balance, txnType, log, withdrawToday

Init == /\ balance = [ a \in Accounts |-> Nat]
        /\ log = << >>
        /\ txnType = { "deposit", "withdraw", "transfer"}

Deposit(acctTo, amount) == /\ txnType = "deposit"
                         /\ balance' = [ balance EXCEPT ![acctTo] = @ + amount]
                         /\ log' = Append("deposit",amount)
                         /\ UNCHANGED << >>

Withdraw( acctFrom, amount) == /\ txnType = "withdraw"
                        /\ acctFrom \in Accounts 
                        /\ balance[acctFrom] >= amount 
                        /\ balance' = [ balance EXCEPT ![acctFrom] = @ - amount]
                        /\ log = Append("withdraw", amount)
                        /\ UNCHANGED << >>

Transfer(acctFrom, acctTo, amount) ==/\ txnType = "transfer"
                                     /\ (\A a \in Accounts: acctFrom /\ acctTo \in Accounts)    
                                     /\ acctFrom # acctTo   
                                     /\ balance[acctFrom] >= amount    
                                     /\ DailyLimit \in Nat
                                     /\ withdrawToday + amount <= DailyLimit
                                     /\ balance' = [ balance EXCEPT ![acctFrom] = @ - amount,
                                                                    ![acctTo] = @ + amount]
                                     /\ log = Append( " transfer", amount)
                                     /\ UNCHANGED << >>
                                     


Next == \A a, acctFrom, acctTo \in Accounts, amount\in 1..100: \/ Deposit(a, amount)                                     
                                             \/ Withdraw(acctFrom, amount)
                                             \/ Transfer( acctFrom, acctTo, amount)


NoNegativeBalance == \A a \in Accounts: balance[a] >= 0




Spec == Init /\ [][Next]_<< balance,txnType, log, withdrawToday>>                                                                          
                     

=============================================================================

