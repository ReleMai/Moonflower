package haven;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** A movable portrait dock with scalable client-feature navigation. */
public class MoonFlowerPortraitHub extends Widget {
    private static final Color HEALTH = new Color(222, 69, 78, 255);
    private static final Color HARD_HEALTH = new Color(104, 48, 66, 255);
    private static final Color STAMINA = new Color(58, 176, 211, 255);
    private static final Color ENERGY = new Color(236, 194, 59, 255);
    private static final Color EMPTY = new Color(5, 13, 19, 230);
    private static final int VITAL_SEGMENTS = 96;
    private static final PUtils.Convolution ART_FILTER = new PUtils.Lanczos(3);
    private static final double REVEAL_SECONDS = 0.28;
    private static final double[] EXTRA_BUFF_ANGLES = {30, 47, 64, 81, 99, 116, 133, 150};

    private final GameUI gui;
    private final List<HudIconButton> mainButtons = new ArrayList<>();
    private final List<FeatureAction> featureActions = new ArrayList<>();
    private final Avaview avatar;
    private final DockOrnament ornament;
    private final VitalOverlay vitals;
    private final FeatureVine featureVine;
    private final BuffMoreButton buffMoreButton;
    private Bufflist buffs;
    private QuickSlotsWdg equipment;
    private Tex dockTexture;
    private UI.Grab dragging;
    private Coord dragOrigin;
    private Coord portraitOrigin = Coord.z;
    private int portraitSize;
    private int currentScale;
    private int dockY;
    private boolean positionLoaded;
    private boolean defaultAnchored;
    private boolean featuresExpanded;
    private boolean equipmentExpanded;
    private boolean buffsExpanded;
    private double featureReveal;
    private double equipmentReveal;
    private double buffReveal;

    public MoonFlowerPortraitHub(GameUI gui) {
        this.gui = gui;
        featuresExpanded = MoonFlowerHudSettings.featureVineExpanded();
        equipmentExpanded = false;
        featureReveal = featuresExpanded ? 1.0 : 0.0;
        equipmentReveal = 0.0;
        addFeatureActions();
        currentScale = MoonFlowerHudSettings.portraitScale();
        portraitSize = scaled(84);
        avatar = add(new Avaview(Coord.of(portraitSize, portraitSize), gui.plid, "avacam"), Coord.z);
        avatar.drawv = false;
        ornament = add(new DockOrnament(), Coord.z);
        vitals = add(new VitalOverlay(), Coord.z);
        featureVine = add(new FeatureVine(), Coord.z);
        buffMoreButton = add(new BuffMoreButton(), Coord.z);
        addMainButtons();
        applySettings();
        featureVine.show(featuresExpanded);
        buffMoreButton.hide();
    }

    private void addFeatureActions() {
        featureActions.add(new FeatureAction(6, "Cookbook", gui::isCookbookOpen, gui::toggleCookbook));
        featureActions.add(new FeatureAction(7, "Fishing Journal", gui::isFishingJournalOpen, gui::toggleFishingJournal));
        featureActions.add(new FeatureAction(8, "Fishing Helper", gui::isFishingHelperOpen, gui::toggleFishingHelper));
        featureActions.add(new FeatureAction(9, "Ring of Brodgar Wiki", gui::isWikiOpen, gui::toggleWiki));
    }

    private void addMainButtons() {
        mainButtons.add(addButton(0, "Inventory", gui::isInventoryWindowOpen, gui::toggleInventoryWindow));
        mainButtons.add(addButton(1, "Equipment & gear vine",
                gui::isEquipmentWindowOpen, this::toggleEquipment));
        mainButtons.add(addButton(2, "Character Sheet", gui::isCharacterWindowOpen, gui::toggleCharacterWindow));
        mainButtons.add(addButton(3, "Kith & Kin", gui::isKinWindowOpen, gui::toggleKinWindow));
        mainButtons.add(addButton(4, "Options", gui::isOptionsWindowOpen, gui::toggleOptionsWindow));
        mainButtons.add(addButton(5, "MoonFlower Features", () -> featuresExpanded,
                () -> setFeaturesExpanded(!featuresExpanded)));
    }

    private HudIconButton addButton(int icon, String tooltip, BooleanSupplier state, Runnable action) {
        HudIconButton button = new HudIconButton(icon, state, action);
        button.settip(tooltip);
        return add(button, Coord.z);
    }

    private void setFeaturesExpanded(boolean expanded) {
        if(featuresExpanded == expanded)
            return;
        featuresExpanded = expanded;
        Utils.setprefb(MoonFlowerHudSettings.FEATURE_VINE_EXPANDED, expanded);
        clearanims(FeatureRevealAnimation.class);
        featureVine.show();
        new FeatureRevealAnimation(expanded ? 1.0 : 0.0);
        featureVine.raise();
        for(HudIconButton button : mainButtons)
            button.raise();
    }

