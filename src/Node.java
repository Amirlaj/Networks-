// IN2011 Computer Networks
// Coursework 1 (2025/2026)
//
// Submission by
//  amir lajevardi
//  240064391
//  amir.lajevardi@city.ac.uk

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;


// DO NOT EDIT starts
// This gives the interface that your code must implement.
// These descriptions are intended to help you understand how the interface
// will be used. See the RFC for how the protocol works.

interface NodeInterface {

    /* These methods configure your node.
     * They must both be called once after the node has been created but
     * before it is used. */

    // Set the name of the node.
    public void setNodeName(String nodeName) throws Exception;

    // Open a UDP port for sending and receiving messages.
    public void openPort(int portNumber) throws Exception;


    /*
     * These methods query and change how the network is used.
     */

    // Handle all incoming messages.
    // If you wait for more than delay miliseconds and
    // there are no new incoming messages return.
    // If delay is zero then wait for an unlimited amount of time.
    public void handleIncomingMessages(int delay) throws Exception;

    // Determines if a node can be contacted and is responding correctly.
    // Handles any messages that have arrived.
    public boolean isActive(String nodeName) throws Exception;

    // You need to keep a stack of nodes that are used to relay messages.
    // The base of the stack is the first node to be used as a relay.
    // The first node must relay to the second node and so on.

    // Adds a node name to a stack of nodes used to relay all future messages.
    public void pushRelay(String nodeName) throws Exception;

    // Pops the top entry from the stack of nodes used for relaying.
    // No effect if the stack is empty
    public void popRelay() throws Exception;


    /*
     * These methods provide access to the basic functionality of
     * CRN-25 network.
     */

    // Checks if there is an entry in the network with the given key.
    // Handles any messages that have arrived.
    public boolean exists(String key) throws Exception;

    // Reads the entry stored in the network for key.
    // If there is a value, return it.
    // If there isn't a value, return null.
    // Handles any messages that have arrived.
    public String read(String key) throws Exception;

    // Sets key to be value.
    // Returns true if it worked, false if it didn't.
    // Handles any messages that have arrived.
    public boolean write(String key, String value) throws Exception;

    // If key is set to currentValue change it to newValue.
    // Returns true if it worked, false if it didn't.
    // Handles any messages that have arrived.
    public boolean CAS(String key, String currentValue, String newValue) throws Exception;

}
// DO NOT EDIT ends


public class Node implements NodeInterface {
    private static final byte SPACE = 0x20;
    private static final int  RECV_BUF = 65535;
    private static final long REQ_TIMEOUT_MS = 5000;
    
    
    
    // not met
    private static final int  MAX_TRIES = 4;
    private static final int  K = 3;
    private static final int  LOOKUP_ROUNDS = 6;
    private static final long RELAY_EXPIRY_MS = 60_000;
    private String myName;
    private byte[] myHash;
    private DatagramSocket sock;
    private int myPort;
    private final Map<String, AddrEntry> peers = new HashMap<>();

    private final Map<String, String> store = new HashMap<>();

    private final List<String> relays = new ArrayList<>();

    private final Map<String, Pending> waiting = new HashMap<>();

    private final Map<String, RelayPending> forwarded = new HashMap<>();

    private final Random rng = new SecureRandom();

    private static final class AddrEntry {
        final String name;
        final InetAddress ip;
        final int port;
        
        
        
        
        // d
        final byte[] hash;
        long lastSeen;
        AddrEntry(String n, InetAddress i, int p, byte[] h) {
            name = n; ip = i; port = p; hash = h;
            lastSeen = System.currentTimeMillis(); } }
    private static final class Pending {
        final byte[] txid;
        byte[] reply;
        
        
        boolean done;
        Pending(byte[] t) { txid = t; } }
    private static final class RelayPending {
        final byte[] innerTxid;
        final byte[] outerTxid;
        
        
        final InetAddress origAddr;
        final int origPort;
        final long createdMs = System.currentTimeMillis();
        RelayPending(byte[] inner, byte[] outer, InetAddress a, int p) {
            
            
            innerTxid = inner; outerTxid = outer; origAddr = a; origPort = p; } }


