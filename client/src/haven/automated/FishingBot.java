package haven.automated;

import haven.Button;
import haven.CheckBox;
import haven.Coord;
import haven.GameUI;
import haven.Gob;
import haven.Inventory;
import haven.Label;
import haven.Loading;
import haven.MenuGrid;
import haven.UI;
import haven.Utils;
import haven.WItem;
import haven.Widget;
import haven.automated.helpers.FishingAtlas;
import haven.fishing.FishingJournalWindow;
import haven.fishing.FishingAnalytics;
import haven.fishing.FishingObservation;
import haven.fishing.FishingTackleCatalog;
import haven.widgets.MultiSelectList;
import haven.widgets.SingleSelectList;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;

import static haven.OCache.posres;

/** Visible fishing helper with safe tackle preparation and a local catch journal. */
public class FishingBot extends Widget implements Runnable {
    private static final String HIGHEST_CHANCE = "Highest chance";
    private static final long LOOP_DELAY_MS = 250;
    private static final long ATTEMPT_TIMEOUT_MS = 180_000;
    private static final int WATER_SEARCH_RADIUS = 3;
    private static final double MAX_CAST_DISTANCE = 33.0;

    private final GameUI gui;
    private final FishingEquipment equipment;
    private final FishingJournalWindow journalWindow;

    private volatile boolean closed;
    private volatile boolean active;
    private volatile boolean prepareOnly;
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
    private volatile String desiredChance = "Spot chances: waiting for a fishing choice.";
    private volatile FishingSelections selections = FishingSelections.empty();
    private volatile String preferredFish;
    private volatile List<String> desiredFishTargets = List.of(HIGHEST_CHANCE);
    private long nextInventoryRefresh;
    private Future<List<FishingObservation>> knowledgeQuery;
    private final List<FishingObservation> knowledgeObservations = new ArrayList<>();
    private final Map<String, Integer> sessionTargetSelections = new HashMap<>();
    private long knowledgeGeneration = -1;
    private int completedSelections;
    private final EnumMap<FishingAtlas.Part, List<String>> availableChoices =
            new EnumMap<>(FishingAtlas.Part.class);
    private final EnumMap<FishingAtlas.Part, Set<String>> excludedChoices =
            new EnumMap<>(FishingAtlas.Part.class);

    private final Button startButton;
    private final CheckBox startCheckBox;
    private final Label statusLabel;
    private final Label chanceLabel;
    private final Label baitLabel;
    private final SingleSelectList<String> fishingPoleChoice;
    private final MultiSelectList<String> hookChoice;
    private final MultiSelectList<String> fishLineChoice;
    private final MultiSelectList<String> baitChoice;
    private final MultiSelectList<String> lureChoice;
    private final SingleSelectList<String> preferredFishChoice;

    public FishingBot(GameUI gui) {
        super(UI.scale(800, 560));
        this.gui = gui;
        equipment = new FishingEquipment(gui);
        journalWindow = gui.fishingJournalWindow;

        addLabel("Choose Fishing Pole:", UI.scale(20, 0));
        addLabel("Choose Hook:", UI.scale(30, 73));
        addLabel("Choose Fishline:", UI.scale(155, 0));
        baitLabel = addLabel("Choose Bait:", UI.scale(295, 0));
        addLabel("Target Fish:", UI.scale(425, 0));

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
        preferredFish = Utils.getpref(targetPreference(), HIGHEST_CHANCE);
        List<String> initialTargets = new ArrayList<>();
        initialTargets.add(HIGHEST_CHANCE);
        if(!HIGHEST_CHANCE.equals(preferredFish))
            initialTargets.add(preferredFish);
        desiredFishTargets = List.copyOf(initialTargets);
        preferredFishChoice = add(new SingleSelectList<String>(UI.scale(165, 108), 18,
                initialTargets) {
            @Override
            protected void changed(String selection, int index) {
                preferredFish = selection == null ? HIGHEST_CHANCE : selection;
                Utils.setpref(targetPreference(), preferredFish);
            }
        }, UI.scale(410, 20));

        startCheckBox = add(new CheckBox("Auto cast") {{ a = true; }}, UI.scale(70, 177));
        add(new Button(UI.scale(70), "Prepare") {
            @Override
            public void click() {
                if(active)
                    setStatus("Stop the current fishing run before preparing again.");
                else
                    startPreparation();
            }
        }, UI.scale(165, 175));
        startButton = add(new Button(UI.scale(60), "Start") {
            @Override
            public void click() {
                if(active)
                    stopAutomation("Stopped by user.");
                else
                    startAutomation();
            }
        }, UI.scale(240, 175));
        add(new Button(UI.scale(75), "Journal") {
            @Override
            public void click() {
                gui.toggleFishingJournal();
            }
        }, UI.scale(305, 175));
        statusLabel = add(new Label("Highlighted inventory items are allowed for fishing."),
                UI.scale(10, 204));
        chanceLabel = add(new Label("Spot chances: waiting for a fishing choice."), UI.scale(10, 224));

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
        beginPreparation(false);
    }

