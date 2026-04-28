public class HelperTest {

    private static void check(String name, boolean condition) {
        if (condition) {
            System.out.println("[OK] " + name);
        } else {
            System.out.println("[FAIL] " + name);
        }
    }

    public static void main(String[] args) throws Exception {

        check("isNodeName N:test", Node.isNodeName("N:test"));
        check("isNodeName D:test false", !Node.isNodeName("D:test"));
        check("isDataName D:message", Node.isDataName("D:message"));
        check("isDataName N:message false", !Node.isDataName("N:message"));
        check("isNodeName null false", !Node.isNodeName(null));
        check("isDataName null false", !Node.isDataName(null));

        String hash1 = Node.hashIDHex("N:test");
        String hash2 = Node.hashIDHex("D:message");

        System.out.println("hash N:test = " + hash1);
        System.out.println("hash D:message = " + hash2);

        check("hash length is 64", hash1.length() == 64);
        check("distance same hash is 0", Node.hashDistance(hash1, hash1) == 0);
        check(
                "distance is symmetric",
                Node.hashDistance(hash1, hash2) == Node.hashDistance(hash2, hash1)
        );
        check("bad hash gives -1", Node.hashDistance("bad", hash2) == -1);

        check(
                "encode Hello",
                Node.encodeString("Hello").equals("0 Hello ")
        );

        check(
                "encode Hello World",
                Node.encodeString("Hello World").equals("1 Hello World ")
        );

        check(
                "encode empty string",
                Node.encodeString("").equals("0  ")
        );

        Node.DecodeResult decoded1 = Node.decodeString("0 Hello ");
        check("decode Hello not null", decoded1 != null);
        check("decode Hello value", decoded1 != null && decoded1.value.equals("Hello"));

        Node.DecodeResult decoded2 = Node.decodeString("1 Hello World ");
        check("decode Hello World not null", decoded2 != null);
        check("decode Hello World value", decoded2 != null && decoded2.value.equals("Hello World"));

        Node.DecodeResult decoded3 = Node.decodeString("0  ");
        check("decode empty not null", decoded3 != null);
        check("decode empty value", decoded3 != null && decoded3.value.equals(""));

        check("bad decode empty", Node.decodeString("") == null);
        check("bad decode no number", Node.decodeString("Hello") == null);
        check("bad decode wrong number", Node.decodeString("2 Hello ") == null);

        Node.Address address1 = Node.parseAddress("127.0.0.1:20110");
        check("parse good address not null", address1 != null);
        check("parse good address ip", address1 != null && address1.ip.equals("127.0.0.1"));
        check("parse good address port", address1 != null && address1.port == 20110);

        check("parse bad address no port", Node.parseAddress("127.0.0.1") == null);
        check("parse bad address text port", Node.parseAddress("127.0.0.1:abc") == null);
        check("parse bad address too high", Node.parseAddress("127.0.0.1:99999") == null);

        System.out.println("Phase 2 helper tests finished.");
        String encoded = Node.encodeString("Hello World");

        System.out.println("Normal: [" + encoded + "]");
        System.out.println("Dots:   [" + encoded.replace(' ', '.') + "]");
        System.out.println("Length: " + encoded.length());
        System.out.println("[" + Node.encodeString("Hello").replace(' ', '.') + "]");
        System.out.println("[" + Node.encodeString("").replace(' ', '.') + "]");
        System.out.println("[" + Node.encodeString("a b c").replace(' ', '.') + "]");
    }
}