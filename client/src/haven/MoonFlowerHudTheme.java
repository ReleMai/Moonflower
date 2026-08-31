package haven;

import java.awt.Color;

/** Shared drawing vocabulary for the custom in-game HUD surfaces. */
public final class MoonFlowerHudTheme {
    public static final Color INK = new Color(3, 12, 18, 224);
    public static final Color INK_DEEP = new Color(1, 7, 11, 238);
    public static final Color TEAL = new Color(24, 95, 105, 225);
    public static final Color TEAL_BRIGHT = new Color(73, 174, 178, 235);
    public static final Color GOLD = new Color(207, 164, 72, 245);
    public static final Color GOLD_SOFT = new Color(118, 88, 43, 225);
    public static final Color IVORY = new Color(239, 225, 185, 245);
    public static final Color RUBY = new Color(196, 55, 48, 245);

    private MoonFlowerHudTheme() {
    }

    public static boolean active() {
        return MoonFlowerHudSettings.enabled();
    }

    public static void drawPanel(GOut g, Coord origin, Coord size) {
        drawPanel(g, origin, size, INK.getAlpha());
    }

    public static void drawPanel(GOut g, Coord origin, Coord size, int alpha) {
        if(size.x <= 0 || size.y <= 0)
            return;
        drawWindowBackground(g, origin, size, alpha);
        int outerInset = windowBackgroundInset(size);
        Coord panelOrigin = origin.add(outerInset, outerInset);
        Coord panelSize = size.sub(outerInset * 2, outerInset * 2);
        if(panelSize.x <= 0 || panelSize.y <= 0)
            return;
        int cut = Math.min(UI.scale(4), Math.min(panelSize.x, panelSize.y) / 3);
        g.chcolor(withAlpha(INK_DEEP, Math.min(175, alpha)));
        g.frect(panelOrigin.add(cut, 0), Coord.of(Math.max(0, panelSize.x - (cut * 2)), panelSize.y));
        g.frect(panelOrigin.add(0, cut), Coord.of(panelSize.x, Math.max(0, panelSize.y - (cut * 2))));
        g.chcolor(withAlpha(TEAL, Math.min(210, alpha)));
        g.frect(panelOrigin.add(UI.scale(2), UI.scale(2)), panelSize.sub(UI.scale(4), UI.scale(4)));
        g.chcolor(withAlpha(INK, alpha));
        g.frect(panelOrigin.add(UI.scale(3), UI.scale(3)), panelSize.sub(UI.scale(6), UI.scale(6)));
        drawFrameOverlay(g, origin, size, false);
        g.chcolor();
    }

    public static void drawSlot(GOut g, Coord origin, Coord size, boolean occupied, boolean active) {
        drawPanel(g, origin, size, occupied ? 232 : 205);
        int inset = UI.scale(4);
        Coord inner = size.sub(inset * 2, inset * 2);
        if(inner.x > 0 && inner.y > 0) {
            g.chcolor(active ? new Color(44, 123, 124, 210) : new Color(2, 10, 15, 190));
            g.frect(origin.add(inset, inset), inner);
        }
        if(active) {
            g.chcolor(IVORY);
            g.line(origin.add(UI.scale(2), UI.scale(2)),
                    origin.add(size.x - UI.scale(3), UI.scale(2)), UI.scale(1));
        }
        g.chcolor();
    }

    public static void drawInventoryBackdrop(GOut g, Coord size) {
        drawWindowBackground(g, Coord.z, size, 245);
        int inset = UI.scale(4);
        drawCurvedVine(g, Coord.of(inset, size.y - inset), Coord.of(size.x - inset, size.y - inset), 1.0);
        drawCurvedVine(g, Coord.of(inset, inset), Coord.of(inset, size.y - inset), 1.0);
        drawCurvedVine(g, Coord.of(size.x - inset, inset), Coord.of(size.x - inset, size.y - inset), 1.0);
        drawBlossom(g, Coord.of(size.x - UI.scale(7), size.y - UI.scale(7)), UI.scale(3));
    }

