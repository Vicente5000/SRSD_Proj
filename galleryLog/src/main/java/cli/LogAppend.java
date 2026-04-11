package cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidParameterException;
import java.util.List;

import model.Record;
import enums.PersonType;
import crypto.Encryption;
import crypto.IntegrityViolationException;
import crypto.KeyDerivation;
import enums.*;
import storage.FileManager;

public class LogAppend {
    private static final String INVALID = "invalid";
    private static final String INTEGRITY_VIOLATION = "integrity violation";
    private static final String UNIMPLEMENTED = "unimplemented";
    private static final String PATH = "./logs/Logs.csv";

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
                appendArrival(command);
                return;
            case LEAVE:
                appendLeave(command);
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
        PersonType subjectType = null;
        String subjectName = null;
        Integer roomId = null;
        String batchFile = null;
        Mode mode = null;

        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];

            switch (part) {
                case "-T":
                    if (index + 1 >= parts.length) {
                        return null;
                    }
                    timestamp = parseBoundedInt(parts[++index]);
                    if (timestamp == null || timestamp == 0) {
                        return null;
                    }
                    break;
                case "-K":
                    if (index + 1 >= parts.length) {
                        return null;
                    }
                    token = parts[++index];
                    if (token.startsWith("-")) {
                        return null;
                    }
                    break;
                case "-E":
                    if (index + 1 >= parts.length) {
                        return null;
                    }
                    subjectType = PersonType.EMPLOYEE;
                    subjectName = parts[++index];
                    if (subjectName.startsWith("-")) {
                        return null;
                    }
                    break;
                case "-G":
                    if (index + 1 >= parts.length) {
                        return null;
                    }
                    subjectType = PersonType.GUEST;
                    subjectName = parts[++index];
                    if (subjectName.startsWith("-")) {
                        return null;
                    }
                    break;
                case "-A":
                    mode = Mode.ARRIVAL;
                    break;
                case "-L":
                    mode = Mode.LEAVE;
                    break;
                case "-R":
                    if (index + 1 >= parts.length) {
                        return null;
                    }
                    roomId = parseBoundedInt(parts[++index]);
                    if (roomId == null) {
                        return null;
                    }
                    break;
                case "-B":
                    if (index + 1 >= parts.length) {
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
            return new ParsedCommand(Mode.BATCH, token, logPath, batchFile, timestamp, subjectType, subjectName, roomId);
        }

        if (timestamp == null || subjectType == null || subjectName == null || mode == null) {
            return null;
        }

        if (mode == Mode.LEAVE && roomId != null) {
            return null;
        }

        return new ParsedCommand(Mode.BATCH, token, logPath, batchFile, timestamp, subjectType, subjectName, roomId);
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

    private void appendArrival(ParsedCommand command) {
        List<Record> records = loadRecords(PATH, command.token);
        if(records == null){
            addRecord(command);
            return;
        }

        if(command.timestamp <= records.getLast().timestamp){
            throw new InvalidParameterException(INVALID);
        }
        Record lastEntry = getLastUserEntry(command.subjectName, command.subjectType, records);
        if((lastEntry == null && command.roomId != null)){
            throw new InvalidParameterException(INVALID);
        }
        addRecord(command);
    }

    private Record getLastUserEntry(String name, PersonType subjectType , List<Record> records){
        for(int i = records.size() - 1; i >= 0; i --){
            Record record = records.get(i);
            if(record.name == name && record.type == subjectType){
                return record;
            }
        }
        return null;
    }

    private void appendLeave(ParsedCommand command) {
        System.out.println(UNIMPLEMENTED);
    }

    private void appendBatch(String batchFile) {
        try{
            List<String> lines = Files.readAllLines(Path.of(batchFile));
            for(int i = 0; i < lines.size(); i++){
                String line = lines.get(i);
               handleBatch(line);
            }
        } catch (IOException e){

        }
    }

    private void handleBatch(String rawCommand){
        if(rawCommand.contains("-B")){
            System.err.println("Batch file cannot contain batch command");
            return;
        }    
        handle(rawCommand);
        
    }

    private List<Record> loadRecords(String logPath, String token) {
        try {
            byte[] key = KeyDerivation.deriveKey(token);
            Encryption encryption = new Encryption(key);
            return FileManager.readRecords(Paths.get(logPath), encryption);
        } catch (IntegrityViolationException e) {
            System.out.println(INTEGRITY_VIOLATION);
            return null;
        } catch (Exception e) {
            System.out.println(INTEGRITY_VIOLATION);
            return null;
        }
    }

    private void addRecord(ParsedCommand command){
        try {
            FileManager.replayAndAppend(Path.of(PATH), new Record(command.timestamp, command.subjectType, command.subjectName, Action.ARRIVE, Place.GALLERY, command.roomId), new Encryption(KeyDerivation.deriveKey(command.token)));
        } catch (IOException e) {
            throw new InvalidParameterException(INVALID);
        }
    }

    private enum Mode {
        ARRIVAL,
        LEAVE,
        BATCH
    }

    private static final record ParsedCommand (Mode mode, String token, String logPath, String batchFile, Integer timestamp, PersonType subjectType, String subjectName, Integer roomId) {}
}
