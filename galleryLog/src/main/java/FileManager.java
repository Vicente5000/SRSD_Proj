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

    public static String getLine(String fileName, int lineNumber) throws IOException {
        return getLine(Path.of(fileName), lineNumber);
    }

    public static String getLine(Path filePath, int lineNumber) throws IOException {
        if (lineNumber < 1) {
            throw new IllegalArgumentException("lineNumber must be >= 1");
        }

        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        if (lineNumber > lines.size()) {
            throw new IndexOutOfBoundsException("lineNumber exceeds file length");
        }

        String line = lines.get(lineNumber - 1);
        return decrypt(line);
    }

    public static List<String> getAllLines(String fileName) throws IOException {
        return getAllLines(Path.of(fileName));
    }

    public static List<String> getAllLines(Path filePath) throws IOException {
        List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
        List<String> result = new ArrayList<>(lines.size());

        for (String line : lines) {
            result.add(decrypt(line));
        }

        return result;
    }

    public static void appendLine(String fileName, String line) throws IOException {
        appendLine(Path.of(fileName), line);
    }

    public static void appendLine(Path filePath, String line) throws IOException {
        String value = encrypt(line);
        Files.writeString(
                filePath,
                value + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }
    private static String encrypt(String line){
        if(line == null){
            return line;
        }
        //call encrypt function
        return line;
    }

    private static String decrypt(String line) {
        if (line == null) {
            return line;
        }
        //call decrypt function
        return line;
    }
}
