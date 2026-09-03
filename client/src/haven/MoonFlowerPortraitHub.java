package haven;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** A movable portrait dock with scalable client-feature navigation. */
public class MoonFlowerPortraitHub extends Widget {
    private static final Color HEALTH = new Color(222, 69, 78, 255);
    private static final Color RECOVERABLE_HEALTH = new Color(239, 158, 54, 255);
    private static final Color STAMINA = new Color(58, 176, 211, 255);
    private static final Color ENERGY_HEALING = new Color(83, 190, 101, 255);
    private static final Color ENERGY_LOW = new Color(236, 194, 59, 255);
    private static final Color ENERGY_STARVING = new Color(215, 58, 62, 255);
    private static final Color EMPTY = new Color(5, 13, 19, 230);
    private static final int VITAL_SEGMENTS = 96;
    private static final PUtils.Convolution ART_FILTER = new PUtils.Lanczos(3);
    private static final double REVEAL_SECONDS = 0.28;
    private static final double COMBAT_REVEAL_SECONDS = 0.42;
    private static final int DOCK_WIDTH = 520;
    private static final int DOCK_HEIGHT = 288;
    private static final int DOCK_TOP = 210;

    private final GameUI gui;
    private final List<HudIconButton> mainButtons = new ArrayList<>();
    private final List<FeatureAction> featureActions = new ArrayList<>();
    private final DockBacking backing;
    private final Avaview avatar;
    private final DockOrnament ornament;
    private final VitalOverlay vitals;
    private final FeatureVine featureVine;
    private final BuffMoreButton buffMoreButton;
    private final EquipmentScrollCover equipmentScrollCover;
    private Bufflist buffs;
    private QuickSlotsWdg equipment;
    private Tex dockTexture;
    private Tex combatCrownTexture;
    private Coord equipmentScrollOrigin = Coord.z;
    private Coord equipmentScrollSize = Coord.z;
    private final Tex[] movementIcons = new Tex[4];
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
    private double combatReveal;

    public MoonFlowerPortraitHub(GameUI gui) {
        this.gui = gui;
        featuresExpanded = MoonFlowerHudSettings.featureVineExpanded();
        equipmentExpanded = false;
        featureReveal = featuresExpanded ? 1.0 : 0.0;
        equipmentReveal = 0.0;
        addFeatureActions();
        currentScale = MoonFlowerHudSettings.portraitScale();
        portraitSize = scaled(104);
        backing = add(new DockBacking(), Coord.z);
        avatar = add(new Avaview(Coord.of(portraitSize, portraitSize), gui.plid, "avacam"), Coord.z);
        avatar.drawv = false;
        ornament = add(new DockOrnament(), Coord.z);
        vitals = add(new VitalOverlay(), Coord.z);
        featureVine = add(new FeatureVine(), Coord.z);
        buffMoreButton = add(new BuffMoreButton(), Coord.z);
        equipmentScrollCover = add(new EquipmentScrollCover(), Coord.z);
        addMainButtons();
        applySettings();
        featureVine.show(featuresExpanded);
        buffMoreButton.hide();
    }

    private void addFeatureActions() {
        featureActions.add(new FeatureAction(6, "Cookbook", gui::isCookbookOpen, gui::toggleCookbook));
        featureActions.add(new FeatureAction(7, "Fishing System", () ->
                gui.isFishingJournalOpen() || gui.isFishingHelperOpen(), gui::toggleFishingSystem));
        featureActions.add(new FeatureAction(8, "Botanical Wayfinder", gui::isForagingOpen, gui::toggleForaging));
        featureActions.add(new FeatureAction(9, "Ring of Brodgar Wiki", gui::isWikiOpen, gui::toggleWiki));
        featureActions.add(new FeatureAction(3, "World Activity Board", gui::isWorldActivityBoardOpen,
                gui::toggleWorldActivityBoard));
        featureActions.add(new FeatureAction(2, "Session Conservatory", gui::isSessionConservatoryOpen,
                gui::toggleSessionConservatory));
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
        return MoonFlowerHudAssets.scaledSocketCenter(index, ornamentSize()).add(0, dockY);
    }

    private Coord ornamentSize() {
        return Coord.of(scaled(DOCK_WIDTH), scaled(DOCK_HEIGHT));
    }

