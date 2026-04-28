// IN2011 Computer Networks
// Coursework 2024/2025
//
// Submission by
//  YOUR_NAME_GOES_HERE
//  YOUR_STUDENT_ID_NUMBER_GOES_HERE
//  YOUR_EMAIL_GOES_HERE


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



// Complete this!
public class Node implements NodeInterface {


    //helper method
    // =========================
    // Phase 2 Helper Methods
    // =========================

    static boolean isNodeName(String key) {
        return key != null && key.startsWith("N:");
    }

    static boolean isDataName(String key) {
        return key != null && key.startsWith("D:");
    }

    static String hashIDHex(String key) throws Exception {
        if (key == null) {
            throw new IllegalArgumentException("key cannot be null");
        }

        byte[] hashBytes = HashID.computeHashID(key);
        return bytesToHex(hashBytes);
    }

    static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        StringBuilder result = new StringBuilder();

        for (byte b : bytes) {
            result.append(String.format("%02x", b & 0xff));
        }

        return result.toString();
    }

    static byte[] hexToBytes(String hex) {
        if (hex == null) {
            return null;
        }

        if (hex.length() % 2 != 0) {
            return null;
        }

        byte[] result = new byte[hex.length() / 2];

        for (int i = 0; i < hex.length(); i += 2) {
            int high = Character.digit(hex.charAt(i), 16);
            int low = Character.digit(hex.charAt(i + 1), 16);

            if (high == -1 || low == -1) {
                return null;
            }

            result[i / 2] = (byte) ((high << 4) + low);
        }

        return result;
    }

    static int hashDistance(String hashA, String hashB) {
        byte[] a = hexToBytes(hashA);
        byte[] b = hexToBytes(hashB);

        if (a == null || b == null) {
            return -1;
        }

        if (a.length != 32 || b.length != 32) {
            return -1;
        }

        int matchingLeadingBits = 0;

        for (int i = 0; i < 32; i++) {
            int difference = (a[i] ^ b[i]) & 0xff;

            if (difference == 0) {
                matchingLeadingBits += 8;
            } else {
                int matchingBitsInThisByte = Integer.numberOfLeadingZeros(difference) - 24;
                matchingLeadingBits += matchingBitsInThisByte;
                break;
            }
        }

        return 256 - matchingLeadingBits;
    }

    static int countSpaces(String value) {
        if (value == null) {
            return 0;
        }

        int count = 0;

        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == ' ') {
                count++;
            }
        }

        return count;
    }

    static String encodeString(String value) {
        if (value == null) {
            value = "";
        }

        int spaces = countSpaces(value);
        return spaces + " " + value + " ";
    }

    static DecodeResult decodeString(String text, int startIndex) {
        if (text == null) {
            return null;
        }

        if (startIndex < 0 || startIndex >= text.length()) {
            return null;
        }

        int firstSpace = text.indexOf(' ', startIndex);

        if (firstSpace == -1) {
            return null;
        }

        String numberText = text.substring(startIndex, firstSpace);

        if (numberText.length() == 0) {
            return null;
        }

        int spacesNeeded;

        try {
            spacesNeeded = Integer.parseInt(numberText);
        } catch (NumberFormatException e) {
            return null;
        }

        if (spacesNeeded < 0) {
            return null;
        }

        int valueStart = firstSpace + 1;
        int spacesSeen = 0;

        for (int i = valueStart; i < text.length(); i++) {
            if (text.charAt(i) == ' ') {
                if (spacesSeen == spacesNeeded) {
                    String value = text.substring(valueStart, i);
                    return new DecodeResult(value, i + 1);
                }

                spacesSeen++;
            }
        }

        return null;
    }

    static DecodeResult decodeString(String text) {
        return decodeString(text, 0);
    }

    static class DecodeResult {
        final String value;
        final int nextIndex;

        DecodeResult(String value, int nextIndex) {
            this.value = value;
            this.nextIndex = nextIndex;
        }
    }

    static Address parseAddress(String addressText) {
        if (addressText == null) {
            return null;
        }

        int colonIndex = addressText.lastIndexOf(':');

        if (colonIndex == -1) {
            return null;
        }

        String ip = addressText.substring(0, colonIndex);
        String portText = addressText.substring(colonIndex + 1);

        if (ip.length() == 0 || portText.length() == 0) {
            return null;
        }

        if (ip.contains(" ") || portText.contains(" ")) {
            return null;
        }

        int port;

        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            return null;
        }

        if (port < 1 || port > 65535) {
            return null;
        }

        return new Address(ip, port);
    }

    static class Address {
        final String ip;
        final int port;

        Address(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        public String toString() {
            return ip + ":" + port;
        }
    }
    //-------- helper method end ---------

    public void setNodeName(String nodeName) throws Exception {
	throw new Exception("Not implemented");
    }

    public void openPort(int portNumber) throws Exception {
	throw new Exception("Not implemented");
    }

    public void handleIncomingMessages(int delay) throws Exception {
	throw new Exception("Not implemented");
    }
    
    public boolean isActive(String nodeName) throws Exception {
	throw new Exception("Not implemented");
    }
    
    public void pushRelay(String nodeName) throws Exception {
	throw new Exception("Not implemented");
    }

    public void popRelay() throws Exception {
        throw new Exception("Not implemented");
    }

    public boolean exists(String key) throws Exception {
	throw new Exception("Not implemented");
    }
    
    public String read(String key) throws Exception {
	throw new Exception("Not implemented");
    }

    public boolean write(String key, String value) throws Exception {
	throw new Exception("Not implemented");
    }

    public boolean CAS(String key, String currentValue, String newValue) throws Exception {
	throw new Exception("Not implemented");
    }
}
