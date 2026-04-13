package crypto;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;

public class KeyDerivation {

    private static final byte[] BASE_SALT = "Salt".getBytes(StandardCharsets.UTF_8);
    private static final int ITERATIONS = 65536;
    private static final int KEY_BITS = 256;

    public static byte[] deriveKey(String token, String logContext) {
        PBEKeySpec spec = null;
        try {
            spec = new PBEKeySpec(token.toCharArray(), BASE_SALT, ITERATIONS, KEY_BITS);
            return SecretKeyFactory
                    .getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (Exception e) {
            throw new RuntimeException("Key derivation failed", e);
        } finally {
            if (spec != null) spec.clearPassword();
        }
    }
}