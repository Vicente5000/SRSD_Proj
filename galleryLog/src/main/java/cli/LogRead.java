package cli;

import java.util.ArrayList;
import java.util.List;

public class LogRead {
    private static final String INVALID = "invalid";
    private static final String INTEGRITY_VIOLATION = "integrity violation";
    private static final String UNIMPLEMENTED = "unimplemented";

    public static void main(String[] args) {
        String rawCommand = String.join(" ", args);
        new LogRead().handle(rawCommand);
    }

    public void handle(String rawCommand) {
        ParsedCommand command = parse(rawCommand);
        if (command == null) {
            System.out.println(INVALID);
            return;
        }

        if (!confirmToken(command.logPath, command.token)) {
            System.out.println(INTEGRITY_VIOLATION);
            return;
        }

        switch (command.mode) {
            case STATE:
                readState(command.logPath);
                return;
            case ROOMS:
                readRooms(command.logPath, command.subjectType, command.subjectName);
                return;
            case INTERSECTION:
                readIntersection(command.intersectionNames);
                return;
            default:
                System.out.println(INVALID);
        }
    }

    private ParsedCommand parse(String rawCommand) {
        if (rawCommand == null) {
            return null;
        }

        String trimmed = rawCommand.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String[] parts = trimmed.split("\\s+");
        if (parts.length < 2 || !"logread".equals(parts[0])) {
            return null;
        }

        Mode mode = null;
        String token = null;
        String logPath = null;
        List<SubjectSpec> subjects = new ArrayList<>();

        for (int index = 1; index < parts.length; index++) {
            String part = parts[index];

            switch (part) {
                case "-K":
                    if (token != null || index + 1 >= parts.length) {
                        return null;
                    }
                    token = parts[++index];
                    if (token.startsWith("-")) {
                        return null;
                    }
                    break;
                case "-S":
                    if (mode != null) {
                        return null;
                    }
                    mode = Mode.STATE;
                    break;
                case "-R":
                    if (mode != null) {
                        return null;
                    }
                    mode = Mode.ROOMS;
                    break;
                case "-I":
                    if (mode != null) {
                        return null;
                    }
                    mode = Mode.INTERSECTION;
                    break;
                case "-E":
                case "-G":
                    if (index + 1 >= parts.length) {
                        return null;
                    }

                    String name = parts[++index];
                    if (name.startsWith("-")) {
                        return null;
                    }

                    subjects.add(new SubjectSpec(toSubjectTag(part), name));
                    break;
                default:
                    if (part.startsWith("-")) {
                        return null;
                    }

                    if (logPath != null) {
                        return null;
                    }
                    logPath = part;
                    break;
            }
        }

        if (token == null || logPath == null || mode == null) {
            return null;
        }

        if (mode == Mode.STATE) {
            if (!subjects.isEmpty()) {
                return null;
            }
            return new ParsedCommand(mode, token, logPath, null, null, new ArrayList<>());
        }

        if (mode == Mode.ROOMS) {
            if (subjects.size() != 1) {
                return null;
            }
            SubjectSpec subject = subjects.get(0);
            return new ParsedCommand(mode, token, logPath, subject.type, subject.name, new ArrayList<>());
        }

        if (mode == Mode.INTERSECTION && subjects.isEmpty()) {
            return null;
        }

        List<String> intersectionNames = new ArrayList<>();
        for (SubjectSpec subject : subjects) {
            intersectionNames.add(subject.type + "\u0000" + subject.name);
        }

        return new ParsedCommand(mode, token, logPath, null, null, intersectionNames);
    }

    private String toSubjectTag(String flag) {
        if ("-E".equals(flag)) {
            return "Employee";
        }
        if ("-G".equals(flag)) {
            return "Guest";
        }
        return null;
    }

    private boolean confirmToken(String logPath, String token) {
        return true;
    }

    private void readState(String logPath) {
        //add error when multiple subjects
    }

    private void readRooms(String logPath, String subjectType, String subjectName) {
        //add error when multiple subjects
    }

    private void readIntersection(List<String> names) {
        System.out.println(UNIMPLEMENTED);
    }

    private enum Mode {
        STATE,
        ROOMS,
        INTERSECTION
    }

    private static final class ParsedCommand {
        private final Mode mode;
        private final String token;
        private final String logPath;
        private final String subjectType;
        private final String subjectName;
        private final List<String> intersectionNames;

        private ParsedCommand(Mode mode, String token, String logPath, String subjectType,
                              String subjectName, List<String> intersectionNames) {
            this.mode = mode;
            this.token = token;
            this.logPath = logPath;
            this.subjectType = subjectType;
            this.subjectName = subjectName;
            this.intersectionNames = intersectionNames;
        }
    }

    private static final class SubjectSpec {
        private final String type;
        private final String name;

        private SubjectSpec(String type, String name) {
            this.type = type;
            this.name = name;
        }
    }
}
