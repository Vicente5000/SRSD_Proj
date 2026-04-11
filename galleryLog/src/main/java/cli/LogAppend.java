package cli;

public class LogAppend {
    private static final String INVALID = "invalid";

    public static void main(String[] args) {
        String rawCommand = String.join(" ", args);
        new LogAppend().handle(rawCommand);
    }

    public void handle(String rawCommand) {
        System.out.println(INVALID);
    }
}
