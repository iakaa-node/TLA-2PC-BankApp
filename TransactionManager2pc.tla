----------------------- MODULE TransactionManager2pc -----------------------


EXTENDS Naturals, Sequences, TLC

CONSTANTS
    RM,                 \* Resource Managers
    Transactions

VARIABLES
    tmState,            \* TM state
    votes,              \* votes received
    decision,           \* final decision
    timeout,            \* timeout indicator
    prepared            \* participants prepared


\* Initial State                                                           


Init ==
    /\ tmState = "INIT"
    /\ votes = [rm \in RM |-> "NONE"]
    /\ decision = "UNDECIDED"
    /\ timeout = FALSE
    /\ prepared = {}


\* Start Transaction                                                       


StartTransaction ==
    /\ tmState = "INIT"
    /\ tmState' = "WAITING"
    /\ UNCHANGED <<votes, decision, timeout, prepared>>


\* Resource Manager votes COMMIT                                           


VoteCommit(rm) ==
    /\ tmState = "WAITING"
    /\ rm \in RM
    /\ votes[rm] = "NONE"
    /\ votes' = [votes EXCEPT ![rm] = "VOTE-COMMIT"]
    /\ prepared' = prepared \cup {rm}
    /\ UNCHANGED <<tmState, decision, timeout>>

\* Resource Manager votes ABORT                                            

VoteAbort(rm) ==
    /\ tmState = "WAITING"
    /\ rm \in RM
    /\ votes[rm] = "NONE"
    /\ votes' = [votes EXCEPT ![rm] = "VOTE-ABORT"]
    /\ prepared' = prepared \cup {rm}
    /\ UNCHANGED <<tmState, decision, timeout>>


\* Timeout occurs                                                          

Timeout ==
    /\ tmState = "WAITING"
    /\ timeout = FALSE
    /\ {prepared} < {RM}
    /\ timeout' = TRUE
    /\ UNCHANGED <<tmState, votes, decision, prepared>>

\* Global Commit                                                           

GlobalCommit ==
    /\ tmState = "WAITING"
    /\ \A rm \in RM : votes[rm] = "VOTE-COMMIT"
    /\ decision' = "COMMIT"
    /\ tmState' = "COMMITTED"
    /\ UNCHANGED <<votes, timeout, prepared>>

\* Global Abort                                                            
GlobalAbort ==
    /\ tmState = "WAITING"
    /\ (
          \E rm \in RM : votes[rm] = "VOTE-ABORT"
          \/ timeout = TRUE
       )
    /\ decision' = "ABORT"
    /\ tmState' = "ABORTED"
    /\ UNCHANGED <<votes, timeout, prepared>>

\* Next-State Relation                                                     
Next ==  \/ StartTransaction
         \/ \E rm \in RM : VoteCommit(rm) \/  VoteAbort(rm)
         \/ Timeout
         \/ GlobalCommit
         \/ GlobalAbort

\* Safety Invariants                                                       
NoCommitAfterAbort ==
    ~(decision = "COMMIT" /\ decision = "ABORT")

CommitRequiresAllVotes ==
    decision = "COMMIT"
      => \A rm \in RM : votes[rm] = "VOTE-COMMIT"

AbortOnAnyAbort ==
    (\E rm \in RM : votes[rm] = "VOTE-ABORT")
       => decision # "COMMIT"

\* Specification                                                           
Spec ==
    Init /\ [][Next]_<<tmState, votes, decision, timeout, prepared>>

=============================================================================

=============================================================================
\* Modification History
\* Last modified Fri Jul 10 13:01:58 PDT 2026 by HP
\* Created Wed May 06 11:26:48 PDT 2026 by HP
