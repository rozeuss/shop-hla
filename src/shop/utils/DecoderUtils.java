package shop.utils;

import hla.rti1516e.encoding.*;

public class DecoderUtils {

    private DecoderUtils() {
    }

    public static int decodeInt(EncoderFactory encoderFactory, ByteWrapper bytes) {
        HLAinteger32BE value = encoderFactory.createHLAinteger32BE();
        try {
            value.decode(bytes);
        } catch (DecoderException de) {
            System.out.println("DecoderInt Exception: " + de.getMessage());
        }
        return value.getValue();
    }

    public static boolean decodeBoolean(EncoderFactory encoderFactory, ByteWrapper bytes) {
        HLAboolean value = encoderFactory.createHLAboolean();
        try {
            value.decode(bytes);
        } catch (DecoderException de) {
            System.out.println("DecoderBoolean Exception: " + de.getMessage());
        }
        return value.getValue();
    }
}
