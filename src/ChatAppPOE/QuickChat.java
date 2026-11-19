package ChatAppPOE;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class QuickChat {

    public static void main(String[] args) {
        try (Scanner keyboard = new Scanner(System.in)) {
            displayHeading();
            Login account = registerAccount(keyboard);
            if (!account.checkUserName() || !account.checkPasswordComplexity()
                    || !account.checkCellPhoneNumber()) {
                System.out.println("Account creation failed. Please enter valid registration details.");
                return;
            }
            if (performLogin(keyboard, account)) {
                System.out.println("\nWelcome to QuickChat.");
                try {
                    Message.reserveStoredIds(MessageJson.read(Message.storagePath()));
                } catch (IOException exception) {
                    System.out.println("Stored messages are unavailable. " + exception.getMessage());
                }
                int messageLimit = readNonNegativeInteger(
                        keyboard, "How many messages would you like to enter? ");
                runMessageMenu(keyboard, messageLimit, account.getUserName());
            }
        } catch (NoSuchElementException endOfInput) {
            System.out.println("\nInput ended. Goodbye.");
        }
    }

    private static Login registerAccount(Scanner keyboard) {
        System.out.println("CREATE YOUR ACCOUNT");
        System.out.println("-------------------");

        System.out.print("First name: ");
        String firstName = keyboard.nextLine().trim();

        System.out.print("Last name: ");
        String lastName = keyboard.nextLine().trim();

        System.out.print("Username: ");
        String username = keyboard.nextLine().trim();

        System.out.print("Password: ");
        String password = keyboard.nextLine();

        System.out.print("Cellphone number (+27 and nine digits): ");
        String cellphone = keyboard.nextLine().trim();

        Login account = new Login(firstName, lastName, username, password, cellphone);

        System.out.println();
        System.out.println("VALIDATION RESULTS");
        System.out.println("------------------");
        System.out.println(account.usernameMessage());
        System.out.println(account.passwordMessage());
        System.out.println(account.cellPhoneMessage());

        System.out.println();
        System.out.println("REGISTRATION");
        System.out.println("------------");
        System.out.println(account.registerUser());

        return account;
    }

    private static void displayHeading() {
        System.out.println("======================================");
        System.out.println("              QUICKCHAT");
        System.out.println("======================================");
        System.out.println();
    }

    /** Returns true only when the entered login credentials are accepted. */
    private static boolean performLogin(Scanner keyboard, Login account) {
        System.out.println();
        System.out.println("======================================");
        System.out.println("                 LOGIN");
        System.out.println("======================================");

        System.out.print("Username: ");
        String enteredUsername = keyboard.nextLine().trim();

        System.out.print("Password: ");
        String enteredPassword = keyboard.nextLine();

        String loginResult = account.returnLoginStatus(enteredUsername, enteredPassword);
        System.out.println();
        System.out.println(loginResult);
        return account.loginUser(enteredUsername, enteredPassword);
    }

    private static void runMessageMenu(Scanner keyboard, int messageLimit, String sender) {
        int messagesProcessed = 0;
        boolean running = true;

        while (running) {
            System.out.println();
            System.out.println("========== QUICKCHAT MENU ==========");
            System.out.println("1) Send Messages");
            System.out.println("2) Show recently sent messages");
            System.out.println("3) Quit");
            System.out.println("4) Stored Messages");
            System.out.print("Choose an option (1-4): ");

            String option = keyboard.nextLine().trim();
            switch (option) {
                case "1":
                    if (messagesProcessed >= messageLimit) {
                        System.out.println("You have entered the maximum number of messages selected.");
                    } else {
                        messagesProcessed = sendMessages(
                                keyboard, messagesProcessed, messageLimit, sender
                        );
                        if (messagesProcessed >= messageLimit) {
                            System.out.println("All selected message entries have been processed.");
                            System.out.println("Total messages sent successfully: "
                                    + Message.getTotalMessagesSent());
                        }
                    }
                    break;
                case "2":
                    System.out.println("\nSENT MESSAGE REPORT");
                    System.out.println(MessageArchive.sentReport());
                    break;
                case "3":
                    System.out.println("Total messages sent successfully: "
                            + Message.getTotalMessagesSent());
                    System.out.println("Thank you for using QuickChat. Goodbye.");
                    running = false;
                    break;
                case "4":
                    runStoredMenu(keyboard);
                    break;
                default:
                    System.out.println("Invalid menu option. Please enter 1, 2, 3, or 4.");
                    break;
            }
        }
    }

    private static void runStoredMenu(Scanner keyboard) {
        while (true) {
            System.out.println("\n========== STORED MESSAGES ==========");
            System.out.println("1) List senders and recipients");
            System.out.println("2) Show longest stored message");
            System.out.println("3) Search by message ID");
            System.out.println("4) Search sent and stored messages by recipient");
            System.out.println("5) Delete a stored message by hash");
            System.out.println("6) Show full stored message report");
            System.out.println("0) Return to main menu");
            String option = readLine(keyboard, "Choose an option: ");
            if (option.equals("0")) {
                return;
            }

            try {
                MessageArchive archive = MessageArchive.load();
                switch (option) {
                    case "1" -> System.out.println(archive.senderRecipientList());
                    case "2" -> {
                        Message message = archive.longestStored();
                        System.out.println(message == null ? "No stored messages."
                                : MessageArchive.report(new Message[]{message}, ""));
                    }
                    case "3" -> {
                        Message message = archive.findById(readLine(keyboard, "Message ID: "));
                        System.out.println(message == null ? "No message matches that ID."
                                : MessageArchive.report(new Message[]{message}, ""));
                    }
                    case "4" -> {
                        String recipient = readLine(keyboard, "Recipient number: ");
                        System.out.println(MessageArchive.report(archive.findByRecipient(recipient),
                                "No sent or stored messages match that recipient."));
                    }
                    case "5" -> {
                        String hash = readLine(keyboard, "Message hash: ");
                        System.out.println(MessageArchive.deleteStoredByHash(hash, ""));
                        // The optional ID is only needed when multiple records share a hash.
                        if (MessageArchive.hasSharedStoredHash(archive.storedMessages(), hash)) {
                            String id = readLine(keyboard, "Message ID (blank to cancel): ");
                            if (!id.isEmpty()) {
                                System.out.println(MessageArchive.deleteStoredByHash(hash, id));
                            }
                        }
                    }
                    case "6" -> System.out.println(MessageArchive.report(archive.storedMessages(),
                            "No stored messages."));
                    default -> System.out.println("Invalid option. Enter a number from 0 to 6.");
                }
            } catch (IOException exception) {
                System.out.println("Could not access stored messages. " + exception.getMessage());
            }
        }
    }

    private static String readLine(Scanner keyboard, String prompt) {
        System.out.print(prompt);
        return keyboard.nextLine().trim();
    }

    /** Uses the loop counter as the zero-based message number required by the hash example. */
    private static int sendMessages(Scanner keyboard,
                                    int firstMessageNumber,
                                    int messageLimit, String sender) {
        int messagesProcessed = firstMessageNumber;

        for (int messageNumber = firstMessageNumber;
             messageNumber < messageLimit;
             messageNumber++) {

            System.out.println();
            System.out.println("MESSAGE " + (messageNumber + 1) + " OF " + messageLimit);

            String recipient;
            while (true) {
                recipient = MessageArchive.normalizeRecipient(readLine(keyboard,
                        "Recipient cell number (+27 followed by nine digits): "));
                if (Message.isValidRecipient(recipient)) {
                    break;
                }
                System.out.println("Invalid recipient. Please enter a valid South African number.");
            }

            Message message;
            while (true) {
                System.out.print("Enter a message (maximum 250 characters): ");
                String messageText = keyboard.nextLine();
                message = new Message(messageNumber, sender, recipient, messageText);
                String lengthResult = message.checkMessageLength();
                if ("Message ready to send.".equals(lengthResult)) {
                    break;
                }
                System.out.println(lengthResult);
            }

            System.out.println(message.checkRecipientCell());
            System.out.println("Message ID generated: " + message.getMessageID());
            System.out.println("Message Hash: " + message.createMessageHash());

            while (!message.isCompleted()) {
                System.out.println("Choose what to do with this message:");
                System.out.println("1) Send Message");
                System.out.println("2) Disregard Message");
                System.out.println("3) Store Message to send later");
                System.out.print("Select 1, 2, or 3: ");

                String action = keyboard.nextLine().trim();
                String result = message.SentMessage(action);
                System.out.println(result);

                if ("SENT".equals(message.getStatus()) || "STORED".equals(message.getStatus())) {
                    System.out.println("Full message details:");
                    System.out.println(message.getFullDetails());
                }
            }

            messagesProcessed++;
        }

        return messagesProcessed;
    }

    private static int readNonNegativeInteger(Scanner keyboard, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = keyboard.nextLine().trim();
            try {
                int number = Integer.parseInt(input);
                if (number >= 0) {
                    return number;
                }
            } catch (NumberFormatException ignored) {
                // The message below gives the user a clear retry instruction.
            }
            System.out.println("Please enter a whole number that is zero or greater.");
        }
    }
}
=======
package ChatAppPOE;

