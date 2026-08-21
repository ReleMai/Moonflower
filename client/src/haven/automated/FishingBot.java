package haven.automated;

import haven.Button;
import haven.CheckBox;
import haven.Coord;
import haven.GItem;
import haven.GameUI;
import haven.Gob;
import haven.Inventory;
import haven.Label;
import haven.Loading;
import haven.MenuGrid;
import haven.UI;
import haven.WItem;
import haven.Widget;
import haven.Window;
import haven.automated.helpers.FishingAtlas;
import haven.fishing.FishingJournalService;
import haven.fishing.FishingJournalWindow;
import haven.fishing.FishingObservation;
import haven.widgets.MultiSelectList;
import haven.widgets.SingleSelectList;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static haven.OCache.posres;

/** Visible fishing helper with safe tackle preparation and a local catch journal. */
public class FishingBot extends Window implements Runnable {
    private static final long LOOP_DELAY_MS = 250;
    private static final long ATTEMPT_TIMEOUT_MS = 180_000;
    private static final long POSE_CORRELATION_MS = 5_000;
    private static final int WATER_SEARCH_RADIUS = 3;
    private static final double MAX_CAST_DISTANCE = 33.0;

    private final GameUI gui;
    private final FishingEquipment equipment;
    private final FishingJournalService journal;
    private final FishingJournalWindow journalWindow;
    private final Set<GItem> seenInventoryItems = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<PendingCatch> pendingCatches = new ArrayList<>();

    private volatile boolean closed;
    private volatile boolean active;
    private volatile Thread runner;
    private Integer fishActionId;
    private State state = State.IDLE;
    private long stateChangedAt;
    private long attemptStartedAt;
    private long lastFishingPoseAt;
    private long cancelCursorAt;
    private int timeoutCount;
    private boolean cancelCursorPending;
    private boolean choiceSelected;
    private FishingEnvironment.Target waterTarget;
    private FishingEquipment.Snapshot tackle;
    private String choiceRowsJson = "[]";
    private volatile String desiredButtonText = "Start";
    private volatile String desiredStatus = "Highlighted inventory items are allowed for fishing.";
    private volatile FishingSelections selections = FishingSelections.empty();
    private long nextInventoryRefresh;
    private final EnumMap<FishingAtlas.Part, List<String>> availableChoices =
            new EnumMap<>(FishingAtlas.Part.class);
    private final EnumMap<FishingAtlas.Part, Set<String>> excludedChoices =
            new EnumMap<>(FishingAtlas.Part.class);

    private final Button startButton;
    private final CheckBox startCheckBox;
    private final Label statusLabel;
    private final Label baitLabel;
    private final SingleSelectList<String> fishingPoleChoice;
    private final MultiSelectList<String> hookChoice;
    private final MultiSelectList<String> fishLineChoice;
    private final MultiSelectList<String> baitChoice;
    private final MultiSelectList<String> lureChoice;

    public FishingBot(GameUI gui) {
        super(UI.scale(460, 225), "Fishing Helper");
        this.gui = gui;
        equipment = new FishingEquipment(gui);
        journal = gui.fishingJournalService;
        journalWindow = gui.fishingJournalWindow;

        addLabel("Choose Fishing Pole:", UI.scale(20, 0));
        addLabel("Choose Hook:", UI.scale(30, 73));
        addLabel("Choose Fishline:", UI.scale(155, 0));
        baitLabel = addLabel("Choose Bait:", UI.scale(295, 0));

        for(FishingAtlas.Part part : new FishingAtlas.Part[]{FishingAtlas.Part.LINE, FishingAtlas.Part.HOOK,
                FishingAtlas.Part.BAIT, FishingAtlas.Part.LURE})
            excludedChoices.put(part, new LinkedHashSet<>());

        fishingPoleChoice = add(new SingleSelectList<String>(UI.scale(120, 54), 18,
                Collections.emptyList()) {
            @Override
            protected void changed(String selection, int index) {
                updateFishingMode();
                selectionChanged();
            }
        }, UI.scale(10, 20));
        hookChoice = add(choiceList(FishingAtlas.Part.HOOK, UI.scale(130, 72)),
                UI.scale(10, 92));
        fishLineChoice = add(choiceList(FishingAtlas.Part.LINE, UI.scale(130, 144)),
                UI.scale(135, 20));
        baitChoice = add(choiceList(FishingAtlas.Part.BAIT, UI.scale(140, 144)),
                UI.scale(260, 20));
        lureChoice = add(choiceList(FishingAtlas.Part.LURE, UI.scale(140, 144)),
                UI.scale(260, 20));
        lureChoice.hide();

        startCheckBox = add(new CheckBox("Auto cast") {{ a = true; }}, UI.scale(70, 177));
        startButton = add(new Button(UI.scale(60), "Start") {
            @Override
            public void click() {
                if(active)
                    stopAutomation("Stopped by user.");
                else
                    startAutomation();
            }
        }, UI.scale(180, 175));
        add(new Button(UI.scale(75), "Journal") {
            @Override
            public void click() {
                gui.toggleFishingJournal();
            }
        }, UI.scale(250, 175));
        statusLabel = add(new Label("Highlighted inventory items are allowed for fishing."),
                UI.scale(10, 204));

        findFishActionId();
        refreshInventoryChoices();
    }

