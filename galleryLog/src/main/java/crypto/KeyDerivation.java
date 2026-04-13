package crypto;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;

public class KeyDerivation {

    private static final byte[] BASE_SALT = "Salt".getBytes(StandardCharsets.UTF_8);
    private static final int ITERATIONS = 65536;
    private static final int KEY_BITS = 256;

    public static byte[] deriveKey(String token, String logContext) {
        PBEKeySpec spec = null;
        try {
            byte[] contextualSalt = deriveContextualSalt(logContext);
            spec = new PBEKeySpec(token.toCharArray(), contextualSalt, ITERATIONS, KEY_BITS);
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

    private static byte[] deriveContextualSalt(String logContext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(BASE_SALT);
            digest.update((byte) 0);
            digest.update(canonicalizeLogContext(logContext).getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (Exception e) {
            throw new RuntimeException("salt derivation failed", e);
        }
    }

    private static String canonicalizeLogContext(String logContext) {
        if (logContext == null || logContext.isBlank()) {
            return "";
        }
        return Path.of(logContext).toAbsolutePath().normalize().toString();
    }
}