    private synchronized void startPreparation() {
        beginPreparation(true);
    }

    private void beginPreparation(boolean prepareOnly) {
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
        this.prepareOnly = prepareOnly;
        state = State.PREPARING;
        stateChangedAt = System.currentTimeMillis();
        timeoutCount = 0;
        clearAttempt();
        setStartButton("Stop");
        setStatus("Preparing selected fishing equipment...");
    }

    /** Refreshes the standalone helper without starting or changing the native Fishing action. */
    public synchronized void opened() {
        refreshInventoryChoices();
        if(!active)
            setStatus("Choose tackle, then click Prepare or Start.");
    }

    /** Exposes only the names already discovered by the helper's reachable-inventory scan. */
    public synchronized FishingTackleCatalog tackleCatalog() {
        refreshInventoryChoices();
        FishingSelections selected = selections;
        return(new FishingTackleCatalog(
                availableChoices.getOrDefault(FishingAtlas.Part.POLE, List.of()),
                availableChoices.getOrDefault(FishingAtlas.Part.LINE, List.of()),
                availableChoices.getOrDefault(FishingAtlas.Part.HOOK, List.of()),
                availableChoices.getOrDefault(FishingAtlas.Part.BAIT, List.of()),
                availableChoices.getOrDefault(FishingAtlas.Part.LURE, List.of()),
                selected.pole, first(selected.lines), first(selected.hooks),
                first(selected.lure ? selected.lures : selected.baits), selected.lure));
    }

    /** Applies one exact visible preset without starting any fishing action. */
    public synchronized boolean selectTackle(String pole, String line, String hook,
                                              String consumableKind, String consumable) {
        if(active)
            return(false);
        refreshInventoryChoices();
        boolean lure = "lure".equalsIgnoreCase(consumableKind);
        if(!contains(FishingAtlas.Part.POLE, pole) || !contains(FishingAtlas.Part.LINE, line) ||
                !contains(FishingAtlas.Part.HOOK, hook) ||
                !contains(lure ? FishingAtlas.Part.LURE : FishingAtlas.Part.BAIT, consumable) ||
                lure != "Primitive Casting-Rod".equals(pole))
            return(false);
        fishingPoleChoice.setItems(availableChoices.getOrDefault(FishingAtlas.Part.POLE, List.of()), pole);
        selectOnly(FishingAtlas.Part.LINE, fishLineChoice, line);
        selectOnly(FishingAtlas.Part.HOOK, hookChoice, hook);
        selectOnly(FishingAtlas.Part.BAIT, baitChoice, lure ? null : consumable);
        selectOnly(FishingAtlas.Part.LURE, lureChoice, lure ? consumable : null);
        updateFishingMode();
        updateSelectionSnapshot();
        setStatus("Tideglass preset selected. Review it or prepare the pole.");
        return(true);
    }

    /** User-invoked preparation uses the existing bounded, verified transaction. */
    public synchronized void prepareSelectedTackle() {
        if(active)
            setStatus("Stop the current fishing run before swapping presets.");
        else
            startPreparation();
    }

    public String visibleStatus() {
        return(desiredStatus);
    }

    public void stopAutomation() {
        stopAutomation("Stopped.");
    }