    public void setNodeName(String nodeName) throws Exception {
        if (nodeName == null || !nodeName.startsWith("N:")) {
            throw new IllegalArgumentException("Node name must start with N:"); }
        myName = nodeName;
        
        myHash = HashID.computeHashID(nodeName); }
    public void openPort(int portNumber) throws Exception {
        if (sock != null) {
            
            
            try { sock.close(); } catch (Exception ignored) {} }
        sock = new DatagramSocket(portNumber);
        myPort = portNumber; }


    public void handleIncomingMessages(int delay) throws Exception {
        long deadline = (delay == 0) ? Long.MAX_VALUE
                
                : System.currentTimeMillis() + delay;
        while (true) {
            
            
            
            long left = (deadline == Long.MAX_VALUE) ? Long.MAX_VALUE
                    : deadline - System.currentTimeMillis();
            if (left <= 0) return;
            int wait = (int) Math.min(left, 1000);
            boolean got = processOnePacket(wait);
            cleanupExpiredRelays();
            
            //sec3. 
            if (!got && delay != 0
                    && System.currentTimeMillis() >= deadline) return; } }
    private boolean processOnePacket(int waitMs) throws Exception {
        if (sock == null) {
            throw new IllegalStateException("openPort() was not called");
        }
        byte[] buf = new byte[RECV_BUF];
        DatagramPacket dp = new DatagramPacket(buf, buf.length);
        try { sock.setSoTimeout(Math.max(1, waitMs));
            
            
            
            sock.receive(dp);
        } catch (SocketTimeoutException e) {
            return false; }
        byte[] data = Arrays.copyOf(dp.getData(), dp.getLength());
        try {
            
            
            
            
            dispatch(data, dp.getAddress(), dp.getPort());
        } catch (Throwable t) { }
        return true; }

    private void dispatch(byte[] data, InetAddress src, int srcPort) throws Exception {
        if (data.length < 4) return;
        
        
        
        // 
        if (data[2] != SPACE) return;
        byte[] txid = { data[0], data[1] };
        if (txid[0] == SPACE || txid[1] == SPACE) return;
        switch ((char) data[3]) {
            case 'G': onName(txid, src, srcPort);               
            break;
            case 'N': onNearest(txid, data, src, srcPort);        break;
            case 'E': onExists(txid, data, src, srcPort);         break;
            case 'R': onRead(txid, data, src, srcPort);           break;
            case 'W': onWrite(txid, data, src, srcPort);     
            break;
            case 'C': onCAS(txid, data, src, srcPort); 
            break;
            case 'V': onRelay(txid, data, src, srcPort);   
            break;
            case 'H': case 'O': case 'F':
            case 'S': case 'X': case 'D':
                onResponse(txid, data, src, srcPort);
                break;
            case 'I':                              break;
            default:                              break; } }


