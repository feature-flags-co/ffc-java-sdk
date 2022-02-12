package co.featureflags.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

final class VariationSplittingAlgorithm {
    static boolean ifKeyBelongsPercentage(String key, List<Double> percentageRange) {
        try {
            double min = percentageRange.get(0);
            double max = percentageRange.get(1);
            if (min == 0D && max == 1D)
                return true;
            double percentage = percentageOfKey(key);
            return percentage >= min && percentage < max;
        } catch (Exception ex) {
            return false;
        }

    }

    static double percentageOfKey(String key) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(key.getBytes(StandardCharsets.US_ASCII));
            byte[] digest = md5.digest();
            int magicNumber = toInt32(digest, 0);
            return Math.abs((double) magicNumber / Integer.MIN_VALUE);
        } catch (Exception ex) {
            return 0D;
        }
    }

    public static int toInt32( byte[] bytes, int index )
            throws Exception {
        if ( bytes.length != 4 )
            throw new Exception( "The length of the byte array must be at least 4 bytes long." );
        return (int) ( (int) ( 0xff & bytes[index] ) << 56 | (int) ( 0xff & bytes[index + 1] ) << 48
                | (int) ( 0xff & bytes[index + 2] ) << 40 | (int) ( 0xff & bytes[index + 3] ) << 32 );
    }
}