    private MultiSelectList<String> choiceList(FishingAtlas.Part part, Coord size) {
        return(new MultiSelectList<String>(size, 18, Collections.emptyList()) {
            @Override
            protected void changed(List<String> selected) {
                Set<String> excluded = excludedChoices.get(part);
                List<String> available = availableChoices.getOrDefault(part, Collections.emptyList());
                excluded.addAll(available);
                excluded.removeAll(selected);
                selectionChanged();
            }
        });
    }

    private void selectionChanged() {
        updateSelectionSnapshot();
        if(active)
            stopAutomation("Tackle selection changed.");
    }

    private Label addLabel(String text, Coord coordinate) {
        Label label = new Label(text) {{
            setstroked(Color.BLACK);
            setcolor(Color.LIGHT_GRAY);
        }};
        return(add(label, coordinate));
    }

    public synchronized void startAutomation() {
        if(closed || active)
            return;
        refreshInventoryChoices();
        if(selections.pole == null) {
            setStatus("No fishing pole is currently reachable in inventory, hands, or the equipped belt.");
            return;
        }
        if(runner == null || !runner.isAlive()) {
            Thread thread = new Thread(this, "FishingBot");
            runner = thread;
            gui.fishingThread = thread;
            thread.start();
        }
        active = true;
        state = State.PREPARING;
        stateChangedAt = System.currentTimeMillis();
        timeoutCount = 0;
        clearAttempt();
        baselineInventory();
        setStartButton("Stop");
        setStatus("Preparing selected fishing equipment...");
    }

    public void stopAutomation() {
        stopAutomation("Stopped.");
    }