    private void onName(byte[] txid, InetAddress src, int srcPort) throws Exception {
        if (myName == null) return;
        
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(txid);
        out.write(SPACE);
        
        
        
        
        
        out.write('H');
        out.write(SPACE);
        out.write(encodeString(myName));
        sendBytes(out.toByteArray(), src, srcPort); }
    private void onNearest(byte[] txid, byte[] data, InetAddress src, int srcPort) throws Exception {
        if (data.length < 5 + 64 || data[4] != SPACE) return;
        byte[] target;
        
        
        
        try {
            target = hexToBytes(new String(data, 5, 64, StandardCharsets.US_ASCII));
        } catch (Exception e) {
            return; }
        List<AddrEntry> nearest = closestKnownTo(target, K);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(txid);
        out.write(SPACE);
        out.write('O');
        out.write(SPACE);
        for (AddrEntry e : nearest) {
            out.write(encodeString(e.name));
            out.write(encodeString(e.ip.getHostAddress() + ":" + e.port)); }
        sendBytes(out.toByteArray(), src, srcPort); }
    private void onExists(byte[] txid, byte[] data, InetAddress src, int srcPort) throws Exception {
        if (data.length < 6 || data[4] != SPACE) return;
        ParsedString key = decodeString(data, 5);
        if (key == null) return;

        
        
        
        
        
        char code;
        if (haveLocally(key.value))              code = 'Y';
        else if (amOneOfClosestThree(key.value)) code = 'N';
        else                                     code = '?';

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(txid);
        out.write(SPACE);
        out.write('F');
        out.write(SPACE);
        out.write((byte) code);
        sendBytes(out.toByteArray(), src, srcPort); }
 //from here
    private void onRead(byte[] txid, byte[] data, InetAddress src, int srcPort) throws Exception {
        if (data.length < 6 || data[4] != SPACE) return;
        ParsedString key = decodeString(data, 5);
        if (key == null) return;

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(txid);
        out.write(SPACE);
        out.write('S');
        out.write(SPACE);
        if (haveLocally(key.value)) {
            out.write('Y');
            out.write(SPACE);
            out.write(encodeString(lookupLocal(key.value))); 
        } else if (amOneOfClosestThree(key.value)) {
            out.write('N'); } else {
            out.write('?'); }
        sendBytes(out.toByteArray(), src, srcPort); }

    
    
    
    
    
    private void onWrite(byte[] txid, byte[] data, InetAddress src, int srcPort) throws Exception {
        if (data.length < 6 || data[4] != SPACE) return;
        ParsedString key = decodeString(data, 5);
        if (key == null) return;
        ParsedString val = decodeString(data, key.next);
        if (val == null) return;

        char code;
        if (haveLocally(key.value)) {
            saveLocal(key.value, val.value);
            code = 'R';
        } else if (amOneOfClosestThree(key.value)) {
            saveLocal(key.value, val.value);
            code = 'A';
        } else {
            code = 'X'; }

        
        
        
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(txid);
        out.write(SPACE);
        out.write('X');
        out.write(SPACE);
        
        //<...>
        
        
        
        out.write((byte) code);
        sendBytes(out.toByteArray(), src, srcPort); }

    private void onCAS(byte[] txid, byte[] data, InetAddress src, int srcPort) throws Exception {
        if (data.length < 6 || data[4] != SPACE) return;
        ParsedString key = decodeString(data, 5);
        if (key == null) return;
        ParsedString expected = decodeString(data, key.next);
        if (expected == null) return;
        ParsedString proposed = decodeString(data, expected.next);
        if (proposed == null) return;

        
        
        
        char code;
        synchronized (store) {
            if (haveLocally(key.value)) {
                String have = lookupLocal(key.value);
                if (have != null && have.equals(expected.value)) {
                    saveLocal(key.value, proposed.value);
                    code = 'R';
                } else {
                    code = 'N';}
            } else if (amOneOfClosestThree(key.value)) {
                saveLocal(key.value, proposed.value);
                code = 'A';
            } else {
                code = 'X'; } }

        //
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(txid);
        out.write(SPACE);
        out.write('D');
        out.write(SPACE);
        out.write((byte) code);
        sendBytes(out.toByteArray(), src, srcPort); }

    private void onRelay(byte[] txid, byte[] data, InetAddress src, int srcPort) throws Exception {
        if (data.length < 6 || data[4] != SPACE) return;
        ParsedString name = decodeString(data, 5);
        if (name == null || name.next >= data.length) return;
        byte[] inner = Arrays.copyOfRange(data, name.next, data.length);
        if (inner.length < 4 || inner[2] != SPACE) return;
        byte[] innerTxid = { inner[0], inner[1] };
        if (innerTxid[0] == SPACE || innerTxid[1] == SPACE) return;

        
        
        
        char innerType = (char) inner[3];
        boolean innerIsRequest =
                innerType == 'G' || innerType == 'N' || innerType == 'E'
                        || innerType == 'R' || innerType == 'W' || innerType == 'C'
                        || innerType == 'V';

        InetAddress dstAddr;
        int dstPort;
        if (myName != null && myName.equals(name.value) && myPort > 0) {
            dstAddr = InetAddress.getLoopbackAddress();
            dstPort = myPort;
        } else {
            AddrEntry dst = peers.get(name.value);
            if (dst == null) return;
            dstAddr = dst.ip;
            dstPort = dst.port; }

        
        
        // not ended
        
        
        sendBytes(inner, dstAddr, dstPort);

        if (innerIsRequest) {
            forwarded.put(pendingKey(innerTxid, dstAddr, dstPort),
                    new RelayPending(innerTxid, txid, src, srcPort)); } }

