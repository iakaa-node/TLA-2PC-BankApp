-------------------------------- MODULE json --------------------------------
EXTENDS Sequences, TLC
\*Simulated JSON/NDJSON support.
\* this operator acts as a placeholder for the runtime trace. TLC 
\* will simulate TraceData fromthe model

CONSTANT TraceData
ndjsonDeserialize(path) == TraceData

\* Event field access
GetField(event, field) == event[field]
HasField(event, field) == field \in DOMAIN event
=============================================================================
\* Modification History
\* Last modified Thu Jul 23 10:25:26 PDT 2026 by HP
\* Created Tue Jul 21 11:24:12 PDT 2026 by HP