    private synchronized void stopAutomation(String reason) {
        active = false;
        clearAttempt();
        state = State.IDLE;
        setStartButton("Start");
        cancelCurrentAction();
        Thread thread = runner;
        if(thread != null && thread != Thread.currentThread())
            thread.interrupt();
        boolean restored = false;
        boolean cursorStowed = false;
        try {
            cursorStowed = returnCursorToInventory();
            restored = equipment.restoreDisplacedHands();
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String cursorStatus = cursorStowed ? "" : " A cursor-held item still needs a free inventory space.";
        setStatus(reason + (restored ? " Hand items restored." : " Displaced items remain safe in inventory.") +
                cursorStatus);
    }

    @Override
    public void run() {
        runner = Thread.currentThread();
        while(!closed) {
            try {
                if(active)
                    tickAutomation();
                resolvePendingCatches();
                Thread.sleep(LOOP_DELAY_MS);
            } catch(InterruptedException ignored) {
                if(closed)
                    break;
            } catch(RuntimeException e) {
                new haven.Warning(e, "Fishing helper stopped after an unexpected client error")
                        .level(haven.Warning.ERROR).issue();
                try {
                    stopAutomation("Fishing helper stopped after an unexpected client error.");
                } catch(RuntimeException cleanupError) {
                    active = false;
                    state = State.IDLE;
                    setStartButton("Start");
                    setStatus("Fishing helper stopped; verify the cursor and hand slots before restarting.");
                    new haven.Warning(cleanupError, "Fishing helper cleanup failed")
                            .level(haven.Warning.ERROR).issue();
                }
            }
        }
        if(Thread.currentThread() == runner)
            runner = null;
    }

    private void tickAutomation() throws InterruptedException {
        long now = System.currentTimeMillis();
        if(!preconditionsReady())
            return;
        if(gui.fv != null && gui.fv.current != null) {
            deactivate("Fishing helper stopped because combat started.");
            return;
        }
        String vitalError = vitalError();
        if(vitalError != null) {
            deactivate(vitalError);
            return;
        }
        if(gui.maininv.isRoom(3, 2) == null) {
            deactivate("Fishing helper needs a free 3x2 inventory area for catches and safe equipment moves.");
            return;
        }

        boolean fishingPose = isFishingPose();
        if(fishingPose)
            lastFishingPoseAt = now;
        observeNewInventoryItems(now, fishingPose);
        if(!active)
            return;

        if(cancelCursorPending && now >= cancelCursorAt) {
            gui.map.wdgmsg("click", Coord.z, waterTarget.coordinate.floor(posres), 3, 0);
            cancelCursorPending = false;
        }

        if(state == State.ARMING) {
            long delay = usingLure() ? 1500 : 500;
            if(now - stateChangedAt >= delay)
                sendCast(now);
            return;
        }

        if(state == State.FISHING) {
            if(usingLure() && !choiceSelected)
                selectBestFishingChoice();
            if(now - attemptStartedAt > ATTEMPT_TIMEOUT_MS) {
                timeoutCount++;
                clearAttempt();
                state = State.PREPARING;
                if(timeoutCount >= 2)
                    deactivate("Fishing helper stopped after two fishing timeouts.");
                else
                    setStatus("Fishing attempt timed out; preparing one bounded retry.");
                return;
            }
            if(!fishingPose && now - attemptStartedAt > 5000 && now - lastFishingPoseAt > 3500 &&
                    !FishingChoiceWindow.present(gui, this, journalWindow)) {
                clearAttempt();
                state = State.PREPARING;
                setStatus("Fishing ended; checking for consumed or lost tackle.");
            }
            return;
        }

        FishingSelections selected = selections;
        FishingEquipment.Result result = equipment.prepare(selected.pole, selected.lines, selected.hooks,
                selected.lure ? selected.lures : selected.baits, selected.lure);
        if(!active)
            return;
        if(result.waiting) {
            setStatus(result.message);
            return;
        }
        if(!result.ready()) {
            deactivate("Fishing helper stopped: " + result.message);
            return;
        }
        tackle = result.snapshot;
        if(!startCheckBox.a) {
            active = false;
            state = State.IDLE;
            setStartButton("Start");
            setStatus("Fishing pole is equipped and prepared. Auto cast is disabled.");
            return;
        }
        FishingEnvironment.Target water = FishingEnvironment.findNearbyWater(gui, WATER_SEARCH_RADIUS,
                MAX_CAST_DISTANCE);
        if(water == null) {
            deactivate("Fishing helper found no nearby fishable water within " + WATER_SEARCH_RADIUS + " tiles.");
            return;
        }
        waterTarget = water;
        armFishing(now);
    }

    private boolean preconditionsReady() {
        if(gui.ui == null || gui.ui.sess == null || gui.map == null || gui.maininv == null ||
                gui.getequipory() == null || gui.map.player() == null) {
            deactivate("Fishing helper stopped because the player, map, inventory, or equipment UI disappeared.");
            return(false);
        }
        return(true);
    }

    private String vitalError() {
        try {
            double hp = gui.getmeters("hp").get(1).a;
            if(hp < 0.02)
                return("Fishing helper stopped because HP is critically low.");
            double energy = gui.getmeter("nrj", 0).a;
            if(energy < 0.25)
                return("Fishing helper stopped because energy is low.");
            return(null);
        } catch(RuntimeException e) {
            return("Fishing helper stopped because HP or energy could not be read safely.");
        }
    }

    private void armFishing(long now) {
        if(!active)
            return;
        if(fishActionId == null)
            findFishActionId();
        if(fishActionId == null) {
            deactivate("Fishing action is not currently available. Learn Fishing or reopen the action menu.");
            return;
        }
        gui.menu.wdgmsg("use", fishActionId, 0);
        state = State.ARMING;
        stateChangedAt = now;
        setStatus("Fishing action selected; waiting for its cast cursor.");
    }

    private void sendCast(long now) {
        if(!active)
            return;
        if(waterTarget == null || gui.map == null) {
            deactivate("Fishing target disappeared before casting.");
            return;
        }
        baselineInventory();
        gui.map.wdgmsg("click", Coord.z, waterTarget.coordinate.floor(posres), 1, 0);
        state = State.FISHING;
        attemptStartedAt = now;
        lastFishingPoseAt = 0;
        choiceSelected = false;
        choiceRowsJson = "[]";
        cancelCursorPending = true;
        cancelCursorAt = now + 500;
        setStatus("Fishing at nearby water; new fish will be journaled as candidate catches.");
    }

    private void observeNewInventoryItems(long now, boolean fishingPose) {
        if(!active)
            return;
        FishingEnvironment.Target observedTarget = waterTarget;
        FishingEquipment.Snapshot observedTackle = tackle;
        String observedChoices = choiceRowsJson;
        for(WItem item : FishingInventory.catchItems(gui)) {
            if(item == null || item.item == null || !seenInventoryItems.add(item.item))
                continue;
            if(state != State.FISHING || observedTarget == null || observedTackle == null)
                continue;
            boolean correlated = fishingPose || now - lastFishingPoseAt <= POSE_CORRELATION_MS;
            if(correlated)
                pendingCatches.add(new PendingCatch(item.item, now,
                        FishingEnvironment.capture(gui, observedTarget, observedTackle, observedChoices, now)));
        }
    }

    private void resolvePendingCatches() {
        long now = System.currentTimeMillis();
        for(PendingCatch pending : new ArrayList<>(pendingCatches)) {
            FishingEquipment.ItemData fish = FishingEquipment.describe(pending.item);
            boolean fishItem = FishingAtlas.isFish(fish.displayName, fish.resourceName);
            boolean resolvedNonFish = !fish.displayName.isEmpty() && !fishItem;
            if(resolvedNonFish || now - pending.detectedAt > 5000 && !fishItem) {
                pendingCatches.remove(pending);
                continue;
            }
            if(!fishItem)
                continue;
            if(fish.quality == null && now - pending.detectedAt < 3000)
                continue;
            FishingObservation observation = pending.base.copy()
                    .fish(fish.resourceName, fish.displayName, fish.quality)
                    .build();
            journal.record(observation);
            pendingCatches.remove(pending);
            setStatus("Saved candidate catch: " +
                    (fish.displayName.isEmpty() ? fish.resourceName : fish.displayName) + ".");
        }
    }

    private void selectBestFishingChoice() {
        if(!active)
            return;
        FishingChoiceWindow.selectBest(gui, this, journalWindow).ifPresent(selection -> {
            choiceRowsJson = selection.rowsJson;
            choiceSelected = true;
            setStatus("Aiming for " + selection.choice.fishName + " at " +
                    selection.choice.finalPercent + "%.");
        });
    }

    private void findFishActionId() {
        try {
            if(gui.menu == null || gui.menu.paginae == null)
                return;
            for(MenuGrid.Pagina page : gui.menu.paginae) {
                try {
                    if(page.res != null && page.res.get() != null &&
                            "paginae/act/fish".equals(page.res.get().name) && page.id instanceof Integer) {
                        fishActionId = (Integer)page.id;
                        return;
                    }
                } catch(Loading ignored) {
                }
            }
        } catch(RuntimeException e) {
            setStatus("Could not resolve the Fishing action yet.");
        }
    }

    private boolean usingLure() {
        return(selections.lure);
    }

    private boolean isFishingPose() {
        Gob player = gui.map == null ? null : gui.map.player();
        if(player == null)
            return(false);
        Set<String> poses = player.getPoses();
        return(poses.contains("fishidle") || poses.contains("napp1"));
    }

    private void baselineInventory() {
        seenInventoryItems.clear();
        if(gui.maininv == null)
            return;
        for(WItem item : FishingInventory.catchItems(gui)) {
            if(item != null && item.item != null)
                seenInventoryItems.add(item.item);
        }
    }

    private void deactivate(String message) {
        stopAutomation(message);
        gui.msg(message, Color.WHITE);
    }

    private void clearAttempt() {
        attemptStartedAt = 0;
        lastFishingPoseAt = 0;
        cancelCursorPending = false;
        choiceSelected = false;
        waterTarget = null;
        tackle = null;
        choiceRowsJson = "[]";
    }

    private void cancelCurrentAction() {
        try {
            if(gui.map != null && gui.map.player() != null)
                gui.map.wdgmsg("click", Coord.z, gui.map.player().rc.floor(posres), 1, 0);
            if(gui.map != null && gui.map.pfthread != null)
                gui.map.pfthread.interrupt();
        } catch(RuntimeException ignored) {
        }
    }

    private boolean returnCursorToInventory() throws InterruptedException {
        if(gui.vhand == null || gui.maininv == null)
            return(gui.vhand == null);
        WItem cursor = gui.vhand;
        int width = Math.max(1, (cursor.sz.x + Inventory.sqsz.x - 1) / Inventory.sqsz.x);
        int height = Math.max(1, (cursor.sz.y + Inventory.sqsz.y - 1) / Inventory.sqsz.y);
        Coord room = gui.maininv.isRoom(width, height);
        if(room == null)
            return(false);
        gui.maininv.wdgmsg("drop", room);
        long deadline = System.currentTimeMillis() + 2000;
        while(gui.vhand != null && System.currentTimeMillis() < deadline)
            Thread.sleep(50);
        return(gui.vhand == null);
    }

    private void setStatus(String message) {
        desiredStatus = message == null ? "" : message;
    }

    private void setStartButton(String text) {
        desiredButtonText = text;
    }

    @Override
    public void tick(double dt) {
        super.tick(dt);
        if(System.currentTimeMillis() >= nextInventoryRefresh) {
            refreshInventoryChoices();
            nextInventoryRefresh = System.currentTimeMillis() + 750;
        }
        if(!Objects.equals(startButton.text.text, desiredButtonText))
            startButton.change(desiredButtonText);
        if(!Objects.equals(statusLabel.texts, desiredStatus))
            statusLabel.settext(desiredStatus);
    }

    @Override
    public void wdgmsg(Widget sender, String message, Object... args) {
        if(sender == this && Objects.equals(message, "close")) {
            stop();
        } else {
            super.wdgmsg(sender, message, args);
        }
    }

    public synchronized void stop() {
        if(closed)
            return;
        stopAutomation("Fishing helper closed.");
        closed = true;
        Thread thread = runner;
        if(thread != null)
            thread.interrupt();
        haven.Utils.setprefc("wndc-fishingBotWindow", c);
        gui.fishingBot = null;
        gui.fishingThread = null;
        reqdestroy();
    }

    @Override
    public void destroy() {
        if(!closed) {
            active = false;
            closed = true;
            Thread thread = runner;
            if(thread != null)
                thread.interrupt();
            gui.fishingBot = null;
            gui.fishingThread = null;
        }
        super.destroy();
    }

    private enum State {
        IDLE, PREPARING, ARMING, FISHING
    }

    private static final class PendingCatch {
        final GItem item;
        final long detectedAt;
        final FishingObservation base;

        PendingCatch(GItem item, long detectedAt, FishingObservation base) {
            this.item = item;
            this.detectedAt = detectedAt;
            this.base = base;
        }
    }

    private void refreshInventoryChoices() {
        EnumMap<FishingAtlas.Part, List<String>> refreshed = FishingInventory.fishingChoices(gui);
        if(refreshed.equals(availableChoices)) {
            updateSelectionSnapshot();
            return;
        }
        String selectedPole = fishingPoleChoice.getSelected();
        availableChoices.clear();
        availableChoices.putAll(refreshed);
        fishingPoleChoice.setItems(refreshed.getOrDefault(FishingAtlas.Part.POLE, Collections.emptyList()),
                selectedPole);
        refreshChoice(hookChoice, FishingAtlas.Part.HOOK);
        refreshChoice(fishLineChoice, FishingAtlas.Part.LINE);
        refreshChoice(baitChoice, FishingAtlas.Part.BAIT);
        refreshChoice(lureChoice, FishingAtlas.Part.LURE);
        updateFishingMode();
        updateSelectionSnapshot();
    }

    private void refreshChoice(MultiSelectList<String> list, FishingAtlas.Part part) {
        List<String> available = availableChoices.getOrDefault(part, Collections.emptyList());
        Set<String> excluded = excludedChoices.get(part);
        List<String> selected = new ArrayList<>();
        for(String item : available) {
            if(!excluded.contains(item))
                selected.add(item);
        }
        list.setItems(available, selected);
    }

    private void updateFishingMode() {
        boolean lure = "Primitive Casting-Rod".equals(fishingPoleChoice.getSelected());
        if(lure) {
            baitChoice.hide();
            lureChoice.show();
        } else {
            baitChoice.show();
            lureChoice.hide();
        }
        baitLabel.settext(lure ? "Choose Lure:" : "Choose Bait:");
    }

    private void updateSelectionSnapshot() {
        String pole = fishingPoleChoice.getSelected();
        boolean lure = "Primitive Casting-Rod".equals(pole);
        selections = new FishingSelections(pole, fishLineChoice.getSelected(), hookChoice.getSelected(),
                baitChoice.getSelected(), lureChoice.getSelected(), lure);
    }

    private static final class FishingSelections {
        final String pole;
        final List<String> lines;
        final List<String> hooks;
        final List<String> baits;
        final List<String> lures;
        final boolean lure;

        FishingSelections(String pole, List<String> lines, List<String> hooks, List<String> baits,
                          List<String> lures, boolean lure) {
            this.pole = pole;
            this.lines = List.copyOf(lines);
            this.hooks = List.copyOf(hooks);
            this.baits = List.copyOf(baits);
            this.lures = List.copyOf(lures);
            this.lure = lure;
        }

        static FishingSelections empty() {
            return(new FishingSelections(null, Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList(), false));
        }
    }
}