    private synchronized void stopAutomation(String reason) {
        active = false;
        prepareOnly = false;
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
            boolean choiceWindowPresent = FishingChoiceWindow.present(gui, this, journalWindow);
            choiceSelected = retainChoiceSelection(choiceSelected, choiceWindowPresent);
            if(choiceWindowPresent && !choiceSelected)
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
                    !choiceWindowPresent) {
                clearAttempt();
                state = State.PREPARING;
                setStatus("Fishing ended; checking for consumed or lost tackle.");
            }
            return;
        }

        FishingSelections selected = selections;
        FishingEquipment.Result result = equipment.prepare(selected.pole, selected.lines, selected.hooks,
                selected.lure ? selected.lures : selected.baits, selected.lure,
                !prepareOnly && startCheckBox.a);
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
        boolean preparationOnly = prepareOnly;
        prepareOnly = false;
        if(!tackle.equipped && !preparationOnly) {
            active = false;
            state = State.IDLE;
            setStartButton("Start");
            setStatus("Tackle is attached. Put the selected pole in a hand, then click Start.");
            return;
        }
        if(preparationOnly || !startCheckBox.a) {
            active = false;
            state = State.IDLE;
            setStartButton("Start");
            setStatus("Fishing pole tackle is prepared." +
                    (tackle.equipped ? " Pole is equipped." : " Put the pole in a hand before fishing.") +
                    (preparationOnly ? "" : " Auto cast is disabled."));
            return;
        }
        if(!refreshKnowledge()) {
            setStatus("Loading recorded tackle and fishing-spot knowledge...");
            return;
        }
        List<FishingEnvironment.Target> nearby = FishingEnvironment.nearbyWater(gui,
                WATER_SEARCH_RADIUS, MAX_CAST_DISTANCE);
        FishingEnvironment.Target water = chooseWaterTarget(nearby);
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
        gui.map.wdgmsg("click", Coord.z, waterTarget.coordinate.floor(posres), 1, 0);
        state = State.FISHING;
        attemptStartedAt = now;
        lastFishingPoseAt = 0;
        choiceSelected = false;
        choiceRowsJson = "[]";
        gui.noteFishingChoices(choiceRowsJson);
        gui.fishingCatchTracker().noteFishingAttempt(waterTarget, tackle);
        setChanceText("Spot chances: waiting for the server at this pole and location.");
        cancelCursorPending = true;
        cancelCursorAt = now + 500;
        setStatus("Fishing at nearby water; new fish will be journaled as candidate catches.");
    }

    private void selectBestFishingChoice() {
        if(!active)
            return;
        FishingChoiceWindow.select(gui, this, journalWindow, preferredFish).ifPresent(selection -> {
            choiceRowsJson = selection.rowsJson;
            gui.noteFishingChoices(choiceRowsJson);
            choiceSelected = true;
            List<String> targets = new ArrayList<>();
            targets.add(HIGHEST_CHANCE);
            targets.addAll(selection.fishNames);
            if(preferredFish != null && !HIGHEST_CHANCE.equals(preferredFish) &&
                    targets.stream().noneMatch(preferredFish::equalsIgnoreCase))
                targets.add(preferredFish);
            desiredFishTargets = List.copyOf(targets);
            FishingObservation survey = FishingEnvironment.captureSurvey(gui, waterTarget, tackle,
                    choiceRowsJson, selection.choice, System.currentTimeMillis());
            gui.fishingJournalService.record(survey);
            knowledgeObservations.add(survey);
            completedSelections++;
            sessionTargetSelections.merge(targetKey(waterTarget), 1, Integer::sum);
            setChanceText("Spot chances: " + selection.summary);
            String targetNote = HIGHEST_CHANCE.equals(preferredFish) || selection.matchedPreferred ? "" :
                    " Preferred fish was unavailable, so the highest chance was used.";
            setStatus("Aiming for " + selection.choice.fishName + " at " +
                    selection.choice.finalPercent + "%. Choice and rig recorded." + targetNote);
        });
    }

    static boolean retainChoiceSelection(boolean choiceSelected, boolean choiceWindowPresent) {
        return(choiceSelected && choiceWindowPresent);
    }

    private boolean refreshKnowledge() {
        if(knowledgeQuery != null) {
            if(!knowledgeQuery.isDone())
                return(false);
            try {
                List<FishingObservation> loaded = knowledgeQuery.get();
                knowledgeObservations.clear();
                if(loaded != null)
                    knowledgeObservations.addAll(loaded);
            } catch(Exception failure) {
                new haven.Warning(failure, "Could not load fishing automation knowledge")
                        .level(haven.Warning.ERROR).issue();
            } finally {
                knowledgeQuery = null;
                knowledgeGeneration = gui.fishingJournalService.generation();
            }
        }
        if(knowledgeGeneration != gui.fishingJournalService.generation()) {
            knowledgeQuery = gui.fishingJournalService.recent(2000);
            return(false);
        }
        return(true);
    }

    private FishingEnvironment.Target chooseWaterTarget(List<FishingEnvironment.Target> candidates) {
        if(candidates == null || candidates.isEmpty() || tackle == null)
            return(null);
        FishingAnalytics.Snapshot knowledge = FishingAnalytics.analyze(knowledgeObservations);
        FishingAnalytics.RigKey rig = FishingAnalytics.RigKey.of(tackle.pole.displayName,
                tackle.pole.quality, tackle.line.displayName, tackle.line.quality,
                tackle.hook.displayName, tackle.hook.quality, tackle.consumableKind,
                tackle.consumable.displayName, tackle.consumable.quality);
        boolean scouting = completedSelections % 4 == 3;
        Comparator<FishingEnvironment.Target> ranking;
        if(scouting) {
            ranking = Comparator
                    .comparingInt((FishingEnvironment.Target target) ->
                            sessionTargetSelections.getOrDefault(targetKey(target), 0))
                    .thenComparingInt(target -> knowledge.score(target.coordinate.x,
                            target.coordinate.y, rig).rigSamples)
                    .thenComparingInt(target -> -target.waterNeighbors)
                    .thenComparingDouble(target -> target.distance);
        } else {
            ranking = Comparator
                    .comparingInt((FishingEnvironment.Target target) -> knowledge.score(
                            target.coordinate.x, target.coordinate.y, rig).rankingChance()).reversed()
                    .thenComparingInt(target -> sessionTargetSelections.getOrDefault(targetKey(target), 0))
                    .thenComparingInt(target -> -target.waterNeighbors)
                    .thenComparingDouble(target -> target.distance);
        }
        return(candidates.stream().sorted(ranking).findFirst().orElse(candidates.get(0)));
    }

    private static String targetKey(FishingEnvironment.Target target) {
        if(target == null)
            return("");
        Coord tile = target.coordinate.floor(haven.MCache.tilesz);
        return(tile.x + ":" + tile.y);
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
        setChanceText("Spot chances: waiting for a fishing choice.");
    }

    private void setChanceText(String text) {
        desiredChance = text == null ? "" : text;
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
        if(!Objects.equals(chanceLabel.texts, desiredChance))
            chanceLabel.settext(desiredChance);
        List<String> targetChoices = desiredFishTargets;
        if(!targetChoices.equals(lastDisplayedFishTargets)) {
            preferredFishChoice.setItems(targetChoices, preferredFish);
            lastDisplayedFishTargets = targetChoices;
        }
    }

    private List<String> lastDisplayedFishTargets = List.of();

    private String targetPreference() {
        return("fishing-target-fish/" + (gui.genus == null ? "" : gui.genus));
    }

    public synchronized void stop() {
        stopAutomation("Fishing automation stopped.");
    }

    public synchronized void shutdown() {
        if(!closed)
            destroy();
    }

    @Override
    public void destroy() {
        if(!closed) {
            active = false;
            closed = true;
            Thread thread = runner;
            if(thread != null)
                thread.interrupt();
            gui.fishingThread = null;
        }
        super.destroy();
    }

    private enum State {
        IDLE, PREPARING, ARMING, FISHING
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

    private void selectOnly(FishingAtlas.Part part, MultiSelectList<String> list, String selected) {
        List<String> available = availableChoices.getOrDefault(part, List.of());
        Set<String> excluded = excludedChoices.get(part);
        excluded.clear();
        excluded.addAll(available);
        List<String> wanted = new ArrayList<>();
        if(selected != null) {
            for(String value : available) {
                if(FishingAtlas.sameDisplayName(value, selected)) {
                    excluded.remove(value);
                    wanted.add(value);
                    break;
                }
            }
        }
        list.setItems(available, wanted);
    }

    private boolean contains(FishingAtlas.Part part, String wanted) {
        if(wanted == null || wanted.isBlank())
            return(false);
        for(String value : availableChoices.getOrDefault(part, List.of())) {
            if(FishingAtlas.sameDisplayName(value, wanted))
                return(true);
        }
        return(false);
    }

    private static String first(List<String> values) {
        return(values == null || values.isEmpty() ? "" : values.get(0));
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
