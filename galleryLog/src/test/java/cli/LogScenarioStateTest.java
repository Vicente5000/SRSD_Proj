package cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogScenarioStateTest {

    @TempDir
    Path tempDir;

    @Test
    void stateMatchesExpectedForEmployeeAndGuestRoomFlow() throws Exception {
        Path logsDir = tempDir.resolve("logs");
        Files.createDirectories(logsDir);
        Path logPath = logsDir.resolve("log1");
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

    @Test
    void fredRoomHistoryMatchesExpectedSequence() throws Exception {
        Path logsDir = tempDir.resolve("logs");
        Files.createDirectories(logsDir);
        Path logPath = logsDir.resolve("log1");
        String log = logPath.toString();

        LogAppend append = new LogAppend();
        append.handle("-T 1 -K secret -A -E Fred " + log);
        append.handle("-T 2 -K secret -A -E Fred -R 1 " + log);
        append.handle("-T 5 -K secret -L -E Fred -R 1 " + log);
        append.handle("-T 6 -K secret -A -E Fred -R 2 " + log);
        append.handle("-T 7 -K secret -L -E Fred -R 2 " + log);
        append.handle("-T 8 -K secret -A -E Fred -R 3 " + log);
        append.handle("-T 9 -K secret -L -E Fred -R 3 " + log);
        append.handle("-T 10 -K secret -A -E Fred -R 1 " + log);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            new LogRead().handle("-K secret -R -E Fred " + log);
        } finally {
            System.setOut(originalOut);
        }

        assertEquals("1,2,3,1\n", output.toString(StandardCharsets.UTF_8));
    }

    @Test
    void intersectionReturnsOnlyRoomsWithRealOverlap() throws Exception {
        Path logsDir = tempDir.resolve("logs");
        Files.createDirectories(logsDir);
        Path logPath = logsDir.resolve("logI");
        String log = logPath.toString();

        LogAppend append = new LogAppend();
        append.handle("-T 1 -K secret -A -E Fred " + log);
        append.handle("-T 2 -K secret -A -G Jill " + log);

        // Room 1 has overlap: Fred [3,7), Jill [4,6)
        append.handle("-T 3 -K secret -A -E Fred -R 1 " + log);
        append.handle("-T 4 -K secret -A -G Jill -R 1 " + log);
        append.handle("-T 6 -K secret -L -G Jill -R 1 " + log);
        append.handle("-T 7 -K secret -L -E Fred -R 1 " + log);

        // Room 2 has no overlap: Fred [8,9), Jill [10,11)
        append.handle("-T 8 -K secret -A -E Fred -R 2 " + log);
        append.handle("-T 9 -K secret -L -E Fred -R 2 " + log);
        append.handle("-T 10 -K secret -A -G Jill -R 2 " + log);
        append.handle("-T 11 -K secret -L -G Jill -R 2 " + log);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            new LogRead().handle("-K secret -I -E Fred -G Jill " + log);
        } finally {
            System.setOut(originalOut);
        }

        assertEquals("1\n", output.toString(StandardCharsets.UTF_8));
    }
}
