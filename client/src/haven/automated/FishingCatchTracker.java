package haven.automated;

import haven.Equipory;
import haven.GItem;
import haven.GameUI;
import haven.Gob;
import haven.WItem;
import haven.automated.helpers.FishingAtlas;
import haven.fishing.FishingJournalService;
import haven.fishing.FishingObservation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Records fish caught through either the native Fishing action or the helper. */
public final class FishingCatchTracker {
    private static final long POLL_INTERVAL_MS = 250;
    private static final long POSE_CORRELATION_MS = 7000;
    private static final long ITEM_INFO_TIMEOUT_MS = 8000;
    private static final long CHOICE_CONTEXT_TIMEOUT_MS = 180_000;
    private static final int WATER_SEARCH_RADIUS = 3;
    private static final double MAX_CAST_DISTANCE = 33.0;

    private final GameUI gui;
    private final FishingJournalService journal;
    private final FishingPoleInspector poleInspector = new FishingPoleInspector();
    private final Set<GItem> knownItems = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<PendingCatch> pending = new ArrayList<>();
    private long nextPollAt;
    private long lastFishingPoseAt;
    private volatile String latestChoiceRowsJson = "[]";
    private volatile long latestChoiceRowsAt;
    private volatile FishingEnvironment.Target latestTarget;
    private volatile FishingEquipment.Snapshot latestTackle;
    private volatile long latestAttemptAt;

    public FishingCatchTracker(GameUI gui, FishingJournalService journal) {
        this.gui = gui;
        this.journal = journal;
    }

    /** Supplies the server choice table captured by the visible fishing helper. */
    public void noteFishingChoices(String rowsJson) {
        latestChoiceRowsJson = rowsJson == null || rowsJson.isBlank() ? "[]" : rowsJson;
        latestChoiceRowsAt = System.currentTimeMillis();
    }

    /** Preserves the helper's exact cast tile and verified rig for catch correlation. */
    void noteFishingAttempt(FishingEnvironment.Target target, FishingEquipment.Snapshot tackle) {
        latestTarget = target;
        latestTackle = tackle;
        latestAttemptAt = System.currentTimeMillis();
    }

    public void tick() {
        long now = System.currentTimeMillis();
        FishingChoiceWindow.inspect(gui, gui.fishingBot, gui.fishingJournalWindow)
                .ifPresent(snapshot -> noteFishingChoices(snapshot.rowsJson));
        if(now < nextPollAt)
            return;
        nextPollAt = now + POLL_INTERVAL_MS;
        if(gui.maininv == null || gui.map == null || gui.map.player() == null)
            return;

        if(isFishingPose())
            lastFishingPoseAt = now;
        observeNewItems(now);
        resolvePending(now);
    }

    private void observeNewItems(long now) {
        boolean relatedToFishing = now - lastFishingPoseAt <= POSE_CORRELATION_MS;
        for(WItem item : FishingInventory.catchItems(gui)) {
            if(item == null || item.item == null || !knownItems.add(item.item))
                continue;
            FishingEquipment.ItemData data = FishingItemMetadata.describe(item);
            if(!relatedToFishing || (!data.displayName.isEmpty() &&
                    !FishingAtlas.isFish(data.displayName, data.resourceName)))
                continue;
            FishingObservation context = captureContext(now);
            if(context != null)
                pending.add(new PendingCatch(item.item, now, context));
        }
    }

    private void resolvePending(long now) {
        for(PendingCatch candidate : new ArrayList<>(pending)) {
            FishingEquipment.ItemData fish = FishingItemMetadata.describe(candidate.item);
            boolean fishItem = FishingAtlas.isFish(fish.displayName, fish.resourceName);
            if(!fishItem) {
                if(now - candidate.detectedAt > ITEM_INFO_TIMEOUT_MS)
                    pending.remove(candidate);
                continue;
            }
            if(fish.quality == null && now - candidate.detectedAt < 2500)
                continue;
            journal.record(candidate.context.copy()
                    .fish(fish.resourceName, fish.displayName, fish.quality)
                    .build());
            pending.remove(candidate);
        }
    }

    private FishingObservation captureContext(long now) {
        boolean helperAttempt = now - latestAttemptAt <= CHOICE_CONTEXT_TIMEOUT_MS;
        FishingEquipment.Snapshot tackle = helperAttempt ? latestTackle : currentTackle();
        FishingEnvironment.Target water = helperAttempt ? latestTarget :
                FishingEnvironment.findNearbyWater(gui, WATER_SEARCH_RADIUS, MAX_CAST_DISTANCE);
        if(tackle == null || water == null)
            return(null);
        String choices = now - latestChoiceRowsAt <= CHOICE_CONTEXT_TIMEOUT_MS ?
                latestChoiceRowsJson : "[]";
        return(FishingEnvironment.capture(gui, water, tackle, choices, now));
    }

    private FishingEquipment.Snapshot currentTackle() {
        Equipory equipory = gui.getequipory();
        if(equipory == null)
            return(null);
        for(int slot : new int[]{6, 7}) {
            WItem pole = equipory.slots[slot];
            if(pole == null || pole.item == null || FishingAtlas.classify(
                    FishingItemMetadata.name(pole), FishingItemMetadata.resource(pole)) != FishingAtlas.Part.POLE)
                continue;
            FishingPoleInspector.State state = poleInspector.inspect(pole);
            if(state.loading)
                return(null);
            FishingPoleInspector.Kind consumable = state.bait != null ?
                    FishingPoleInspector.Kind.BAIT : FishingPoleInspector.Kind.LURE;
            return(new FishingEquipment.Snapshot(FishingItemMetadata.describe(pole),
                    itemOrEmpty(state.line), itemOrEmpty(state.hook),
                    itemOrEmpty(state.consumable(consumable)),
                    consumable == FishingPoleInspector.Kind.BAIT ? "bait" : "lure", true));
        }
        return(null);
    }

    private static FishingEquipment.ItemData itemOrEmpty(FishingEquipment.ItemData item) {
        return(item == null ? FishingEquipment.ItemData.EMPTY : item);
    }

    private boolean isFishingPose() {
        Gob player = gui.map.player();
        if(player == null)
            return(false);
        Set<String> poses = player.getPoses();
        return(poses.contains("fishidle") || poses.contains("napp1"));
    }

    private static final class PendingCatch {
        final GItem item;
        final long detectedAt;
        final FishingObservation context;

        PendingCatch(GItem item, long detectedAt, FishingObservation context) {
            this.item = item;
            this.detectedAt = detectedAt;
            this.context = context;
        }
    }
}
