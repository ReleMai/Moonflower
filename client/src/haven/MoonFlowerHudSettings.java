package haven;

import java.util.Locale;

/**
 * Preference names and bounded values for the in-game MoonFlower HUD.
 * Keeping these values together prevents rendering and option panels from
 * silently disagreeing about supported ranges.
 */
public final class MoonFlowerHudSettings {
    public static final String ENABLED = "moonflower-hud-enabled";
    public static final String CHOICE_MADE = "moonflower-hud-choice-made-v1";
    public static final String EDIT_MODE = "moonflower-hud-edit-mode";
    public static final String VITAL_STYLE = "moonflower-hud-vital-style";
    public static final String PORTRAIT_SCALE = "moonflower-hud-portrait-scale";
    public static final String FEATURE_VINE_EXPANDED = "moonflower-hud-feature-vine-expanded";
    public static final String SHOW_VITAL_NUMBERS = "moonflower-hud-show-vital-numbers";
    public static final String CLOCK_REDUCED_MOTION = "moonflower-clock-reduced-motion";
    public static final String ACTION_BAR_SCALE = "moonflower-action-bar-scale";
    public static final String CHAT_TEXT_SIZE = "moonflower-chat-text-size";
    public static final String CHAT_BACKGROUND_ALPHA = "moonflower-chat-background-alpha";
    public static final String CHAT_ALERT_VOLUME = "moonflower-chat-alert-volume";
    public static final String CHAT_SHOW_TIMESTAMPS = "moonflower-chat-show-timestamps";
    public static final String CHAT_SHOW_PREVIEWS = "moonflower-chat-show-previews";
    public static final String CHAT_PREVIEW_SECONDS = "moonflower-chat-preview-seconds";
    public static final String CHAT_PREVIEW_COUNT = "moonflower-chat-preview-count";
    public static final String CHAT_ALERT_ACTIVE_CHANNEL = "moonflower-chat-alert-active-channel";
    public static final String CHAT_KEYWORDS = "moonflower-chat-keywords";
    public static final String COMBAT_STATUS_OFFSET = "moonflower-combat-status-offset";
    public static final String COMBAT_DECK_OFFSET = "moonflower-combat-deck-offset";

    public static final int STYLE_RINGS = 0;
    public static final int STYLE_RIBBONS = 1;

    private MoonFlowerHudSettings() {
    }

    public static boolean enabled() {
        return Utils.getprefb(ENABLED, false);
    }

    public static boolean choiceMade() {
        return Utils.getprefb(CHOICE_MADE, false);
    }

    public static boolean editMode() {
        return Utils.getprefb(EDIT_MODE, false);
    }

    public static int vitalStyle() {
        return clamp(Utils.getprefi(VITAL_STYLE, STYLE_RINGS), STYLE_RINGS, STYLE_RIBBONS);
    }

    public static int portraitScale() {
        return clamp(Utils.getprefi(PORTRAIT_SCALE, 100), 80, 140);
    }

    public static boolean featureVineExpanded() {
        return Utils.getprefb(FEATURE_VINE_EXPANDED, false);
    }

    public static boolean showVitalNumbers() {
        return Utils.getprefb(SHOW_VITAL_NUMBERS, true);
    }

    public static boolean clockReducedMotion() {
        return Utils.getprefb(CLOCK_REDUCED_MOTION, false);
    }

    public static int actionBarScale() {
        return clamp(Utils.getprefi(ACTION_BAR_SCALE, 100), 100, 160);
    }

    public static int chatTextSize() {
        return clamp(Utils.getprefi(CHAT_TEXT_SIZE, 12), 10, 24);
    }

    public static int chatBackgroundAlpha() {
        return clamp(Utils.getprefi(CHAT_BACKGROUND_ALPHA, 128), 32, 224);
    }

    public static int chatAlertVolume() {
        return clamp(Utils.getprefi(CHAT_ALERT_VOLUME, 70), 0, 100);
    }

    public static boolean chatShowTimestamps() {
        return Utils.getprefb(CHAT_SHOW_TIMESTAMPS, true);
    }