    private void toggleEquipment() {
        gui.toggleEquipmentWindow();
    }

    public void setEquipmentWindowOpen(boolean windowOpen) {
        boolean expanded = MoonFlowerHudSettings.equipmentToolbarExpanded(windowOpen);
        if(equipmentExpanded == expanded)
            return;
        equipmentExpanded = expanded;
        clearanims(EquipmentRevealAnimation.class);
        if(equipment != null)
            equipment.show();
        new EquipmentRevealAnimation(expanded ? 1.0 : 0.0);
    }

    private void setBuffsExpanded(boolean expanded) {
        if(buffsExpanded == expanded)
            return;
        buffsExpanded = expanded;
        clearanims(BuffRevealAnimation.class);
        new BuffRevealAnimation(expanded ? 1.0 : 0.0);
    }

    private int scaled(int value) {
        return UI.scale(MoonFlowerHudSettings.scaled(value, currentScale));
    }

    private Coord socketCenter(int index) {
        return MoonFlowerHudAssets.scaledSocketCenter(index, Coord.of(scaled(430), scaled(187))).add(0, dockY);
    }

    public void applySettings() {
        currentScale = MoonFlowerHudSettings.portraitScale();
        portraitSize = scaled(84);
        dockY = scaled(269);
        resize(scaled(430), scaled(540));

        portraitOrigin = Coord.of(scaled(173), dockY + scaled(44));
        avatar.resize(Coord.of(portraitSize, portraitSize));
        avatar.move(portraitOrigin);

        Coord ornamentSize = Coord.of(scaled(430), scaled(187));
        rebuildDockTexture(ornamentSize);
        ornament.resize(ornamentSize);
        ornament.move(Coord.of(0, dockY));
        vitals.resize(sz);
        featureVine.resize(sz);
        featureVine.move(Coord.z);

        int buttonSize = scaled(34);
        int iconSize = scaled(23);
        for(int i = 0; i < mainButtons.size(); i++) {
            HudIconButton button = mainButtons.get(i);
            Coord center = MoonFlowerHudAssets.scaledSocketCenter(i, ornamentSize).add(0, dockY);
            button.resize(buttonSize, buttonSize);
            button.move(center.sub(buttonSize / 2, buttonSize / 2));
            button.rebuildIcon(iconSize);
        }
        buffMoreButton.resize(scaled(30), scaled(30));
        buffMoreButton.move(portraitOrigin.add((portraitSize - buffMoreButton.sz.x) / 2,
                portraitSize + scaled(60)));
        featureVine.relayout();

        if(buffs != null)
            configureBuffs();
        layoutEquipment();
        positionLoaded = false;
        if(parent != null)
            parentResized(parent.sz);
    }

    private void rebuildDockTexture(Coord targetSize) {
        if(dockTexture != null)
            dockTexture.dispose();
        BufferedImage filtered = PUtils.convolvedown(MoonFlowerHudAssets.dockOrnament, targetSize, ART_FILTER);
        dockTexture = new TexI(filtered);
    }

    public void attachBuffs(Bufflist list) {
        if(list == null)
            return;
        if(list.parent != this)
            reparent(list, this);
        buffs = list;
        buffs.setManualLayout(true);
        buffs.show();
        configureBuffs();
        layoutBuffs();
    }

    public void detachBuffs(Widget classicParent, Coord classicPosition) {
        if(buffs == null)
            return;
        Bufflist list = buffs;
        buffs = null;
        buffsExpanded = false;
        buffReveal = 0;
        buffMoreButton.hide();
        list.setManualLayout(false);
        list.setDisplay(1.0, Bufflist.num);
        if(list.parent != classicParent)
            reparent(list, classicParent);
        list.move(classicPosition);
        list.show();
    }

    private static void reparent(Widget child, Widget newParent) {
        Widget oldParent = child.parent;
        if(oldParent == newParent)
            return;
        if(oldParent != null) {
            child.unlink();
            oldParent.childseq++;
        }
        child.parent = newParent;
        child.link();
        newParent.childseq++;
    }

    private void configureBuffs() {
        double buffScale = Math.max(0.72, Math.min(0.96, 0.82 * (currentScale / 100.0)));
        buffs.setDisplay(buffScale, Bufflist.num);
        buffs.setManualLayout(true);
    }

