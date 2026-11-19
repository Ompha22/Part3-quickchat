package ChatAppPOE;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the flat message objects written by QuickChat into an array.
 * JSON tokens and string decoding follow Bray (2017), RFC 8259, sections 5-8.
 * https://www.rfc-editor.org/rfc/rfc8259
 */
final class MessageJson {
    private static final Pattern TOKEN = Pattern.compile(
            "[ \t\r\n]*(?:([\\[\\]{},:])|(-?(?:0|[1-9][0-9]*))"
            + "|\"((?:[^\"\\\\\\x00-\\x1f]|\\\\(?:[\"\\\\/bfnrt]|u[0-9a-fA-F]{4}))*+)\"|\\z)");
    private static final Pattern ESCAPE =
            Pattern.compile("\\\\(u[0-9a-fA-F]{4}|[\"\\\\/bfnrt])");

    private MessageJson() {
    }

    static synchronized Message[] read(Path file) throws IOException {
        String json;
        try {
            json = Files.readString(file);
        } catch (NoSuchFileException missing) {
            return new Message[0];
        }
        if (json.isBlank()) {
            return new Message[0];
        }

        Cursor cursor = new Cursor(json);
        Message[] records = new Message[8];
        int size = 0;
        cursor.require("[");
        if (!cursor.take("]")) {
            do {
                Message message = readMessage(cursor);
                if (size == records.length) {
                    records = Arrays.copyOf(records, records.length * 2);
                }
                records[size++] = message;
            } while (cursor.take(","));
            cursor.require("]");
        }
        cursor.require("");
        return Arrays.copyOf(records, size);
    }

    private static Message readMessage(Cursor cursor) throws IOException {
        Map<String, String> fields = new HashMap<>();
        cursor.require("{");
        do {
            String key = cursor.value(3);
            cursor.require(":");
            String value = cursor.value(key.equals("messageNumber") ? 2 : 3);
            if (fields.putIfAbsent(key, value) != null) {
                throw new IOException("Duplicate JSON field: " + key);
            }
        } while (cursor.take(","));
        cursor.require("}");

        for (String required : new String[]{"messageID", "messageNumber", "recipient",
                "message", "messageHash", "status"}) {
            if (!fields.containsKey(required)) {
                throw new IOException("Missing JSON field: " + required);
            }
        }
        if (!"STORED".equals(fields.get("status"))) {
            throw new IOException("Expected a stored message in the archive.");
        }
        try {
            return Message.restoreStored(fields.get("messageID"),
                    Integer.parseInt(fields.get("messageNumber")),
                    fields.getOrDefault("sender", "Unknown (Part 2)"),
                    fields.get("recipient"), fields.get("message"), fields.get("messageHash"));
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid message number in the JSON archive.", exception);
        }
    }

    static synchronized void append(Path file, Message message) throws IOException {
        Message[] records = read(file);
        for (Message record : records) {
            if (record.getMessageID().equals(message.getMessageID())) {
                throw new IOException("That message ID already exists in the archive.");
            }
        }
        records = Arrays.copyOf(records, records.length + 1);
        records[records.length - 1] = message;
        write(file, records);
    }

    /** Replace the archive only after the entire new JSON file has been written. */
    static synchronized void write(Path file, Message[] records) throws IOException {
        Path target = file.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        StringJoiner json = new StringJoiner(",\n", "[\n", "\n]\n");
        for (Message message : records) {
            json.add("  " + message.toJsonObject().replace("\n", "\n  "));
        }
        Path temporary = Files.createTempFile(target.getParent(), "quickchat-", ".tmp");
        try {
            Files.writeString(temporary, json.toString());
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String unescape(String text) {
        Matcher escapes = ESCAPE.matcher(text);
        StringBuilder result = new StringBuilder();
        while (escapes.find()) {
            String token = escapes.group(1);
            char decoded = switch (token.charAt(0)) {
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> (char) Integer.parseInt(token.substring(1), 16);
                default -> token.charAt(0);
            };
            escapes.appendReplacement(result, Matcher.quoteReplacement(String.valueOf(decoded)));
        }
        return escapes.appendTail(result).toString();
    }

    private static final class Cursor {
        private final Matcher matcher;
        private int position;

        Cursor(String json) {
            matcher = TOKEN.matcher(json);
        }

        boolean take(String punctuation) {
            if (!matcher.region(position, matcher.regionEnd()).lookingAt()) {
                return false;
            }
            boolean matches = punctuation.isEmpty()
                    ? matcher.group().isBlank() : punctuation.equals(matcher.group(1));
            if (matches) {
                position = matcher.end();
            }
            return matches;
        }

        void require(String punctuation) throws IOException {
            if (!take(punctuation)) {
                throw new IOException("Malformed message archive near character " + position + ".");
            }
        }

        String value(int group) throws IOException {
            if (!matcher.region(position, matcher.regionEnd()).lookingAt()
                    || matcher.group(group) == null) {
                throw new IOException("Invalid JSON value near character " + position + ".");
            }
            position = matcher.end();
            return group == 3 ? unescape(matcher.group(group)) : matcher.group(group);
        }
    }
}