    private void onResponse(byte[] txid, byte[] data, InetAddress src, int srcPort) {
        String k = pendingKey(txid, src, srcPort);

        Pending p = waiting.get(k);
        if (p != null && !p.done) {
            p.reply = data;
            p.done = true;
            return; }

        
        
        
        RelayPending rp = forwarded.remove(k);
        if (rp != null) {
            byte[] forward = data.clone();
            forward[0] = rp.outerTxid[0];
            forward[1] = rp.outerTxid[1];
            try {
                sendBytes(forward, rp.origAddr, rp.origPort);
            } catch (Exception ignored) {
            }
            return; } }


    
    
    
    
    private static byte[] encodeString(String s) {
        if (s == null) s = "";
        byte[] body = s.getBytes(StandardCharsets.UTF_8);
        int spaces = 0;
        for (byte b : body) if (b == SPACE) spaces++;
        byte[] head = (spaces + " ").getBytes(StandardCharsets.US_ASCII);
        byte[] out = new byte[head.length + body.length + 1];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(body, 0, out, head.length, body.length);
        out[out.length - 1] = SPACE;
        return out; }
    private static final class ParsedString {
        final String value;
        final int next;
        ParsedString(String v, int n) { value = v; next = n; } }
    private static ParsedString decodeString(byte[] data, int from) {
        try {
            int p = from;
            int countStart = p;
            while (p < data.length && data[p] != SPACE) {
                if (data[p] < '0' || data[p] > '9') return null;
                p++; }
            if (p >= data.length) return null;
            int count = Integer.parseInt(
                    new String(data, countStart, p - countStart, StandardCharsets.US_ASCII));
            if (count < 0) return null;
            p++;

            int contentStart = p;
            int spaces = 0;
            while (p < data.length) {
                if (data[p] == SPACE) {
                    spaces++;
                    if (spaces == count + 1) break;
                }
                p++; }
            
            
        //sec     
            if (p >= data.length || spaces != count + 1) return null;
            String value = new String(data, contentStart,
                    p - contentStart, StandardCharsets.UTF_8);
            return new ParsedString(value, p + 1); } catch (Exception e) {
            return null; } }

    
    
    

    private static int distance(byte[] a, byte[] b) {
        if (a == null || b == null || a.length != 32 || b.length != 32) return 256;
        int matching = 0;
        for (int i = 0; i < 32; i++) {
            int x = (a[i] ^ b[i]) & 0xFF;
            if (x == 0) {
                matching += 8;
            } else {
                for (int bit = 7; bit >= 0; bit--) {
                    if ((x & (1 << bit)) == 0) matching++;
                    else break; }
                break; } }
        return 256 - matching; }

    
    
    
    
    
    
    private List<AddrEntry> closestKnownTo(byte[] target, int n) {
        List<AddrEntry> all = new ArrayList<>(peers.values());
        all.sort((x, y) -> Integer.compare(distance(x.hash, target),
                distance(y.hash, target)));
        return (all.size() > n) ? all.subList(0, n) : all; }

    
    
    
    
    private boolean amOneOfClosestThree(String key) throws Exception {
        if (myHash == null) return true;
        byte[] kh = HashID.computeHashID(key);
        int myDist = distance(myHash, kh);
        int closer = 0;
        for (AddrEntry e : peers.values()) {
            if (e.name.equals(myName)) continue;
            if (distance(e.hash, kh) < myDist) {
                closer++;
                if (closer >= 3) return false; } }
        return true; }

    private boolean haveLocally(String key) {
        if (key == null) return false;
        if (key.startsWith("N:")) return peers.containsKey(key);
        return store.containsKey(key); }

    private String lookupLocal(String key) {
        if (key == null) return null;
        if (key.startsWith("N:")) {
            AddrEntry e = peers.get(key);
            return (e == null) ? null : (e.ip.getHostAddress() + ":" + e.port); }
        return store.get(key); }

