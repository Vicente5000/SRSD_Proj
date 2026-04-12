package cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogBatchFilesTest {

    private static final List<String> VALID_BATCH_LINES = List.of(
        "-T 40 -K secret123 -A -G Carvallho LOG_PATH",
        "-T 41 -K secret123 -A -G Carvallho -R 2 LOG_PATH",
        "-T 42 -K secret123 -L -G Carvallho -R 2 LOG_PATH",
        "-T 43 -K secret123 -A -G Carvallho -R 1 LOG_PATH",
        "-T 44 -K secret123 -L -G Carvallho -R 1 LOG_PATH",
        "-T 45 -K secret123 -L -G Carvallho LOG_PATH"
    );

    private static final List<String> INVALID_BATCH_LINES = List.of(
        "-T 40 -K secret123 -A -G Carvallho LOG_PATH",
        "-T 40 -K secret123 -A -G Carvallho -R 2 LOG_PATH",
        "-T 42 -K secret123 -L -G Carvallho -R 2 LOG_PATH",
        "-T 43 -K secret123 -A -G Carvallho -R 1 LOG_PATH",
        "-T 44 -K secret123 -L -G Carvallho -R 1 LOG_PATH",
        "-T 45 -K secret123 -L -G Carvallho LOG_PATH"
    );

    private static final List<String> JOHN_JAMES_BATCH_LINES = List.of(
        "-K secret -T 0 -A -E John LOG_PATH",
        "-K secret -T 1 -A -R 0 -E John LOG_PATH",
        "-K secret -T 2 -A -G James LOG_PATH",
        "-K secret -T 3 -A -R 0 -G James LOG_PATH"
    );

    @TempDir
    Path tempDir;

    @Test
    void batchTxtProcessesToExpectedFinalState() throws Exception {
        Path logPath = tempDir.resolve("log1.csv");
        Path rewrittenBatch = tempDir.resolve("batch.rewritten.txt");

        writeBatchFile(rewrittenBatch, VALID_BATCH_LINES, logPath.toString());

        LogAppend append = new LogAppend();
        append.handle("-B " + rewrittenBatch);

        String state = readState(logPath.toString(), "secret123");
        assertEquals("\n\n", state);
    }

    @Test
    void invalidBatchContinuesAndStillProcessesLaterLines() throws Exception {
        Path logPath = tempDir.resolve("log1.csv");
        Path rewrittenBatch = tempDir.resolve("invalidBatch.rewritten.txt");

        writeBatchFile(rewrittenBatch, INVALID_BATCH_LINES, logPath.toString());

        String appendOutput = captureOutput(() -> new LogAppend().handle("-B " + rewrittenBatch));

        // One line is out-of-order and should fail, but batch should keep processing.
        assertTrue(appendOutput.contains("integrity violation") || appendOutput.contains("invalid"));

        String state = readState(logPath.toString(), "secret123");
        assertEquals("\n\n", state);
    }

    @Test
    void johnJamesBatchLeavesJamesInRoomZero() throws Exception {
        Path logPath = tempDir.resolve("log2");
        Path rewrittenBatch = tempDir.resolve("johnJamesBatch.txt");

        writeBatchFile(rewrittenBatch, JOHN_JAMES_BATCH_LINES, logPath.toString());

        String appendOutput = captureOutput(() -> new LogAppend().handle("-B " + rewrittenBatch));

        assertEquals(2, countOccurrences(appendOutput, "invalid"));

        String state = readState(logPath.toString(), "secret");
        assertEquals("\nJames\n0, James\n", state);
    }

    private static String readState(String logPath, String token) {
        return captureOutput(() -> new LogRead().handle("-K " + token + " -S " + logPath));
    }

    private static String captureOutput(Runnable action) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            action.run();
        } finally {
            System.setOut(originalOut);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static void writeBatchFile(Path dest, List<String> lines, String logPath) throws Exception {
        List<String> rewritten = lines.stream()
                .map(line -> line.replace("LOG_PATH", logPath))
                .toList();
        Files.write(dest, rewritten, StandardCharsets.UTF_8);
    }
}
