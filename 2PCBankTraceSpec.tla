-------------------------- MODULE 2PCBankTraceSpec --------------------------
EXTENDS Naturals, Sequences, TLC, json, IOUtils

CONSTANTS
    RM,
    Messages,
    Accounts,
    Txns,
    DailyLimit


\* Original formal specification                                            *)



VARIABLES i,tmState,rmState,tmPrepared,votes,balance,txnType,withdrawToday,balBefore,balAfter,sum,log, msgs               
vars == <<tmState,rmState,tmPrepared,votes,balance,txnType,withdrawToday,balBefore,balAfter,sum,log, msgs>>

INSTANCE TwoPCBankSpec

\* Load merged execution trace                                              *)
TraceSpecInit == i = 1


Trace == ndjsonDeserialize(IOEnv["TRACE_PATH"])


\* Current runtime event                                                    *)

CurrentEvent == Trace[i]


\* Generic event recognizer                                                 


IsEvent(name) == /\ i \in 1..Len(Trace)
                 /\ "event" \in DOMAIN Trace[i] => Trace[i].event = name
                 /\ CurrentEvent["event"] = name
                 /\ i' = i + 1

\* Trace Builder                                                            
IsDeposit ==

    /\ IsEvent("Deposit")
    /\ LET acct ==
           CurrentEvent["account"]

           amt ==
           CurrentEvent["amount"]

       IN Deposit(acct, amt)

------------------------------------------------------------

IsWithdraw ==

    /\ IsEvent("Withdraw")
    /\ LET acct ==
           CurrentEvent["account"]

           amt ==
           CurrentEvent["amount"]

       IN Withdraw(acct, amt)

------------------------------------------------------------

IsTransfer ==

    /\ IsEvent("Transfer")

    /\ LET debit ==
           CurrentEvent["debit"]

           credit ==
           CurrentEvent["credit"]

           amt ==
           CurrentEvent["amount"]

       IN Transfer(debit, credit, amt)

------------------------------------------------------------

IsTMPrepare ==

    /\ IsEvent("TMPrepare")

    /\ TMPrepare

------------------------------------------------------------

IsRMRcvCommitMsg ==

    /\ IsEvent("RMRcvCommitMsg")

    /\ RMRcvCommitMsg

------------------------------------------------------------

IsRMRcvAbortMsg ==

    /\ IsEvent("RMRcvAbortMsg")

    /\ RMRcvAbortMsg

------------------------------------------------------------

IsTMCommit ==

    /\ IsEvent("TMCommit")

    /\ TMCommit

------------------------------------------------------------

IsTMAbort ==

    /\ IsEvent("TMAbort")

    /\ TMAbort


\* Trace-driven Next relation                                               
TraceNext ==

    \/ IsDeposit
    \/ IsWithdraw
    \/ IsTransfer
    \/ IsTMPrepare
    \/ IsRMRcvCommitMsg
    \/ IsRMRcvAbortMsg
    \/ IsTMCommit
    \/ IsTMAbort
\* Trace Acceptance
TraceAccepted == TLCGet("state").diameter - 1 = Len(Trace)
    
    
\* Trace-constrained specification                                         

BuilderSpec == Init /\ [][Next]_vars
    

=============================================================================
