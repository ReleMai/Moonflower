package haven.inventoryqol;

import haven.Coord;
import haven.FlowerMenu;
import haven.GItem;
import haven.GameUI;
import haven.Inventory;
import haven.Loading;
import haven.WItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Runs explicit, serial native flower-menu actions against one inventory. */
final class InventoryBulkActionController implements AutoCloseable {
    enum Action {
        /* This is the non-bird fallback. Bird carcasses use a narrower sequence
         * because Pluck must happen before Clean and Butcher. */
        BUTCHER_ALL("Butcher all", true, "Wring Neck", "Skin", "Flay", "Clean", "Butcher",
                "Collect Bones", "Collect bones", "Gather Bones",
                "Gather bones", "Take Bones", "Take bones"),
        CRACK_ALL("Crack all", false, "Crack", "Crack Open", "Crack open");

        final String label;
        final boolean needsSharpTool;
        final List<String> options;

        Action(String label, boolean needsSharpTool, String... options) {
            this.label = label;
            this.needsSharpTool = needsSharpTool;
            this.options = Arrays.asList(options);
        }

        List<String> optionsFor(String itemText) {
            return(this == BUTCHER_ALL ? butcherOptionsForText(itemText) : options);
        }
    }

    private static final int MAX_PASSES = 12;
    private static final String[] LIVE_BUTCHERABLE_ANIMAL_RESOURCES = {
            "adder", "buck", "chick", "chicken", "cock", "doe", "drake", "duck", "hedgehog",
            "hen", "mallard", "mole", "rabbit", "rooster", "squirrel"
    };
    private static final String[] BIRD_MARKERS = {
            "bird", "bullfinch", "chick", "chicken", "cock", "crane", "duck", "drake", "eagle",
            "goshawk", "hen", "magpie", "mallard", "pelican", "ptarmigan", "quail", "rock dove",
            "rockdove", "seagull", "swan", "wood grouse", "woodgrouse"
    };
    private final GameUI gui;
    private final Inventory inventory;
    private volatile Thread worker;
    private volatile boolean stop;
    private volatile String status = "Idle";
    private volatile int completed;

    InventoryBulkActionController(GameUI gui, Inventory inventory) {
        this.gui = gui;
        this.inventory = inventory;
    }

    synchronized void start(Action action) {
        stop();
        if(gui == null || inventory == null) {
            status = "Inventory unavailable";
            return;
        }
        if(gui.vhand != null) {
            status = "Clear the cursor first";
            gui.error("Clear the cursor before starting " + action.label + ".");
            return;
        }
        stop = false;
        completed = 0;
        status = "Starting " + action.label;
        worker = new Thread(() -> run(action), "MoonFlower-inventory-" + action.name().toLowerCase());
        worker.setDaemon(true);
        worker.start();
    }

    synchronized void stop() {
        stop = true;
        Thread active = worker;
        if(active != null)
            active.interrupt();
        FlowerMenu.setNextSelection(null);
        if(active != null)
            status = "Stopping";
    }

    boolean running() {
        Thread active = worker;
        return(active != null && active.isAlive());
    }

    String status() {
        return(status);
    }

    int completed() {
        return(completed);
    }

    private void run(Action action) {
        SharpToolAutoManager.Batch toolBatch = null;
        try {
            if(action.needsSharpTool) {
                toolBatch = gui.sharpToolAutoManager.beginBatch();
                if(!toolBatch.success()) {
                    status = "Stopped: " + toolBatch.error;
                    gui.error(toolBatch.error);
                    return;
                }
                status = String.format("Using %s q%.1f", toolBatch.toolName(), toolBatch.quality());
            }

            for(int pass = 0; pass < MAX_PASSES && !stop; pass++) {
                int matchesThisPass = 0;
                List<GItem> snapshot = snapshot(action);
                for(int index = 0; index < snapshot.size() && !stop; index++) {
                    GItem item = snapshot.get(index);
                    if(!attached(item))
                        continue;
                    status = action.label + " — checking " + (index + 1) + "/" + snapshot.size();
                    FlowerMenu.AutoSelection request = FlowerMenu.requestNextSelection(
                            action.optionsFor(itemText(item)));
                    long before = inventoryStamp();
                    item.wdgmsg("iact", Coord.z, 0);
                    boolean matched;
                    try {
                        matched = request.result().get(selectionTimeout(), TimeUnit.MILLISECONDS);
                    } catch(TimeoutException timeout) {
                        FlowerMenu.cancelNextSelection(request);
                        matched = false;
                    }
                    if(!matched)
                        continue;
                    matchesThisPass++;
                    completed++;
                    status = action.label + " — " + completed + " action" + (completed == 1 ? "" : "s");
                    waitForAcknowledgement(before);
                    Thread.sleep(pacingDelay());
                }
                if(matchesThisPass == 0)
                    break;
            }
            status = stop ? "Stopped after " + completed : "Finished — " + completed + " action" +
                    (completed == 1 ? "" : "s");
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            status = "Stopped after " + completed;
        } catch(Exception failure) {
            status = "Stopped: " + failure.getMessage();
            gui.error(action.label + " stopped: " + failure.getMessage() + ".");
        } finally {
            FlowerMenu.setNextSelection(null);
            if(toolBatch != null)
                toolBatch.close();
            synchronized(this) {
                worker = null;
            }
        }
    }

