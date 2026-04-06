import java.util.Scanner;

public class TUI {
    private final LogAppend logAppend;
    private final LogRead logRead;

    public TUI() {
        this.logAppend = new LogAppend();
        this.logRead = new LogRead();
    }

    public void run() {
        System.out.println("SRSD Gallery Log Simple TUI");
        System.out.println("Type a command that starts with logappend or logread.");
        System.out.println("Type help for examples, or exit to quit.");

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("> ");
                if (!scanner.hasNextLine()) {
                    break;
                }

                String input = scanner.nextLine().trim();
                if (input.isEmpty()) {
                    continue;
                }

                if ("exit".equalsIgnoreCase(input) || "quit".equalsIgnoreCase(input)) {
                    System.out.println("Exiting TUI.");
                    break;
                }

                if ("help".equalsIgnoreCase(input)) {
                    showHelp();
                    continue;
                }

                if (input.startsWith("logappend")) {
                    logAppend.handle(input);
                    continue;
                }

                if (input.startsWith("logread")) {
                    logRead.handle(input);
                    continue;
                }

                System.out.println("Unknown command. Use logappend or logread.");
            }
        }
    }

    private void showHelp() {
        System.out.println("Examples:");
        System.out.println("  logappend -T 12 -K token -A -E Alice -R 3 gallery.log");
        System.out.println("  logappend -B batch.txt");
        System.out.println("  logread -K token -S gallery.log");
        System.out.println("  logread -K token -R -E Alice gallery.log");
    }
}
