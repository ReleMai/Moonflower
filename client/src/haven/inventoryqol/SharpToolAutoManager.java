package haven.inventoryqol;

import haven.FlowerMenu;
import haven.GItem;
import haven.GameUI;
import haven.Gob;
import haven.Resource;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Coordinates temporary sharp-tool equipment for manual and bulk processing. */
public final class SharpToolAutoManager implements AutoCloseable {
    private final GameUI gui;
    private final SharpToolSwapper swapper;
    private final AtomicBoolean occupied = new AtomicBoolean(false);
    private volatile boolean closed;
    private volatile boolean batchActive;
    private volatile Thread manualWorker;
    private volatile long animalTargetUntil;

    public SharpToolAutoManager(GameUI gui) {
        this.gui = gui;
        this.swapper = new SharpToolSwapper(gui);
    }

    /** Returns true when this manager retained the flower-menu choice for a guarded swap. */
    public boolean intercept(FlowerMenu menu, FlowerMenu.Petal option) {
        if(closed || batchActive || menu == null || option == null || !isProcessingOption(option.name) ||
                requiresAnimalTarget(option.name) && System.currentTimeMillis() > animalTargetUntil)
            return(false);
        if(!occupied.compareAndSet(false, true)) {
            gui.error("Another sharp-tool action is still finishing.");
            return(false);
        }
        manualWorker = new Thread(() -> runManual(menu, option), "MoonFlower-sharp-tool-action");
        manualWorker.setDaemon(true);
        manualWorker.start();
        return(true);
    }

    public Batch beginBatch() throws InterruptedException {
        if(closed || !occupied.compareAndSet(false, true))
            return(Batch.error(this, "Another sharp-tool action is still running."));
        batchActive = true;
        try {
            SharpToolSwapper.Session session = swapper.equipBest();
            if(!session.success()) {
                batchActive = false;
                occupied.set(false);
                return(Batch.error(this, session.error));
            }
            return(new Batch(this, session, null));
        } catch(InterruptedException interrupted) {
            batchActive = false;
            occupied.set(false);
            throw interrupted;
        } catch(RuntimeException failure) {
            batchActive = false;
            occupied.set(false);
            throw failure;
        }
    }

    public boolean batchActive() {
        return(batchActive);
    }

    public void noteItemInteraction(GItem item) {
        if(item == null)
            return;
        String name = "";
        String resource = "";
        try { name = item.getname(); } catch(RuntimeException ignored) {}
        try {
            Resource loaded = item.getres();
            resource = loaded == null ? "" : loaded.name;
        } catch(RuntimeException ignored) {}
        String target = (name + " " + resource).toLowerCase(Locale.ROOT);
        if(target.contains("dead") || target.contains("carcass") || target.contains("corpse"))
            animalTargetUntil = System.currentTimeMillis() + 3000L;
    }

    public void noteGobInteraction(Gob gob) {
        if(gob == null)
            return;
        String resource = "";
        try {
            Resource loaded = gob.getres();
            resource = loaded == null ? "" : loaded.name;
        } catch(RuntimeException ignored) {}
        if(Boolean.TRUE.equals(gob.knocked) || resource.contains("/kritter/"))
            animalTargetUntil = System.currentTimeMillis() + 3000L;
    }

    static boolean isProcessingOption(String option) {
        String normalized = option == null ? "" : option.toLowerCase(Locale.ROOT)
                .replace('-', ' ').trim();
        return(normalized.equals("skin") || normalized.equals("flay") ||
                normalized.equals("clean") || normalized.equals("gut") ||
                normalized.equals("pluck") || normalized.equals("scale") ||
                normalized.equals("butcher") || normalized.equals("collect bones") ||
                normalized.equals("gather bones") || normalized.equals("take bones"));
    }

    private static boolean requiresAnimalTarget(String option) {
        String normalized = option == null ? "" : option.toLowerCase(Locale.ROOT).trim();
        return(normalized.equals("clean") || normalized.equals("gut"));
    }

    private void runManual(FlowerMenu menu, FlowerMenu.Petal option) {
        SharpToolSwapper.Session session = null;
        try {
            session = swapper.equipBest();
            if(!session.success()) {
                gui.error(session.error);
                menu.choosePrepared(null);
                return;
            }
            menu.choosePrepared(option);
            waitForProcessing();
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            gui.error("Sharp-tool switching was interrupted; check the cursor and hand slots.");
            try {
                menu.choosePrepared(null);
            } catch(RuntimeException ignored) {
            }
        } catch(RuntimeException failure) {
            gui.error("Sharp-tool switching failed: " + failure.getMessage() + ".");
            try {
                menu.choosePrepared(null);
            } catch(RuntimeException ignored) {
            }
        } finally {
            if(session != null)
                session.close();
            manualWorker = null;
            occupied.set(false);
        }
    }

    private void waitForProcessing() throws InterruptedException {
        long startDeadline = System.currentTimeMillis() + 3500L;
        boolean progressSeen = false;
        while(!closed && System.currentTimeMillis() < startDeadline) {
            if(gui.prog != null) {
                progressSeen = true;
                break;
            }
            Thread.sleep(50L);
        }
        if(progressSeen) {
            long finishDeadline = System.currentTimeMillis() + 90_000L;
            while(!closed && gui.prog != null && System.currentTimeMillis() < finishDeadline)
                Thread.sleep(100L);
        } else {
            Thread.sleep(500L);
        }
    }

    private void finishBatch(SharpToolSwapper.Session session) {
        try {
            if(session != null)
                session.close();
        } finally {
            batchActive = false;
            occupied.set(false);
        }
    }

    @Override
    public void close() {
        closed = true;
        Thread worker = manualWorker;
        if(worker != null)
            worker.interrupt();
    }

    public static final class Batch implements AutoCloseable {
        private final SharpToolAutoManager owner;
        private final SharpToolSwapper.Session session;
        public final String error;
        private boolean closed;

        private Batch(SharpToolAutoManager owner, SharpToolSwapper.Session session, String error) {
            this.owner = owner;
            this.session = session;
            this.error = error;
        }

        static Batch error(SharpToolAutoManager owner, String error) {
            return(new Batch(owner, null, error));
        }

        public boolean success() { return(error == null); }
        public String toolName() { return(session == null ? "" : session.toolName); }
        public double quality() { return(session == null ? 0 : session.quality); }

        @Override
        public void close() {
            if(closed)
                return;
            closed = true;
            if(session != null)
                owner.finishBatch(session);
        }
    }
}