    private void saveLocal(String key, String value) {
        if (key == null || value == null) return;
        if (key.startsWith("N:")) {
            try { rememberPeer(key, value); } catch (Exception ignored) {}
        } else if (key.startsWith("D:")) {
            synchronized (store) { store.put(key, value); } } }

    private void rememberPeer(String name, String addrText) throws Exception {
        if (name == null || addrText == null) return;
        int colon = addrText.lastIndexOf(':');
        if (colon < 0) return;
        InetAddress ip;
        int port;
        try {
            ip = InetAddress.getByName(addrText.substring(0, colon));
            port = Integer.parseInt(addrText.substring(colon + 1));
        } catch (Exception e) {
            return; }

        byte[] h = HashID.computeHashID(name);

        AddrEntry existing = peers.get(name);
        if (existing != null) {
            peers.put(name, new AddrEntry(name, ip, port, h));
            return;}
// 
        if (myHash != null && Arrays.equals(h, myHash)) {
            peers.put(name, new AddrEntry(name, ip, port, h));
            return;}

        if (myHash == null) {
            peers.put(name, new AddrEntry(name, ip, port, h));
            return;}

        int d = distance(myHash, h);
        int countAtThisDistance = 0;
        for (AddrEntry e : peers.values()) {
            if (e.name.equals(myName)) continue;
            if (distance(myHash, e.hash) == d) countAtThisDistance++; }
        if (countAtThisDistance < 3) {
            peers.put(name, new AddrEntry(name, ip, port, h)); } }


    private byte[] newTxid() {
        byte[] t = new byte[2];
        do { rng.nextBytes(t); } while (t[0] == SPACE || t[1] == SPACE);
        return t; }
//short ex.
    private static String hexTxid(byte[] txid) {
        return String.format("%02x%02x", txid[0] & 0xFF, txid[1] & 0xFF); }

