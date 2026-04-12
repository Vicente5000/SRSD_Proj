package cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogScenarioStateTest {

    @TempDir
    Path tempDir;

    @Test
    void stateMatchesExpectedForEmployeeAndGuestRoomFlow() {
        Path logPath = tempDir.resolve("log1");
        String log = logPath.toString();

        LogAppend append = new LogAppend();
        append.handle("-T 1 -K secret -A -E Fred " + log);
        append.handle("-T 2 -K secret -A -G Jill " + log);
        append.handle("-T 3 -K secret -A -E Fred -R 1 " + log);
        append.handle("-T 4 -K secret -A -G Jill -R 1 " + log);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            new LogRead().handle("-K secret -S " + log);
        } finally {
            System.setOut(originalOut);
        }

        assertEquals("Fred\nJill\n1, Fred,Jill\n", output.toString(StandardCharsets.UTF_8));
    }
}
