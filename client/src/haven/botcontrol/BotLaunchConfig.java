package haven.botcontrol;

public final class BotLaunchConfig {
    public static final String ENV_ACCOUNT_USERNAME = "HAVEN_ACCOUNT_USERNAME";
    public static final String ENV_ACCOUNT_SECRET = "HAVEN_ACCOUNT_SECRET";
    public static final String ENV_BOT_CHARACTER = "HAVEN_BOT_CHARACTER";
    public static final String ENV_BOT_WORLD = "HAVEN_BOT_WORLD";
    private static volatile boolean autoLoginEnabled = true;
    private static volatile boolean autoSelectEnabled = true;
    private static volatile String characterOverride;

    private BotLaunchConfig() {
    }

    public static String accountUsername() {
        return read(ENV_ACCOUNT_USERNAME);
    }

    public static String accountSecret() {
        return read(ENV_ACCOUNT_SECRET);
    }

    public static String preferredCharacter() {
        return read(ENV_BOT_CHARACTER);
    }

    public static String preferredWorld() {
        return read(ENV_BOT_WORLD);
    }

    public static boolean hasAccountCredentials() {
        return accountUsername() != null && accountSecret() != null;
    }

    public static void pauseAutomation(String characterName) {
        characterOverride = normalize(characterName);
        autoLoginEnabled = false;
        autoSelectEnabled = false;
    }

    public static void stopAutomation(String characterName) {
        characterOverride = normalize(characterName);
        autoLoginEnabled = false;
        autoSelectEnabled = false;
    }

    public static void resumeAutomation(String characterName) {
        String normalized = normalize(characterName);
        if (normalized != null) {
            characterOverride = normalized;
        }
        autoLoginEnabled = true;
        autoSelectEnabled = true;
    }

    public static boolean shouldAutoLogin() {
        return autoLoginEnabled && hasAccountCredentials();
    }

    public static boolean shouldAutoSelectCharacter() {
        return autoSelectEnabled && desiredCharacter() != null;
    }

    public static String desiredCharacter() {
        String override = normalize(characterOverride);
        if (override != null) {
            return override;
        }
        return normalize(preferredCharacter());
    }

    private static String read(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key);
        }
        return (value == null || value.isBlank()) ? null : value;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