    public void attachEquipment(QuickSlotsWdg slots) {
        if(slots == null)
            return;
        if(slots.parent != this)
            reparent(slots, this);
        equipment = slots;
        equipment.setPortraitIntegrated(true);
        equipmentExpanded = MoonFlowerHudSettings.equipmentToolbarExpanded(gui.isEquipmentWindowOpen());
        equipmentReveal = equipmentExpanded ? 1.0 : 0.0;
        equipment.setPortraitRollout(equipmentReveal);
        equipment.show(equipmentReveal > 0.01);
        layoutEquipment();
    }

    public void detachEquipment(Widget classicParent, Coord classicPosition, boolean visible) {
        if(equipment == null)
            return;
        QuickSlotsWdg slots = equipment;
        equipment = null;
        slots.setPortraitIntegrated(false);
        if(slots.parent != classicParent)
            reparent(slots, classicParent);
        slots.move(classicPosition);
        slots.show(visible);
    }

    private void layoutEquipment() {
        if(equipment == null)
            return;
        equipment.setPortraitRollout(equipmentReveal);
        int targetY = dockY - scaled(30);
        int collapsedY = portraitOrigin.y + (portraitSize / 2);
        int y = collapsedY + (int)Math.round((targetY - collapsedY) * Utils.smoothstep(equipmentReveal));
        equipment.move(Coord.of((sz.x - equipment.sz.x) / 2, y));
        equipment.show(equipmentReveal > 0.01);
    }

    private void layoutBuffs() {
        if(buffs == null)
            return;
        buffs.move(Coord.z);
        buffs.resize(sz);
        List<Buff> icons = new ArrayList<>(buffs.children(Buff.class));
        Coord center = portraitOrigin.add(portraitSize / 2, portraitSize / 2);
        int primary = Math.min(4, icons.size());
        for(int i = 0; i < primary; i++) {
            Buff buff = icons.get(i);
            buff.setCircularDisplay(true);
            buff.show();
            double[] angles = (primary == 4) ? new double[] {40, 65, 115, 140} :
                    (primary == 3) ? new double[] {50, 90, 130} :
                    (primary == 2) ? new double[] {60, 120} : new double[] {90};
            double angle = Math.toRadians(angles[i]);
            int radius = (portraitSize / 2) + scaled(76);
            Coord target = center.add((int)Math.round(Math.cos(angle) * radius),
                    (int)Math.round(Math.sin(angle) * radius)).sub(buff.sz.div(2));
            buff.c = target;
        }
        int extra = Math.max(0, icons.size() - primary);
        Coord bud = buffMoreButton.c.add(buffMoreButton.sz.div(2));
        for(int i = primary; i < icons.size(); i++) {
            Buff buff = icons.get(i);
            buff.setCircularDisplay(true);
            int index = i - primary;
            double local = Utils.clip((buffReveal * 1.24) - (index * 0.045), 0.0, 1.0);
            buff.show(local > 0.01);
            double angle = Math.toRadians(EXTRA_BUFF_ANGLES[index % EXTRA_BUFF_ANGLES.length]);
            int radius = (portraitSize / 2) + scaled(105);
            Coord targetCenter = center.add((int)Math.round(Math.cos(angle) * radius),
                    (int)Math.round(Math.sin(angle) * radius));
            Coord animated = bud.add(targetCenter.sub(bud).mul(Utils.smoothstep(local)));
            buff.c = animated.sub(buff.sz.div(2));
        }
        buffMoreButton.extraCount = extra;
        buffMoreButton.show(extra > 0);
    }

    public void parentResized(Coord parentSize) {
        if(parentSize == null || parentSize.x <= 0 || parentSize.y <= 0)
            return;
        if(!positionLoaded) {
            Coord saved = Utils.getprefc(MoonFlowerHudSettings.hubPositionKey(gui.chrid), Coord.of(-1, -1));
            defaultAnchored = saved.x < 0 || saved.y < 0;
            c = defaultAnchored ? defaultPosition(parentSize) : saved;
            positionLoaded = true;
        }
        if(defaultAnchored)
            c = defaultPosition(parentSize);
        c = clampToParent(c, parentSize);
    }

    private Coord defaultPosition(Coord parentSize) {
        return MoonFlowerHudSettings.centeredBottomPosition(parentSize, sz, UI.scale(8));
    }

    private Coord clampToParent(Coord requested, Coord parentSize) {
        int x = Math.max(0, Math.min(requested.x, Math.max(0, parentSize.x - sz.x)));
        /* The upper portion of this widget is rollout space, while the visible
         * ornament and lower buff cradle end well before sz.y. Clamp against
         * that painted bound so the portrait can sit flush with the screen. */
        int paintedBottom = scaled(490);
        int y = Math.max(0, Math.min(requested.y, Math.max(0, parentSize.y - paintedBottom)));
        return Coord.of(x, y);
    }

