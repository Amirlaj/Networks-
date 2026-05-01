IN2011 Computer Networks - Coursework 1 (CRN-25)

Author: Amir Lajevardi (240064391, amir.lajevardi@city.ac.uk)
Build
-----
    javac *.java
Standard Java only, no external libraries.
Run
---
Local test (spins up N nodes on localhost and writes/reads 16 lines
of text across them, default N=2):
    java LocalTest
    java LocalTest 5
    java LocalTest 10
Azure lab test (one node that joins the real lab network):
    java AzureLabTest 
What works
----------
All 10 NodeInterface methods are implemented: setNodeName, openPort,
handleIncomingMessages, isActive, pushRelay, popRelay, exists, read,
write, CAS.d
All RFC message types are handled:
  G/H  name request/response
  N/O  nearest request/response
  E/F  key-existence request/response
  R/S  read request/response
  W/X  write request/response
  C/D  compare-and-swap request/response
  V    relay
  I    info messages (received and discarded, as the RFC permits)
Protocol behaviours implemented:
  - SHA256 hashing and distance = 256 minus matching leading bits
  - Maximum three address pairs stored per distance bucket
  - Own address storedc at startup (loopback placeholder, overwritten
    when a real address =is supplied via write or another node)
  - Writes only accepted when the node is one of the three closest
    to the key, otherwise rejected with X
  - 5-second timeout, up to 3 retries per request
Requests and responses matched so
    duplicated and reordered packets are handled correctly
  - CAS is synchronised so it is atomic
  Relays support multiple hops and self-relay (a node asked to
    relay to itself loops the inner packet back through loopback)
  Malformed and short packets are caught and dropped without
    crashing the message-handling loop
Notes
-----
i added some notes into the files those are only for my understading 
When the bucket for a given distance is full, older entries are
kept rather than the new one, since the RFC says nodes should
prefer stable peers and there is no easy liveness signal.

A node cannot reliably determine its own external IP address from
the socket alone (NAT, Azure, etc.). Self is stored under loopback
at startup; AzureLabTest writes the correct external address
explicitly so that other nodes can reach it.
Testing
-------
LocalTest passes for N = 2, 5, and 10 (32/32 writes and reads each).
Manual relay tested with a 3-node setup A -> B -> C.
Files
-----
  Node.java          implementation
  HashID.java        provided
  LocalTest.java     provided
  AzureLabTest.java  Azure smoke test from the brief
  README.txt         this file
  capture.pcapng     Wireshark capture of AzureLabTest running on
                     the Azure VM
