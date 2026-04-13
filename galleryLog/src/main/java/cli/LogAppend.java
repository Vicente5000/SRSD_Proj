package cli;

import java.io.IOException;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import model.Record;
import crypto.Encryption;
import crypto.IntegrityViolationException;
import crypto.KeyDerivation;
import enums.*;
import storage.FileManager;

public class LogAppend {
    private static final String INVALID = "invalid";
    private static final String INTEGRITY_VIOLATION = "integrity violation";
    private static final String UNIMPLEMENTED = "unimplemented";
    private boolean inBatchMode = false;

    public static void main(String[] args) {
        String rawCommand = String.join(" ", args);
        new LogAppend().handle(rawCommand);
    }

    public void handle(String rawCommand) {
        ParsedCommand command = parse(rawCommand);
        if (command == null) {
            fail(INVALID);
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
                fail(INVALID);
                return;
        }
    }

    private void fail(String message) {
        System.out.println(message);
        if (!inBatchMode) {
            System.exit(111);
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
        Path logPath = null;
        PersonType subjectType = null;
        String subjectName = null;
        Integer roomId = null;
        Path batchFile = null;
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
                    if (!token.matches("[a-zA-Z0-9]+") || token.startsWith("-")) {
                        return null;
                    }
                    break;
                case "-E":
                    if(subjectType == PersonType.GUEST){
                        return null;
                    }
                    if (index + 1 >= parts.length) {
                        return null;
                    }
                    subjectType = PersonType.EMPLOYEE;
                    subjectName = parts[++index];
                    if (!subjectName.matches("[a-zA-Z]+") || subjectName.startsWith("-")) {
                        return null;
                    }
                    break;
                case "-G":
                    if(subjectType == PersonType.EMPLOYEE){
                        return null;
                    }
                    if (index + 1 >= parts.length) {
                        return null;
                    }
                    subjectType = PersonType.GUEST;
                    subjectName = parts[++index];
                    if (!subjectName.matches("[a-zA-Z]+") || subjectName.startsWith("-")) {
                        return null;
                    }
                    break;
                case "-A":
                    if(mode != null){
                        return null;
                    }
                    mode = Mode.ARRIVAL;
                    break;
                case "-L":
                    if(mode != null){
                        return null;
                    }
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
                    batchFile = Path.of(parts[++index]);
                    if (batchFile.toString().startsWith("-")) {
                        return null;
                    }
                    mode = Mode.BATCH;
                    break;
                default:
                    if (part.startsWith("-")) {
                        return null;
                    }

                    logPath = Path.of(part);
                    break;
            }
        }

        if ((token == null || logPath == null) && mode != Mode.BATCH){
            return null;
        }

        if (batchFile != null) {
            if (timestamp != null || subjectType != null || mode != Mode.BATCH || roomId != null) {
                return null;
            }
            return new ParsedCommand(mode, token, logPath, batchFile, timestamp, subjectType, subjectName, roomId, null, null);
        }

        if (timestamp == null || subjectType == null || subjectName == null || mode == null) {
            return null;
        }

        return new ParsedCommand(
                mode,
                token,
                logPath,
                batchFile,
                timestamp,
                subjectType,
                subjectName,
                roomId,
                mode == Mode.ARRIVAL ? Action.ARRIVE : Action.LEAVE,
                roomId != null ? Place.ROOM : Place.GALLERY
        );
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

    private void appendArrival(ParsedCommand command) {
        Encryption enc = new Encryption(KeyDerivation.deriveKey(command.token, command.logPath.toString()));
        List<Record> records = loadRecords(command.logPath, enc);
        if(records == null){
            return;
        }

        if(records.isEmpty()){
            if (command.roomId != null) {
                fail(INVALID);
                return;
            }
            addRecord(command, 0, enc);
            return;
        }

        if(command.timestamp <= records.getLast().timestamp){
            fail(INVALID);
            return;
        }
        Record lastEntry = getLastUserEntry(command.subjectName, command.subjectType, records);

        if(lastEntry == null ){
            if(command.roomId != null){
                fail(INVALID);
                return;
            }
            addRecord(command, 0, enc);
            return;
        }
        int currentRoom = getUserRoom(lastEntry);

        if (currentRoom == -2) {
            if (command.roomId != null) {
                fail(INVALID);
                return;
            }
        } else if (currentRoom == -1) {
            if (command.roomId == null) {
                fail(INVALID);
                return;
            }
        } else {
            fail(INVALID);
            return;
        }

        addRecord(command, lastEntry.timestamp, enc);
    }