    public void resetPosition() {
        Utils.setpref(MoonFlowerHudSettings.hubPositionKey(gui.chrid), null);
        positionLoaded = false;
        defaultAnchored = true;
        if(parent != null)
            parentResized(parent.sz);
    }

    @Override
    public void draw(GOut g) {
        if(!GameUI.showUI)
            return;
        layoutBuffs();
        layoutEquipment();
        if(buffs != null) {
            Coord center = portraitOrigin.add(portraitSize / 2, portraitSize / 2);
            Coord cradle = center.add(0, scaled(118));
            MoonFlowerHudTheme.drawCurvedVine(g, center.add(-scaled(72), scaled(53)), cradle, 1.0);
            MoonFlowerHudTheme.drawCurvedVine(g, center.add(scaled(72), scaled(53)), cradle, 1.0);
            MoonFlowerHudTheme.drawBlossom(g, cradle, scaled(4));
        }
        if(equipment != null && equipmentReveal > 0.01)
            MoonFlowerHudTheme.drawCurvedVine(g, socketCenter(1),
                    equipment.c.add(equipment.sz.x / 2, equipment.sz.y), equipmentReveal);
        if(MoonFlowerHudSettings.editMode())
            FastText.aprintfstroked(g, Coord.of(sz.x / 2, sz.y - scaled(2)), 0.5, 1,
                    "Drag empty space to move");
        super.draw(g);
    }

    private void drawRings(GOut g) {
        Coord center = portraitOrigin.add(portraitSize / 2, portraitSize / 2);
        int healthRadius = portraitSize / 2 + scaled(2);
        int staminaRadius = portraitSize / 2 + scaled(12);
        int energyRadius = portraitSize / 2 + scaled(22);
        int thickness = scaled(9);
        drawVitalTrack(g, center, healthRadius, thickness);
        drawFluidArcValue(g, center, healthRadius, hardHealth(), HARD_HEALTH, thickness, false);
        drawFluidArcValue(g, center, healthRadius, meter("hp"), HEALTH, thickness, true);
        drawVitalTrack(g, center, staminaRadius, thickness);
        drawFluidArcValue(g, center, staminaRadius, meter("stam"), STAMINA, thickness, true);
        drawVitalTrack(g, center, energyRadius, thickness);
        drawFluidArcValue(g, center, energyRadius, meter("nrj"), ENERGY, thickness, true);
        drawVitalNumbers(g, center, healthRadius, staminaRadius, energyRadius);
    }

    private void drawVitalTrack(GOut g, Coord center, int radius, int width) {
        drawArcSegments(g, center, radius, 1.0, colorWithAlpha(EMPTY, 105), width + scaled(5), VITAL_SEGMENTS);
        drawArcSegments(g, center, radius, 1.0, EMPTY, width, VITAL_SEGMENTS);
        drawArcSegments(g, center, radius - (width / 2), 1.0,
                colorWithAlpha(MoonFlowerHudTheme.GOLD_SOFT, 110), scaled(1), VITAL_SEGMENTS);
        drawArcSegments(g, center, radius + (width / 2), 1.0,
                colorWithAlpha(MoonFlowerHudTheme.GOLD_SOFT, 110), scaled(1), VITAL_SEGMENTS);
    }

    private void drawFluidArcValue(GOut g, Coord center, int radius, double value,
                                   Color color, int width, boolean endpoint) {
        value = Math.max(0, Math.min(1, value));
        if(value <= 0)
            return;
        drawArcSegments(g, center, radius, value, colorWithAlpha(color, 34),
                width + scaled(8), VITAL_SEGMENTS);
        drawArcSegments(g, center, radius, value, colorWithAlpha(color, 82),
                width + scaled(4), VITAL_SEGMENTS);
        drawArcSegments(g, center, radius, value, color, width, VITAL_SEGMENTS);
        drawArcSegments(g, center, radius - scaled(2), value,
                colorWithAlpha(MoonFlowerHudTheme.IVORY, 115), Math.max(1, scaled(2)), VITAL_SEGMENTS);
        if(endpoint)
            drawArcEndpoint(g, center, radius, value, color);
    }

