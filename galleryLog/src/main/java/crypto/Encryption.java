package crypto;

import enums.Action;
import enums.PersonType;
import enums.Place;
import model.Entry;
import model.Record;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.*;
import java.security.*;
import java.util.Arrays;

public class Encryption {

    // ── Constants ────────────────────────────────────────────────────────────
    public static final byte RECORD_VERSION = 0x01;

    private static final int IV_LEN = Entry.IV_LEN;
    private static final int GCM_TAG_BITS = 128;
    private static final int HASH_LEN = Entry.HASH_LEN;

    /** Fixed overhead for an encoded entry: IV + hashOfLastEntry. */
    public static final int ENTRY_FIXED_OVERHEAD = IV_LEN + HASH_LEN;

    private final SecretKeySpec encKey;   // AES-256 only (no macKey needed)
    private byte[] lastEntryHash = new byte[HASH_LEN];

    public Encryption(byte[] encryptionKey) {
        requireLen(encryptionKey, 32, "encryptionKey");
        this.encKey = new SecretKeySpec(encryptionKey, "AES");
    }

    // ── Entry encryption ─────────────────────────────────────────────────────

    public Entry encrypt(String plaintext)
            throws IOException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }
        return encryptBytes(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public Entry encrypt(Record record)
            throws IOException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        return encryptBytes(serializeRecord(record));
    }
    
    public Record decrypt(Entry entry)
            throws IOException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException {
        if (entry == null) {
            throw new IllegalArgumentException("entry must not be null");
        }
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_BITS, entry.iv);
        cipher.init(Cipher.DECRYPT_MODE, encKey, gcmSpec);
        cipher.updateAAD(entry.hashOfLastEntry);

        try {
            byte[] plaintext = cipher.doFinal(entry.cipherText);
            lastEntryHash = sha256(entry.toBytes());
            return deserializeRecord(plaintext);
        } catch (AEADBadTagException e) {
            throw new IntegrityViolationException(e); // ← clean signal to callers
        }
    }
    
    public Record decrypt(String line)
            throws IOException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException {
        return decrypt(Entry.fromLine(line));
    }

    private Entry encryptBytes(byte[] plaintext)
            throws IOException, NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException,
            IllegalBlockSizeException, BadPaddingException {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext must not be null");
        }

        byte[] hashOfLastEntry = Arrays.copyOf(lastEntryHash, HASH_LEN);
        byte[] iv = randomBytes(IV_LEN);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, encKey, gcmSpec);
        cipher.updateAAD(hashOfLastEntry);

        byte[] cipherText = cipher.doFinal(plaintext);
        Entry entry = new Entry(iv, cipherText, hashOfLastEntry);
        lastEntryHash = sha256(entry.toBytes());
        return entry;
    }

    // ── Record serialization / deserialization ───────────────────────────────

    public byte[] serializeRecord(Record record) throws IOException {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {

            dos.writeLong(record.timestamp); 
            dos.writeByte(record.type == PersonType.EMPLOYEE ? 0 : 1);
            byte[] nameBytes = record.name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (nameBytes.length > 0xFFFF) {
                throw new IllegalArgumentException("name UTF-8 length must be at most 65535 bytes");
            }
            dos.writeShort(nameBytes.length);
            dos.write(nameBytes);
            dos.writeByte(record.action == Action.ARRIVE ? 0 : 1);
            dos.writeByte(record.place == Place.GALLERY ? 0 : 1);

            if (record.place == Place.ROOM && record.roomId != null) {
                dos.writeByte(1);
                dos.writeInt(record.roomId);
            } else {
                dos.writeByte(0);
            }

            dos.writeLong(record.lastMoveTimestamp);

            dos.flush();
            return baos.toByteArray();
        }
    }
    
    public Record deserializeRecord(byte[] bytes) throws IOException {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes must not be null");
        }
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (dis.available() < 8 + 1 + 2 + 1 + 1 + 1 + 8) {
                throw new IOException("Event payload too short");
            }
            
            long timestamp = dis.readLong();
            PersonType type = dis.readByte() == 0 ? PersonType.EMPLOYEE : PersonType.GUEST;
            int nameLen = dis.readUnsignedShort();
            byte[] nameBytes = dis.readNBytes(nameLen);
            if (nameBytes.length != nameLen) {
                throw new IOException("Truncated event name");
            }
            String name = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8);
            
            Action action = dis.readByte() == 0 ? Action.ARRIVE : Action.LEAVE;
            Place place = dis.readByte() == 0 ? Place.GALLERY : Place.ROOM;
            
            boolean hasRoom = dis.readByte() == 1;
            if (hasRoom && dis.available() < 4 + 8) {
                throw new IOException("Truncated room id or last move timestamp");
            }
            if (!hasRoom && dis.available() < 8) {
                throw new IOException("Truncated last move timestamp");
            }
            Integer roomId = hasRoom ? dis.readInt() : null;
            
            long lastMoveTimestamp = dis.readLong();
            if (dis.available() != 0) {
                throw new IOException("Unexpected trailing bytes in record payload");
            }
            
            if (place == Place.ROOM && roomId == null) {
                throw new IOException("Room records must include roomId");
            }
            if (place == Place.GALLERY && roomId != null) {
                throw new IOException("Gallery records must not include roomId");
            }
            
            return new Record(timestamp, type, name, action, place, roomId, lastMoveTimestamp);
        }
    }

    private byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e); // unreachable in practice
        }
    }

    private byte[] randomBytes(int len) {
        byte[] b = new byte[len];
        new SecureRandom().nextBytes(b);
        return b;
    }

    private static void requireLen(byte[] arr, int len, String name) {
        if (arr == null || arr.length != len) {
            throw new IllegalArgumentException(name + " must be exactly " + len + " bytes");
        }
    }

}