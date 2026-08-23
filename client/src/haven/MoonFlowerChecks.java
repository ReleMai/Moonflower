package haven;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Offline checks for the product identity and mutable-data boundary. */
public final class MoonFlowerChecks {
    private MoonFlowerChecks() {
    }

    public static void main(String[] args) {
        require("MoonFlower".equals(Config.confid), "client identity");
        require("v1.0.0".equals(Config.clientVersion), "client version");

        Path dataDirectory = ClientData.directory();
        require(dataDirectory.getFileName() != null &&
                "MoonFlower".equals(dataDirectory.getFileName().toString()),
                "client data directory");
        require(ClientData.sqlite("fishing.db").contains("MoonFlower"),
                "SQLite data path");

        Path installDirectory = ClientInstall.directory();
        require(Files.isRegularFile(installDirectory.resolve("res/customclient/bgsizer.png")),
                "packaged resource directory");
        Path decoded = ClientInstall.launcherOriginal(Paths.get(
                "C:\\Users\\Test\\AppData\\Local\\Haven Launcher\\cache\\file\\" +
                "C%3a\\Program Files %28x86%29\\Steam\\steamapps\\workshop\\content\\" +
                "3051280\\1234567890\\hafen.jar"));
        require(decoded != null && decoded.toString().equals(
                "C:\\Program Files (x86)\\Steam\\steamapps\\workshop\\content\\" +
                "3051280\\1234567890\\hafen.jar"),
                "Steam chained-launch path decoding");

        System.out.println("MoonFlower branding checks passed.");
    }

    private static void require(boolean condition, String description) {
        if(!condition)
            throw(new AssertionError("Unexpected " + description));
    }
}