    private void drawArcEndpoint(GOut g, Coord center, int radius, double value, Color color) {
        double angle = (-Math.PI / 2) + (Math.PI * 2 * value);
        Coord point = center.add((int)Math.round(Math.cos(angle) * radius),
                (int)Math.round(Math.sin(angle) * radius));
        g.chcolor(colorWithAlpha(color, 55));
        g.fellipse(point, Coord.of(scaled(6), scaled(6)));
        g.chcolor(color);
        g.fellipse(point, Coord.of(scaled(3), scaled(3)));
        g.chcolor(MoonFlowerHudTheme.IVORY);
        g.fellipse(point, Coord.of(Math.max(1, scaled(1)), Math.max(1, scaled(1))));
        g.chcolor();
    }

    private void drawArcSegments(GOut g, Coord center, int radius, double value, Color color, int width, int segments) {
        int count = (int)Math.round(segments * Math.max(0, Math.min(1, value)));
        g.chcolor(color);
        for(int i = 0; i < count; i++) {
            double a1 = (-Math.PI / 2) + ((Math.PI * 2 * i) / segments);
            double a2 = (-Math.PI / 2) + ((Math.PI * 2 * (i + 1)) / segments);
            Coord p1 = center.add((int)Math.round(Math.cos(a1) * radius), (int)Math.round(Math.sin(a1) * radius));
            Coord p2 = center.add((int)Math.round(Math.cos(a2) * radius), (int)Math.round(Math.sin(a2) * radius));
            g.line(p1, p2, width);
        }
        g.chcolor();
    }

    private void drawRibbons(GOut g) {
        int width = scaled(42);
        int height = scaled(15);
        int y = dockY + scaled(125);
        MoonFlowerHudTheme.drawCurvedVine(g, Coord.of(scaled(139), y + (height / 2)),
                Coord.of(scaled(289), y + (height / 2)), 1.0);
        drawRibbon(g, "H", scaled(147), y, width, height, meter("hp"), HEALTH);
        drawRibbon(g, "S", scaled(194), y, width, height, meter("stam"), STAMINA);
        drawRibbon(g, "E", scaled(241), y, width, height, meter("nrj"), ENERGY);
    }

    private void drawVitalNumbers(GOut g, Coord center, int healthRadius, int staminaRadius, int energyRadius) {
        if(!MoonFlowerHudSettings.showVitalNumbers())
            return;
        IMeter.HealthState health = IMeter.lastHealthState;
        String hp = (health == null) ? Integer.toString(percent(meter("hp"))) : (health.shp + "/" + health.mhp);
        drawRingNumber(g, center, healthRadius, Math.toRadians(210), hp, HEALTH);
        drawRingNumber(g, center, staminaRadius, Math.toRadians(270), percent(meter("stam")) + "%", STAMINA);
        drawRingNumber(g, center, energyRadius, Math.toRadians(330), percent(meter("nrj")) + "%", ENERGY);
    }

    private void drawRingNumber(GOut g, Coord center, int radius, double angle,
                                String value, Color color) {
        Coord badge = center.add((int)Math.round(Math.cos(angle) * radius),
                (int)Math.round(Math.sin(angle) * radius));
        int halfWidth = scaled(Math.max(13, ((value.length() * 5) + 8) / 2));
        int halfHeight = scaled(6);
        g.chcolor(colorWithAlpha(color, 62));
        g.fellipse(badge, Coord.of(halfWidth + scaled(3), halfHeight + scaled(3)));
        g.chcolor(colorWithAlpha(color, 220));
        g.fellipse(badge, Coord.of(halfWidth + scaled(1), halfHeight + scaled(1)));
        g.chcolor(MoonFlowerHudTheme.INK_DEEP);
        g.fellipse(badge, Coord.of(halfWidth, halfHeight));
        g.chcolor();
        FastText.aprintfstroked(g, badge, 0.5, 0.5, "%s", value);
    }

    private void drawRibbon(GOut g, String label, int x, int y, int width, int height, double value, Color color) {
        Coord origin = Coord.of(x, y);
        Coord size = Coord.of(width, height);
        g.chcolor(colorWithAlpha(color, 42));
        g.frect(origin.sub(scaled(3), scaled(3)), size.add(scaled(6), scaled(6)));
        g.chcolor(EMPTY);
        g.frect(origin, size);
        if("H".equals(label)) {
            g.chcolor(HARD_HEALTH);
            g.frect(origin, Coord.of((int)Math.round(width * hardHealth()), height));
        }
        g.chcolor(color);
        int fillWidth = (int)Math.round(width * value);
        g.frect(origin, Coord.of(fillWidth, height));
        if(fillWidth > scaled(4)) {
            g.chcolor(colorWithAlpha(MoonFlowerHudTheme.IVORY, 105));
            g.line(origin.add(scaled(2), scaled(2)),
                    Coord.of(x + fillWidth - scaled(2), y + scaled(2)), Math.max(1, scaled(1)));
        }
        g.chcolor(MoonFlowerHudTheme.GOLD_SOFT);
        g.rect(origin, size);
        g.chcolor();
        if(MoonFlowerHudSettings.showVitalNumbers())
            FastText.aprintfstroked(g, Coord.of(x + width / 2, y + height / 2), 0.5, 0.5,
                    "%s %d", label, percent(value));
    }