    public static boolean chatShowPreviews() {
        return Utils.getprefb(CHAT_SHOW_PREVIEWS, true);
    }

    public static int chatPreviewSeconds() {
        return clamp(Utils.getprefi(CHAT_PREVIEW_SECONDS, 5), 1, 15);
    }

    public static int chatPreviewCount() {
        return clamp(Utils.getprefi(CHAT_PREVIEW_COUNT, 4), 1, 8);
    }

    public static boolean chatAlertActiveChannel() {
        return Utils.getprefb(CHAT_ALERT_ACTIVE_CHANNEL, false);
    }

    public static String chatKeywords() {
        return Utils.getpref(CHAT_KEYWORDS, "");
    }

    public static boolean chatContainsKeyword(String text, String keywordList) {
        if(text == null || keywordList == null || keywordList.trim().isEmpty())
            return false;
        String lower = text.toLowerCase(Locale.ROOT);
        for(String keyword : keywordList.split(",")) {
            String candidate = keyword.trim().toLowerCase(Locale.ROOT);
            if(!candidate.isEmpty() && lower.contains(candidate))
                return true;
        }
        return false;
    }

    public static String formatChatText(String text, String timestamp, boolean showTimestamp, boolean keyword) {
        return (keyword ? "\u2726 " : "") + (showTimestamp ? ("[" + timestamp + "] ") : "") + text;
    }

    public static int actionBarColumns(int barNumber, boolean horizontalFallback) {
        int fallback = horizontalFallback ? 10 : 1;
        int columns = Utils.getprefi("actionBarColumns" + barNumber, fallback);
        return (columns == 1 || columns == 5 || columns == 10) ? columns : fallback;
    }

    public static boolean actionBarLocked(int barNumber) {
        return Utils.getprefb("moonflower-action-bar-locked-" + barNumber, false);
    }

    public static void setActionBarLocked(int barNumber, boolean locked) {
        Utils.setprefb("moonflower-action-bar-locked-" + barNumber, locked);
    }

    public static boolean equipmentToolbarExpanded(boolean equipmentWindowOpen) {
        return !equipmentWindowOpen;
    }

    public static boolean equipmentToolbarExpanded(boolean equipmentWindowOpen, boolean characterWindowOpen) {
        return equipmentToolbarExpanded(equipmentWindowOpen);
    }

    public static String hubPositionKey(String characterId) {
        return "moonflower-hud-position-" + ((characterId == null || characterId.isEmpty()) ? "default" : characterId);
    }

    public static Coord combatStatusOffset() {
        return Utils.getprefc(COMBAT_STATUS_OFFSET, Coord.z);
    }

    public static Coord combatDeckOffset() {
        return Utils.getprefc(COMBAT_DECK_OFFSET, Coord.of(0, -UI.scale(180)));
    }

    public static void setCombatStatusOffset(Coord offset) {
        Utils.setprefc(COMBAT_STATUS_OFFSET, offset == null ? Coord.z : offset);
    }

    public static void setCombatDeckOffset(Coord offset) {
        Utils.setprefc(COMBAT_DECK_OFFSET, offset == null ? Coord.of(0, -UI.scale(180)) : offset);
    }

    public static void resetCombatLayout() {
        Utils.setpref(COMBAT_STATUS_OFFSET, null);
        Utils.setpref(COMBAT_DECK_OFFSET, null);
    }

    public static Coord centeredBottomPosition(Coord parentSize, Coord hubSize, int margin) {
        int x = Math.max(0, (parentSize.x - hubSize.x) / 2);
        int y = Math.max(0, parentSize.y - hubSize.y - Math.max(0, margin));
        return Coord.of(x, y);
    }

    public static int scaled(int value, int percent) {
        return Math.max(1, (int)Math.round(value * (clamp(percent, 1, 200) / 100.0)));
    }

    public static int rowsForColumns(int columns) {
        if(columns <= 0)
            throw new IllegalArgumentException("columns must be positive");
        return (10 + columns - 1) / columns;
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