    /** Engraved drawer tab sized to sit inside an inventory window's title rail. */
    public static void drawInventoryToolTab(GOut g, Coord origin, Coord size,
                                            boolean active, boolean hover, boolean pressed) {
        if(size.x <= 2 || size.y <= 2)
            return;
        g.chcolor(255, 255, 255, pressed ? 215 : 255);
        g.image(MoonFlowerUiAssets.inventoryToolsTab, origin, size);
        if(hover || active) {
            int inset = UI.scale(9);
            g.chcolor(active ? IVORY : GOLD);
            g.line(origin.add(inset, size.y - UI.scale(2)),
                    origin.add(size.x - inset, size.y - UI.scale(2)), Math.max(1, UI.scale(1)));
        }
        g.chcolor();
    }

    /** Generated portrait-matched frame with live content painted beneath it. */
    public static void drawInventoryToolsDrawer(GOut g, Coord size) {
        if(size.x <= 0 || size.y <= 0)
            return;
        /* These insets follow the transparent frame's actual carved opening. */
        int side = UI.scale(57);
        int top = UI.scale(68);
        int bottom = UI.scale(50);
        Coord bodyOrigin = Coord.of(side, top);
        Coord bodySize = Coord.of(Math.max(1, size.x - side * 2), Math.max(1, size.y - top - bottom));
        g.chcolor(255, 255, 255, 238);
        g.image(MoonFlowerUiAssets.panelTexture, bodyOrigin, bodySize);
        g.chcolor();
        g.image(MoonFlowerUiAssets.inventoryToolsFrame, Coord.z, size);
    }

    /** Directional living hinge connecting the inventory frame to its tools drawer. */
    public static void drawInventoryPanelHinge(GOut g, Coord size, boolean attachedLeft, double reveal) {
        reveal = Utils.clip(reveal, 0.0, 1.0);
        if(reveal <= 0 || size.x <= UI.scale(12))
            return;
        int y = UI.scale(18);
        Coord edge = Coord.of(attachedLeft ? 0 : size.x - 1, y);
        Coord inner = edge.add(attachedLeft ? UI.scale(31) : -UI.scale(31), UI.scale(7));
        drawCurvedVine(g, edge, inner, reveal);
        if(reveal > 0.45)
            drawBlossom(g, edge.add(attachedLeft ? UI.scale(7) : -UI.scale(7), UI.scale(2)), UI.scale(3));
        if(reveal > 0.78)
            drawBlossom(g, inner, UI.scale(3));
    }

