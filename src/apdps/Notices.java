package apdps;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Messages from the admin to a farmer (for example "your product was deleted, and why").
 * They are kept in a small text file, not in the database, so no table is added or changed.
 * One line per message: username, time, text (separated by tabs). A message is removed from
 * the file as soon as the farmer has been shown it.
 */
public class Notices {
    private static final Path FILE = Paths.get(System.getProperty("user.home"), "apdps_notices.txt");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Stores a message for the given username. */
    public static synchronized void add(String username, String message) throws IOException {
        String line = username + "\t" + LocalDateTime.now().format(TIME) + "\t" + clean(message)
            + System.lineSeparator();
        Files.write(FILE, line.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /** Returns the waiting messages of the username (oldest first) and removes them from the file. */
    public static synchronized List<String> take(String username) {
        List<String> mine = new ArrayList<>();
        try {
            if (!Files.exists(FILE)) {
                return mine;
            }
            List<String> keep = new ArrayList<>();
            for (String line : Files.readAllLines(FILE, StandardCharsets.UTF_8)) {
                String[] parts = line.split("\t", 3);
                if (parts.length == 3 && parts[0].equals(username)) {
                    mine.add(parts[1] + " - " + parts[2]);
                } else if (!line.trim().isEmpty()) {
                    keep.add(line);
                }
            }
            if (!mine.isEmpty()) {
                Files.write(FILE, keep, StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            // if the file cannot be read there is simply nothing to show
        }
        return mine;
    }

    private static String clean(String text) {
        return text.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }
}
