package haven;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Resolves the package containing MoonFlower's read-only resources. */
public final class ClientInstall {
    private static final Path PACKAGE_MARKER = Paths.get("res", "customclient", "bgsizer.png");
    private static final String LAUNCHER_CACHE_MARKER = "\\haven launcher\\cache\\file\\";

    private ClientInstall() {
    }

    public static Path directory() {
        String configured = System.getProperty("haven.gamedir");
        Path source = null;
        try {
            source = Utils.srcpath(ClientInstall.class);
        } catch(RuntimeException ignored) {
        }
        return(resolve(configured, source, Utils.path(System.getProperty("user.dir", "."))));
    }

    public static String directoryString() {
        return(directory().toString() + File.separator);
    }

    static Path resolve(String configured, Path source, Path working) {
        List<Path> candidates = new ArrayList<>();
        if((configured != null) && !configured.isBlank())
            candidates.add(Utils.path(configured));

        if(source != null) {
            Path direct = Files.isDirectory(source) ? source : source.getParent();
            if(direct != null)
                candidates.add(direct);
            Path original = launcherOriginal(source);
            if(original != null) {
                Path directory = Files.isDirectory(original) ? original : original.getParent();
                if(directory != null)
                    candidates.add(directory);
            }
        }

        if(working != null)
            candidates.add(working);

        for(Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if(Files.isRegularFile(normalized.resolve(PACKAGE_MARKER)))
                return(normalized);
        }

        throw(new IllegalStateException(
            "MoonFlower could not locate its packaged resources. Reinstall or update the private Workshop item."));
    }

    static Path launcherOriginal(Path cached) {
        String raw = cached.toAbsolutePath().normalize().toString();
        String lower = raw.toLowerCase(java.util.Locale.ROOT);
        int marker = lower.indexOf(LAUNCHER_CACHE_MARKER);
        if(marker < 0)
            return(null);
        String encoded = raw.substring(marker + LAUNCHER_CACHE_MARKER.length());
        if(encoded.isBlank())
            return(null);
        try {
            /* URLDecoder treats '+' as a space, but '+' is valid in a Windows path. */
            String decoded = URLDecoder.decode(encoded.replace("+", "%2B"), StandardCharsets.UTF_8);
            return(Paths.get(decoded));
        } catch(IllegalArgumentException e) {
            return(null);
        }
    }
}