    private static Color colorWithAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(),
                Math.max(0, Math.min(255, alpha)));
    }

    private void drawSpeed(GOut g) {
        double speed = playerSpeed();
        if(speed <= 0)
            return;
        Coord areaOrigin = speedAreaOrigin();
        Coord areaSize = speedAreaSize();
        MoonFlowerHudTheme.drawPanel(g, areaOrigin, areaSize, 210);
        FastText.aprintfstroked(g, areaOrigin.add(areaSize.x / 2, areaSize.y / 2), 0.5, 0.5,
                "%.2f u/s", speed);
    }

    private Coord speedAreaOrigin() {
        return Coord.of(portraitOrigin.x + (portraitSize / 2) - scaled(31),
                portraitOrigin.y + portraitSize - scaled(15));
    }

    private Coord speedAreaSize() {
        return Coord.of(scaled(62), scaled(13));
    }

    private double playerSpeed() {
        if(ui == null || ui.sess == null)
            return 0;
        Gob player = ui.sess.glob.oc.getgob(gui.plid);
        return(player == null) ? 0 : Math.max(0, player.gobSpeed);
    }

    private String vitalTooltip(int vital) {
        if(vital == MoonFlowerVitalInfo.HEALTH)
            return MoonFlowerVitalInfo.healthTooltip(IMeter.lastHealthState, meter("hp"));
        if(vital == MoonFlowerVitalInfo.STAMINA)
            return MoonFlowerVitalInfo.percentageTooltip("Stamina", meter("stam"), "Available");
        return MoonFlowerVitalInfo.percentageTooltip("Energy", meter("nrj"), "Current");
    }

    private double hardHealth() {
        IMeter.HealthState health = IMeter.lastHealthState;
        if(health == null)
            return meter("hp");
        return Math.max(0, Math.min(1, health.hardPercentage / 100.0));
    }

    private double meter(String name) {
        IMeter.Meter meter = gui.getmeter(name, 0);
        return meter == null ? 0 : Math.max(0, Math.min(1, meter.a));
    }

    private int percent(double value) {
        return (int)Math.round(value * 100);
    }

    @Override
    public boolean mousedown(MouseDownEvent ev) {
        if((ev.b == 1 || ev.b == 2) && MoonFlowerHudSettings.editMode()) {
            if(dragging != null)
                dragging.remove();
            dragging = ui.grabmouse(this);
            dragOrigin = ev.c;
            return true;
        }
        return super.mousedown(ev);
    }

    @Override
    public void mousemove(MouseMoveEvent ev) {
        if(dragging != null) {
            c = c.add(ev.c).sub(dragOrigin);
            if(parent != null)
                c = clampToParent(c, parent.sz);
            return;
        }
        super.mousemove(ev);
    }

    @Override
    public boolean mouseup(MouseUpEvent ev) {
        if((ev.b == 1 || ev.b == 2) && dragging != null) {
            dragging.remove();
            dragging = null;
            defaultAnchored = false;
            Utils.setprefc(MoonFlowerHudSettings.hubPositionKey(gui.chrid), c);
            return true;
        }
        return super.mouseup(ev);
    }

    @Override
    public void destroy() {
        if(dockTexture != null)
            dockTexture.dispose();
        vitals.disposeTooltip();
        for(HudIconButton button : mainButtons)
            button.disposeIcon();
        featureVine.disposeTextures();
        super.destroy();
    }

    private class DockOrnament extends Widget {
        @Override
        public void draw(GOut g) {
            g.image(dockTexture, Coord.z);
        }
    }

    private class FeatureRevealAnimation extends NormAnim {
        private final double start = featureReveal;
        private final double target;

        FeatureRevealAnimation(double target) {
            super(REVEAL_SECONDS);
            this.target = target;
        }

        public void ntick(double a) {
            featureReveal = start + (Utils.smoothstep(a) * (target - start));
            featureVine.relayout();
            if(a == 1.0 && target == 0)
                featureVine.hide();
        }
    }

    private class EquipmentRevealAnimation extends NormAnim {
        private final double start = equipmentReveal;
        private final double target;

        EquipmentRevealAnimation(double target) {
            super(REVEAL_SECONDS);
            this.target = target;
        }

        public void ntick(double a) {
            equipmentReveal = start + (Utils.smoothstep(a) * (target - start));
            layoutEquipment();
            if(a == 1.0 && target == 0 && equipment != null)
                equipment.hide();
        }
    }

    private class BuffRevealAnimation extends NormAnim {
        private final double start = buffReveal;
        private final double target;

        BuffRevealAnimation(double target) {
            super(REVEAL_SECONDS);
            this.target = target;
        }

        public void ntick(double a) {
            buffReveal = start + (Utils.smoothstep(a) * (target - start));
            layoutBuffs();
        }
    }

    private class BuffMoreButton extends Widget {
        int extraCount;

        @Override
        public void draw(GOut g) {
            MoonFlowerHudTheme.drawCircularSlot(g, sz.div(2),
                    (Math.min(sz.x, sz.y) / 2) - UI.scale(1), buffsExpanded);
            FastText.aprintfstroked(g, sz.div(2), 0.5, 0.5,
                    buffsExpanded ? "-" : "+%d", extraCount);
        }

        @Override
        public Object tooltip(Coord c, Widget prev) {
            return buffsExpanded ? "Show only four effects" : "Show all active effects";
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            if(ev.b == 1 && ev.c.isect(Coord.z, sz)) {
                setBuffsExpanded(!buffsExpanded);
                return true;
            }
            return false;
        }
    }

    private class FeatureVine extends Widget {
        private final List<FeatureVineButton> entries = new ArrayList<>();

        FeatureVine() {
            for(FeatureAction action : featureActions)
                entries.add(add(new FeatureVineButton(action), Coord.z));
        }

        void relayout() {
            int width = scaled(166);
            int height = scaled(30);
            int x = scaled(250);
            int y = scaled(8);
            int gap = scaled(7);
            Coord root = socketCenter(5);
            for(int i = 0; i < entries.size(); i++) {
                FeatureVineButton entry = entries.get(i);
                double local = Utils.clip((featureReveal * 1.28) - (i * 0.075), 0.0, 1.0);
                entry.reveal = local;
                entry.resize(width, height);
                Coord target = Coord.of(x - (i * scaled(4)), y);
                Coord start = root.sub(width - scaled(14), height / 2);
                entry.move(start.add(target.sub(start).mul(Utils.smoothstep(local))));
                entry.rebuildIcon(scaled(20));
                entry.show(local > 0.01);
                y += height + gap;
            }
        }

        @Override
        public void draw(GOut g) {
            Coord socket = socketCenter(5);
            for(FeatureVineButton entry : entries)
                MoonFlowerHudTheme.drawCurvedVine(g, socket,
                        Coord.of(entry.c.x + entry.sz.x - scaled(14), entry.c.y + (entry.sz.y / 2)), entry.reveal);
            super.draw(g);
        }

        void disposeTextures() {
            for(FeatureVineButton entry : entries)
                entry.disposeTextures();
        }
    }

    private class FeatureVineButton extends Widget {
        private final FeatureAction action;
        private final Tex label;
        private Tex icon;
        private int iconSize;
        private boolean hover;
        private double reveal;

        FeatureVineButton(FeatureAction action) {
            this.action = action;
            this.label = Text.renderstroked(action.label, MoonFlowerHudTheme.IVORY, Color.BLACK).tex();
            settip(action.label);
        }

        void rebuildIcon(int size) {
            if(icon != null && iconSize == size)
                return;
            if(icon != null)
                icon.dispose();
            iconSize = size;
            icon = new TexI(PUtils.convolvedown(MoonFlowerHudAssets.buttonIcons[action.iconIndex],
                    Coord.of(size, size), ART_FILTER));
        }

        @Override
        public void draw(GOut g) {
            boolean selected = action.state.getAsBoolean();
            MoonFlowerHudTheme.drawLeafButton(g, Coord.z, sz, selected, hover);
            g.aimage(label, Coord.of(scaled(12), sz.y / 2), 0, 0.5);
            g.aimage(icon, Coord.of(sz.x - scaled(15), sz.y / 2), 0.5, 0.5);
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            if(ev.b == 1 && ev.c.isect(Coord.z, sz)) {
                action.action.run();
                return true;
            }
            return false;
        }

        @Override
        public boolean mousehover(MouseHoverEvent ev, boolean hovering) {
            hover = hovering;
            return false;
        }

        void disposeTextures() {
            if(icon != null)
                icon.dispose();
            label.dispose();
        }
    }

    private class VitalOverlay extends Widget {
        private String cachedTooltipText;
        private Text cachedTooltip;

        @Override
        public void draw(GOut g) {
            if(MoonFlowerHudSettings.vitalStyle() == MoonFlowerHudSettings.STYLE_RIBBONS)
                drawRibbons(g);
            else
                drawRings(g);
            drawSpeed(g);
        }

        @Override
        public Object tooltip(Coord c, Widget prev) {
            double speed = playerSpeed();
            if(speed > 0 && c.isect(speedAreaOrigin(), speedAreaSize()))
                return renderTooltip(MoonFlowerVitalInfo.speedTooltip(speed));

            int vital = -1;
            if(MoonFlowerHudSettings.vitalStyle() == MoonFlowerHudSettings.STYLE_RIBBONS) {
                int y = dockY + scaled(125);
                if(c.isect(Coord.of(scaled(147), y), Coord.of(scaled(42), scaled(15))))
                    vital = MoonFlowerVitalInfo.HEALTH;
                else if(c.isect(Coord.of(scaled(194), y), Coord.of(scaled(42), scaled(15))))
                    vital = MoonFlowerVitalInfo.STAMINA;
                else if(c.isect(Coord.of(scaled(241), y), Coord.of(scaled(42), scaled(15))))
                    vital = MoonFlowerVitalInfo.ENERGY;
            } else {
                Coord center = portraitOrigin.add(portraitSize / 2, portraitSize / 2);
                double distance = Math.hypot(c.x - center.x, c.y - center.y);
                vital = MoonFlowerVitalInfo.nearestRing(distance,
                        portraitSize / 2 + scaled(2), portraitSize / 2 + scaled(12),
                        portraitSize / 2 + scaled(22), scaled(5));
            }
            return(vital < 0) ? null : renderTooltip(vitalTooltip(vital));
        }

        private Text renderTooltip(String text) {
            if(!text.equals(cachedTooltipText)) {
                if(cachedTooltip != null)
                    cachedTooltip.dispose();
                cachedTooltipText = text;
                cachedTooltip = RichText.render(text, UI.scale(360));
            }
            return cachedTooltip;
        }

        void disposeTooltip() {
            if(cachedTooltip != null)
                cachedTooltip.dispose();
        }
    }

    private class HudIconButton extends Widget {
        private static final Color HOVER = new Color(112, 206, 213, 245);
        private static final Color ACTIVE = new Color(244, 197, 75, 255);
        private final int iconIndex;
        private final BooleanSupplier state;
        private final Runnable action;
        private Tex icon;
        private boolean hover;

        HudIconButton(int iconIndex, BooleanSupplier state, Runnable action) {
            this.iconIndex = iconIndex;
            this.state = state;
            this.action = action;
        }

        void rebuildIcon(int size) {
            if(icon != null)
                icon.dispose();
            icon = new TexI(PUtils.convolvedown(MoonFlowerHudAssets.buttonIcons[iconIndex],
                    Coord.of(size, size), ART_FILTER));
        }

        @Override
        public void draw(GOut g) {
            boolean selected = state != null && state.getAsBoolean();
            if(selected || hover)
                drawHalo(g, selected ? ACTIVE : HOVER, selected ? UI.scale(2) : UI.scale(1));
            g.aimage(icon, sz.div(2), 0.5, 0.5);
        }

        private void drawHalo(GOut g, Color color, int width) {
            g.chcolor(color);
            Coord center = sz.div(2);
            int radius = Math.max(1, (Math.min(sz.x, sz.y) / 2) - UI.scale(1));
            for(int i = 0; i < 32; i++) {
                double a1 = (Math.PI * 2 * i) / 32;
                double a2 = (Math.PI * 2 * (i + 1)) / 32;
                Coord p1 = center.add((int)Math.round(Math.cos(a1) * radius),
                        (int)Math.round(Math.sin(a1) * radius));
                Coord p2 = center.add((int)Math.round(Math.cos(a2) * radius),
                        (int)Math.round(Math.sin(a2) * radius));
                g.line(p1, p2, width);
            }
            g.chcolor();
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            if(ev.b == 1 && ev.c.isect(Coord.z, sz)) {
                action.run();
                return true;
            }
            return false;
        }

        @Override
        public void mousemove(MouseMoveEvent ev) {
            hover = ev.c.isect(Coord.z, sz);
        }

        @Override
        public boolean mousehover(MouseHoverEvent ev, boolean hovering) {
            hover = hovering;
            return false;
        }

        void disposeIcon() {
            if(icon != null)
                icon.dispose();
        }
    }

    private static class FeatureAction {
        final int iconIndex;
        final String label;
        final BooleanSupplier state;
        final Runnable action;

        FeatureAction(int iconIndex, String label, BooleanSupplier state, Runnable action) {
            this.iconIndex = iconIndex;
            this.label = label;
            this.state = state;
            this.action = action;
        }
    }
}
