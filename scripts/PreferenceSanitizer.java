import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Properties;

public final class PreferenceSanitizer {
    private static final List<String> EXACT_KEYS = List.of(
        "savedAccounts",
        "token-id",
        "token-desc",
        "webMapEndpoint",
        "uploadMapTiles",
        "enableLocationTracking",
        "liveLocationName",
        "cookBookEndpoint",
        "cookBookToken"
    );
    private static final List<String> PREFIXES = List.of(
        "savedtoken-",
        "lasttoken-",
        "saved-tokens@",
        "loginname@",
        "tokenname@"
    );

    private static boolean sensitive(String key) {
        if (EXACT_KEYS.contains(key))
            return true;
        for (String prefix : PREFIXES) {
            if (key.startsWith(prefix))
                return true;
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException("usage: PreferenceSanitizer SOURCE DESTINATION");
        Path source = Path.of(args[0]).toAbsolutePath().normalize();
        Path destination = Path.of(args[1]).toAbsolutePath().normalize();
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(source)) {
            properties.loadFromXML(input);
        }
        int before = properties.size();
        properties.keySet().removeIf(key -> sensitive(String.valueOf(key)));

        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), destination.getFileName().toString(), ".privacy-cleanup.tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.storeToXML(output, "MoonFlower preferences", "UTF-8");
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        System.out.printf("Removed file-backed keys: %d%n", before - properties.size());
        System.out.printf("Retained nonsensitive keys: %d%n", properties.size());
        System.out.println("No preference values were displayed or copied outside the sanitized destination.");
    }
}
