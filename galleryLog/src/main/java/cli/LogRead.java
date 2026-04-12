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
import java.nio.file.Path;
import java.util.*;

public class LogRead {
    private static final String INVALID = "invalid";
    private static final String INTEGRITY_VIOLATION = "integrity violation";

    public static void main(String[] args) {
        String rawCommand = String.join(" ", args);
        new LogRead().handle(rawCommand);
    }

    public void handle(String rawCommand) {
        ParsedCommand command = parse(rawCommand);
        if (command == null) {
            System.out.println(INVALID);
            System.exit(111);
        }

        switch (command.mode) {
            case STATE:
                readState(command.logPath, command.token);
                return;
            case ROOMS:
                readRooms(command.logPath, command.token, command.subjectType, command.subjectName);
                return;
            case INTERSECTION:
                readIntersection(command.logPath,command.token,command.intersectionNames);
                return;
            default:
                System.out.println(INVALID);
                System.exit(111);
        }
    }

    private List<Record> loadRecords(String logPath, String token) {
        try {
            byte[] key = KeyDerivation.deriveKey(token, logPath);
            Encryption encryption = new Encryption(key);
            return FileManager.readRecords(Paths.get(logPath), encryption);
        } catch (IntegrityViolationException e) {
            System.out.println(INTEGRITY_VIOLATION);
            System.exit(111);
            return null;
        } catch (Exception e) {
            System.out.println(INVALID);
            System.exit(111);
            return null;
        }
    }

