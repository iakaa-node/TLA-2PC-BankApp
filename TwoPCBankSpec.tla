--------------------------- MODULE TwoPCBankSpec ---------------------------
EXTENDS Naturals, Sequences, TLC  
CONSTANTS RM, Messages, Accounts, Txns, DailyLimit
            
VARIABLES tmState, rmState,tmPrepared, votes, balance, txnType, withdrawToday,  balBefore, balAfter, sum, log , msgs              

\* instantiating 2PC module
TwoPC == INSTANCE 2PC 
 
 \*instantiating BankSpec module
Bank == INSTANCE BankSpec 
                      
\* initial state
Init == /\ TwoPC!TPInit
        /\ Bank!Init
        
\*execution logic
Deposit(acctFrom, amount) == /\ txnType' = "deposit"
                             /\ balance' = [ balance EXCEPT ![acctFrom] = @ + amount]
                             /\ log' = Append("deposit",amount)
                             /\ UNCHANGED << tmState, rmState, tmPrepared, withdrawToday, balBefore, balAfter>>

Withdraw( a, amount) == /\ txnType' = "withdraw"
                        /\ a \in Accounts \* check if account exist
                        /\ balance[a] >= amount \* check if the account has enough funds
                        /\ balance' = [ balance EXCEPT ![a] = @ - amount]
                        /\ log' = Append("withdraw", amount)
                        /\ UNCHANGED << tmState, rmState, tmPrepared, withdrawToday, balBefore, balAfter>>

Transfer(acctFrom, acctTo, amount) == /\ txnType' = "transfer"
                                      /\ (\A a \in Accounts: acctFrom /\ acctTo \in Accounts)    
                                     /\ balance[acctFrom] >= amount     \*check sufficient funds
                                      /\ DailyLimit \in Nat
                                      /\ sum = 0
                                      /\ sum' = sum + amount
                                      /\ withdrawToday = 0
                                     /\ withdrawToday' = sum'
                                      /\ \A withdraw, transfer \in txnType: 
                                      withdrawToday <= DailyLimit \* check withdrawal limit
                                      /\ acctFrom # acctTo            \* no same acct transfer
                                      /\ balance' = [balance EXCEPT ![acctFrom]
                                          = @ - amount, ![acctTo] = @ + amount] 
                                     /\ UNCHANGED << tmState, rmState, tmPrepared, votes>>                  
                              
TMPrepare  == /\ TwoPC!RMPrepare("prepare")
              /\ \E acctFrom, acctTo \in Accounts, amount \in Nat: 
                    Transfer(acctFrom, acctTo, amount) 
                                                  

RMRcvCommitMsg ==  /\ TwoPC!RMRcvCommitMsg("commit")
RMRcvAbortMsg == /\ TwoPC!RMRcvAbortMsg("abort")

TMCommit == /\ TwoPC!TMCommit
        
 TMAbort ==  /\ TwoPC!TMAbort
 
 \* Next state
 Next == \/ TMPrepare
         \/ \A acctFrom, amt \in Nat: Withdraw(acctFrom, amt)
         \/ \A acctTo, amt \in Nat: Deposit(acctTo, amt)
         \/ \A acctFrom, acctTo, amt \in Nat : Transfer(acctFrom, acctTo, amt)
         \/RMRcvCommitMsg
         \/RMRcvAbortMsg
         \/ TMCommit
         \/ TMAbort
         
\* safety properties
NoNegativeBalance == \A acctFrom \in Accounts: balance >= 0
                    

Atomicity == \A rm \in RM:(votes ="vote-commit" /\ rmState = "committed") => (tmState = "COMMIT")

Consistent == \A r1,r2 \in RM: ~(rmState[r1] = "aborted" /\ rmState[r2] = "committed")

\* the specification formula
Spec == Init /\ [][Next]_<<tmState, rmState, tmPrepared, balance, balBefore, balAfter, log>>         
                             

=============================================================================
\* Modification History
\* Last modified Tue Jul 28 17:31:26 PDT 2026 by HP
\* Created Fri Mar 20 20:16:22 PDT 2026 by HP
