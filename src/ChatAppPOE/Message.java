package ChatAppPOE;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

public class Message {

    private static final int MAX_MESSAGE_LENGTH = 250;

    // South African international format: +27 followed by nine digits.
    // https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html
    private static final Pattern RECIPIENT_PATTERN =
            Pattern.compile("^\\+27[0-9]{9}$");

    private static Message[] sessionMessages = new Message[0];
    private static final Set<String> GENERATED_MESSAGE_IDS = new HashSet<>();
    private static int totalMessagesSent = 0;
    private static Path storageFile = Paths.get("messages.json");

    private final String messageID;
    private final int messageNumber;
    private final String recipient;
    private final String messageText;
    private final String messageHash;
    private final String sender;

    private MessageStatus status = MessageStatus.PENDING;

    private enum MessageStatus {
        PENDING,
        SENT,
        STORED,
        DISCARDED
    }

    /** Creates a message with a randomly generated ten-digit message ID. */
    public Message(int messageNumber, String recipient, String messageText) {
        this(generateMessageID(), messageNumber, recipient, messageText);
    }

    public Message(int messageNumber, String sender, String recipient, String messageText) {
        this(generateMessageID(), messageNumber, sender, recipient, messageText);
    }

    /**
     * Creates a message using a supplied ID. The ID constructor is useful for
     * deterministic unit tests; the console application uses the random-ID
     * constructor above.
     */
    public Message(String messageID, int messageNumber,
                   String recipient, String messageText) {
        this(messageID, messageNumber, "Unknown (Part 2)", recipient, messageText);
    }

    Message(String messageID, int messageNumber, String sender,
            String recipient, String messageText) {
        this(messageID, messageNumber, sender, recipient, messageText, null,
                MessageStatus.PENDING);
    }

    private Message(String messageID, int messageNumber, String sender,
                    String recipient, String messageText, String hash, MessageStatus status) {
        this.sender = sender;
        this.messageID = messageID == null ? "" : messageID.trim();
        this.messageNumber = messageNumber;
        this.recipient = recipient == null ? "" : recipient.trim();
        this.messageText = messageText == null ? "" : messageText.trim();
        this.messageHash = hash == null ? buildMessageHash() : hash;
        this.status = status;
    }

    static Message restoreStored(String id, int number, String sender,
                                 String recipient, String text, String hash) {
        return new Message(id, number, sender, recipient, text, hash, MessageStatus.STORED);
    }

    static void reserveStoredIds(Message[] messages) {
        synchronized (GENERATED_MESSAGE_IDS) {
            for (Message message : messages) {
                GENERATED_MESSAGE_IDS.add(message.getMessageID());
            }
        }
    }

    private static String generateMessageID() {
        // Values range from 1,000,000,000 through 9,999,999,999 (ten digits).
        // The set prevents duplicate IDs during this application session.
        while (true) {
            long candidate = ThreadLocalRandom.current().nextLong(
                    1_000_000_000L, 10_000_000_000L
            );
            String id = Long.toString(candidate);
            synchronized (GENERATED_MESSAGE_IDS) {
                if (GENERATED_MESSAGE_IDS.add(id)) {
                    return id;
                }
            }
        }
    }

    /** Ensures that a message ID contains no more than ten characters. */
    public boolean checkMessageID() {
        return !messageID.isEmpty() && messageID.length() <= 10;
    }

    /**
     * Validates the recipient number and returns the exact confirmation or
     * error message required by the Part 2 specification.
     */
    static boolean isValidRecipient(String recipient) {
        return RECIPIENT_PATTERN.matcher(recipient).matches();
    }

    public String checkRecipientCell() {
        if (isValidRecipient(recipient)) {
            return "Cell phone number successfully captured.";
        }

        return "Cell phone number is incorrectly formatted or does not contain "
                + "an international code. Please correct the number and try again.";
    }

    /** Returns the Part 2 message-length result, including the excess length. */
    public String checkMessageLength() {
        if (messageText.length() <= MAX_MESSAGE_LENGTH) {
            return "Message ready to send.";
        }

        int excessCharacters = messageText.length() - MAX_MESSAGE_LENGTH;
        return "Message exceeds 250 characters by " + excessCharacters
                + "; please reduce the size.";
    }

   
    public String createMessageHash() {
        return messageHash;
    }

    private String buildMessageHash() {
        String idPrefix;
        if (messageID.length() >= 2) {
            idPrefix = messageID.substring(0, 2);
        } else {
            idPrefix = ("00" + messageID).substring(messageID.length());
        }

        String firstWord = "";
        String lastWord = "";
        String trimmedText = messageText.trim();

        if (!trimmedText.isEmpty()) {
            String[] words = trimmedText.split("\\s+");
            for (String word : words) {
                String cleanedWord = word.replaceAll("[^\\p{L}\\p{N}]", "");
                if (!cleanedWord.isEmpty()) {
                    if (firstWord.isEmpty()) {
                        firstWord = cleanedWord;
                    }
                    lastWord = cleanedWord;
                }
            }
        }

        String wordHash = (firstWord + lastWord).toUpperCase(Locale.ROOT);
        return idPrefix + ":" + messageNumber + ":" + wordHash;
    }

    /**
     * Interactive form required by the brief. The overload taking a choice is
     * used by QuickChat and by unit tests so the decision logic is testable.
     */
    public String SentMessage() {
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        System.out.println("1) Send Message");
        System.out.println("2) Disregard Message");
        System.out.println("3) Store Message to send later");
        System.out.print("Choose an option: ");
        return SentMessage(scanner.nextLine());
    }

