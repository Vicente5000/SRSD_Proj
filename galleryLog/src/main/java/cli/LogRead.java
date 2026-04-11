package cli;

import crypto.Encryption;
import crypto.IntegrityViolationException;
import crypto.KeyDerivation;
import enums.Action;
import enums.PersonType;
import enums.Place;
import model.Record;
import storage.FileManager;

import java.nio.file.Paths;
import java.util.*;

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

        switch (command.mode) {
            case STATE:
                readState(command.logPath, command.token);
                return;
            case ROOMS:
                readRooms(command.logPath, command.token, command.subjectType, command.subjectName);
                return;
            case INTERSECTION:
                readIntersection(command.intersectionNames);
                return;
            default:
                System.out.println(INVALID);
        }
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

    private void readState(String logPath, String token) {
        List<Record> records = loadRecords(logPath, token);
        if (records == null) return;

        Set<String> employeesInGallery = new HashSet<>();
        Set<String> guestsInGallery    = new HashSet<>();

        Map<Integer, Set<String>> roomOccupants = new HashMap<>();

        for (Record record : records) {
            String name = record.name;

            if (record.action == Action.ARRIVE) {
                if (record.place == Place.GALLERY) {
                    if (record.type == PersonType.EMPLOYEE) employeesInGallery.add(name);
                    else                                     guestsInGallery.add(name);
                } else {
                    roomOccupants
                            .computeIfAbsent(record.roomId, k -> new HashSet<>())
                            .add(name);
                }

            } else {
                if (record.place == Place.GALLERY) {
                    if (record.type == PersonType.EMPLOYEE) employeesInGallery.remove(name);
                    else                                     guestsInGallery.remove(name);
                } else {
                    Set<String> occupants = roomOccupants.get(record.roomId);
                    if (occupants != null) {
                        occupants.remove(name);
                        if (occupants.isEmpty()) roomOccupants.remove(record.roomId);
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder();

        // Line 1: employees, lexicographic order
        List<String> employees = new ArrayList<>(employeesInGallery);
        Collections.sort(employees);
        sb.append(String.join(",", employees)).append("\n");

        // Line 2: guests, lexicographic order
        List<String> guests = new ArrayList<>(guestsInGallery);
        Collections.sort(guests);
        sb.append(String.join(",", guests)).append("\n");

        // Lines 3+: rooms in ascending numeric order, names lexicographic
        List<Integer> roomIds = new ArrayList<>(roomOccupants.keySet());
        Collections.sort(roomIds);
        for (int roomId : roomIds) {
            List<String> names = new ArrayList<>(roomOccupants.get(roomId));
            Collections.sort(names);
            sb.append(roomId).append(": ").append(String.join(",", names)).append("\n");
        }

        System.out.print(sb);
    }

    private void readRooms(String logPath, String token, PersonType subjectType, String subjectName) {
        List<Record> records = loadRecords(logPath, token);
        if (records == null) return;

        List<Integer> roomsVisited = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();

        for (Record record : records) {
            if (!record.name.equals(subjectName)) continue;
            if (!(record.type == subjectType)) continue;

            if (record.action == Action.ARRIVE && record.place == Place.ROOM) {
                roomsVisited.add(record.roomId);
            }
        }

        if (roomsVisited.isEmpty()) return;

        System.out.println(roomsVisited.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",")));
    }


    private void readIntersection(List<String> names) {
        System.out.println(UNIMPLEMENTED);
    }


    private ParsedCommand parse(String rawCommand) {
        if (rawCommand == null) return null;

        String trimmed = rawCommand.trim();
        if (trimmed.isEmpty()) return null;

        String[] parts = trimmed.split("\\s+");

        Mode mode = null;
        String token = null;
        String logPath = null;
        List<SubjectSpec> subjects = new ArrayList<>();

        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];

            switch (part) {
                case "-K":
                    if (token != null || index + 1 >= parts.length) return null;
                    token = parts[++index];
                    if (!token.matches("[a-zA-Z0-9]+")) return null;
                    break;
                case "-S":
                    if (mode != null) return null;
                    mode = Mode.STATE;
                    break;
                case "-R":
                    if (mode != null) return null;
                    mode = Mode.ROOMS;
                    break;
                case "-I":
                    if (mode != null) return null;
                    mode = Mode.INTERSECTION;
                    break;
                case "-E":
                case "-G":
                    if (index + 1 >= parts.length) return null;
                    String name = parts[++index];
                    if (!name.matches("[a-zA-Z]+")) return null;
                    subjects.add(new SubjectSpec(toSubjectTag(part), name));
                    break;
                default:
                    if (part.startsWith("-")) return null;
                    if (logPath != null) return null;
                    logPath = part;
                    break;
            }
        }

        if (token == null || logPath == null || mode == null) return null;

        if (mode == Mode.STATE) {
            if (!subjects.isEmpty()) return null;
            return new ParsedCommand(mode, token, logPath, null, null, new ArrayList<>());
        }

        if (mode == Mode.ROOMS) {
            if (subjects.size() != 1) return null;
            SubjectSpec subject = subjects.get(0);
            return new ParsedCommand(mode, token, logPath, subject.type, subject.name, new ArrayList<>());
        }

        // INTERSECTION
        if (subjects.isEmpty()) return null;
        List<String> intersectionNames = new ArrayList<>();
        for (SubjectSpec subject : subjects) {
            intersectionNames.add(subject.type + "\u0000" + subject.name);
        }
        return new ParsedCommand(mode, token, logPath, null, null, intersectionNames);
    }

    private PersonType toSubjectTag(String flag) {
        return "-E".equals(flag) ? PersonType.EMPLOYEE : PersonType.GUEST;
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private enum Mode { STATE, ROOMS, INTERSECTION }

    private static final class ParsedCommand {
        private final Mode mode;
        private final String token;
        private final String logPath;
        private final PersonType subjectType;
        private final String subjectName;
        private final List<String> intersectionNames;

        private ParsedCommand(Mode mode, String token, String logPath, PersonType subjectType,
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
        private final PersonType type;
        private final String name;

        private SubjectSpec(PersonType type, String name) {
            this.type = type;
            this.name = name;
        }
    }
}