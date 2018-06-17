package shop.utils;

import hla.rti1516e.encoding.*;
import lombok.experimental.UtilityClass;

@UtilityClass
public class DecoderUtils {

    public int decodeInt(EncoderFactory encoderFactory, ByteWrapper bytes) {
        HLAinteger32BE value = encoderFactory.createHLAinteger32BE();
        try {
            value.decode(bytes);
        } catch (DecoderException de) {
            System.out.println("DecoderInt Exception: " + de.getMessage());
        }
        return value.getValue();
    }

    public boolean decodeBoolean(EncoderFactory encoderFactory, ByteWrapper bytes) {
        HLAboolean value = encoderFactory.createHLAboolean();
        try {
            value.decode(bytes);
        } catch (DecoderException de) {
            System.out.println("DecoderBoolean Exception: " + de.getMessage());
        }
        return value.getValue();
    }

    public byte[] encodeInt(EncoderFactory encoderFactory, int val) {
        return encoderFactory.createHLAinteger32BE((val)).toByteArray();
    }

    public byte[] encodeBoolean(EncoderFactory encoderFactory, boolean val) {
        return encoderFactory.createHLAboolean((val)).toByteArray();
    }
}
