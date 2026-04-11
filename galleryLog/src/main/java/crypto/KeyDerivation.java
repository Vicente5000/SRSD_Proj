package crypto;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class KeyDerivation {

    private static final byte[] SALT = "GalleryLogSalt!!".getBytes();
    private static final int ITERATIONS = 65536;
    private static final int KEY_BITS = 256;

    public static byte[] deriveKey(String token) {
        try {
            var spec = new PBEKeySpec(token.toCharArray(), SALT, ITERATIONS, KEY_BITS);
            return SecretKeyFactory
                    .getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (Exception e) {
            throw new RuntimeException("Key derivation failed", e);
        }
    }
}