import java.util.Scanner;

public class QuickChat {

    public static void main(String[] args) {

        Scanner keyboard = new Scanner(System.in);

        displayHeading();

        System.out.println("CREATE YOUR ACCOUNT");
        System.out.println("-------------------");
        
        //prompt the user to enter their first name
        System.out.print("First name: ");
        String name = keyboard.nextLine().trim();
       
        //prompt the user to enter their last name
        System.out.print("Last name: ");
        String surname = keyboard.nextLine().trim();
        
        //prompt the user to enter their Username
        System.out.print("Username: ");
        String username = keyboard.nextLine().trim();
        
        //prompt the user to enter their password
        System.out.print("Password: ");
        String password = keyboard.nextLine();
        
        //prompt the user to enter their cellphone number (+27)
        System.out.print("Cellphone number (+27): ");
        String cellphone = keyboard.nextLine().trim();


        Login account = new Login(
                name,
                surname,
                username,
                password,
                cellphone
        );


        System.out.println();
        System.out.println("VALIDATION RESULTS");
        System.out.println("------------------");

        System.out.println(account.usernameMessage());
        System.out.println(account.passwordMessage());


        if (account.checkCellPhoneNumber()) {

            System.out.println(
                    "Cell phone number successfully added."
            );

        } else {

            System.out.println(
                    "Cell phone number incorrectly formatted "
                    + "or does not contain international code."
            );
        }


        System.out.println();
        System.out.println("REGISTRATION");
        System.out.println("------------");

        String registrationMessage = account.registerUser();

        System.out.println(registrationMessage);


        boolean registrationValid =
                account.checkUserName()
                && account.checkPasswordComplexity()
                && account.checkCellPhoneNumber();


        if (registrationValid) {

            performLogin(keyboard, account);

        } else {

            System.out.println();
            System.out.println(
                    "Account creation failed. "
                    + "Please enter valid registration details."
            );
        }

        keyboard.close();
    }


    private static void displayHeading() {

        System.out.println(
                "======================================"
        );

        System.out.println(
                "             QUICKCHAT"
        );

        System.out.println(
                "======================================"
        );

        System.out.println();
    }


    private static void performLogin(Scanner keyboard,
                                     Login account) {

        System.out.println();
        System.out.println(
                "======================================"
        );

        System.out.println(
                "                LOGIN"
        );

        System.out.println(
                "======================================"
        );

        System.out.print("Username: ");
        String enteredName = keyboard.nextLine().trim();

        System.out.print("Password: ");
        String enteredPassword = keyboard.nextLine();

        String loginResult =
                account.returnLoginStatus(
                        enteredName,
                        enteredPassword
                );

        System.out.println();
        System.out.println(loginResult);
    }
}
>>>>>>> origin/master
