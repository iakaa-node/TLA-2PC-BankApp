--------------------------- MODULE shard2TLAspec ---------------------------


EXTENDS Naturals, Sequences, TLC

\* CONSTANTS                                                               

CONSTANTS
    Transactions,
    Accounts

\* VARIABLES                                                               

VARIABLES

    accBalance,         \* account balances
    txnState,           \* transaction states
    txnAmount,          \* transfer amount
    creditAcc,          \* destination account
    vote,               \* vote result
    connState,          \* connection state
    trace               \* execution traces

\* TRANSACTION STATES                                                      

TxnStates ==
{
    "INIT",
    "PREPARED",
    "VOTE-COMMIT",
    "VOTE-ABORT",
    "COMMITTED",
    "ABORTED"
}

\* INITIALIZATION                                                          

Init ==

    /\ accBalance =
        [a \in Accounts |-> 3000]

    /\ txnState =
        [t \in Transactions |-> "INIT"]

    /\ txnAmount =
        [t \in Transactions |-> 0]

    /\ creditAcc =
        [t \in Transactions |-> CHOOSE a \in Accounts : TRUE]

    /\ vote =
        [t \in Transactions |-> "NONE"]

    /\ connState =
        [t \in Transactions |-> "CLOSED"]

    /\ trace = << >>

\* PREPARE ACTION                                                          *)


Prepare(txn, acc, amount) ==

    /\ txnState[txn] = "INIT"

    /\ acc \in Accounts

    /\ amount > 0

    /\ txnAmount' =
        [txnAmount EXCEPT ![txn] = amount]

    /\ creditAcc' =
        [creditAcc EXCEPT ![txn] = acc]

    /\ connState' =
        [connState EXCEPT ![txn] = "OPEN"]

    /\ txnState' =
        [txnState EXCEPT ![txn] = "PREPARED"]

    /\ vote' =
        [vote EXCEPT ![txn] = "VOTE-COMMIT"]


    /\ UNCHANGED accBalance

\* PREPARE FAILURE                                                         

PrepareAbort(txn, acc, amount) ==

    /\ txnState[txn] = "INIT"

    /\ acc \notin Accounts

    /\ amount > 0

    /\ txnAmount' =
        [txnAmount EXCEPT ![txn] = amount]

    /\ creditAcc' =
        [creditAcc EXCEPT ![txn] = acc]

    /\ connState' =
        [connState EXCEPT ![txn] = "OPEN"]

    /\ txnState' =
        [txnState EXCEPT ![txn] = "VOTE-ABORT"]

    /\ vote' =
        [vote EXCEPT ![txn] = "VOTE-ABORT"]


    /\ UNCHANGED accBalance

\* COMMIT ACTION                                                           

Commit(txn) ==

    /\ txnState[txn] = "PREPARED"

    /\ vote[txn] = "VOTE-COMMIT"

    /\ LET acc == creditAcc[txn]
           amt == txnAmount[txn]
       IN
        accBalance' =
            [accBalance EXCEPT ![acc] = @ + amt]

    /\ txnState' =
        [txnState EXCEPT ![txn] = "COMMITTED"]

    /\ connState' =
        [connState EXCEPT ![txn] = "CLOSED"]


    /\ UNCHANGED <<txnAmount, creditAcc, vote>>

\* ABORT ACTION                                                            

Abort(txn) ==

    /\ txnState[txn] \in {"PREPARED", "VOTE-ABORT"}

    /\ txnState' =
        [txnState EXCEPT ![txn] = "ABORTED"]

    /\ connState' =
        [connState EXCEPT ![txn] = "CLOSED"]

    /\ UNCHANGED
        <<accBalance, txnAmount, creditAcc, vote>>

\* NEXT-STATE RELATION                                                     

Next ==

    \E txn \in Transactions :

        \/ \E acc \in Accounts :
              \E amt \in 1..500 :
                    Prepare(txn, acc, amt)

        \/ \E amt \in 1..500 :
              PrepareAbort(txn, 9999, amt)

        \/ Commit(txn)

        \/ Abort(txn)

\* INVARIANTS                                                              


TypeInvariant ==

    /\ txnState \in [Transactions -> TxnStates]

    /\ vote \in [Transactions -> STRING]

    /\ connState \in [Transactions -> STRING]

    /\ accBalance \in [Accounts -> Nat]

SafeBalance ==

    \A a \in Accounts :
        accBalance[a] >= 0

CommittedImpliesVote ==

    \A t \in Transactions :
        txnState[t] = "COMMITTED"
            => vote[t] = "VOTE-COMMIT"

ClosedAfterCommit ==

    \A t \in Transactions :
        txnState[t] = "COMMITTED"
            => connState[t] = "CLOSED"

ClosedAfterAbort ==

    \A t \in Transactions :
        txnState[t] = "ABORTED"
            => connState[t] = "CLOSED"

\* SPECIFICATION                                                           


Spec == Init /\ [][Next]_<<
        accBalance,
        txnState,
        txnAmount,
        creditAcc,
        vote,
        connState,
        trace>>

=============================================================================

=============================================================================
\* Modification History
\* Last modified Fri Jul 10 21:11:45 PDT 2026 by HP
\* Created Wed May 06 13:24:37 PDT 2026 by HP