    public void applySettings() {
        currentScale = MoonFlowerHudSettings.portraitScale();
        portraitSize = scaled(104);
        dockY = scaled(DOCK_TOP);
        resize(scaled(DOCK_WIDTH), scaled(DOCK_TOP + DOCK_HEIGHT + 8));

        Coord ornamentSize = ornamentSize();
        Coord portraitCenter = MoonFlowerHudAssets.scaledPortraitCenter(ornamentSize).add(0, dockY);
        portraitOrigin = portraitCenter.sub(portraitSize / 2, portraitSize / 2);
        avatar.resize(Coord.of(portraitSize, portraitSize));
        avatar.move(portraitOrigin);

        rebuildDockTexture(ornamentSize);
        rebuildCombatCrownTexture(combatCrownSize());
        backing.resize(ornamentSize);
        backing.move(Coord.of(0, dockY));
        ornament.resize(ornamentSize);
        ornament.move(Coord.of(0, dockY));
        vitals.resize(sz);
        featureVine.resize(sz);
        featureVine.move(Coord.z);
        equipmentScrollCover.resize(sz);
        equipmentScrollCover.move(Coord.z);

        int iconSize = scaled(23);
        for(int i = 0; i < mainButtons.size(); i++) {
            HudIconButton button = mainButtons.get(i);
            button.rebuildIcon(iconSize);
        }
        layoutMainButtons();
        buffMoreButton.resize(scaled(30), scaled(30));
        Coord overflowCenter = MoonFlowerHudAssets.scaledBuffOverflowCenter(ornamentSize).add(0, dockY);
        buffMoreButton.move(overflowCenter.sub(buffMoreButton.sz.div(2)));
        featureVine.relayout();
        rebuildMovementIcons(scaled(16));

        if(buffs != null)
            configureBuffs();
        if(equipment != null)
            equipment.setPortraitSlotOffsets(equipmentSlotOffsets());
        layoutEquipment();
        positionLoaded = false;
        if(parent != null)
            parentResized(parent.sz);
    }

    private void rebuildDockTexture(Coord targetSize) {
        if(dockTexture != null)
            dockTexture.dispose();
        BufferedImage filtered = PUtils.convolvedown(MoonFlowerHudAssets.dockOrnament, targetSize, ART_FILTER);
        Coord slotSize = QuickSlotsWdg.slotSquareBg.sz();
        Coord first = MoonFlowerHudAssets.scaledEquipmentSlotCenter(0, targetSize);
        Coord last = MoonFlowerHudAssets.scaledEquipmentSlotCenter(
                MoonFlowerHudAssets.equipmentSlotCenters.length - 1, targetSize);
        int paddingX = scaled(14);
        int paddingY = scaled(14);
        int left = Math.max(0, first.x - (slotSize.x / 2) - paddingX);
        int top = Math.max(0, first.y - (slotSize.y / 2) - paddingY);
        int right = Math.min(targetSize.x, last.x + (slotSize.x / 2) + paddingX);
        equipmentScrollOrigin = Coord.of(left, top);
        equipmentScrollSize = Coord.of(Math.max(1, right - left), Math.max(1, targetSize.y - top));
        dockTexture = new TexI(filtered);
    }

    private void rebuildCombatCrownTexture(Coord targetSize) {
        if(combatCrownTexture != null)
            combatCrownTexture.dispose();
        combatCrownTexture = new TexI(PUtils.convolvedown(MoonFlowerHudAssets.combatCrown,
                targetSize, ART_FILTER));
    }

    private void rebuildMovementIcons(int size) {
        for(int i = 0; i < movementIcons.length; i++) {
            if(movementIcons[i] != null)
                movementIcons[i].dispose();
            movementIcons[i] = new TexI(PUtils.convolvedown(MoonFlowerHudAssets.movementIcons[i],
                    Coord.of(size, size), ART_FILTER));
        }
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
        double buffScale = Math.max(0.66, Math.min(0.84, 0.76 * (currentScale / 100.0)));
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
        equipment.setPortraitSlotOffsets(equipmentSlotOffsets());
        equipmentExpanded = MoonFlowerHudSettings.equipmentToolbarExpanded(gui.isEquipmentWindowOpen());
        equipmentReveal = equipmentExpanded ? 1.0 : 0.0;
        equipment.setPortraitRollout(equipmentReveal);
        equipment.show(equipmentReveal > 0.01);
        equipmentScrollCover.raise();
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
        Coord firstSlot = equipmentFirstSlotOrigin();
        equipment.setPortraitRollout(equipmentReveal);
        equipment.move(firstSlot.add(0, dockY));
        equipment.show(equipmentReveal > 0.01);
    }

    private Coord equipmentFirstSlotOrigin() {
        Coord center = MoonFlowerHudAssets.scaledEquipmentSlotCenter(0, ornamentSize());
        return center.sub(QuickSlotsWdg.slotSquareBg.sz().div(2));
    }

    private Coord[] equipmentSlotOffsets() {
        Coord first = equipmentFirstSlotOrigin();
        Coord half = QuickSlotsWdg.slotSquareBg.sz().div(2);
        Coord[] offsets = new Coord[MoonFlowerHudAssets.equipmentSlotCenters.length];
        for(int i = 0; i < offsets.length; i++) {
            Coord origin = MoonFlowerHudAssets.scaledEquipmentSlotCenter(i, ornamentSize()).sub(half);
            offsets[i] = origin.sub(first);
        }
        return offsets;
    }

