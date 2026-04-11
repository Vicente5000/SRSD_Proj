package storage;

import crypto.Encryption;
import crypto.IntegrityViolationException;
import model.Entry;
import model.Record;

import javax.crypto.AEADBadTagException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class FileManager {

    private FileManager() {}

    // ── Read all records ─────────────────────────────────────────────────────

    /**
     * Reads and decrypts every record in the file, verifying the hash chain
     * as it goes. Throws IntegrityViolationException if any entry fails.
     */
    public static List<Record> readRecords(Path filePath, Encryption encryption)
            throws IOException {
        validateArgs(filePath, encryption);

        if (!Files.exists(filePath)) {
            return new ArrayList<>(); // empty log is valid
        }

        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        List<Record> records = new ArrayList<>(lines.size());

        for (String line : lines) {
            if (line.isBlank()) continue; // skip empty trailing lines

            try {
                records.add(encryption.decrypt(line));
            } catch (AEADBadTagException e) {
                // GCM tag failure = chain broken or file tampered
                throw new IntegrityViolationException(e);
            } catch (Exception e) {
                throw new IntegrityViolationException(e);
            }
        }

        return records;
    }

    // ── Write one record ─────────────────────────────────────────────────────

    /**
     * Appends one encrypted record to the file.
     *
     * IMPORTANT: the Encryption instance passed here must have already
     * replayed all existing entries (via readRecords) so that lastEntryHash
     * is at the correct position in the chain. Use writeRecord only after
     * readRecords on the same Encryption instance.
     */
    public static void writeRecord(Path filePath, Record record, Encryption encryption)
            throws IOException {
        validateArgs(filePath, encryption);
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }

        Entry entry;
        try {
            entry = encryption.encrypt(record);
        } catch (Exception e) {
            throw new IOException("failed to encrypt record", e);
        }

        Files.writeString(
                filePath,
                entry.toLine() + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
    }

    // ── Combined: replay chain then append ───────────────────────────────────

    /**
     * The correct way to append a new record to an existing log.
     *
     * This method:
     *   1. Reads + verifies the existing chain (advances lastEntryHash)
     *   2. Appends the new encrypted record at the correct chain position
     *   3. Returns all existing records so the caller can validate state
     *
     * This is the method LogAppend should always use.
     */
    public static List<Record> replayAndAppend(Path filePath, Record record, Encryption encryption)
            throws IOException {
        validateArgs(filePath, encryption);
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }

        // Step 1: replay existing chain — this advances lastEntryHash correctly
        List<Record> existing = readRecords(filePath, encryption);

        // Step 2: now encrypt and append (lastEntryHash is at the right position)
        writeRecord(filePath, record, encryption);

        return existing;
    }
    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void validateArgs(Path filePath, Encryption encryption) {
        if (filePath == null) {
            throw new IllegalArgumentException("filePath must not be null");
        }
        if (encryption == null) {
            throw new IllegalArgumentException("encryption must not be null");
        }
    }
}