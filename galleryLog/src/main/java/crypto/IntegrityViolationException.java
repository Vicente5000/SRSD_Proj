package crypto;

/**
 * Encryption — entry encryption with AES-256-GCM.
 *
 * Entry layout:
 * IV || cipherText || hashOfLastEntry
 *
 */

public class IntegrityViolationException extends RuntimeException {

    public IntegrityViolationException(Throwable cause) {
        super("integrity violation", cause);
    }
}
