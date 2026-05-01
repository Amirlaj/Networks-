IN2011 Computer Networks - Coursework 1 (CRN-25)

Author: amir lajevardi (240064391, amir.lajevardi@city.ac.uk)


Build
-----
    javac *.java
Just standard Java, no libraries.

Run
Local test (spins up N nodes on localhost and writes/reads 16 lines
of text across them, default N=2):
    java LocalTest
    java LocalTest 5
    java LocalTest 10
Azure lab test (one node that joins the real lab network):
    java AzureLabTest your.email@city.ac.uk 10.x.x.x [port]
What works
----------
All 10 NodeInterface methods (setNodeName, openPort,
handleIncomingMessages, isActive, pushRelay, popRelay, exists,
read, write, CAS).
All the message types in the RFC: G/H name, N/O nearest, E/F
existence, R/S read, W/X write, C/D compare-and-swap, V relay,
and I info messages are dropped as the spec allows.
Other things I made sure of:
  - SHA-256 hashes, distance = 256 minus matching leading bits
  - max 3 addresses per distance bucket
  - writes only accepted if we're one of the 3 closest, else X
  - 5 second timeout, up to 3 retries per request
  - request/reply matched by (txid, peer ip, peer port) so
    duplicated/reordered packets don't confuse anything
  - CAS is synchronised so it's actually atomic
  - relays support multiple hops and self-relay (a node can
    end up being asked to relay to itself, which I handle by
    looping the inner packet back through localhost)
  - bad/short/malformed packets are caught and dropped, no
    crashing the message pump
Notes
-----
The RFC has a slightly weird example for the string encoding
(it shows " " encoding as "2" but the consistent rule gives "1").
I followed the consistent rule because it's what the bootstrap
bytes in LocalTest actually use.
When the bucket for a distance is full I keep the older entries
rather than the new one, since the spec says to prefer stable
nodes and there's no easy way to test liveness.
A node can't really know its own external IP just from the
socket (NAT, Azure etc.). AzureLabTest handles this by writing
its own address explicitly. For self-relay I just loopback to
127.0.0.1.
Testing
-------
LocalTest with N = 2, 5, 10 all pass (32/32 writes+reads each).
I also tested relay with a small 3-node setup A -> B -> C and
that worked too.
Files
-----
  Node.java          my implementation
  HashID.java        provided
  LocalTest.java     provided
  AzureLabTest.java  Azure smoke test from the brief
  README.txt         this file
  capture.pcapng     Wireshark capture from running AzureLabTest
                     on the Azure VM
                     
                     
note that some comments in the code is only for myself is only there for myself.