    public static void drawInventorySlot(GOut g, Coord origin, Coord size, boolean masked) {
        int gap = Math.max(1, UI.scale(1));
        Coord slotSize = size.sub(gap, gap);
        g.chcolor(masked ? new Color(7, 15, 17, 245) : new Color(3, 17, 22, 238));
        g.frect(origin, slotSize);
        g.chcolor(masked ? GOLD_SOFT : new Color(31, 92, 98, 205));
        g.rect(origin, slotSize);
        if(!masked) {
            int corner = Math.min(UI.scale(6), slotSize.x / 4);
            g.chcolor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 150));
            g.line(origin.add(1, 1), origin.add(corner, 1), Math.max(1, UI.scale(1)));
            g.line(origin.add(1, 1), origin.add(1, corner), Math.max(1, UI.scale(1)));
            Coord stem = origin.add(slotSize.x - UI.scale(3), slotSize.y - UI.scale(3));
            g.chcolor(new Color(73, 174, 178, 145));
            g.line(stem, stem.add(-UI.scale(5), -UI.scale(3)), Math.max(1, UI.scale(1)));
            g.line(stem.add(-UI.scale(3), -UI.scale(2)), stem.add(-UI.scale(2), -UI.scale(6)), Math.max(1, UI.scale(1)));
        }
        g.chcolor();
    }

    public static void drawCombatStatusRail(GOut g, Coord origin, Coord size) {
        if(size.x <= 0 || size.y <= 0)
            return;
        drawWindowBackground(g, origin, size, 208);
        drawFrameOverlay(g, origin, size, false);
        Coord center = origin.add(size.div(2));
        drawCurvedVine(g, origin.add(UI.scale(10), size.y / 2), center.sub(UI.scale(28), 0), 1.0);
        drawCurvedVine(g, origin.add(size.x - UI.scale(10), size.y / 2), center.add(UI.scale(28), 0), 1.0);
        drawBlossom(g, center, UI.scale(4));
    }

    public static void drawCombatActionDeck(GOut g, Coord origin, Coord size) {
        if(size.x <= 0 || size.y <= 0)
            return;
        drawWindowBackground(g, origin, size, 220);
        drawWindowFrame(g, origin, size);
        Coord left = origin.add(UI.scale(14), UI.scale(10));
        Coord right = origin.add(size.x - UI.scale(14), UI.scale(10));
        drawCurvedVine(g, left, right, 1.0);
        drawBlossom(g, origin.add(size.x / 2, UI.scale(10)), UI.scale(4));
        int petalY = Math.max(UI.scale(18), size.y / 2);
        drawBlossom(g, origin.add(UI.scale(9), petalY), UI.scale(6));
        drawBlossom(g, origin.add(size.x - UI.scale(9), petalY), UI.scale(6));
        drawCurvedVine(g, origin.add(UI.scale(7), size.y - UI.scale(9)),
                origin.add(size.x / 2 - UI.scale(18), size.y - UI.scale(9)), 1.0);
        drawCurvedVine(g, origin.add(size.x - UI.scale(7), size.y - UI.scale(9)),
                origin.add(size.x / 2 + UI.scale(18), size.y - UI.scale(9)), 1.0);
        drawBlossom(g, origin.add(size.x / 2, size.y - UI.scale(9)), UI.scale(5));
    }

    public static void drawCombatActionSlot(GOut g, Coord origin, Coord size, boolean selected, boolean backup) {
        g.chcolor(INK_DEEP);
        g.frect(origin, size);
        Color border = selected ? TEAL_BRIGHT : (backup ? IVORY : GOLD_SOFT);
        g.chcolor(border);
        g.rect(origin, size);
        int corner = Math.min(UI.scale(7), Math.min(size.x, size.y) / 3);
        g.line(origin, origin.add(corner, 0), Math.max(1, UI.scale(2)));
        g.line(origin, origin.add(0, corner), Math.max(1, UI.scale(2)));
        g.line(origin.add(size.x - 1, size.y - 1), origin.add(size.x - 1 - corner, size.y - 1), Math.max(1, UI.scale(2)));
        g.line(origin.add(size.x - 1, size.y - 1), origin.add(size.x - 1, size.y - 1 - corner), Math.max(1, UI.scale(2)));
        if(selected)
            drawBlossom(g, origin.add(size.x - UI.scale(4), UI.scale(4)), UI.scale(3));
        else if(backup)
            drawBlossom(g, origin.add(UI.scale(4), size.y - UI.scale(4)), UI.scale(3));
        g.chcolor();
    }

    /** State-only highlight for the action wells already painted into the
     * portrait combat crown. It deliberately leaves their artwork visible. */
    public static void drawCombatActionSelection(GOut g, Coord origin, Coord size,
                                                  boolean selected, boolean backup) {
        if(!selected && !backup)
            return;
        Color border = selected ? TEAL_BRIGHT : IVORY;
        Coord center = origin.add(size.div(2));
        int radius = Math.max(1, Math.min(size.x, size.y) / 2);
        g.chcolor(new Color(border.getRed(), border.getGreen(), border.getBlue(), 235));
        int segments = 36;
        for(int ring = 0; ring < 2; ring++) {
            int rr = Math.max(1, radius - ring - UI.scale(1));
            for(int i = 0; i < segments; i++) {
                double a1 = (Math.PI * 2 * i) / segments;
                double a2 = (Math.PI * 2 * (i + 1)) / segments;
                Coord p1 = center.add((int)Math.round(Math.cos(a1) * rr), (int)Math.round(Math.sin(a1) * rr));
                Coord p2 = center.add((int)Math.round(Math.cos(a2) * rr), (int)Math.round(Math.sin(a2) * rr));
                g.line(p1, p2, Math.max(1, UI.scale(1)));
            }
        }
        Coord blossom = selected ? center.add(radius - UI.scale(3), -radius + UI.scale(3)) :
                center.add(-radius + UI.scale(3), radius - UI.scale(3));
        drawBlossom(g, blossom, UI.scale(3));
        g.chcolor();
    }

    public static void drawOpponentHealthPlate(GOut g, Coord origin, Coord size,
                                                Double fraction, String label) {
        if(size.x <= 0 || size.y <= 0)
            return;
        g.chcolor(new Color(3, 13, 17, 238));
        g.frect(origin, size);
        if(fraction != null) {
            double value = Math.max(0, Math.min(1, fraction));
            g.chcolor(new Color(116, 27, 37, 235));
            g.frect(origin.add(UI.scale(2), UI.scale(2)),
                    Coord.of((int)Math.round((size.x - UI.scale(4)) * value),
                            Math.max(1, size.y - UI.scale(4))));
            g.chcolor(new Color(231, 92, 91, 120));
            g.line(origin.add(UI.scale(3), UI.scale(3)),
                    origin.add(UI.scale(3) + (int)Math.round((size.x - UI.scale(6)) * value), UI.scale(3)),
                    Math.max(1, UI.scale(1)));
        }
        g.chcolor(GOLD_SOFT);
        g.rect(origin, size);
        g.chcolor();
        FastText.aprintfstroked(g, origin.add(size.x / 2, size.y / 2), 0.5, 0.5,
                "%s", fastTextLabel(label == null ? "Opponent health unavailable" : label));
    }

    static String fastTextLabel(String value) {
        StringBuilder safe = new StringBuilder(value.length());
        for(int i = 0; i < value.length(); i++) {
            char glyph = value.charAt(i);
            if(glyph < 256)
                safe.append(glyph);
            else if(glyph == '\u2022' || glyph == '\u00b7')
                safe.append('-');
            else
                safe.append('?');
        }
        return safe.toString();
    }

    public static void drawCombatRelationCard(GOut g, Coord size, boolean primary) {
        if(size.x <= 0 || size.y <= 0)
            return;
        drawWindowBackground(g, Coord.z, size, primary ? 232 : 214);
        drawFrameOverlay(g, Coord.z, size, primary);
        drawCurvedVine(g, Coord.of(UI.scale(8), size.y - UI.scale(7)),
                Coord.of(size.x - UI.scale(8), size.y - UI.scale(7)), 1.0);
    }

    public static void drawInventoryLockOverlay(GOut g, Coord origin, Coord size) {
        Coord inset = Coord.of(UI.scale(2), UI.scale(2));
        Coord inner = size.sub(inset.mul(2));
        g.chcolor(new Color(113, 79, 20, 105));
        g.frect(origin.add(inset), inner);
        g.chcolor(GOLD);
        g.rect(origin.add(inset), inner);
        g.chcolor();
    }

    public static void drawVineWindowOverlay(GOut g, Coord origin, Coord size) {
        drawWindowFrame(g, origin, size);
    }

    public static void drawWindowBackground(GOut g, Coord origin, Coord size, int alpha) {
        if(size.x <= 0 || size.y <= 0)
            return;
        int inset = windowBackgroundInset(size);
        Coord innerOrigin = origin.add(inset, inset);
        Coord innerSize = size.sub(inset * 2, inset * 2);
        if(innerSize.x <= 0 || innerSize.y <= 0)
            return;
        int cornerCut = Math.min(UI.scale(10), Math.min(innerSize.x, innerSize.y) / 4);
        g.chcolor(255, 255, 255, Math.max(0, Math.min(255, alpha)));
        if(cornerCut <= 0) {
            g.image(MoonFlowerUiAssets.panelTexture, innerOrigin, innerSize);
        } else {
            /* The generated frame's rail begins inside its transparent outer
             * ornament. Keep one continuous texture inside that rail, then
             * clip the four inner corners so no square fill can protrude past
             * the visible outline. */
            Coord topLeft = innerOrigin.add(cornerCut, 0);
            Coord topRight = innerOrigin.add(innerSize.x - cornerCut, cornerCut);
            Coord middleLeft = innerOrigin.add(0, cornerCut);
            Coord middleRight = innerOrigin.add(innerSize.x, innerSize.y - cornerCut);
            Coord bottomLeft = innerOrigin.add(cornerCut, innerSize.y - cornerCut);
            Coord bottomRight = innerOrigin.add(innerSize.x - cornerCut, innerSize.y);
            g.image(MoonFlowerUiAssets.panelTexture, innerOrigin, topLeft, topRight, innerSize);
            g.image(MoonFlowerUiAssets.panelTexture, innerOrigin, middleLeft, middleRight, innerSize);
            g.image(MoonFlowerUiAssets.panelTexture, innerOrigin, bottomLeft, bottomRight, innerSize);
        }
        g.chcolor();
    }

    static int windowBackgroundInset(Coord size) {
        return Math.min(UI.scale(6), Math.max(0, Math.min(size.x, size.y) / 8));
    }

    public static void drawCornerMenuDock(GOut g, Coord size, Coord gridOrigin) {
        if(size.x <= 0 || size.y <= 0)
            return;
        int gap = UI.scale(4);
        Coord bodyOrigin = Coord.of(Math.max(0, gridOrigin.x - gap), Math.max(0, gridOrigin.y - gap));
        Coord bodySize = size.sub(bodyOrigin);
        drawWindowBackground(g, bodyOrigin, bodySize, 232);
        drawFrameOverlay(g, bodyOrigin, bodySize, false);
        drawCurvedVine(g, bodyOrigin.add(UI.scale(8), UI.scale(7)),
                Coord.of(size.x - UI.scale(10), bodyOrigin.y + UI.scale(7)), 1.0);
        drawBlossom(g, bodyOrigin.add(UI.scale(2), UI.scale(2)), UI.scale(4));
    }

    public static void drawChatControlDock(GOut g, Coord size) {
        if(size.x <= 0 || size.y <= 0)
            return;
        drawWindowBackground(g, Coord.z, size, 224);
        drawFrameOverlay(g, Coord.z, size, false);
        int stemX = Math.max(UI.scale(8), size.x / 2);
        drawCurvedVine(g, Coord.of(stemX, UI.scale(9)),
                Coord.of(stemX, size.y - UI.scale(9)), 1.0);
        drawBlossom(g, Coord.of(stemX, size.y / 2), UI.scale(3));
    }

    public static void drawMenuGridSlot(GOut g, Coord origin, Coord size,
                                        boolean occupied, boolean pressed) {
        Coord slotSize = size.sub(1, 1);
        /* Keep the cell surface stable while the mouse is down. The previous
         * bright pressed fill, followed by MenuGrid's dark overlay, produced a
         * conspicuous blue flash on every page or action click. */
        g.chcolor(new Color(2, 13, 18, 232));
        g.frect(origin, slotSize);
        g.chcolor(occupied ? new Color(43, 128, 132, 220) : new Color(28, 73, 78, 185));
        g.rect(origin, slotSize);
        int corner = Math.min(UI.scale(5), Math.min(slotSize.x, slotSize.y) / 4);
        g.chcolor(occupied ? GOLD : GOLD_SOFT);
        g.line(origin.add(1, 1), origin.add(corner, 1), Math.max(1, UI.scale(1)));
        g.line(origin.add(1, 1), origin.add(1, corner), Math.max(1, UI.scale(1)));
        if(pressed) {
            int inset = Math.max(2, UI.scale(2));
            g.chcolor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 225));
            g.rect(origin.add(inset, inset), slotSize.sub(inset * 2, inset * 2));
        }
        g.chcolor();
    }

    public static void drawWindowFrame(GOut g, Coord origin, Coord size) {
        if(size.x < UI.scale(48) || size.y < UI.scale(48)) {
            drawFrameOverlay(g, origin, size, false);
            return;
        }
        int edge = Math.min(MoonFlowerUiAssets.windowFrame[0].sz().x, Math.min(size.x, size.y) / 3);
        int middleWidth = Math.max(1, size.x - (edge * 2));
        int middleHeight = Math.max(1, size.y - (edge * 2));
        Tex[] frame = MoonFlowerUiAssets.windowFrame;
        g.chcolor();
        g.image(frame[0], origin, Coord.of(edge, edge));
        g.image(frame[1], origin.add(edge, 0), Coord.of(middleWidth, edge));
        g.image(frame[2], origin.add(size.x - edge, 0), Coord.of(edge, edge));
        g.image(frame[3], origin.add(0, edge), Coord.of(edge, middleHeight));
        g.image(frame[5], origin.add(size.x - edge, edge), Coord.of(edge, middleHeight));
        g.image(frame[6], origin.add(0, size.y - edge), Coord.of(edge, edge));
        g.image(frame[7], origin.add(edge, size.y - edge), Coord.of(middleWidth, edge));
        g.image(frame[8], origin.add(size.x - edge, size.y - edge), Coord.of(edge, edge));
    }

    public static void drawFrameOverlay(GOut g, Coord origin, Coord size, boolean blossom) {
        if(size.x <= 1 || size.y <= 1)
            return;
        int w = Math.max(1, UI.scale(1));
        g.chcolor(GOLD_SOFT);
        g.line(origin, origin.add(size.x - 1, 0), w);
        g.line(origin, origin.add(0, size.y - 1), w);
        g.line(origin.add(0, size.y - 1), origin.add(size.x - 1, size.y - 1), w);
        g.line(origin.add(size.x - 1, 0), origin.add(size.x - 1, size.y - 1), w);
        int corner = Math.min(UI.scale(11), Math.min(size.x, size.y) / 3);
        g.chcolor(GOLD);
        g.line(origin, origin.add(corner, 0), w);
        g.line(origin, origin.add(0, corner), w);
        g.line(origin.add(size.x - 1, size.y - 1), origin.add(size.x - 1 - corner, size.y - 1), w);
        g.line(origin.add(size.x - 1, size.y - 1), origin.add(size.x - 1, size.y - 1 - corner), w);
        if(blossom)
            drawBlossom(g, origin.add(UI.scale(8), UI.scale(8)), UI.scale(4));
        g.chcolor();
    }

    public static void drawVine(GOut g, Coord from, Coord to) {
        Coord elbow = Coord.of(from.x, to.y);
        g.chcolor(GOLD_SOFT);
        g.line(from, elbow, Math.max(1, UI.scale(2)));
        g.line(elbow, to, Math.max(1, UI.scale(2)));
        g.chcolor(TEAL_BRIGHT);
        Coord leaf = elbow.add(to.x >= from.x ? UI.scale(4) : -UI.scale(4), UI.scale(4));
        g.line(elbow, leaf, Math.max(1, UI.scale(2)));
        g.chcolor();
    }

    public static void drawCurvedVine(GOut g, Coord from, Coord to, double reveal) {
        reveal = Utils.clip(reveal, 0.0, 1.0);
        if(reveal <= 0)
            return;
        Coord control = Coord.of((from.x + to.x) / 2 + UI.scale(18), (from.y + to.y) / 2);
        Coord previous = from;
        int segments = Math.max(2, (int)Math.ceil(18 * reveal));
        g.chcolor(GOLD_SOFT);
        for(int i = 1; i <= segments; i++) {
            double t = (i / 18.0);
            if(t > reveal)
                t = reveal;
            double u = 1.0 - t;
            Coord point = Coord.of((int)Math.round((u * u * from.x) + (2 * u * t * control.x) + (t * t * to.x)),
                    (int)Math.round((u * u * from.y) + (2 * u * t * control.y) + (t * t * to.y)));
            g.line(previous, point, Math.max(1, UI.scale(2)));
            previous = point;
        }
        if(reveal > 0.42) {
            Coord leaf = previous.add(to.x < from.x ? UI.scale(5) : -UI.scale(5), UI.scale(5));
            g.chcolor(TEAL_BRIGHT);
            g.line(previous, leaf, Math.max(1, UI.scale(3)));
        }
        g.chcolor();
    }

    public static void drawLeafButton(GOut g, Coord origin, Coord size, boolean active, boolean hover) {
        if(size.x <= 0 || size.y <= 0)
            return;
        int bulb = Math.min(size.y, UI.scale(30));
        g.chcolor(new Color(2, 12, 17, hover || active ? 242 : 218));
        g.fellipse(origin.add(size.x - (bulb / 2), size.y / 2), Coord.of(bulb / 2, bulb / 2));
        g.frect(origin.add(UI.scale(8), UI.scale(4)), Coord.of(size.x - (bulb / 2) - UI.scale(8), size.y - UI.scale(8)));
        g.chcolor(active ? TEAL_BRIGHT : GOLD_SOFT);
        g.line(origin.add(UI.scale(8), UI.scale(4)), origin.add(size.x - (bulb / 2), UI.scale(4)), UI.scale(1));
        g.line(origin.add(UI.scale(8), size.y - UI.scale(5)), origin.add(size.x - (bulb / 2), size.y - UI.scale(5)), UI.scale(1));
        g.line(origin.add(UI.scale(2), size.y / 2), origin.add(UI.scale(8), UI.scale(4)), UI.scale(1));
        g.line(origin.add(UI.scale(2), size.y / 2), origin.add(UI.scale(8), size.y - UI.scale(5)), UI.scale(1));
        g.chcolor();
    }

    public static void drawCircularSlot(GOut g, Coord center, int radius, boolean active) {
        g.chcolor(INK_DEEP);
        g.fellipse(center, Coord.of(radius, radius));
        g.chcolor(active ? TEAL_BRIGHT : GOLD_SOFT);
        int segments = 32;
        for(int i = 0; i < segments; i++) {
            double a1 = (Math.PI * 2 * i) / segments;
            double a2 = (Math.PI * 2 * (i + 1)) / segments;
            Coord p1 = center.add((int)Math.round(Math.cos(a1) * radius), (int)Math.round(Math.sin(a1) * radius));
            Coord p2 = center.add((int)Math.round(Math.cos(a2) * radius), (int)Math.round(Math.sin(a2) * radius));
            g.line(p1, p2, Math.max(1, UI.scale(2)));
        }
        g.chcolor();
    }

    public static void drawBlossom(GOut g, Coord center, int radius) {
        if(radius <= 0)
            return;
        g.chcolor(IVORY);
        for(int i = 0; i < 6; i++) {
            double angle = (Math.PI * 2 * i) / 6.0;
            Coord petal = center.add((int)Math.round(Math.cos(angle) * radius),
                    (int)Math.round(Math.sin(angle) * radius));
            g.line(center, petal, Math.max(1, UI.scale(2)));
        }
        g.chcolor(RUBY);
        g.frect(center.sub(UI.scale(1), UI.scale(1)), Coord.of(UI.scale(3), UI.scale(3)));
        g.chcolor();
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.max(0, Math.min(255, alpha)));
    }
}