    /** Processes choices 1/send, 2/disregard, or 3/store. */
    public String SentMessage(String choice) {
        if (choice == null) {
            return "Invalid message option. Please select 1, 2, or 3.";
        }

        String selectedOption = choice.trim().toLowerCase(Locale.ROOT);

        if (selectedOption.equals("1")
                || selectedOption.equals("send")
                || selectedOption.equals("send message")) {
            return sendMessage();
        }

        if (selectedOption.equals("2")
                || selectedOption.equals("0")
                || selectedOption.equals("disregard")
                || selectedOption.equals("discard")
                || selectedOption.equals("disregard message")) {
            if (status != MessageStatus.PENDING) {
                return "This message has already been processed.";
            }
            status = MessageStatus.DISCARDED;
            addToSession();
            return "Press 0 to delete the message.";
        }

        if (selectedOption.equals("3")
                || selectedOption.equals("store")
                || selectedOption.equals("store message")
                || selectedOption.equals("store to send later")) {
            return storeMessage();
        }

        return "Invalid message option. Please select 1, 2, or 3.";
    }

    private String sendMessage() {
        if (status != MessageStatus.PENDING) {
            return "This message has already been processed.";
        }
        if (!checkMessageID()) {
            return "Message ID must be between one and ten characters long.";
        }
        String lengthResult = checkMessageLength();
        if (!"Message ready to send.".equals(lengthResult)) {
            return lengthResult;
        }
        String recipientResult = checkRecipientCell();
        if (!"Cell phone number successfully captured.".equals(recipientResult)) {
            return recipientResult;
        }

        status = MessageStatus.SENT;
        totalMessagesSent++;
        addToSession();
        return "Message successfully sent.";
    }

    /** Stores this message as JSON in messages.json, or the configured path. */
    public String storeMessage() {
        if (status != MessageStatus.PENDING) {
            return "This message has already been processed.";
        }
        if (!checkMessageID()) {
            return "Message ID must be between one and ten characters long.";
        }
        String lengthResult = checkMessageLength();
        if (!"Message ready to send.".equals(lengthResult)) {
            return lengthResult;
        }
        String recipientResult = checkRecipientCell();
        if (!"Cell phone number successfully captured.".equals(recipientResult)) {
            return recipientResult;
        }

        status = MessageStatus.STORED;
        try {
            MessageJson.append(storageFile, this);
            return "Message successfully stored.";
        } catch (IOException exception) {
            status = MessageStatus.PENDING;
            return "Message could not be stored. " + exception.getMessage();
        }
    }

    /** Returns the full details of each successfully sent message this session. */
    public String printMessages() {
        StringBuilder output = new StringBuilder();
        for (Message message : sessionSnapshot()) {
            if (message.status == MessageStatus.SENT) {
                if (output.length() > 0) {
                    output.append(System.lineSeparator());
                }
                output.append(message.getFullDetails());
            }
        }

        if (output.length() == 0) {
            return "No messages have been sent yet.";
        }
        return output.toString();
    }

    /** Returns the total number of messages successfully sent this session. */
    public int returnTotalMessagess() {
        return getTotalMessagesSent();
    }

    public static int getTotalMessagesSent() {
        return totalMessagesSent;
    }

    public String getMessageID() {
        return messageID;
    }

    public int getMessageNumber() {
        return messageNumber;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getMessageText() {
        return messageText;
    }

    public String getMessageHash() {
        return messageHash;
    }

    public String getStatus() {
        return status.name();
    }

    public boolean isCompleted() {
        return status != MessageStatus.PENDING;
    }

    /** Displays details in the order required by the Part 2 brief. */
    public String getFullDetails() {
        return "Message ID: " + messageID + System.lineSeparator()
                + "Message Hash: " + messageHash + System.lineSeparator()
                + "Recipient: " + recipient + System.lineSeparator()
                + "Message: " + messageText;
    }

    public String getSender() {
        return sender;
    }

    private void addToSession() {
        synchronized (Message.class) {
            sessionMessages = Arrays.copyOf(sessionMessages, sessionMessages.length + 1);
            sessionMessages[sessionMessages.length - 1] = this;
        }
    }

    static synchronized Message[] sessionSnapshot() {
        return sessionMessages.clone();
    }

    static Path storagePath() {
        return storageFile;
    }

    String toJsonObject() {
        return "{\n"
                + "  \"messageID\": \"" + escapeJson(messageID) + "\",\n"
                + "  \"messageNumber\": " + messageNumber + ",\n"
                + "  \"sender\": \"" + escapeJson(sender) + "\",\n"
                + "  \"recipient\": \"" + escapeJson(recipient) + "\",\n"
                + "  \"message\": \"" + escapeJson(messageText) + "\",\n"
                + "  \"messageHash\": \"" + escapeJson(messageHash) + "\",\n"
                + "  \"status\": \"" + status.name() + "\"\n"
                + "}";
    }

    /** Escapes control characters and quotation marks for valid JSON strings. */
    private static String escapeJson(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"':
                    escaped.append("\\\"");
                    break;
                case '\\':
                    escaped.append("\\\\");
                    break;
                case '\b':
                    escaped.append("\\b");
                    break;
                case '\f':
                    escaped.append("\\f");
                    break;
                case '\n':
                    escaped.append("\\n");
                    break;
                case '\r':
                    escaped.append("\\r");
                    break;
                case '\t':
                    escaped.append("\\t");
                    break;
                default:
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                    break;
            }
        }
        return escaped.toString();
    }

    // Package-private test helpers; these do not affect normal application use.
    static void resetSessionForTesting() {
        synchronized (Message.class) {
            sessionMessages = new Message[0];
        }
        totalMessagesSent = 0;
        synchronized (GENERATED_MESSAGE_IDS) {
            GENERATED_MESSAGE_IDS.clear();
        }
        storageFile = Paths.get("messages.json");
    }

    static void setStorageFilePathForTesting(Path path) {
        storageFile = path;
    }
}
