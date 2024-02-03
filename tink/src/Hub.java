import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

/* Написал логику для хаба и сенсора, остальное не успел, к сожалению из-за работы

* */
public class Hub {
    private static final String HUB_NAME = "HUB01";
    static int numOfSerials = 0;


    public static void main(String[] args) {
        String str = sendRequest(whoIsHere(), "http://localhost:9998");
        Packet packet = base64Decoder("OAL_fwMCAQhTRU5TT1IwMQ8EDGQGT1RIRVIxD7AJBk9USEVSMgCsjQYGT1RIRVIzCAAGT1RIRVI03Q");
        if (packet == null) {
//            System.out.println("битый пакет");
        } else {
//            System.out.println(packet);
//            System.out.println(str);
        }
    }


    private static String sendRequest(String reqPacket, String url) {
        HttpClient client = HttpClient.newHttpClient();
        try {
            HttpRequest request = HttpRequest.newBuilder(new URI(url)).POST(HttpRequest.BodyPublishers.ofString(reqPacket)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
//            System.out.println(response.statusCode());

            switch (response.statusCode()) {
                case 200:
                    return response.body();
                case 204:
                    System.exit(0);
                default:
                    System.exit(99);
            }

        } catch (URISyntaxException | InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
        return null;
    }


    private static String whoIsHere() {
        Packet packet = new Packet();
        Payload payload = new Payload();
        payload.devType = 1;
        payload.src = 1;
        payload.dst = 16383;
//        numOfSerials++;
        payload.serial = 1;
        payload.cmd = 1;
        CmdBody cmdBody = new CmdBody();
        cmdBody.devName = HUB_NAME;
        payload.cmdBody = cmdBody;
        packet.payload = payload;
        byte[] binarPacket = packet.getBinaryDataPacket();
        byte[] encode = Base64.getUrlEncoder().encode(binarPacket);
//        System.out.println(new String(encode));
        String finalEncoded = new String(encode);
        return finalEncoded.replace("=", "");
    }



    private static byte computeCRC8(byte[] bytes) {
        byte generator = 0x1D;
        byte crc = 0;
        for (byte currByte : bytes) {
            crc ^= currByte;
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x80) != 0) {
                    crc = (byte)((crc << 1) ^ generator);
                } else {
                    crc <<= 1;
                }
            }
        }
        return crc;
    }


    private static Packet base64Decoder(String base) {
        byte[] decoded = Base64.getUrlDecoder().decode(base);
        byte crc = computeCRC8(Arrays.copyOfRange(decoded, 1, decoded.length - 1));
        if (crc != decoded[decoded.length - 1]) {
            return null;
        }
        Packet packet = new Packet(decoded);
        return packet;
    }


    private static class Packet {
        private int length;
        private int crc8;
        private Payload payload;

        public byte[] getBinaryDataPacket() {
            List<Byte> pay = this.payload.getBinaryDataPayload();
            byte[] bytik = new byte[pay.size()];
            for (int i = 0; i < pay.size(); i++) {
                bytik[i] = pay.get(i);
            }
            byte[] result = new byte[bytik.length + 2];
            result[0] = (byte) bytik.length;
            // System.out.println(result[0]);
            for (int i = 0; i < bytik.length; i++) {
                result[i + 1] = bytik[i];
            }
            result[result.length - 1] = computeCRC8(bytik);
            return result;
        }

        public Packet(byte[] decodedBytes) {
            this.length = Byte.toUnsignedInt(decodedBytes[0]);
            this.crc8 = Byte.toUnsignedInt(decodedBytes[decodedBytes.length - 1]);
            this.payload = new Payload(Arrays.copyOfRange(decodedBytes, 1, decodedBytes.length - 1));
        }


        public Packet() {
        }

        @Override
        public String toString() {
            return "Packet{" +
                    "length=" + length +
                    ", crc8=" + crc8 +
                    ", payload=" + payload +
                    '}';
        }
    }

    private static byte[] leb128Encoder(long varuint) {
        List<Byte> result = new ArrayList<>();

        do {
            byte value = (byte) (varuint & 0x7F);
            varuint >>= 7;

            if (varuint != 0) {
                value |= (byte) 0x80;
            }

            result.add(value);
        } while (varuint != 0);

        byte[] byteArray = new byte[result.size()];
        for (int i = 0; i < result.size(); i++) {
            byteArray[i] = result.get(i);
        }

        return byteArray;
    }



    private static Pair leb128Decoder(byte[] twoBytes, int startIndex) {
        int startId = startIndex;
        while (Byte.toUnsignedInt(twoBytes[startIndex]) > 127) {
            startIndex++;
        }
        startIndex++;
        int finishId = startIndex;
        byte[] tmp = Arrays.copyOfRange(twoBytes, startId, finishId);
        int MASK_DATA = 0x7f;
        int MASK_CONTINUE = 0x80;
        long value = 0;
        int bitSize = 0;
        int read;
        int BITS_LONG = 64;
        InputStream inputStream = new ByteArrayInputStream(tmp);
        try {
            do {

                if ((read = inputStream.read()) == -1) {
                    throw new IOException("Unexpected EOF");
                }

                value += (long) (read & MASK_DATA) << bitSize;
                bitSize += 7;
                if (bitSize >= BITS_LONG) {
                    throw new ArithmeticException("LEB128 value exceeds maximum value for long type.");
                }

            } while ((read & MASK_CONTINUE) != 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new Pair(value, finishId);
    }


    private static class Payload {
        private int src;
        private int dst;
        private int serial;
        private int devType;
        private int cmd;
        private Object cmdBody;


        public Payload() {
        }


        public Payload(byte[] payloadDecodedBytes) {
            int indexPosition = 0;
            Pair pair = leb128Decoder(payloadDecodedBytes, indexPosition);
            this.src = Integer.parseInt(String.valueOf(pair.getValue()));
            indexPosition = pair.getIndex();

            pair = leb128Decoder(payloadDecodedBytes, indexPosition);
            this.dst = Integer.parseInt(String.valueOf(pair.getValue()));
            indexPosition = pair.getIndex();

            pair = leb128Decoder(payloadDecodedBytes, indexPosition);
            this.serial = Integer.parseInt(String.valueOf(pair.getValue()));
            indexPosition = pair.getIndex();

            this.devType = Byte.toUnsignedInt(payloadDecodedBytes[indexPosition]);
            indexPosition++;
            this.cmd = Byte.toUnsignedInt(payloadDecodedBytes[indexPosition]);
            byte[] byteString = Arrays.copyOfRange(payloadDecodedBytes, indexPosition + 1, payloadDecodedBytes.length);
            if (((devType == 3) || (devType == 4) || (devType == 5)) && cmd == 4) {
                this.cmdBody = (byteString[0] == 0x1);
            } else if (((devType == 4) || (devType == 5)) && cmd == 5) {
                this.cmdBody = (byteString[0] == 0x1);
            } else {
                this.cmdBody = new CmdBody(byteString, devType, cmd);
            }
        }

        public List<Byte> getBinaryDataPayload() {
            List<Byte> result = new ArrayList<>();
            byte[] bytes = leb128Encoder(this.src);
            for (byte aByte : bytes) {
                result.add(aByte);
            }
            bytes = leb128Encoder(this.dst);
            for (byte aByte : bytes) {
                result.add(aByte);
            }
            bytes = leb128Encoder(this.serial);
            for (byte aByte : bytes) {
                result.add(aByte);
            }
//            System.out.println((byte) this.devType);
            result.add((byte) this.devType);
//            System.out.println((byte) this.cmd);
            result.add((byte) this.cmd);
            if (((devType == 3) || (devType == 4) || (devType == 5)) && cmd == 4) {
                result.add((Byte) cmdBody);
            } else if (((devType == 4) || (devType == 5)) && cmd == 5) {
                result.add((Byte) cmdBody);
            } else {
                CmdBody cmdBody1 = (CmdBody) this.cmdBody;
                result.addAll(cmdBody1.getBinaryCmndBody(devType, cmd));
            }
            return result;
        }



        @Override
        public String toString() {
            return "Payload{" +
                    "src=" + src +
                    ", dst=" + dst +
                    ", serial=" + serial +
                    ", devType=" + devType +
                    ", cmd=" + cmd +
                    ", cmdBody=" + cmdBody +
                    '}';
        }
    }


    private static class CmdBody {
        String devName;
        Object devProps;

        public List<Byte> getBinaryCmndBody(int devType, int  cmd) {
            List<Byte> result = new ArrayList<>();
            if (this.devName != null && !this.devName.isEmpty()) {
                byte[] res = this.devName.getBytes();
                result.add((byte) this.devName.length());
                for (byte re : res) {
//                    System.out.println(re);
                    result.add(re);
                }
            }
            if (devProps != null) {
                switch (devType) {
                    case 2:
                        switch (cmd) {
                            case 1:
                            case 2:
                                DevPropsEnvSensorWI devProps1 = (DevPropsEnvSensorWI) this.devProps;
                                result.addAll(devProps1.getBinatyEnvSensorWI());
                                break;
                            case 4:
                                DevPropsEnvSensorStatus devProps2 = (DevPropsEnvSensorStatus) this.devProps;
                                result.addAll(devProps2.getBinaryEnvSensorStatus());
                                break;
                        }
                        break;
                    case 3:
                        switch (cmd) {
                            case 1:
                            case 2:
                                DevPropsSwitchWI devProps3 = (DevPropsSwitchWI) this.devProps;
                                result.addAll(devProps3.getBinarySwitchWI());
                                break;
                        }
                        break;
                    case 6:
                        if (cmd == 6) {
                            ClockTick devProps4 = (ClockTick) this.devProps;
                            result.addAll(devProps4.getbinaryClock());
                        }
                        break;
                }
            }

//            System.out.println(result);
            return result;

        }
        public CmdBody() {
        }

        public CmdBody(byte[] byteString, int devType, int cmd) {
            Pair name = getName(byteString);
            this.devName = (String) name.getValue();
            int index = name.getIndex();
            if (devName == null || devName.isEmpty()) {
                index = 0;
            }
            if (index <= byteString.length) {
                byte[] transByte = Arrays.copyOfRange(byteString, index, byteString.length);
                switch (devType) {
                    case 1:
                    case 4:
                    case 5:
                        this.devProps = null;
                        break;
                    case 2:
                        switch (cmd) {
                            case 1:
                            case 2:
                                this.devProps = new DevPropsEnvSensorWI(transByte);
                                break;
                            case 3:
                                this.devProps = null;
                                break;
                            case 4:
                                this.devProps = new DevPropsEnvSensorStatus(transByte);
                                break;
                        }
                        break;
                    case 3:
                        switch (cmd) {
                            case 1:
                            case 2:
                                this.devProps = new DevPropsSwitchWI(transByte);
                                break;
                            case 3:
                                this.devProps = null;
                                break;
                        }
                        break;
                    case 6:
                        if (cmd == 2) {
                            this.devProps = null;
                        } else {
                            this.devProps = new ClockTick(transByte);
                        }
                        break;
                }
            }

        }


        @Override
        public String toString() {
            return "CmdBody{" +
                    "devName='" + devName + '\'' +
                    ", devProps=" + devProps +
                    '}';
        }
    }


    private static class ClockTick {
        long timestamp;


        public ClockTick(byte[] encodedTime) {
//            System.out.println(Arrays.toString(encodedTime));
            this.timestamp = Long.parseLong(String.valueOf(leb128Decoder(encodedTime, 0).getValue()));
        }

        public List<Byte> getbinaryClock() {
            byte[] b = leb128Encoder(this.timestamp);
            List<Byte> result = new ArrayList<>();
            for (byte b1 : b) {
                result.add(b1);
            }
            return result;
        }


        @Override
        public String toString() {
            return "ClockTick{" +
                    "timestamp=" + timestamp +
                    '}';
        }
    }


    private static class Pair {
        private Object value;
        private int index;


        public Pair(Object value, int index) {
            this.value = value;
            this.index = index;
        }


        public Object getValue() {
            return value;
        }

        public int getIndex() {
            return index;
        }


        @Override
        public String toString() {
            return "Pair{" +
                    "value=" + value +
                    ", index=" + index +
                    '}';
        }
    }


    private static class DevPropsEnvSensorWI {
        int sensors;
        List<Triggers> triggers;


        public DevPropsEnvSensorWI(byte[] transByte) {
            this.sensors = Byte.toUnsignedInt(transByte[0]);
//            System.out.println(Integer.toBinaryString(sensors));
//            System.out.println(sensors & 0x01);
//            System.out.println((sensors >> 1) & 0x01);
//            System.out.println((sensors >> 2) & 0x01);
//            System.out.println((sensors >> 3) & 0x01);


            triggers = new ArrayList<>();
            int op;
            long value;
            Pair name;
            byte[] restOfTransByte = Arrays.copyOfRange(transByte, 2, transByte.length);
            int index = 0;

//          [12, 100, 6, 79, 84, 72, 69, 82, 49, 15, -80, 9, 6, 79, 84, 72, 69, 82, 50, 0, -84, -115, 6, 6, 79, 84, 72, 69, 82, 51, 8, 0, 6, 79, 84, 72, 69, 82, 52]
//          12,     100,                    6,79, 84, 72, 69, 82, 49,
//          15,     -80, 9,                 6,79, 84, 72, 69, 82, 50,
//          0,      -84, -115,6,            6,79, 84, 72, 69, 82, 51
//          8,      0,                      6,79, 84, 72, 69, 82, 52

            Pair pair;
            while (index < restOfTransByte.length) {
                op = Byte.toUnsignedInt(restOfTransByte[index]);
//                System.out.println(op + " : " + Integer.toBinaryString(op));
                index++;
                pair = leb128Decoder(restOfTransByte, index);
                value = Integer.parseInt(String.valueOf(pair.getValue()));
                index = pair.getIndex();
                name = getName(Arrays.copyOfRange(restOfTransByte, index, restOfTransByte.length));
                triggers.add(new Triggers(op, value, (String) name.getValue()));
                index += name.getIndex();
            }
        }

        public List<Byte> getBinatyEnvSensorWI() {
            List<Byte> result = new ArrayList<>();
            result.add((byte) this.sensors);
            if(this.triggers != null && !this.triggers.isEmpty()) {
                for (Triggers trigger : this.triggers) {
                    if (trigger != null) {
                        result.add((byte) trigger.op);
                        byte[] b = leb128Encoder(trigger.value);
                        for (byte b1 : b) {
                            result.add(b1);
                        }
                        if (trigger.name != null && !trigger.name.isEmpty()) {
                            b = trigger.name.getBytes();
                            for (byte b1 : b) {
                                result.add(b1);
                            }
                        }
                    }
                }
            }
            return result;
        }


        @Override
        public String toString() {
            return "DevPropsEnvSensorWI{" +
                    "sensors=" + sensors +
                    ", triggers=" + triggers +
                    '}';
        }
    }


    private static Pair getName(byte[] inputData) {
        char[] charArray = new String(inputData).toCharArray();
        StringBuilder sb = new StringBuilder();
        int i;
        for (i = 1; i < charArray.length; i++) {
            if (Character.isWhitespace(charArray[i]) || !Character.isLetterOrDigit(charArray[i])) {
                break;
            } else {
                sb.append(charArray[i]);
            }
        }
        return new Pair(sb.toString(), i);
    }


    private static class DevPropsEnvSensorStatus {
        List<Integer> values;


        public DevPropsEnvSensorStatus(byte[] arrOfValues) {
            values = new ArrayList<>();
            int index = 0;
            int decodedValue;
            Pair pair;
            while (index < arrOfValues.length) {
                pair = leb128Decoder(arrOfValues, index);
                decodedValue = Integer.parseInt(String.valueOf(pair.getValue()));
                index = pair.getIndex();
                values.add(decodedValue);
            }
        }

        public List<Byte> getBinaryEnvSensorStatus() {
            List<Byte> result = new ArrayList<>();
            byte[] bytes;
            for (Integer value : values) {
                bytes = leb128Encoder(value);
                for (byte aByte : bytes) {
                    result.add(aByte);
                }

            }
            return result;
        }


        @Override
        public String toString() {
            return "DevPropsEnvSensorStatus{" +
                    "values=" + values +
                    '}';
        }
    }


    private static class DevPropsSwitchWI {
        List<String> devNames;


        public DevPropsSwitchWI(byte[] encodedDevNames) {
            byte[] t = new byte[]{5};
            String reg = new String(t);
            devNames = new ArrayList<>(Arrays.asList(new String(Arrays.copyOfRange(encodedDevNames, 2, encodedDevNames.length)).split(reg)));
        }

        public List<Byte> getBinarySwitchWI() {
            List<Byte> result = new ArrayList<>();
            byte[] b;
            for (String devName : devNames) {
                if (devName != null && !devName.isEmpty()) {
                    b = devName.getBytes();
                    for (byte b1 : b) {
                        result.add(b1);
                    }
                }
            }
            return result;
        }


        @Override
        public String toString() {
            return "DevPropsSwitchWI{" +
                    "devNames=" + devNames +
                    '}';
        }
    }


    private static class Triggers {
        int op;
        long value;
        String name;


        public Triggers(int op, long value, String name) {
            this.op = op;
            this.value = value;
            this.name = name;
        }


        @Override
        public String toString() {
            return "Triggers{" +
                    "op=" + op +
                    ", value=" + value +
                    ", name='" + name + '\'' +
                    '}';
        }
    }


}