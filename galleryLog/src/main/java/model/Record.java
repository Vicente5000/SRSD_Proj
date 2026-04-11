package model;

import enums.Action;
import enums.PersonType;
import enums.Place;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Plaintext event data for log records.
 */
public class Record {

    public final long timestamp;        // epoch seconds
    public final PersonType type;
    public final String name;
    public final Action action;
    public final Place place;
    public final Integer roomId;        // null when place == GALLERY
    public final long lastMoveTimestamp; 

    public Record(long timestamp, PersonType type, String name,
                  Action action, Place place, Integer roomId) {
        this(timestamp, type, name, action, place, roomId, timestamp);
    }

    public Record(long timestamp, PersonType type, String name,
                  Action action, Place place, Integer roomId, long lastMoveTimestamp) {
        if (timestamp < 0) {
            throw new IllegalArgumentException("timestamp must be non-negative epoch seconds");
        }
        this.type = Objects.requireNonNull(type, "type must not be null");

        Objects.requireNonNull(name, "name must not be null");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("name must not be empty");
        }
        if (!name.matches("[A-Za-z]+")) {
            throw new IllegalArgumentException("name must contain alphabetic characters only");
        }
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        if (nameBytes.length > 0xFFFF) {
            throw new IllegalArgumentException("name UTF-8 length must be at most 65535 bytes");
        }

        this.action = Objects.requireNonNull(action, "action must not be null");
        this.place = Objects.requireNonNull(place, "place must not be null");

        if (this.place == Place.ROOM) {
            if (roomId == null) {
                throw new IllegalArgumentException("roomId is required when place is ROOM");
            }
            if (roomId < 0) {
                throw new IllegalArgumentException("roomId must be non-negative");
            }
        } else if (roomId != null) {
            throw new IllegalArgumentException("roomId must be null when place is GALLERY");
        }

        if (lastMoveTimestamp < 0) {
            throw new IllegalArgumentException("lastMoveTimestamp must be non-negative epoch seconds");
        }
        if (lastMoveTimestamp > timestamp) {
            throw new IllegalArgumentException("lastMoveTimestamp must not be after timestamp");
        }

        this.timestamp = timestamp;
        this.name = name;
        this.roomId = roomId;
        this.lastMoveTimestamp = lastMoveTimestamp;
    }
}

