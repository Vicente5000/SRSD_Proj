import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class FileManager {
    private FileManager() {
        // Utility class.
        //maybe add at the start of the class encrypt class depenedeancy
    }

    public static Record readRecord(Path filePath, int lineNumber, Encryption encryption) throws IOException {
        if (encryption == null) {
            throw new IllegalArgumentException("encryption must not be null");
        }
        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be >= 1");
        }

        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        if (lineNumber > lines.size()) {
            throw new IndexOutOfBoundsException("lineNumber exceeds file length");
        }

        String line = lines.get(lineNumber - 1);
        try {
            return encryption.decrypt(line);
        } catch (Exception exception) {
            throw new IOException("failed to decrypt record", exception);
        }
    }

    public static List<Record> readRecords(Path filePath, Encryption encryption) throws IOException {
        if (encryption == null) {
            throw new IllegalArgumentException("encryption must not be null");
        }
        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        List<Record> records = new ArrayList<>(lines.size());

        for (String line : lines) {
            try {
                records.add(encryption.decrypt(line));
            } catch (Exception exception) {
                throw new IOException("failed to decrypt record", exception);
            }
        }

        return records;
    }

    public static void writeRecord(Path filePath, Record record, Encryption encryption) throws IOException {
        if (encryption == null) {
            throw new IllegalArgumentException("encryption must not be null");
        }
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        Entry entry;
        try {
            entry = encryption.encrypt(record);
        } catch (Exception exception) {
            throw new IOException("failed to encrypt record", exception);
        }

        Files.writeString(
                filePath,
                entry.toLine() + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }
}
