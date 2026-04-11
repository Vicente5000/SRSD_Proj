package cli;

public class LogAppend {
    private static final String INVALID = "invalid";
    private static final String INTEGRITY_VIOLATION = "integrity violation";
    private static final String UNIMPLEMENTED = "unimplemented";

    public static void main(String[] args) {
        String rawCommand = String.join(" ", args);
        new LogAppend().handle(rawCommand);
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
            case ARRIVAL:
                appendArrival();
                return;
            case LEAVE:
                appendLeave();
                return;
            case BATCH:
                appendBatch(command.batchFile);
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
        if (parts.length < 1) {
            return null;
        }

        Integer timestamp = null;
        String token = null;
        String logPath = null;
        String subjectType = null;
        String subjectName = null;
        Integer roomId = null;
        String batchFile = null;
        Mode mode = null;

        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];

            switch (part) {
                case "-T":
                    if (timestamp != null || index + 1 >= parts.length) {
                        return null;
                    }
                    timestamp = parseBoundedInt(parts[++index]);
                    if (timestamp == null || timestamp == 0) {
                        return null;
                    }
                    break;
                case "-K":
                    if (token != null || index + 1 >= parts.length) {
                        return null;
                    }
                    token = parts[++index];
                    if (token.startsWith("-")) {
                        return null;
                    }
                    break;
                case "-E":
                case "-G":
                    if (subjectType != null || index + 1 >= parts.length) {
                        return null;
                    }
                    subjectType = toSubjectTag(part);
                    subjectName = parts[++index];
                    if (subjectName.startsWith("-")) {
                        return null;
                    }
                    break;
                case "-A":
                    if (mode != null) {
                        return null;
                    }
                    mode = Mode.ARRIVAL;
                    break;
                case "-L":
                    if (mode != null) {
                        return null;
                    }
                    mode = Mode.LEAVE;
                    break;
                case "-R":
                    if (roomId != null || index + 1 >= parts.length) {
                        return null;
                    }
                    roomId = parseBoundedInt(parts[++index]);
                    if (roomId == null) {
                        return null;
                    }
                    break;
                case "-B":
                    if (batchFile != null || index + 1 >= parts.length) {
                        return null;
                    }
                    batchFile = parts[++index];
                    if (batchFile.startsWith("-")) {
                        return null;
                    }
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

        if (token == null || logPath == null) {
            return null;
        }

        if (batchFile != null) {
            if (timestamp != null || subjectType != null || mode != null || roomId != null) {
                return null;
            }
            return new ParsedCommand(Mode.BATCH, token, logPath, batchFile);
        }

        if (timestamp == null || subjectType == null || subjectName == null || mode == null) {
            return null;
        }

        if (mode == Mode.LEAVE && roomId != null) {
            return null;
        }

        return new ParsedCommand(mode, token, logPath, null);
    }

    private Integer parseBoundedInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0 || parsed > 1073741823) {
                return null;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            return null;
        }
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

    private void appendArrival() {
        System.out.println(UNIMPLEMENTED);
    }

    private void appendLeave() {
        System.out.println(UNIMPLEMENTED);
    }

    private void appendBatch(String batchFile) {
        System.out.println(UNIMPLEMENTED);
    }

    private enum Mode {
        ARRIVAL,
        LEAVE,
        BATCH
    }

    private static final class ParsedCommand {
        private final Mode mode;
        private final String token;
        private final String logPath;
        private final String batchFile;

        private ParsedCommand(Mode mode, String token, String logPath, String batchFile) {
            this.mode = mode;
            this.token = token;
            this.logPath = logPath;
            this.batchFile = batchFile;
        }
    }
}
