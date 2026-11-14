--------------------------- MODULE shard1TLAspec ---------------------------

EXTENDS Naturals, Sequences, FiniteSets, TLC


CONSTANTS Accounts, TxnIDs,  DailyLimit

VARIABLES balances, txState, withdrawToday, logs,              
    


\* Transaction states 
                                 
TxnStates == INIT, PREPARED, VOTE-COMMIT, VOTE-ABORT, COMMITTED, ABORTED       

\* Initial State
Init == /\ balances = [a \in Accounts |-> 5000]
        /\ txState = [t \in TxnIDs |-> [ status |-> "INIT", debit |-> 0, credit |-> 0,
              amount |-> 0 ]]
        /\ withdrawToday = [a \in Accounts |-> 0]

        /\ logs = << >>


\* Helper Predicate: Account Exists  

AccountExists(acc) ==
    acc \in Accounts

* \ OPERATION 

Prepare(txn, debitAcc, creditAcc, amt) ==

    /\ txState[txn].status = "INIT"

    /\ AccountExists(debitAcc)

    /\ AccountExists(creditAcc)

    /\ debitAcc # creditAcc

    /\ balances[debitAcc] >= amt

    /\ withdrawToday[debitAcc] + amt <= DailyLimit

    /\ txState' =
        [txState EXCEPT
            ![txn].status = "PREPARED",
            ![txn].debit = debitAcc,
            ![txn].credit = creditAcc,
            ![txn].amount = amt]

    /\ balances' =
        [balances EXCEPT
            ![debitAcc] = @ - amt]

    /\ withdrawToday' =
        [withdrawToday EXCEPT
            ![debitAcc] = @ + amt]

    /\ logs' =
        Append(logs,
            [ txnID |-> txn,
              action |-> "withdraw",
              account |-> debitAcc,
              amount |-> amt ])

\* PREPARE FAILURE

PrepareFail(txn, debitAcc, creditAcc, amt) ==

    /\ txState[txn].status = "INIT"

    /\ ~(
          AccountExists(debitAcc)
       /\ AccountExists(creditAcc)
       /\ debitAcc # creditAcc
       /\ balances[debitAcc] >= amt
       /\ withdrawToday[debitAcc] + amt <= DailyLimit
       )

    /\ txState' =
        [txState EXCEPT
            ![txn].status = "ABORTED"]

    /\ UNCHANGED
        << balances,
           withdrawToday,
           logs >>
\* COMMIT 

Commit(txn) ==

    /\ txState[txn].status = "PREPARED"

    /\ txState' =
        [txState EXCEPT
            ![txn].status = "COMMITTED"]

    /\ UNCHANGED
        << balances,
           withdrawToday,
           logs >>

\* ABORT / ROLLBACK 

Abort(txn) ==

    LET debitAcc == txState[txn].debit
        amt == txState[txn].amount
    IN

    /\ txState[txn].status = "PREPARED"

    /\ balances' =
        [balances EXCEPT
            ![debitAcc] = @ + amt]

    /\ withdrawToday' =
        [withdrawToday EXCEPT
            ![debitAcc] = @ - amt]

    /\ txState' =
        [txState EXCEPT
            ![txn].status = "ABORTED"]

    /\ UNCHANGED logs
    
\* NEXT STATE RELATION     

Next ==

    \E txn \in TxnIDs,
      debitAcc \in Accounts,
      creditAcc \in Accounts,
      amt \in 1..6000 :

          Prepare(txn, debitAcc, creditAcc, amt)

    \/ PrepareFail(txn, debitAcc, creditAcc, amt)

    \/ Commit(txn)

    \/ Abort(txn)

\* SPECIFICATION   

Spec == Init /\ [][Next]_<<balances,
                           txState,
                           withdrawToday,
                           logs>>

\* INVARIANTS                                         
NoNegativeBalance ==

    \A a \in Accounts :
        balances[a] >= 0

DailyLimitInvariant ==

    \A a \in Accounts :
        withdrawToday[a] <= DailyLimit

NoSameAccountTransfer ==

    \A t \in TxnIDs :
        txState[t].status = "PREPARED"
            =>
        txState[t].debit # txState[t].credit

ValidTransactionStates ==

    \A t \in TxnIDs :
        txState[t].status \in TransactionStates

\* LIVENESS PROPERTY                                                       

EventuallyTerminates ==

    \A t \in TxnIDs :
        <>(txState[t].status = "COMMITTED"
           \/ txState[t].status = "ABORTED")

=============================================================================

=============================================================================
\* Modification History
\* Last modified Thu Oct 22 15:33:54 PDT 2026 by HP
\* Created Wed May 06 13:15:47 PDT 2026 by HP
