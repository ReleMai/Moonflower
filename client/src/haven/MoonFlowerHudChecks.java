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
        require(MoonFlowerHudSettings.equipmentToolbarExpanded(false, true),
                "character sheet leaves the portrait equipment toolbar visible");
        require(!MoonFlowerHudSettings.equipmentToolbarExpanded(true, true),
                "equipment window remains the only full view that hides the toolbar");
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
        require(MoonFlowerHudAssets.clockOrnament.getColorModel().hasAlpha() &&
                        MoonFlowerHudAssets.clockOrnament.getWidth() >
                                (MoonFlowerHudAssets.clockOrnament.getHeight() * 2),
                "inverted world-clock artwork has transparent wide-screen geometry");
        require(MoonFlowerHudAssets.movementIcons.length == 4,
                "four packaged movement-mode icons");
        require(MoonFlowerUiAssets.complete(), "packaged window frame, panel, and chat settings artwork");
        require(MoonFlowerHudTheme.windowBackgroundInset(UI.scale(300, 180)) == UI.scale(6),
                "window background stays inside the generated frame rail");
        require(MoonFlowerHudTheme.windowBackgroundInset(UI.scale(24, 24)) == UI.scale(3),
                "small themed panels retain a proportional inner inset");
        require("WOLF - HEALTH 75%".equals(
                        MoonFlowerHudTheme.fastTextLabel("WOLF \u2022 HEALTH 75%")),
                "opponent labels never pass Unicode glyphs into FastText");
        Coord dockSize = Coord.of(520, 288);
        Coord firstSocket = MoonFlowerHudAssets.scaledSocketCenter(0, dockSize);
        Coord lastSocket = MoonFlowerHudAssets.scaledSocketCenter(5, dockSize);
        require(firstSocket.y >= 80 && Math.abs((firstSocket.x + lastSocket.x) - 519) <= 3,
                "art-detected and mirrored socket centers");
        Coord portraitCenter = MoonFlowerHudAssets.scaledPortraitCenter(dockSize);
        require(Math.abs(portraitCenter.x - 260) <= 2 && portraitCenter.y > 90 && portraitCenter.y < 125,
                "portrait cutout comes from integrated artwork geometry");
        require(MoonFlowerHudAssets.buffSocketCenters.length == 4 &&
                        MoonFlowerHudAssets.equipmentSlotCenters.length == 6 &&
                        MoonFlowerHudAssets.movementSocketCenters.length == 4,
                "integrated buff, equipment, and movement socket counts");
        require(MoonFlowerHudAssets.scaledEquipmentSlotCenter(0, dockSize).x <
                        MoonFlowerHudAssets.scaledEquipmentSlotCenter(5, dockSize).x,
                "integrated equipment slots preserve painted left-to-right order");
        Coord combatCrownSize = dockSize;
        require(MoonFlowerHudAssets.scaledCombatActionCenter(0, combatCrownSize).x <
                        MoonFlowerHudAssets.scaledCombatActionCenter(5, combatCrownSize).x &&
                        MoonFlowerHudAssets.scaledCombatActionCenter(4, combatCrownSize).x <
                                MoonFlowerHudAssets.scaledCombatActionCenter(9, combatCrownSize).x,
                "transformed portrait owns five action wells on each shoulder");
        Coord playerRed = MoonFlowerHudAssets.scaledCombatOpeningCenter(
                "paginae/atk/cornered", false, combatCrownSize);
        Coord opponentRed = MoonFlowerHudAssets.scaledCombatOpeningCenter(
                "paginae/atk/cornered", true, combatCrownSize);
        require(playerRed != null && opponentRed != null && playerRed.x < opponentRed.x &&
                        MoonFlowerHudAssets.scaledCombatOpeningCenter(
                                "paginae/atk/offbalance", false, combatCrownSize) != null &&
                        MoonFlowerHudAssets.scaledCombatOpeningCenter(
                                "paginae/atk/dizzy", true, combatCrownSize) != null &&
                        MoonFlowerHudAssets.scaledCombatOpeningCenter(
                                "paginae/atk/reeling", true, combatCrownSize) != null,
                "transformed portrait exposes separate player and opponent opening groups");
        require(MoonFlowerHudAssets.scaledCombatDefenseCenter(false, combatCrownSize).x <
                        MoonFlowerHudAssets.scaledCombatDefenseCenter(true, combatCrownSize).x &&
                        MoonFlowerHudAssets.scaledCombatInitiativeCenter(false, combatCrownSize).x <
                                MoonFlowerHudAssets.scaledCombatInitiativeCenter(true, combatCrownSize).x,
                "transformed portrait owns mirrored defense and initiative sockets");
        Coord combatPortrait = MoonFlowerHudAssets.scaledCombatPortraitCenter(combatCrownSize);
        int protectedRadius = 70;
        for(int i = 0; i < 10; i++)
            require(Math.abs(MoonFlowerHudAssets.scaledCombatActionCenter(i, combatCrownSize).x -
                            combatPortrait.x) > protectedRadius,
                    "combat action well stays beside the portrait cutout");
        require(MoonFlowerHudAssets.scaledCombatHealthArea(combatCrownSize).br.y <
                        combatPortrait.y - protectedRadius + 8 &&
                        MoonFlowerHudAssets.scaledCombatCooldownCenter(combatCrownSize).y < combatPortrait.y,
                "health and cooldown remain in the shallow top seam");
        Coord leftUtility = MoonFlowerHudAssets.scaledCombatUtilityCenter(0, combatCrownSize);
        Coord rightUtility = MoonFlowerHudAssets.scaledCombatUtilityCenter(3, combatCrownSize);
        require(combatCrownSize.equals(dockSize) &&
                        Math.abs(combatPortrait.x - portraitCenter.x) <= 3 &&
                        Math.abs(combatPortrait.y - portraitCenter.y) <= 6,
                "combat state transforms the same portrait footprint and center");
        require(leftUtility.x < combatPortrait.x && rightUtility.x > combatPortrait.x &&
                        leftUtility.y >= 0 && rightUtility.y < combatCrownSize.y,
                "combat state retains mirrored on-screen utility rails");
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
        IMeter.HealthState damagedHealth = new IMeter.HealthState(
                78, 240, 268, 78 * 100.0 / 268, 240 * 100.0 / 268, "78 / 268");
        require(Math.abs(MoonFlowerVitalInfo.recoverableHealthFraction(damagedHealth) -
                        (162.0 / 268.0)) < 0.0001,
                "recoverable SHP damage is a distinct health-ring segment");
        require("HP 100%  ST 75%  EN 50%".equals(
                FastText.format("HP %d%%  ST %d%%  EN %d%%", 100, 75, 50)),
                "literal percentage formatting");
        require(MoonFlowerVitalInfo.starving(0.20) && !MoonFlowerVitalInfo.starving(0.21),
                "starvation threshold presentation");
        require(MoonFlowerVitalInfo.energyState(0.80) == MoonFlowerVitalInfo.EnergyState.HEALING &&
                        MoonFlowerVitalInfo.energyState(0.79) == MoonFlowerVitalInfo.EnergyState.BELOW_HEALING &&
                        MoonFlowerVitalInfo.energyState(0.20) == MoonFlowerVitalInfo.EnergyState.STARVING,
                "energy colors follow healing, below-healing, and starvation thresholds");
        require(MoonFlowerVitalInfo.percentageTooltip("Energy", 0.80, "Current").contains("State: Healing") &&
                        MoonFlowerVitalInfo.percentageTooltip("Energy", 0.50, "Current").contains("Below healing threshold"),
                "energy tooltip explains the current color state");
        require(MoonFlowerVitalInfo.starvationLabel(0.125).equals("STARVING · 13%"),
                "starvation label retains the live energy level");
        require(MoonFlowerVitalInfo.movementModeName(0).equals("Crawl") &&
                        MoonFlowerVitalInfo.movementModeName(3).equals("Sprint"),
                "movement selector mode labels");
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
