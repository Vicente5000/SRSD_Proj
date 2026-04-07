import java.io.File;
import java.nio.file.Files;
import java.util.List;

public class LogAppend {

    private class Command{
        Integer timestamp;
        String token;
        String employee;
        String guest;
        boolean isArrival;
        boolean isLeave;
        Integer roomId;
        String logPath;
        String batchFile;
    }

    public void handle(String rawCommand) {
        try {
            Command cmd = parse(rawCommand);

            if (cmd.batchFile != null) {
                handleBatch(cmd);
            } else {
                execute(cmd);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Command parse(String raw) throws Exception {
        String[] tokens = raw.split("\\s+");

        Command cmd = new Command();

        for (int i = 0; i < tokens.length; i++) {
            String t = tokens[i];

            switch (t) {
                case "logappend":
                    break;
                case "-T":
                    cmd.timestamp = parseIntSafe(next(tokens, ++i));
                    break;

                case "-K":
                    cmd.token = next(tokens, ++i);
                    break;

                case "-E":
                    cmd.employee = next(tokens, ++i);
                    break;

                case "-G":
                    cmd.guest = next(tokens, ++i);
                    break;

                case "-A":
                    cmd.isArrival = true;
                    break;

                case "-L":
                    cmd.isLeave = true;
                    break;

                case "-R":
                    cmd.roomId = parseIntSafe(next(tokens, ++i));
                    break;

                case "-B":
                    cmd.batchFile = next(tokens, ++i);
                    break;

                default:
                    // last token should be log path
                    if (i == tokens.length - 1) {
                        cmd.logPath = t;
                    } else {
                        throw new Exception("Unknown argument: " + t);
                    }
            }
        }

        return cmd;
    }

    private void handleBatch(Command cmd) throws Exception {
        File file = new File(cmd.batchFile);

        if (!file.exists()) {
            System.out.println("invalid");
            System.exit(111);
        }

        List<String> lines = Files.readAllLines(file.toPath());

        for (String line : lines) {
            try {
                Command sub = parse(line);
                if (sub.batchFile != null){
                    System.err.println("Cannot place batch commands inside batch file");
                } else{
                    validate(sub);
                    execute(sub);
                }
            } catch (Exception e) {
                System.out.println("invalid");
            }
        }
    }

    private void validate(Command c) throws Exception {

        // Required fields
        if (c.batchFile == null) {
            if (c.timestamp == null || c.token == null || c.logPath == null)
                throw new Exception("Missing required args");
        }

        if (c.employee != null && c.guest != null)
            throw new Exception("Cannot have both -E and -G");

        if (c.employee == null && c.guest == null && c.batchFile == null)
            throw new Exception("Must specify -E or -G");

        if (c.isArrival && c.isLeave)
            throw new Exception("Cannot have both -A and -L");

        if (!c.isArrival && !c.isLeave && c.batchFile == null)
            throw new Exception("Must specify -A or -L");

        if (c.employee != null && !c.employee.matches("[a-zA-Z]+"))
            throw new Exception("Invalid employee name");

        if (c.guest != null && !c.guest.matches("[a-zA-Z]+"))
            throw new Exception("Invalid guest name");

        if (c.token != null && !c.token.matches("[a-zA-Z0-9]+"))
            throw new Exception("Invalid token");

        if (c.timestamp != null && (c.timestamp < 1 || c.timestamp > 1073741823)) {
            throw new Exception("Invalid timestamp");
        }

        if (c.roomId != null && (c.roomId < 0 || c.roomId > 1073741823)) {
            throw new Exception("Invalid room");
        }
    }

    private String next(String[] tokens, int i) throws Exception {
        if (i >= tokens.length) throw new Exception("Missing value");
        return tokens[i];
    }

    private Integer parseIntSafe(String s) throws Exception {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            throw new Exception("Invalid number");
        }
    }

    private void execute(Command cmd){
        System.out.println("Timestamp: " + cmd.timestamp);
        System.out.println("Token: " + cmd.token);
        System.out.println("Employee: " + cmd.employee);
        System.out.println("Guest: " + cmd.guest);
        System.out.println("LogFile: " + cmd.logPath);
        System.out.println("Arrival: " + cmd.isArrival);
        System.out.println("Departure: " + cmd.isLeave);
        System.out.println("BatchFile: " + cmd.batchFile);
    }

}