    private List<GItem> snapshot(Action action) {
        List<GItem> items = new ArrayList<>();
        for(WItem item : inventory.getAllItems())
            if(item != null && item.item != null && candidate(action, item.item))
                items.add(item.item);
        return(items);
    }

    static boolean candidate(Action action, GItem item) {
        if(action == null || item == null)
            return(false);
        return(candidateText(action, itemText(item)));
    }

    static boolean candidateText(Action action, String text) {
        String target = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT)
                .replace('-', ' ');
        if(action == Action.BUTCHER_ALL)
            return(isCarcassStageText(target) || isLiveButcherableAnimalText(target));
        return(target.contains("nut") || target.contains("acorn") || target.contains("almond") ||
                target.contains("chestnut") || target.contains("hazel") || target.contains("pecan") ||
                target.contains("pistachio") || target.contains("pine cone") || target.contains("pinecone") ||
                target.contains("crab claw") || target.contains("crabclaw") ||
                hasResourceSuffix(target, "crab") || hasResourceSuffix(target, "lobster"));
    }

    static List<String> butcherOptionsForText(String text) {
        if(isBirdText(text))
            return(Arrays.asList("Wring Neck", "Pluck", "Clean", "Butcher", "Collect Bones", "Collect bones",
                    "Gather Bones", "Gather bones", "Take Bones", "Take bones"));
        return(Arrays.asList("Wring Neck", "Skin", "Flay", "Clean", "Butcher", "Collect Bones", "Collect bones",
                "Gather Bones", "Gather bones", "Take Bones", "Take bones"));
    }

    static boolean isBirdText(String text) {
        String target = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT).replace('-', ' ');
        for(String marker : BIRD_MARKERS) {
            if(target.contains(marker))
                return(true);
        }
        return(false);
    }

    private static boolean isCarcassStageText(String target) {
        return(target.contains("dead") || target.contains("carcass") || target.contains("corpse") ||
                target.contains("cleaned") || target.contains("skinned") ||
                target.contains("flayed") || target.contains("plucked") || target.contains("gutted") ||
                target.contains("butchered") || target.contains("skeleton"));
    }

    private static boolean isLiveButcherableAnimalText(String target) {
        for(String resource : LIVE_BUTCHERABLE_ANIMAL_RESOURCES) {
            if(hasResourceSuffix(target, resource))
                return(true);
        }
        return(false);
    }

    private static String itemText(GItem item) {
        String name = "";
        String resource = "";
        try { name = item.getname(); } catch(RuntimeException ignored) {}
        try { resource = item.getres() == null ? "" : item.getres().name; } catch(RuntimeException ignored) {}
        return(name + " " + resource);
    }

    private static boolean hasResourceSuffix(String target, String basename) {
        String suffix = basename == null ? "" : basename.toLowerCase(java.util.Locale.ROOT);
        return(!suffix.isEmpty() && (target.endsWith("/" + suffix) || target.endsWith("\\" + suffix)));
    }

    private boolean attached(GItem item) {
        try {
            return(item != null && gui.ui != null && gui.ui.widgetid(item) >= 0);
        } catch(RuntimeException ignored) {
            return(false);
        }
    }

    private long inventoryStamp() {
        long stamp = 17L;
        for(WItem widget : inventory.getAllItems()) {
            GItem item = widget.item;
            stamp = (stamp * 31L) + item.wdgid();
            stamp = (stamp * 31L) + item.infoseq;
            try {
                stamp = (stamp * 31L) + item.getname().hashCode();
            } catch(Loading ignored) {
                stamp = (stamp * 31L) + 1L;
            }
        }
        return(stamp);
    }

    private void waitForAcknowledgement(long before) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 90_000L;
        boolean progressSeen = false;
        while(!stop && System.currentTimeMillis() < deadline) {
            if(gui.prog != null)
                progressSeen = true;
            boolean changed = inventoryStamp() != before;
            if((progressSeen && gui.prog == null) || (!progressSeen && changed))
                return;
            Thread.sleep(100L);
        }
        if(!stop)
            throw new IllegalStateException("the server did not acknowledge the inventory action in time");
    }

    private long selectionTimeout() {
        Integer ping = GameUI.getPingValue();
        return(Math.max(1200L, (ping == null ? 200L : ping.longValue()) * 4L + 600L));
    }

    private long pacingDelay() {
        Integer ping = GameUI.getPingValue();
        return(Math.max(150L, (ping == null ? 200L : ping.longValue()) + 75L));
    }

    @Override
    public void close() {
        stop();
    }
}
