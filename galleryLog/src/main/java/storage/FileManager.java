package storage;

import crypto.Encryption;
import crypto.IntegrityViolationException;
import model.Entry;
import model.Record;

import javax.crypto.AEADBadTagException;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class FileManager {

    public static List<Record> readRecords(Path filePath, Encryption encryption)
            throws IOException {
        validateArgs(filePath, encryption);

        if (!Files.exists(filePath)) {
            return new ArrayList<>();
        }

        List<Record> records = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    throw new IntegrityViolationException(new IllegalArgumentException("blank log line"));
                }

                try {
                    records.add(encryption.decrypt(line));
                } catch (AEADBadTagException e) {
                    // GCM tag failure = chain broken or file tampered
                    throw new IntegrityViolationException(e);
                } catch (Exception e) {
                    throw new IntegrityViolationException(e);
                }
            }
        }

        return records;
    }

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

    private static void validateArgs(Path filePath, Encryption encryption) {
        if (filePath == null) {
            throw new IllegalArgumentException("filePath must not be null");
        }
        if (encryption == null) {
            throw new IllegalArgumentException("encryption must not be null");
        }
    }
}