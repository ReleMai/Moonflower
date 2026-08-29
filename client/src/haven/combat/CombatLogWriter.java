package haven.combat;

import haven.Config;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/** Serial append-only JSONL storage kept outside the Steam installation. */
public final class CombatLogWriter {
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withZone(ZoneOffset.UTC);
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(new ThreadFactory() {
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "MoonFlower combat-log writer");
            thread.setDaemon(true);
            return thread;
        }
    });
    private static boolean warned;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            WRITER.shutdown();
            try {
                WRITER.awaitTermination(2, TimeUnit.SECONDS);
            } catch(InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "MoonFlower combat-log shutdown"));
    }

    private CombatLogWriter() {
    }

    public static Path directory() {
        Path local = Config.localdir();
        return local == null ? null : local.resolve("MoonFlower").resolve("combat-logs");
    }

    public static void append(JSONObject record, Instant capturedAt) {
        Path directory = directory();
        if(directory == null)
            return;
        String line = record.toString();
        WRITER.execute(() -> {
            try {
                writeNow(directory, capturedAt, line);
            } catch(IOException | SecurityException error) {
                synchronized(CombatLogWriter.class) {
                    if(!warned) {
                        warned = true;
                        System.err.println("MoonFlower combat log disabled: " + error.getMessage());
                    }
                }
            }
        });
    }

    static Path writeNow(Path directory, Instant capturedAt, String line) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("combat-" + FILE_DATE.format(capturedAt) + ".jsonl");
        Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        return file;
    }
}