    private void readState(String logPath, String token) {
        List<Record> records = loadRecords(logPath, token);

        Set<String> employeesInGallery = new HashSet<>();
        Set<String> guestsInGallery    = new HashSet<>();

        Map<Integer, Map<String, Integer>> roomOccupants = new HashMap<>();

        for (Record record : records) {
            String name = record.name;

            if (record.action == Action.ARRIVE) {
                if (record.place == Place.GALLERY) {
                    if (record.type == PersonType.EMPLOYEE) employeesInGallery.add(name);
                    else                                     guestsInGallery.add(name);
                } else {
                    roomOccupants
                            .computeIfAbsent(record.roomId, k -> new HashMap<>())
                            .merge(name, 1, Integer::sum);
                }

            } else {
                if (record.place == Place.GALLERY) {
                    if (record.type == PersonType.EMPLOYEE) employeesInGallery.remove(name);
                    else                                     guestsInGallery.remove(name);
                } else {
                    Map<String, Integer> occupants = roomOccupants.get(record.roomId);
                    if (occupants != null) {
                        Integer count = occupants.get(name);
                        if (count != null) {
                            if (count <= 1) {
                                occupants.remove(name);
                            } else {
                                occupants.put(name, count - 1);
                            }
                        }
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
            List<String> names = new ArrayList<>();
            for (Map.Entry<String, Integer> occupant : roomOccupants.get(roomId).entrySet()) {
                for (int i = 0; i < occupant.getValue(); i++) {
                    names.add(occupant.getKey());
                }
            }
            Collections.sort(names);
            sb.append(roomId).append(", ").append(String.join(",", names)).append("\n");
        }

        System.out.print(sb);
    }

    private void readRooms(String logPath, String token, PersonType subjectType, String subjectName) {
        List<Record> records = loadRecords(logPath, token);

        List<Integer> roomsVisited = new ArrayList<>();

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


    private void readIntersection(String logPath, String token, List<String> intersectionNames) {
        List<Record> records = loadRecords(logPath, token);

        List<SubjectSpec> subjects = new ArrayList<>();
        for (String encoded : intersectionNames) {
            String[] parts = encoded.split("\u0000", 2);
            if (parts.length != 2) return;
            PersonType type = "EMPLOYEE".equals(parts[0]) ? PersonType.EMPLOYEE : PersonType.GUEST;
            subjects.add(new SubjectSpec(type, parts[1]));
        }

        QueryIndex index = buildQueryIndex(records, subjects);
        if (index.subjectHistories.isEmpty()) {
            return;
        }

        SubjectSpec seed = index.pickSeedSubject();
        if (seed == null) {
            return;
        }

        Set<Integer> candidateRooms = new HashSet<>(index.historyFor(seed).roomsVisited);
        for (SubjectSpec subject : subjects) {
            if (subject.equals(seed)) {
                continue;
            }
            candidateRooms.retainAll(index.historyFor(subject).roomsVisited);
            if (candidateRooms.isEmpty()) {
                return;
            }
        }

        List<Integer> result = new ArrayList<>();
        for (int roomId : candidateRooms) {
            if (index.roomHasOverlap(roomId, subjects)) {
                result.add(roomId);
            }
        }

        if (result.isEmpty()) {
            return;
        }

        Collections.sort(result);
        System.out.println(result.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",")));
    }

    private QueryIndex buildQueryIndex(List<Record> records, List<SubjectSpec> subjects) {
        QueryIndex index = new QueryIndex(subjects);
        Map<String, OpenRoomEntry> currentRoomEntry = new HashMap<>();

        for (Record record : records) {
            String key = record.type.name() + "\u0000" + record.name;
            SubjectHistory history = index.subjectHistories.get(key);
            if (history == null || record.place != Place.ROOM) {
                continue;
            }

            if (record.action == Action.ARRIVE) {
                currentRoomEntry.put(key, new OpenRoomEntry(record.roomId, (int) record.timestamp));
            } else {
                OpenRoomEntry entry = currentRoomEntry.remove(key);
                if (entry != null) {
                    history.addSpan(entry.roomId, entry.startTime, (int) record.timestamp);
                }
            }
        }

        for (Map.Entry<String, OpenRoomEntry> open : currentRoomEntry.entrySet()) {
            index.subjectHistories.get(open.getKey()).addSpan(open.getValue().roomId, open.getValue().startTime, Integer.MAX_VALUE);
        }

        return index;
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
            SubjectSpec subject = subjects.getFirst();
            return new ParsedCommand(mode, token, logPath, subject.type, subject.name, new ArrayList<>());
        }

        if (subjects.isEmpty()) return null;
        List<String> intersectionNames = new ArrayList<>();
        for (SubjectSpec subject : subjects) {
            intersectionNames.add(subject.type.name() + "\u0000" + subject.name);
        }
        return new ParsedCommand(mode, token, logPath, null, null, intersectionNames);
    }

    private PersonType toSubjectTag(String flag) {
        return "-E".equals(flag) ? PersonType.EMPLOYEE : PersonType.GUEST;
    }

    private enum Mode { STATE, ROOMS, INTERSECTION }

    private record ParsedCommand(Mode mode, String token, String logPath, PersonType subjectType, String subjectName,
                                 List<String> intersectionNames) { }

    private record SubjectSpec(PersonType type, String name) { }

    private record OpenRoomEntry(int roomId, int startTime) { }

    private static final class QueryIndex {
        private final Map<String, SubjectHistory> subjectHistories = new HashMap<>();

        private QueryIndex(List<SubjectSpec> subjects) {
            for (SubjectSpec subject : subjects) {
                subjectHistories.put(subjectKey(subject), new SubjectHistory());
            }
        }

        private SubjectHistory historyFor(SubjectSpec subject) {
            return subjectHistories.get(subjectKey(subject));
        }

        private SubjectSpec pickSeedSubject() {
            SubjectSpec best = null;
            int bestSpanCount = Integer.MAX_VALUE;
            for (String key : subjectHistories.keySet()) {
                SubjectHistory history = subjectHistories.get(key);
                if (history.roomsVisited.isEmpty()) {
                    continue;
                }
                int spanCount = history.spanCount();
                if (spanCount < bestSpanCount) {
                    bestSpanCount = spanCount;
                    best = subjectFromKey(key);
                }
            }
            return best;
        }

        private boolean roomHasOverlap(int roomId, List<SubjectSpec> subjects) {
            List<int[]> intersection = null;
            for (SubjectSpec subject : subjects) {
                List<int[]> spans = historyFor(subject).spansForRoom(roomId);
                if (spans.isEmpty()) {
                    return false;
                }
                if (intersection == null) {
                    intersection = new ArrayList<>(spans);
                } else {
                    intersection = intersectIntervals(intersection, spans);
                    if (intersection.isEmpty()) {
                        return false;
                    }
                }
            }
            return intersection != null && !intersection.isEmpty();
        }

        private static List<int[]> intersectIntervals(List<int[]> left, List<int[]> right) {
            List<int[]> result = new ArrayList<>();
            int leftIndex = 0;
            int rightIndex = 0;

            while (leftIndex < left.size() && rightIndex < right.size()) {
                int[] leftInterval = left.get(leftIndex);
                int[] rightInterval = right.get(rightIndex);

                int start = Math.max(leftInterval[1], rightInterval[1]);
                int end = Math.min(leftInterval[2], rightInterval[2]);
                if (start < end) {
                    result.add(new int[]{leftInterval[0], start, end});
                }

                if (leftInterval[2] < rightInterval[2]) {
                    leftIndex++;
                } else {
                    rightIndex++;
                }
            }

            return result;
        }

        private static String subjectKey(SubjectSpec subject) {
            return subject.type.name() + "\u0000" + subject.name;
        }

        private static SubjectSpec subjectFromKey(String key) {
            String[] parts = key.split("\u0000", 2);
            PersonType type = PersonType.valueOf(parts[0]);
            return new SubjectSpec(type, parts[1]);
        }
    }

    private static final class SubjectHistory {
        private final Map<Integer, List<int[]>> spansByRoom = new HashMap<>();
        private final Set<Integer> roomsVisited = new HashSet<>();

        private void addSpan(int roomId, int startTime, int endTime) {
            spansByRoom.computeIfAbsent(roomId, ignored -> new ArrayList<>())
                    .add(new int[]{roomId, startTime, endTime});
            roomsVisited.add(roomId);
        }

        private List<int[]> spansForRoom(int roomId) {
            return spansByRoom.getOrDefault(roomId, List.of());
        }

        private int spanCount() {
            int count = 0;
            for (List<int[]> spans : spansByRoom.values()) {
                count += spans.size();
            }
            return count;
        }
    }
}