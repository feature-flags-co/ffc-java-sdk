package co.featureflags.server;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

final class VariationSplittingAlgorithm {
    static boolean ifKeyBelongsPercentage(String key, List<Double> percentageRange) {
        boolean res = false;
        try {
            Double min = percentageRange.get(0);
            Double max = percentageRange.get(1);
            if (min == 0D && max == 1D)
                return true;
            Double percentage = percentageOfKey(key);
            res = percentage >= min && percentage < max;
        } finally {
            return res;
        }

    }

    static double percentageOfKey(String key) {
        Double res = 0D;
        try {

            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(key.getBytes(StandardCharsets.US_ASCII));
            byte[] digest = md5.digest();
            int magicNumber = toInt32(digest, 0);
            res = Math.abs((double) magicNumber / Integer.MIN_VALUE);
        } finally {
            return res;
        }
    }

    private static int toInt32(byte[] bytes, int index) {
        if (bytes.length < 4)
            return 0;
        return (int) ((int) (0xff & bytes[index]) << 56 | (int) (0xff & bytes[index + 1]) << 48
                | (int) (0xff & bytes[index + 2]) << 40 | (int) (0xff & bytes[index + 3]) << 32);
    }
}
