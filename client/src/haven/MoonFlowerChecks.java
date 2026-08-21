package haven;

import java.nio.file.Path;

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

        System.out.println("MoonFlower branding checks passed.");
    }

    private static void require(boolean condition, String description) {
        if(!condition)
            throw(new AssertionError("Unexpected " + description));
    }
}