    private int getUserRoom(Record record){
        if (record.place == Place.ROOM) {
            if (record.action == Action.ARRIVE) {
                return record.roomId;
            }
            return -1;
        } else {
            if (record.action == Action.ARRIVE){
                return -1;
            } else{
                return -2;
            }
        }
    }

    private Record getLastUserEntry(String name, PersonType subjectType , List<Record> records){
        for(int i = records.size() - 1; i >= 0; i --){
            Record record = records.get(i);
            if(record.name.equals(name) && record.type == subjectType){
                return record;
            }
        }
        return null;
    }

    private void appendLeave(ParsedCommand command) {
        Encryption enc = new Encryption(KeyDerivation.deriveKey(command.token, command.logPath.toString()));
        List<Record> records = loadRecords(command.logPath, enc);
        if(records == null){
            return;
        }

        if(records.isEmpty()){
            fail(INVALID);
            return;
        }

        if(command.timestamp <= records.getLast().timestamp){
            fail(INVALID);
            return;
        }

        Record lastEntry = getLastUserEntry(command.subjectName, command.subjectType, records);

        if(lastEntry == null ){
            fail(INVALID);
            return;
        }

        int currentRoom = getUserRoom(lastEntry);
        boolean inGallery = (currentRoom != -2);

        if(!inGallery){
            fail(INVALID);
            return;
        }

        if (currentRoom == -1 && command.roomId != null) {
            fail(INVALID);
            return;
        }

        if (currentRoom >= 0 && (command.roomId == null || currentRoom != command.roomId)) {
            fail(INVALID);
            return;
        }

        addRecord(command, lastEntry.timestamp, enc);
    }

    private void appendBatch(Path batchFile) {
        inBatchMode = true;
        try (BufferedReader reader = Files.newBufferedReader(batchFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                handleBatchLine(line);
            }
        } catch (IOException e) {
            fail(INVALID);
        } finally {
            inBatchMode = false;
        }
    }

    private void handleBatchLine(String rawCommand) {
        ParsedCommand command = parse(rawCommand);
        if (command == null || command.mode == Mode.BATCH) {
            System.out.println(INVALID);
            return;
        }

        switch (command.mode) {
            case ARRIVAL:
                appendArrival(command);
                return;
            case LEAVE:
                appendLeave(command);
                return;
            default:
                System.out.println(INVALID);
        }
    }

    private List<Record> loadRecords(Path logPath, Encryption enc) {
        try {
            return FileManager.readRecords(logPath, enc);
        } catch (IntegrityViolationException e) {
            fail(INVALID);
            return null;
        } catch (Exception e) {
            fail(INVALID);
            return null;
        }
    }

    private void addRecord(ParsedCommand command, long lastTimeStamp, Encryption enc){
        try {
            Path parent = command.logPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            FileManager.writeRecord(
                    command.logPath,
                    new Record(
                            command.timestamp,
                            command.subjectType,
                            command.subjectName,
                            command.action,
                            command.place,
                            command.roomId,
                            lastTimeStamp
                    ),
                    enc
            );
        } catch (IntegrityViolationException e) {
            fail(INVALID);
        } catch (IOException e) {
            fail(INVALID);
        }
    }

    private enum Mode {
        ARRIVAL,
        LEAVE,
        BATCH
    }

    private static final record ParsedCommand(
            Mode mode,
            String token,
            Path logPath,
            Path batchFile,
            Integer timestamp,
            PersonType subjectType,
            String subjectName,
            Integer roomId,
            Action action,
            Place place
    ) {}
}
