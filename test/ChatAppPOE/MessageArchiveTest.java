package ChatAppPOE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class MessageArchiveTest {
    private static final String SENDER = "dev_1";
    private static final String STORED_RECIPIENT = "+27838884567";
    private static final String LONGEST = "Where are you? You are late! I have asked you to be on time.";
    private static final String DINNER = "It is dinner time!";
    private static final String LAST = "Ok, I am leaving without you.";

    @TempDir
    Path directory;
    private Path file;

    @BeforeEach
    void resetSession() {
        Message.resetSessionForTesting();
        file = directory.resolve("messages.json");
        Message.setStorageFilePathForTesting(file);
    }

    private Message[] enterAssignmentMessages() {
        Message[] fixtures = {
            new Message("1000000001", 0, SENDER, "+27834557896", "Did you get the cake?"),
            new Message("1000000002", 1, SENDER, STORED_RECIPIENT, LONGEST),
            new Message("1000000003", 2, SENDER, "+27834484567", "Yohoooo, I am at your gate."),
            new Message("0838884567", 3, SENDER,
                    "+27834557896", DINNER),
            new Message("1000000005", 4, SENDER, STORED_RECIPIENT, LAST)
        };
        assertEquals("Message successfully sent.", fixtures[0].SentMessage("1"));
        assertEquals("Message successfully stored.", fixtures[1].SentMessage("3"));
        assertEquals("Press 0 to delete the message.", fixtures[2].SentMessage("2"));
        assertEquals("Message successfully sent.", fixtures[3].SentMessage("1"));
        assertEquals("Message successfully stored.", fixtures[4].SentMessage("3"));
        return fixtures;
    }

    @Test
    void populatesAllFiveArraysFromUserActionsAndJson() throws IOException {
        Message[] entered = enterAssignmentMessages();
        MessageArchive archive = MessageArchive.load();
        assertEquals(Arrays.asList("Did you get the cake?", DINNER), texts(archive.sentMessages()));
        assertEquals(Arrays.asList("Yohoooo, I am at your gate."), texts(archive.disregardedMessages()));
        assertEquals(Arrays.asList(LONGEST, LAST), texts(archive.storedMessages()));
        int[] expectedOrder = {0, 2, 3, 1, 4};
        for (int index = 0; index < expectedOrder.length; index++) {
            Message message = entered[expectedOrder[index]];
            assertEquals(message.getMessageID(), archive.messageIds()[index]);
            assertEquals(message.getMessageHash(), archive.messageHashes()[index]);
        }
    }

    @Test
    void displaysTheLongestStoredMessage() throws IOException {
        enterAssignmentMessages();
        assertEquals(LONGEST, MessageArchive.load().longestStored().getMessageText());
    }

    @Test
    void findsTheDeveloperMessageByItsExactId() throws IOException {
        enterAssignmentMessages();
        MessageArchive archive = MessageArchive.load();
        Message match = archive.findById("0838884567");
        assertEquals(DINNER, match.getMessageText());
        assertEquals("+27834557896", match.getRecipient());
        assertNull(archive.findById("838884567"));
    }

    @Test
    void findsStoredAndSentMessagesByRecipient() throws IOException {
        enterAssignmentMessages();
        MessageArchive archive = MessageArchive.load();
        assertEquals(Arrays.asList(LONGEST, LAST),
                texts(archive.findByRecipient(STORED_RECIPIENT)));
        assertEquals(Arrays.asList(LONGEST, LAST),
                texts(archive.findByRecipient("0838884567")));
        assertEquals(Arrays.asList("Did you get the cake?", DINNER),
                texts(archive.findByRecipient("+27834557896")));
        assertEquals(0, archive.findByRecipient("+27834484567").length);

        Message sent = new Message("1000000006", 5, SENDER, STORED_RECIPIENT, "Sent to this recipient.");
        assertEquals("Message successfully sent.", sent.SentMessage("1"));
        assertEquals(Arrays.asList("Sent to this recipient.", LONGEST, LAST),
                texts(MessageArchive.load().findByRecipient(STORED_RECIPIENT)));
    }

    @Test
    void deletesTheRequestedHashAndPersistsTheChange() throws IOException {
        Message[] entered = enterAssignmentMessages();
        String hash = entered[1].getMessageHash();
        assertEquals("Message: “" + LONGEST + "” successfully deleted.",
                MessageArchive.deleteStoredByHash(hash, ""));
        MessageArchive reloaded = MessageArchive.load();
        assertNull(reloaded.findById(entered[1].getMessageID()));
        assertFalse(Arrays.asList(reloaded.messageHashes()).contains(hash));
        assertEquals(Arrays.asList(LAST), texts(reloaded.storedMessages()));
        assertEquals(2, reloaded.sentMessages().length);
    }

    @Test
    void listsSendersAndRecipientsAndProducesFullReports() throws IOException {
        Message[] entered = enterAssignmentMessages();
        MessageArchive archive = MessageArchive.load();
        String row = "Sender: " + SENDER + " | Recipient: " + STORED_RECIPIENT;
        assertEquals(row + System.lineSeparator() + row, archive.senderRecipientList());
        String report = MessageArchive.sentReport();
        String expected = entered[0].getFullDetails() + System.lineSeparator()
                + "Sender: " + SENDER + System.lineSeparator()
                + "Message number: 0" + System.lineSeparator() + "Status: SENT"
                + System.lineSeparator().repeat(2) + entered[3].getFullDetails()
                + System.lineSeparator() + "Sender: " + SENDER + System.lineSeparator()
                + "Message number: 3" + System.lineSeparator() + "Status: SENT";
        assertEquals(expected, report);
        String storedReport = MessageArchive.report(archive.storedMessages(), "");
        assertTrue(storedReport.contains(entered[1].getFullDetails()));
        assertTrue(storedReport.contains(entered[4].getFullDetails()));
        assertFalse(storedReport.contains(DINNER));
    }

    @Test
    void readsLegacyArchiveAndRestoresEscapedTextAfterSessionReset() throws IOException {
        Files.writeString(file, """
                [{"status":"STORED","messageHash":"00:0:LEGACYTEXT","messageNumber":0,
                "recipient":"+27834557896","messageID":"0000000001","message":"Le\\u0067acy text"}]
                """);
        String text = "Hello \"A\"!\nMeet at C:\\Café.";
        Message message = new Message("1000000007", 1, SENDER, STORED_RECIPIENT, text);
        assertEquals("Message successfully stored.", message.storeMessage());
        resetSession();
        MessageArchive archive = MessageArchive.load();
        assertEquals(Arrays.asList("Legacy text", text), texts(archive.storedMessages()));
        assertEquals("Unknown (Part 2)", archive.storedMessages()[0].getSender());
        assertEquals(SENDER, archive.storedMessages()[1].getSender());
        assertEquals("00:0:LEGACYTEXT", archive.storedMessages()[0].getMessageHash());
    }

    @Test
    void preservesMalformedArchiveOnFailedStore() throws IOException {
        Files.writeString(file, "[{ broken JSON }]");
        assertThrows(IOException.class, MessageArchive::load);
        Message message = new Message(0, SENDER, STORED_RECIPIENT, LAST);
        assertTrue(message.storeMessage().startsWith("Message could not be stored."));
        assertFalse(message.isCompleted());
        assertEquals("[{ broken JSON }]", Files.readString(file));
    }

    @Test
    void handlesEmptyArchiveAndHashCollisionsWithoutDeletingTheWrongMessage() throws IOException {
        assertNull(MessageArchive.load().longestStored());
        assertEquals("No stored messages.", MessageArchive.load().senderRecipientList());
        assertEquals("No stored message matches the details entered.",
                MessageArchive.deleteStoredByHash("missing", ""));
        Message first = new Message("1200000001", 0, SENDER, STORED_RECIPIENT, "Hi again");
        Message second = new Message("1200000002", 0, SENDER, STORED_RECIPIENT, "Hi again");
        assertEquals(first.getMessageHash(), second.getMessageHash());
        assertEquals("Message successfully stored.", first.storeMessage());
        assertEquals("Message successfully stored.", second.storeMessage());
        assertTrue(MessageArchive.deleteStoredByHash(first.getMessageHash(), "")
                .startsWith("Several stored messages"));
        assertEquals(2, MessageArchive.load().storedMessages().length);
        assertEquals("Message: “Hi again” successfully deleted.",
                MessageArchive.deleteStoredByHash(first.getMessageHash(), first.getMessageID()));
        assertEquals(second.getMessageID(), MessageArchive.load().storedMessages()[0].getMessageID());
    }

    private static java.util.List<String> texts(Message[] messages) {
        return Arrays.stream(messages).map(Message::getMessageText).toList();
    }
}
