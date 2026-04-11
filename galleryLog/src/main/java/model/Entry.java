import java.util.Arrays;
import java.util.Base64;

public final class Entry {
    public static final int IV_LEN = 12;
    public static final int HASH_LEN = 32;

    public final byte[] iv;
    public final byte[] cipherText;
    public final byte[] hashOfLastEntry;

    public Entry(byte[] iv, byte[] cipherText, byte[] hashOfLastEntry) {
        if (iv == null || iv.length != IV_LEN) {
            throw new IllegalArgumentException("iv must be exactly " + IV_LEN + " bytes");
        }
        if (cipherText == null || cipherText.length == 0) {
            throw new IllegalArgumentException("cipherText must not be null or empty");
        }
        if (hashOfLastEntry == null || hashOfLastEntry.length != HASH_LEN) {
            throw new IllegalArgumentException("hashOfLastEntry must be exactly " + HASH_LEN + " bytes");
        }

        this.iv = Arrays.copyOf(iv, IV_LEN);
        this.cipherText = Arrays.copyOf(cipherText, cipherText.length);
        this.hashOfLastEntry = Arrays.copyOf(hashOfLastEntry, HASH_LEN);
    }

    public byte[] toBytes() {
        byte[] bytes = new byte[IV_LEN + cipherText.length + HASH_LEN];
        System.arraycopy(iv, 0, bytes, 0, IV_LEN);
        System.arraycopy(cipherText, 0, bytes, IV_LEN, cipherText.length);
        System.arraycopy(hashOfLastEntry, 0, bytes, IV_LEN + cipherText.length, HASH_LEN);
        return bytes;
    }

    public String toLine() {
        return Base64.getEncoder().encodeToString(iv)
                + ","
                + Base64.getEncoder().encodeToString(cipherText)
                + ","
                + Base64.getEncoder().encodeToString(hashOfLastEntry);
    }

    public static Entry fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length < IV_LEN + HASH_LEN) {
            throw new IllegalArgumentException("bytes must contain a full entry");
        }

        byte[] iv = Arrays.copyOfRange(bytes, 0, IV_LEN);
        byte[] hashOfLastEntry = Arrays.copyOfRange(bytes, bytes.length - HASH_LEN, bytes.length);
        byte[] cipherText = Arrays.copyOfRange(bytes, IV_LEN, bytes.length - HASH_LEN);
        return new Entry(iv, cipherText, hashOfLastEntry);
    }

    public static Entry fromLine(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("line must not be null or blank");
        }
        String[] parts = line.trim().split(",", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("line must contain exactly 3 CSV columns");
        }

        byte[] iv = Base64.getDecoder().decode(parts[0]);
        byte[] cipherText = Base64.getDecoder().decode(parts[1]);
        byte[] hashOfLastEntry = Base64.getDecoder().decode(parts[2]);
        return new Entry(iv, cipherText, hashOfLastEntry);
    }
}