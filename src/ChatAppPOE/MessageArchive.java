package ChatAppPOE;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.StringJoiner;

/** Builds a consistent view of session messages and the persisted archive. */
final class MessageArchive {
    private final Message[] sentMessages;
    private final Message[] disregardedMessages;
    private final Message[] storedMessages;
    private final Message[] messages;
    // These arrays share an index with messages for exact ID and hash lookup.
    private final String[] messageIds;
    private final String[] messageHashes;

    private MessageArchive(Message[] session, Message[] stored) {
        sentMessages = Arrays.stream(session)
                .filter(message -> message.getStatus().equals("SENT")).toArray(Message[]::new);
        disregardedMessages = Arrays.stream(session)
                .filter(message -> message.getStatus().equals("DISCARDED")).toArray(Message[]::new);
        storedMessages = stored;
        messages = Arrays.copyOf(session, session.length + stored.length);
        System.arraycopy(stored, 0, messages, session.length, stored.length);
        messageIds = new String[messages.length];
        messageHashes = new String[messages.length];
        for (int index = 0; index < messages.length; index++) {
            messageIds[index] = messages[index].getMessageID();
            messageHashes[index] = messages[index].getMessageHash();
        }
    }

    static MessageArchive load() throws IOException {
        return new MessageArchive(Message.sessionSnapshot(), MessageJson.read(Message.storagePath()));
    }

    Message[] sentMessages() {
        return sentMessages.clone();
    }

    Message[] disregardedMessages() {
        return disregardedMessages.clone();
    }

    Message[] storedMessages() {
        return storedMessages.clone();
    }

    String[] messageIds() {
        return messageIds.clone();
    }

    String[] messageHashes() {
        return messageHashes.clone();
    }

    String senderRecipientList() {
        StringJoiner output = new StringJoiner(System.lineSeparator())
                .setEmptyValue("No stored messages.");
        for (Message message : storedMessages) {
            output.add("Sender: " + message.getSender() + " | Recipient: " + message.getRecipient());
        }
        return output.toString();
    }

    Message longestStored() {
        Message longest = null;
        for (Message message : storedMessages) {
            if (longest == null || message.getMessageText().length() > longest.getMessageText().length()) {
                longest = message;
            }
        }
        return longest;
    }

    Message findById(String id) {
        for (int index = 0; index < messageIds.length; index++) {
            if (messageIds[index].equals(id)) {
                return messages[index];
            }
        }
        return null;
    }

    Message[] findByRecipient(String recipient) {
        String target = normalizeRecipient(recipient);
        return Arrays.stream(messages)
                .filter(message -> !message.getStatus().equals("DISCARDED"))
                .filter(message -> normalizeRecipient(message.getRecipient()).equals(target))
                .toArray(Message[]::new);
    }

    static String normalizeRecipient(String recipient) {
        String trimmed = recipient.trim();
        return trimmed.matches("0[0-9]{9}") ? "+27" + trimmed.substring(1) : trimmed;
    }

    static boolean hasSharedStoredHash(Message[] stored, String hash) {
        return Arrays.stream(stored)
                .filter(message -> message.getMessageHash().equalsIgnoreCase(hash))
                .limit(2).count() > 1;
    }

    static String sentReport() {
        MessageArchive session = new MessageArchive(Message.sessionSnapshot(), new Message[0]);
        return report(session.sentMessages(), "No messages have been sent yet.");
    }

    static String deleteStoredByHash(String hash, String id) throws IOException {
        synchronized (MessageJson.class) {
            Path file = Message.storagePath();
            MessageArchive archive = new MessageArchive(new Message[0], MessageJson.read(file));
            Message match = null;
            for (int index = 0; index < archive.messageHashes.length; index++) {
                if (archive.messageHashes[index].equalsIgnoreCase(hash)
                        && (id.isEmpty() || archive.messageIds[index].equals(id))) {
                    if (match != null) {
                        return "Several stored messages share that hash. Enter the message ID to select one.";
                    }
                    match = archive.messages[index];
                }
            }
            if (match == null) {
                return "No stored message matches the details entered.";
            }

            Message deleted = match;
            Message[] remaining = Arrays.stream(archive.storedMessages)
                    .filter(message -> message != deleted).toArray(Message[]::new);
            MessageJson.write(file, remaining);
            return "Message: “" + deleted.getMessageText() + "” successfully deleted.";
        }
    }

    static String report(Message[] records, String emptyMessage) {
        String newline = System.lineSeparator();
        StringJoiner output = new StringJoiner(newline + newline).setEmptyValue(emptyMessage);
        for (Message message : records) {
            output.add(message.getFullDetails()
                    + newline + "Sender: " + message.getSender()
                    + newline + "Message number: " + message.getMessageNumber()
                    + newline + "Status: " + message.getStatus());
        }
        return output.toString();
    }
}