    private void layoutBuffs() {
        if(buffs == null)
            return;
        buffs.move(Coord.z);
        buffs.resize(sz);
        List<Buff> icons = new ArrayList<>(buffs.children(Buff.class));
        int primary = Math.min(4, icons.size());
        for(int i = 0; i < primary; i++) {
            Buff buff = icons.get(i);
            buff.setCircularDisplay(true);
            buff.show();
            Coord normal = MoonFlowerHudAssets.scaledBuffSocketCenter(i, ornamentSize());
            Coord combat = MoonFlowerHudAssets.scaledCombatBuffCenter(i, ornamentSize());
            Coord center = transformedCenter(normal, combat).add(0, dockY);
            buff.c = center.sub(buff.sz.div(2));
        }
        int extra = Math.max(0, icons.size() - primary);
        Coord bud = buffMoreButton.c.add(buffMoreButton.sz.div(2));
        for(int i = primary; i < icons.size(); i++) {
            Buff buff = icons.get(i);
            buff.setCircularDisplay(true);
            int index = i - primary;
            double local = Utils.clip((buffReveal * 1.24) - (index * 0.045), 0.0, 1.0);
            buff.show(local > 0.01);
            int gap = scaled(35);
            int rowWidth = Math.max(0, (extra - 1) * gap);
            Coord targetCenter = Coord.of((sz.x - rowWidth) / 2 + (index * gap), dockY - scaled(18));
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
        return MoonFlowerHudSettings.centeredBottomPosition(parentSize,
                Coord.of(sz.x, paintedBottom()), UI.scale(8));
    }

    private int paintedBottom() {
        return dockY + ornamentSize().y;
    }

    /** Root-relative center shared by the portrait and its combat collar. */
    public Coord combatCrownAnchor() {
        return c.add(portraitOrigin.x + (portraitSize / 2),
                portraitOrigin.y + (portraitSize / 2));
    }

    public Coord combatCrownSize() {
        return ornamentSize();
    }

    public Area combatCrownArea() {
        return Area.sized(c.add(0, dockY), combatCrownSize());
    }

    /** The collar wraps beside the portrait, so its live wells use the full HUD
     * canvas instead of the old above-portrait clipping rectangle. */
    public GOut combatClip(GOut g) {
        return g.reclip(Coord.z, gui.sz);
    }

    private double effectiveCombatReveal() {
        if(MoonFlowerHudSettings.editMode() && gui.fs == null)
            return 1.0;
        return combatReveal;
    }

    public double combatContentReveal() {
        return Utils.smoothstep(Utils.clip((effectiveCombatReveal() - 0.34) / 0.66, 0.0, 1.0));
    }

    /** Fightsess paints only live values here. The combat ornament itself is a
     * sibling skin drawn by this widget below the portrait and utility buttons. */
    public void drawCombatCrown(GOut g) {
        int openingRadius = Math.max(1, combatOpeningDiameter() / 2);
        if(combatContentReveal() > 0.45) {
            drawCombatSideBadge(g, combatOpeningGroupCenter(false).add(0, openingRadius + scaled(10)), false);
            drawCombatSideBadge(g, combatOpeningGroupCenter(true).add(0, openingRadius + scaled(10)), true);
        }
    }

    private void drawCombatSideBadge(GOut g, Coord center, boolean opponent) {
        Coord size = Coord.of(scaled(58), scaled(15));
        Coord origin = center.sub(size.div(2));
        Color accent = opponent ? new Color(176, 42, 54, 245) : MoonFlowerHudTheme.TEAL_BRIGHT;
        g.chcolor(new Color(2, 12, 16, 235));
        g.frect(origin, size);
        g.chcolor(MoonFlowerHudTheme.GOLD_SOFT);
        g.rect(origin, size);
        g.chcolor(accent);
        g.frect(origin.add(scaled(2), scaled(2)), Coord.of(scaled(4), Math.max(1, size.y - scaled(4))));
        g.fellipse(origin.add(size.x - scaled(8), size.y / 2), Coord.of(scaled(3), scaled(3)));
        g.chcolor();
        FastText.aprintfstroked(g, center.sub(scaled(2), 0), 0.5, 0.5,
                "%s", opponent ? "ENEMY" : "PLAYER");
    }

    public Coord combatActionCenter(int index) {
        Area crown = combatCrownArea();
        return crown.ul.add(MoonFlowerHudAssets.scaledCombatActionCenter(index, crown.sz()));
    }

    public Coord combatOpeningCenter(String resourceName, boolean opponent) {
        Area crown = combatCrownArea();
        Coord local = MoonFlowerHudAssets.scaledCombatOpeningCenter(resourceName, opponent, crown.sz());
        return local == null ? null : crown.ul.add(local);
    }

    public Coord combatOpeningCenter(String resourceName) {
        return combatOpeningCenter(resourceName, true);
    }

    private Coord combatOpeningGroupCenter(boolean opponent) {
        Coord red = combatOpeningCenter("paginae/atk/cornered", opponent);
        Coord yellow = combatOpeningCenter("paginae/atk/reeling", opponent);
        return red.add(yellow).div(2);
    }

    public Area combatHealthArea() {
        Area crown = combatCrownArea();
        Area local = MoonFlowerHudAssets.scaledCombatHealthArea(crown.sz());
        return Area.sized(crown.ul.add(local.ul), local.sz());
    }

    public Coord combatMoveCenter(boolean opponent) {
        Area crown = combatCrownArea();
        return crown.ul.add(MoonFlowerHudAssets.scaledCombatMoveCenter(opponent, crown.sz()));
    }

    public Coord combatDefenseCenter(boolean opponent) {
        Area crown = combatCrownArea();
        return crown.ul.add(MoonFlowerHudAssets.scaledCombatDefenseCenter(opponent, crown.sz()));
    }

    public Coord combatInitiativeCenter(boolean opponent) {
        Area crown = combatCrownArea();
        return crown.ul.add(MoonFlowerHudAssets.scaledCombatInitiativeCenter(opponent, crown.sz()));
    }

    public Coord combatCooldownCenter() {
        Area crown = combatCrownArea();
        return crown.ul.add(MoonFlowerHudAssets.scaledCombatCooldownCenter(crown.sz()));
    }

    public int combatActionDiameter() {
        return MoonFlowerHudAssets.scaledCombatActionDiameter(combatCrownSize());
    }

    public int combatOpeningDiameter() {
        return MoonFlowerHudAssets.scaledCombatOpeningDiameter(combatCrownSize());
    }

    public int combatMoveDiameter() {
        return MoonFlowerHudAssets.scaledCombatMoveDiameter(combatCrownSize());
    }

    public int combatDefenseDiameter() {
        return MoonFlowerHudAssets.scaledCombatDefenseDiameter(combatCrownSize());
    }

    public int combatInitiativeDiameter() {
        return MoonFlowerHudAssets.scaledCombatInitiativeDiameter(combatCrownSize());
    }

    public int combatCooldownDiameter() {
        return MoonFlowerHudAssets.scaledCombatCooldownDiameter(combatCrownSize());
    }

    /** Keeps legacy deck consumers anchored to the compact portrait crescent. */
    public Coord combatDeckAnchor(int actionCount) {
        int available = Math.max(1, actionCount);
        int maximumColumns = OptWnd.singleRowCombatMovesCheckBox != null &&
                OptWnd.singleRowCombatMovesCheckBox.a ? 10 : 5;
        int columns = Math.min(available, maximumColumns);
        int rows = (available + columns - 1) / columns;
        int deckHeight = ((rows - 1) * Fightsess.actpitch2) + UI.scale(84);
        Coord crown = combatCrownAnchor();
        return Coord.of(crown.x, crown.y + UI.scale(56) - deckHeight);
    }

    /** Keeps target state immediately above the deck while retaining its own edit offset. */
    public Coord combatStatusCenter(int actionCount) {
        return combatCooldownCenter();
    }

    private Coord clampToParent(Coord requested, Coord parentSize) {
        int x = Math.max(0, Math.min(requested.x, Math.max(0, parentSize.x - sz.x)));
        /* The upper portion of this widget is rollout space, while the visible
         * ornament and lower buff cradle end well before sz.y. Clamp against
         * that painted bound so the portrait can sit flush with the screen. */
        int y = Math.max(0, Math.min(requested.y, Math.max(0, parentSize.y - paintedBottom())));
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
        layoutMainButtons();
        layoutBuffs();
        layoutEquipment();
        if(MoonFlowerHudSettings.editMode())
            FastText.aprintfstroked(g, Coord.of(sz.x / 2, paintedBottom() - scaled(2)), 0.5, 1,
                    "Drag empty space to move");
        super.draw(g);
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        /* Poll the native equipment view so every server/client opening path
         * drives the same cover, not only clicks on the portrait button. */
        setEquipmentWindowOpen(gui.isEquipmentWindowOpen());
        double target = gui.fs == null ? 0.0 : 1.0;
        if(MoonFlowerHudSettings.hudReducedMotion()) {
            combatReveal = target;
        } else {
            double step = Math.max(0.0, dt) / COMBAT_REVEAL_SECONDS;
            if(combatReveal < target)
                combatReveal = Math.min(target, combatReveal + step);
            else if(combatReveal > target)
                combatReveal = Math.max(target, combatReveal - step);
        }
        layoutMainButtons();
    }

    private void layoutMainButtons() {
        if(mainButtons.isEmpty())
            return;
        Coord size = ornamentSize();
        double reveal = Utils.smoothstep(effectiveCombatReveal());
        int buttonSize = scaled(34);
        for(int i = 0; i < mainButtons.size(); i++) {
            Coord normal = MoonFlowerHudAssets.scaledSocketCenter(i, size);
            Coord combat = MoonFlowerHudAssets.scaledCombatUtilityCenter(i, size);
            Coord center = transformedCenter(normal, combat, reveal).add(0, dockY);
            HudIconButton button = mainButtons.get(i);
            button.resize(buttonSize, buttonSize);
            button.move(center.sub(buttonSize / 2, buttonSize / 2));
        }
    }

    private Coord transformedCenter(Coord normal, Coord combat) {
        return transformedCenter(normal, combat, Utils.smoothstep(effectiveCombatReveal()));
    }

    private static Coord transformedCenter(Coord normal, Coord combat, double reveal) {
        return Coord.of((int)Math.round(normal.x + ((combat.x - normal.x) * reveal)),
                (int)Math.round(normal.y + ((combat.y - normal.y) * reveal)));
    }

    private void drawRings(GOut g) {
        Coord center = portraitOrigin.add(portraitSize / 2, portraitSize / 2);
        int healthRadius = healthRadius();
        int staminaRadius = staminaRadius();
        int energyRadius = energyRadius();
        int thickness = scaled(7);
        double softHealth = softHealth();
        double hardHealth = hardHealth();
        double energy = meter("nrj");
        drawVitalTrack(g, center, healthRadius, thickness);
        drawFluidArcRange(g, center, healthRadius, softHealth, hardHealth,
                RECOVERABLE_HEALTH, thickness, true);
        drawFluidArcValue(g, center, healthRadius, softHealth, HEALTH, thickness, true);
        drawVitalTrack(g, center, staminaRadius, thickness);
        drawFluidArcValue(g, center, staminaRadius, meter("stam"), STAMINA, thickness, true);
        drawVitalTrack(g, center, energyRadius, thickness);
        drawFluidArcValue(g, center, energyRadius, energy, energyColor(energy), thickness, true);
        drawVitalNumbers(g, center, healthRadius, staminaRadius, energyRadius);
    }

    private int healthRadius() {
        return portraitSize / 2 + scaled(31);
    }

    private int staminaRadius() {
        return portraitSize / 2 + scaled(9);
    }

    private int energyRadius() {
        return portraitSize / 2 + scaled(20);
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

    private void drawFluidArcRange(GOut g, Coord center, int radius, double start, double end,
                                   Color color, int width, boolean endpoint) {
        start = Math.max(0, Math.min(1, start));
        end = Math.max(start, Math.min(1, end));
        if(end <= start)
            return;
        drawArcSegmentRange(g, center, radius, start, end, colorWithAlpha(color, 40),
                width + scaled(8), VITAL_SEGMENTS);
        drawArcSegmentRange(g, center, radius, start, end, colorWithAlpha(color, 92),
                width + scaled(4), VITAL_SEGMENTS);
        drawArcSegmentRange(g, center, radius, start, end, color, width, VITAL_SEGMENTS);
        drawArcSegmentRange(g, center, radius - scaled(2), start, end,
                colorWithAlpha(MoonFlowerHudTheme.IVORY, 120), Math.max(1, scaled(2)), VITAL_SEGMENTS);
        if(endpoint)
            drawArcEndpoint(g, center, radius, end, color);
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
        drawArcSegmentRange(g, center, radius, 0, value, color, width, segments);
    }

    private void drawArcSegmentRange(GOut g, Coord center, int radius, double start, double end,
                                     Color color, int width, int segments) {
        int first = (int)Math.floor(segments * Math.max(0, Math.min(1, start)));
        int count = (int)Math.round(segments * Math.max(0, Math.min(1, end)));
        g.chcolor(color);
        for(int i = first; i < count; i++) {
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
        Coord health = ribbonOrigin(0);
        Coord energy = ribbonOrigin(2);
        MoonFlowerHudTheme.drawCurvedVine(g, health.add(-scaled(8), height / 2),
                energy.add(width + scaled(8), height / 2), 1.0);
        drawRibbon(g, "H", health.x, health.y, width, height, softHealth(), HEALTH);
        Coord stamina = ribbonOrigin(1);
        drawRibbon(g, "S", stamina.x, stamina.y, width, height, meter("stam"), STAMINA);
        double energyValue = meter("nrj");
        drawRibbon(g, "E", energy.x, energy.y, width, height, energyValue, energyColor(energyValue));
    }

    private Coord ribbonOrigin(int index) {
        int width = scaled(42);
        int gap = scaled(5);
        int total = (width * 3) + (gap * 2);
        return Coord.of((sz.x - total) / 2 + (index * (width + gap)),
                portraitOrigin.y + portraitSize + scaled(9));
    }

    private void drawVitalNumbers(GOut g, Coord center, int healthRadius, int staminaRadius, int energyRadius) {
        if(!MoonFlowerHudSettings.showVitalNumbers())
            return;
        IMeter.HealthState health = IMeter.lastHealthState;
        String hp = (health == null) ? Integer.toString(percent(meter("hp"))) : (health.shp + "/" + health.mhp);
        drawRingNumber(g, center, healthRadius, Math.toRadians(210), hp, HEALTH);
        drawRingNumber(g, center, staminaRadius, Math.toRadians(270), percent(meter("stam")) + "%", STAMINA);
        double energy = meter("nrj");
        drawRingNumber(g, center, energyRadius, Math.toRadians(330), percent(energy) + "%", energyColor(energy));
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
            int softWidth = (int)Math.round(width * softHealth());
            int hardWidth = (int)Math.round(width * hardHealth());
            if(hardWidth > softWidth) {
                g.chcolor(RECOVERABLE_HEALTH);
                g.frect(origin.add(softWidth, 0), Coord.of(hardWidth - softWidth, height));
            }
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
        Speedget control = gui.movementSpeedControl();
        if(control != null) {
            for(int mode = 0; mode < movementIcons.length; mode++) {
                Coord center = movementModeCenter(mode);
                Coord origin = center.sub(movementIcons[mode].sz().div(2));
                boolean selected = mode == control.cur;
                boolean available = mode <= control.max;
                if(selected) {
                    g.chcolor(colorWithAlpha(MoonFlowerHudTheme.GOLD_SOFT, 210));
                    g.fellipse(center, Coord.of(scaled(8), scaled(8)));
                    g.chcolor(new Color(8, 43, 51, 220));
                    g.fellipse(center, Coord.of(scaled(6), scaled(6)));
                }
                g.chcolor(selected ? MoonFlowerHudTheme.IVORY :
                        (available ? new Color(198, 218, 211, 230) : new Color(92, 92, 92, 185)));
                g.image(movementIcons[mode], origin);
                g.chcolor();
            }
        }

        IMeter.Meter energyMeter = gui.getmeter("nrj", 0);
        if(energyMeter != null && MoonFlowerVitalInfo.starving(energyMeter.a)) {
            double energy = Math.max(0, Math.min(1, energyMeter.a));
            Coord warningSize = Coord.of(scaled(92), scaled(13));
            Coord warningOrigin = Coord.of(portraitOrigin.x + ((portraitSize - warningSize.x) / 2),
                    movementModeCenter(0).y - warningSize.y - scaled(12));
            g.chcolor(new Color(96, 13, 22, 235));
            g.frect(warningOrigin, warningSize);
            g.chcolor(new Color(255, 114, 92, 255));
            g.rect(warningOrigin, warningSize);
            g.chcolor();
            FastText.aprintfstroked(g, warningOrigin.add(warningSize.x / 2, warningSize.y / 2),
                    0.5, 0.5, "%s", MoonFlowerVitalInfo.starvationLabel(energy));
        }
    }

    private Coord movementModeCenter(int mode) {
        Coord normal = MoonFlowerHudAssets.scaledMovementSocketCenter(mode, ornamentSize());
        Coord combat = MoonFlowerHudAssets.scaledCombatMovementCenter(mode, ornamentSize());
        return transformedCenter(normal, combat).add(0, dockY);
    }

    private int movementModeAt(Coord c) {
        int radius = scaled(12);
        for(int mode = 0; mode < movementIcons.length; mode++) {
            Coord center = movementModeCenter(mode);
            if(c.isect(center.sub(radius, radius), Coord.of(radius * 2, radius * 2)))
                return mode;
        }
        return -1;
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
        return MoonFlowerVitalInfo.hardHealthFraction(IMeter.lastHealthState, meter("hp"));
    }

    private double softHealth() {
        return MoonFlowerVitalInfo.softHealthFraction(IMeter.lastHealthState, meter("hp"));
    }

    private Color energyColor(double energy) {
        return switch(MoonFlowerVitalInfo.energyState(energy)) {
            case HEALING -> ENERGY_HEALING;
            case BELOW_HEALING -> ENERGY_LOW;
            case STARVING -> ENERGY_STARVING;
        };
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
        if(combatCrownTexture != null)
            combatCrownTexture.dispose();
        for(Tex icon : movementIcons) {
            if(icon != null)
                icon.dispose();
        }
        vitals.disposeTooltip();
        for(HudIconButton button : mainButtons)
            button.disposeIcon();
        featureVine.disposeTextures();
        super.destroy();
    }

    /** Dark wells sit behind the transparent openings so the generated asset is
     * the visible frame while live icons and items remain readable. */
    private class DockBacking extends Widget {
        @Override
        public void draw(GOut g) {
            g.chcolor(new Color(3, 15, 18, 238));
            Coord portrait = MoonFlowerHudAssets.scaledPortraitCenter(sz);
            g.fellipse(portrait, Coord.of(portraitSize / 2, portraitSize / 2));
            for(int i = 0; i < MoonFlowerHudAssets.socketCenters.length; i++)
                g.fellipse(MoonFlowerHudAssets.scaledSocketCenter(i, sz), Coord.of(scaled(18), scaled(18)));
            for(int i = 0; i < MoonFlowerHudAssets.buffSocketCenters.length; i++)
                g.fellipse(MoonFlowerHudAssets.scaledBuffSocketCenter(i, sz), Coord.of(scaled(14), scaled(14)));
            Coord slotSize = QuickSlotsWdg.slotSquareBg.sz();
            for(int i = 0; i < MoonFlowerHudAssets.equipmentSlotCenters.length; i++)
                g.frect(MoonFlowerHudAssets.scaledEquipmentSlotCenter(i, sz).sub(slotSize.div(2)), slotSize);
            for(int i = 0; i < MoonFlowerHudAssets.movementSocketCenters.length; i++)
                g.fellipse(MoonFlowerHudAssets.scaledMovementSocketCenter(i, sz), Coord.of(scaled(7), scaled(7)));
            g.fellipse(MoonFlowerHudAssets.scaledBuffOverflowCenter(sz), Coord.of(scaled(15), scaled(15)));
            g.chcolor();

            double reveal = Utils.smoothstep(effectiveCombatReveal());
            if(reveal > 0.01) {
                g.chcolor(new Color(3, 15, 18, (int)Math.round(238 * reveal)));
                for(int i = 0; i < 10; i++) {
                    int radius = Math.max(1, combatActionDiameter() / 2);
                    g.fellipse(MoonFlowerHudAssets.scaledCombatActionCenter(i, sz), Coord.of(radius, radius));
                }
                for(int i = 0; i < MoonFlowerHudAssets.buffSocketCenters.length; i++) {
                    int radius = scaled(14);
                    g.fellipse(MoonFlowerHudAssets.scaledCombatBuffCenter(i, sz), Coord.of(radius, radius));
                }
                for(int i = 0; i < MoonFlowerHudAssets.movementSocketCenters.length; i++) {
                    int radius = scaled(10);
                    g.fellipse(MoonFlowerHudAssets.scaledCombatMovementCenter(i, sz), Coord.of(radius, radius));
                }
                for(int i = 0; i < mainButtons.size(); i++)
                    g.fellipse(MoonFlowerHudAssets.scaledCombatUtilityCenter(i, sz), Coord.of(scaled(17), scaled(17)));
                g.chcolor();
            }
        }
    }

    private class DockOrnament extends Widget {
        @Override
        public void draw(GOut g) {
            double reveal = Utils.smoothstep(effectiveCombatReveal());
            if(reveal < 0.99) {
                g.chcolor(255, 255, 255, (int)Math.round(255 * (1.0 - reveal)));
                g.image(dockTexture, Coord.z);
            }
            if(reveal > 0.01) {
                g.chcolor(255, 255, 255, (int)Math.round(255 * reveal));
                g.image(combatCrownTexture, Coord.z);
            }
            g.chcolor();
        }
    }

    private class EquipmentScrollCover extends Widget {
        private double cover() {
            return Utils.smoothstep(1.0 - equipmentReveal);
        }

        @Override
        public void draw(GOut g) {
            double cover = cover();
            Coord origin = equipmentScrollOrigin.add(0, dockY);
            int maximumHeight = Math.max(scaled(12), equipmentScrollSize.y - scaled(3));
            int sheetHeight = (int)Math.round(maximumHeight * cover);
            drawEquipmentEnamelCover(g, origin, sheetHeight, cover);
        }

        @Override
        public boolean checkhit(Coord c) {
            double cover = cover();
            if(cover <= 0.01)
                return false;
            Coord origin = equipmentScrollOrigin.add(0, dockY);
            int height = (int)Math.round(equipmentScrollSize.y * cover) + scaled(8);
            return c.isect(origin.sub(scaled(6), scaled(6)),
                    Coord.of(equipmentScrollSize.x + scaled(12), height + scaled(12)));
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            return checkhit(ev.c);
        }
    }

    private void drawEquipmentEnamelCover(GOut g, Coord origin, int sheetHeight, double cover) {
        int width = equipmentScrollSize.x;
        int rollHeight = scaled(11);
        if(sheetHeight > 0) {
            Coord sheetOrigin = origin.add(scaled(3), 0);
            Coord sheetSize = Coord.of(Math.max(1, width - scaled(6)), sheetHeight);
            g.chcolor(MoonFlowerHudTheme.GOLD_SOFT);
            g.frect(sheetOrigin, sheetSize);
            g.chcolor(MoonFlowerHudTheme.TEAL);
            g.frect(sheetOrigin.add(scaled(2), scaled(1)),
                    Coord.of(Math.max(1, sheetSize.x - scaled(4)),
                            Math.max(1, sheetSize.y - scaled(1))));
            g.chcolor(MoonFlowerHudTheme.INK_DEEP);
            g.frect(sheetOrigin.add(scaled(5), scaled(2)),
                    Coord.of(Math.max(1, sheetSize.x - scaled(10)),
                            Math.max(1, sheetSize.y - scaled(3))));
            g.chcolor(new Color(MoonFlowerHudTheme.GOLD_SOFT.getRed(),
                    MoonFlowerHudTheme.GOLD_SOFT.getGreen(), MoonFlowerHudTheme.GOLD_SOFT.getBlue(), 115));
            int filigreeGap = Math.max(2, scaled(9));
            for(int y = sheetOrigin.y + filigreeGap; y < sheetOrigin.y + sheetHeight; y += filigreeGap)
                g.line(sheetOrigin.add(scaled(7), y - sheetOrigin.y),
                        Coord.of(sheetOrigin.x + sheetSize.x - scaled(7), y), Math.max(1, scaled(1)));
            if(cover > 0.72) {
                Coord seal = sheetOrigin.add(sheetSize.x / 2, Math.min(sheetHeight - scaled(8), scaled(34)));
                MoonFlowerHudTheme.drawBlossom(g, seal, scaled(5));
            }
        }

        int rollY = origin.y + sheetHeight;
        Coord rollOrigin = Coord.of(origin.x, rollY - (rollHeight / 2));
        Coord rollSize = Coord.of(width, rollHeight);
        g.chcolor(MoonFlowerHudTheme.GOLD_SOFT);
        g.frect(rollOrigin, rollSize);
        g.chcolor(MoonFlowerHudTheme.INK_DEEP);
        g.frect(rollOrigin.add(scaled(3), scaled(1)), rollSize.sub(scaled(6), scaled(2)));
        g.chcolor(MoonFlowerHudTheme.GOLD);
        g.line(rollOrigin.add(scaled(5), scaled(2)),
                rollOrigin.add(rollSize.x - scaled(5), scaled(2)), Math.max(1, scaled(1)));
        g.chcolor(MoonFlowerHudTheme.GOLD);
        g.fellipse(rollOrigin.add(0, rollHeight / 2), Coord.of(rollHeight / 2, rollHeight / 2));
        g.fellipse(rollOrigin.add(rollSize.x, rollHeight / 2), Coord.of(rollHeight / 2, rollHeight / 2));
        g.chcolor(MoonFlowerHudTheme.INK_DEEP);
        int inner = Math.max(1, (rollHeight / 2) - scaled(2));
        g.fellipse(rollOrigin.add(0, rollHeight / 2), Coord.of(inner, inner));
        g.fellipse(rollOrigin.add(rollSize.x, rollHeight / 2), Coord.of(inner, inner));
        g.chcolor();
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
            Speedget control = gui.movementSpeedControl();
            int movementMode = movementModeAt(c);
            if(control != null && movementMode >= 0) {
                int mode = movementMode;
                String availability = (mode <= control.max) ? "Available" : "Currently unavailable";
                return renderTooltip(String.format("Movement: %s\n%s\nActual speed: %.2f units/second",
                        MoonFlowerVitalInfo.movementModeName(mode), availability, playerSpeed()));
            }

            int vital = -1;
            if(MoonFlowerHudSettings.vitalStyle() == MoonFlowerHudSettings.STYLE_RIBBONS) {
                if(c.isect(ribbonOrigin(0), Coord.of(scaled(42), scaled(15))))
                    vital = MoonFlowerVitalInfo.HEALTH;
                else if(c.isect(ribbonOrigin(1), Coord.of(scaled(42), scaled(15))))
                    vital = MoonFlowerVitalInfo.STAMINA;
                else if(c.isect(ribbonOrigin(2), Coord.of(scaled(42), scaled(15))))
                    vital = MoonFlowerVitalInfo.ENERGY;
            } else {
                Coord center = portraitOrigin.add(portraitSize / 2, portraitSize / 2);
                double distance = Math.hypot(c.x - center.x, c.y - center.y);
                vital = MoonFlowerVitalInfo.nearestRing(distance, healthRadius(), staminaRadius(),
                        energyRadius(), scaled(5));
            }
            return(vital < 0) ? null : renderTooltip(vitalTooltip(vital));
        }

        @Override
        public boolean mousedown(MouseDownEvent ev) {
            Speedget control = gui.movementSpeedControl();
            int mode = movementModeAt(ev.c);
            if(ev.b == 1 && control != null && mode >= 0) {
                if(mode <= control.max)
                    control.set(mode);
                return true;
            }
            return super.mousedown(ev);
        }

        @Override
        public boolean mousewheel(MouseWheelEvent ev) {
            Speedget control = gui.movementSpeedControl();
            if(control != null && control.max >= 0 && movementModeAt(ev.c) >= 0) {
                control.set(Utils.clip(control.cur + ev.a, 0, control.max));
                return true;
            }
            return super.mousewheel(ev);
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
