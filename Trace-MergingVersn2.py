import json
import glob

events = []
traces = ["tm.ndjson", "shard1DB.ndjson", "shard2DB.ndjson"]

# Read all individual trace files
for file in glob.glob("*.ndjson"):
    with open(file) as f:
        for line in f:
            event = json.loads(line)
            events.append(event)

            # sort events by timeSt0amps
            events.sort(key=lambda x: x["time"])

        # write merge trace
        with open("Merged-trace.ndjson", "w") as out:
            for e in events:
                out.write(json.dumps(e) + "\n")

print("Merged-trace.ndjson created")
