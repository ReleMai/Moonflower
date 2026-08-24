package haven;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public final class MoonFlowerHudChecks {
    private MoonFlowerHudChecks() {
    }

    public static void main(String[] args) {
        require(MoonFlowerHudSettings.clamp(5, 10, 20) == 10, "lower preference bound");
        require(MoonFlowerHudSettings.clamp(25, 10, 20) == 20, "upper preference bound");
        require(MoonFlowerHudSettings.scaled(36, 100) == 36, "default toolbar scale");
        require(MoonFlowerHudSettings.scaled(36, 150) == 54, "large toolbar scale");
        require(MoonFlowerHudSettings.rowsForColumns(10) == 1, "horizontal toolbar rows");
        require(MoonFlowerHudSettings.rowsForColumns(5) == 2, "grid toolbar rows");
        require(MoonFlowerHudSettings.rowsForColumns(1) == 10, "vertical toolbar rows");
        require(!MoonFlowerHudSettings.equipmentToolbarExpanded(true),
                "full equipment window hides portrait equipment toolbar");
        require(MoonFlowerHudSettings.equipmentToolbarExpanded(false),
                "closed equipment window reveals portrait equipment toolbar");
        require(MoonFlowerHudSettings.chatContainsKeyword("Need flax soon", "rope, FLAX"),
                "case-insensitive comma-separated chat keyword matching");
        require(!MoonFlowerHudSettings.chatContainsKeyword("Need leather soon", "rope, flax"),
                "unmatched chat keyword remains normal");
        require("\u2726 [12:34] hello".equals(
                        MoonFlowerHudSettings.formatChatText("hello", "12:34", true, true)),
                "chat timestamp and keyword marker formatting");
        Coord centered = MoonFlowerHudSettings.centeredBottomPosition(Coord.of(1920, 1080), Coord.of(430, 460), 8);
        require(centered.equals(Coord.of(745, 612)), "bottom-center portrait dock position");
        Coord combatScreen = Coord.of(1920, 1080);
        Coord combatDeck = MoonFlowerCombatLayout.deckAnchor(combatScreen, Coord.of(0, -UI.scale(180)));
        require(combatDeck.x == 960 && combatDeck.y < 800,
                "custom combat deck defaults above the portrait dock");
        Area combatArea = MoonFlowerCombatLayout.actionDeckArea(combatScreen, Coord.of(0, -UI.scale(180)), 10);
        require(combatArea.ul.x >= 0 && combatArea.ul.y >= 0 && combatArea.br.x <= combatScreen.x && combatArea.br.y <= combatScreen.y,
                "combat ghost deck begins on-screen");
        Coord clampedCombat = MoonFlowerCombatLayout.clampOffset(combatScreen,
                MoonFlowerCombatLayout.statusPreviewArea(combatScreen, Coord.z), Coord.of(4000, 4000));
        Area clampedStatus = MoonFlowerCombatLayout.statusPreviewArea(combatScreen, clampedCombat);
        require(clampedStatus.br.x <= combatScreen.x && clampedStatus.br.y <= combatScreen.y,
                "combat ghost drag clamps to the visible screen");
        require(MoonFlowerHudAssets.complete(), "packaged portrait dock artwork");
        require(MoonFlowerUiAssets.complete(), "packaged window frame, panel, and chat settings artwork");
        Coord firstSocket = MoonFlowerHudAssets.scaledSocketCenter(0, Coord.of(430, 187));
        Coord lastSocket = MoonFlowerHudAssets.scaledSocketCenter(5, Coord.of(430, 187));
        require(firstSocket.y >= 83 && Math.abs((firstSocket.x + lastSocket.x) - 429) <= 2,
                "art-detected and mirrored socket centers");
        require(MoonFlowerVitalInfo.nearestRing(51, 46, 51, 56, 2) == MoonFlowerVitalInfo.STAMINA,
                "stamina ring hover selection");
        require(MoonFlowerVitalInfo.nearestRing(40, 46, 51, 56, 2) == -1,
                "non-ring hover rejection");
        String healthTip = MoonFlowerVitalInfo.healthTooltip(
                new IMeter.HealthState(78, 240, 268, 78 * 100.0 / 268, 240 * 100.0 / 268, "78 / 268"), 0);
        require(healthTip.contains("Soft health: 78 / 268"), "health tooltip current and maximum");
        require(healthTip.contains("Recoverable damage: 162"), "health tooltip recoverable damage");
        require(healthTip.contains("Wound damage: 28"), "health tooltip wound damage");
        require(healthTip.contains("Total missing: 190"), "health tooltip total missing");
        require("HP 100%  ST 75%  EN 50%".equals(
                FastText.format("HP %d%%  ST %d%%  EN %d%%", 100, 75, 50)),
                "literal percentage formatting");
        BufferedImage square = TexI.mkbuf(Coord.of(16, 16));
        Graphics2D graphics = square.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 16, 16);
        graphics.dispose();
        BufferedImage circle = Buff.circularIcon(square, 20);
        require(((circle.getRGB(0, 0) >>> 24) & 0xff) == 0,
                "circular buff icon transparent corner");
        require(((circle.getRGB(10, 10) >>> 24) & 0xff) > 240,
                "circular buff icon opaque center");
        System.out.println("MoonFlower HUD checks passed.");
    }

    private static void require(boolean condition, String description) {
        if(!condition)
            throw new AssertionError("MoonFlower HUD check failed: " + description);
    }
}