    private static String pendingKey(byte[] txid, InetAddress addr, int port) {
        return hexTxid(txid) + "@" + addr.getHostAddress() + ":" + port; }

    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x & 0xFF));
        return sb.toString(); }

    private static byte[] hexToBytes(String s) {
        if (s == null || (s.length() & 1) != 0) throw new IllegalArgumentException();
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(s.charAt(2 * i), 16);
            int lo = Character.digit(s.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException();
            out[i] = (byte) ((hi << 4) | lo); }
        return out; }

    private void sendBytes(byte[] data, InetAddress addr, int port) throws Exception {
        if (sock == null) throw new IllegalStateException("openPort() not called");
        sock.send(new DatagramPacket(data, data.length, addr, port)); }


    private byte[] buildRequest(byte[] txid, char type, byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(txid);
            out.write(SPACE);
            out.write((byte) type);
            out.write(SPACE);
            for (byte[] part : parts) out.write(part);
        } catch (Exception ignored) { }
        return out.toByteArray(); }

    private static final class Wrapped {
        byte[] packet;
        byte[] outerTxid;
        InetAddress addr;
        int port; }

    
    
    
    
    
    
    
    //dont SK
    
    private Wrapped wrapForRelay(byte[] inner, String destName,
                                 InetAddress destAddr, int destPort) throws Exception {
        Wrapped w = new Wrapped();
        if (relays.isEmpty()) {
            w.packet = inner;
            w.outerTxid = new byte[]{ inner[0], inner[1] };
            w.addr = destAddr;
            w.port = destPort;
            return w; }

        
        
        
        
        
        
        List<String> wrapNames = new ArrayList<>();
        wrapNames.add(destName);
        for (int i = relays.size() - 1; i >= 1; i--) wrapNames.add(relays.get(i));

        byte[] cur = inner;
        byte[] outerTx = null;
        for (String n : wrapNames) {
            byte[] tx = newTxid();
            outerTx = tx;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(tx);
            out.write(SPACE);
            out.write('V');
            
            
            
            
            out.write(SPACE);
            out.write(encodeString(n));
            out.write(cur);
            cur = out.toByteArray(); }
//
        AddrEntry first = peers.get(relays.get(0));
        if (first == null) throw new RuntimeException(
                "Don't know address of relay base: " + relays.get(0));
        w.packet = cur;
        w.outerTxid = outerTx;
        w.addr = first.ip;
        w.port = first.port;
        return w; }

    private byte[] sendAndWait(byte[] packet, InetAddress addr, int port,
                               byte[] txid) throws Exception {
        String k = pendingKey(txid, addr, port);
        Pending p = new Pending(txid);
        waiting.put(k, p);
        try {
            for (int attempt = 0; attempt < MAX_TRIES; attempt++) {
                sendBytes(packet, addr, port);
                long deadline = System.currentTimeMillis() + REQ_TIMEOUT_MS;
                while (!p.done && System.currentTimeMillis() < deadline) {
                    long remain = deadline - System.currentTimeMillis();
                    if (remain <= 0) break;
                    
                    
                    
                    
                    int wait = (int) Math.min(remain, 500);
                    processOnePacket(wait);
                    cleanupExpiredRelays(); }
                if (p.done) return p.reply;}
            return null;
        } finally {
            waiting.remove(k);}}
    private void cleanupExpiredRelays() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, RelayPending>> it = forwarded.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().createdMs > RELAY_EXPIRY_MS) it.remove();}}
    public boolean isActive(String nodeName) throws Exception {
        if (nodeName == null) return false;
        AddrEntry e = peers.get(nodeName);
        if (e == null) {
            byte[] h = HashID.computeHashID(nodeName);
            iterativeNearest(h, K, 4);
            e = peers.get(nodeName);}
        byte[] txid = newTxid();
        byte[] req = { txid[0], txid[1], SPACE, 'G' };
        byte[] reply;
        if (relays.isEmpty()) {
            if (e == null) return false;
            reply = sendAndWait(req, e.ip, e.port, txid); } else {
            InetAddress placeholder = (e != null) ? e.ip
                    : InetAddress.getLoopbackAddress();
            int placeholderPort = (e != null) ? e.port : 0;
            Wrapped w = wrapForRelay(req, nodeName, placeholder, placeholderPort);
            reply = sendAndWait(w.packet, w.addr, w.port, w.outerTxid);}

        
        
        
        
        if (reply == null || reply.length < 6) return false;
        if (reply[3] != 'H' || reply[4] != SPACE) return false;
        ParsedString r = decodeString(reply, 5);
        return r != null && nodeName.equals(r.value);}

    public void pushRelay(String nodeName) throws Exception {
        if (nodeName != null) relays.add(nodeName);}

    public void popRelay() throws Exception {
        if (!relays.isEmpty()) relays.remove(relays.size() - 1);}

    public boolean exists(String key) throws Exception {
        if (key == null) return false;
        if (haveLocally(key)) return true;

        byte[] kh = HashID.computeHashID(key);
        for (AddrEntry n : iterativeNearest(kh, K, LOOKUP_ROUNDS)) {
            byte[] txid = newTxid();
            byte[] req = buildRequest(txid, 'E', encodeString(key));
            byte[] reply = sendOptionallyWrapped(req, txid, n);
            if (reply == null || reply.length < 6
                    || reply[3] != 'F' || reply[4] != SPACE) continue;
            switch ((char) reply[5]) {
                case 'Y': return true;
                case 'N': return false;
            } }
        return false;}

    
    
    
    
    public String read(String key) throws Exception {
        if (key == null) return null;
        if (haveLocally(key)) return lookupLocal(key);

        
        
        
        
        byte[] kh = HashID.computeHashID(key);
        for (AddrEntry n : iterativeNearest(kh, K, LOOKUP_ROUNDS)) {
            byte[] txid = newTxid();
            byte[] req = buildRequest(txid, 'R', encodeString(key));
            byte[] reply = sendOptionallyWrapped(req, txid, n);
            if (reply == null || reply.length < 6
                    || reply[3] != 'S' || reply[4] != SPACE) continue;
            char code = (char) reply[5];
            if (code == 'Y') {
                if (reply.length < 8 || reply[6] != SPACE) continue;
                ParsedString v = decodeString(reply, 7);
                if (v == null) continue;
                return v.value;
            } else if (code == 'N') {
                return null;}}
        return null;}

    public boolean write(String key, String value) throws Exception {
        if (key == null || value == null) return false;

        
        
        
        
        if (haveLocally(key) || amOneOfClosestThree(key)) saveLocal(key, value);

        byte[] kh = HashID.computeHashID(key);
        List<AddrEntry> closest = iterativeNearest(kh, K, LOOKUP_ROUNDS);

        boolean ok = haveLocally(key) && value.equals(lookupLocal(key));
        for (AddrEntry n : closest) {
            byte[] txid = newTxid();
            byte[] req = buildRequest(txid, 'W',
                    encodeString(key), encodeString(value));
            byte[] reply = sendOptionallyWrapped(req, txid, n);
            if (reply == null || reply.length < 6
                    || reply[3] != 'X' || reply[4] != SPACE) continue;
            char code = (char) reply[5];
            if (code == 'R' || code == 'A') ok = true;}
        return ok;}

    
    
    
    
    
    public boolean CAS(String key, String currentValue, String newValue) throws Exception {
        if (key == null || currentValue == null || newValue == null) return false;

        if (haveLocally(key)) {
            synchronized (store) {
                String have = lookupLocal(key);
                if (have != null && have.equals(currentValue)) {
                    saveLocal(key, newValue);
                    return true;}
                return false;}}

        
        //.....
        
        byte[] kh = HashID.computeHashID(key);
        for (AddrEntry n : iterativeNearest(kh, K, LOOKUP_ROUNDS)) {
            byte[] txid = newTxid();
            byte[] req = buildRequest(txid, 'C',
                    encodeString(key), encodeString(currentValue), encodeString(newValue));
            byte[] reply = sendOptionallyWrapped(req, txid, n);
            if (reply == null || reply.length < 6
                    || reply[3] != 'D' || reply[4] != SPACE) continue;
            char code = (char) reply[5];
            if (code == 'R' || code == 'A') return true;
            if (code == 'N') return false;}
        return false;}
    
    private byte[] sendOptionallyWrapped(byte[] req, byte[] txid, AddrEntry dst) throws Exception {
        if (relays.isEmpty()) {
            return sendAndWait(req, dst.ip, dst.port, txid);}
        Wrapped w = wrapForRelay(req, dst.name, dst.ip, dst.port);
        return sendAndWait(w.packet, w.addr, w.port, w.outerTxid);}

    
    
    

    private List<AddrEntry> iterativeNearest(byte[] target, int n, int rounds) throws Exception {
        Set<String> asked = new HashSet<>();
        for (int round = 0; round < rounds; round++) {
            List<AddrEntry> cands = closestKnownTo(target, Math.max(n, 5));
            AddrEntry pick = null;
            for (AddrEntry e : cands) {
                if (!asked.contains(e.name)) { pick = e; break; }}
            if (pick == null) break;
            asked.add(pick.name);
            
            
            
            byte[] txid = newTxid();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(txid);
            out.write(SPACE);
            out.write('N');
            
            
            //<--->
            
            
            out.write(SPACE);
            out.write(bytesToHex(target).getBytes(StandardCharsets.US_ASCII));
            byte[] req = out.toByteArray();
            byte[] reply;
            if (relays.isEmpty()) {
                reply = sendAndWait(req, pick.ip, pick.port, txid);} else {
                Wrapped w = wrapForRelay(req, pick.name, pick.ip, pick.port);
                reply = sendAndWait(w.packet, w.addr, w.port, w.outerTxid);}
            if (reply == null) continue;
            if (reply.length < 5 || reply[3] != 'O' || reply[4] != SPACE) continue;
            int p = 5;
            while (p < reply.length) {
                ParsedString k2 = decodeString(reply, p);
                if (k2 == null) break;
                ParsedString v2 = decodeString(reply, k2.next);

                //sec
                
                if (v2 == null) break;
                if (k2.value != null && k2.value.startsWith("N:")) {
                    rememberPeer(k2.value, v2.value);}
                p = v2.next;}}
        return closestKnownTo(target, n); }}