package haven.worldactivity;

import haven.GameUI;
import haven.Gob;
import haven.GobQualityInfo;
import haven.LocalizedResourceTimerInfo;
import haven.OCache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Session-local observation service for the World Activity Board. It never
 * opens a flower menu, sends a server message, or infers a duration from an
 * object name. Later workstation adapters can feed the same entry model.
 */
public final class WorldActivityBoardService implements OCache.ChangeCallback {
    private final GameUI gui;
    private final Map<Long, WorldActivityEntry> entries = new LinkedHashMap<>();
    private OCache observedCache;

    public WorldActivityBoardService(GameUI gui) {
        this.gui = gui;
    }

    public void start() {
        OCache cache = currentCache();
        if(cache == null)
            return;
        OCache old;
        synchronized(entries) {
            if(observedCache == cache)
                return;
            old = observedCache;
            observedCache = cache;
        }
        if(old != null)
            old.uncallback(this);
        cache.callback(this);
        refresh(cache);
    }

    public void close() {
        OCache cache;
        synchronized(entries) {
            cache = observedCache;
            observedCache = null;
            entries.clear();
        }
        if(cache != null)
            cache.uncallback(this);
    }

    /** Refreshes object presence without changing any server or world state. */
    public void refresh() {
        OCache cache;
        synchronized(entries) {
            cache = observedCache;
        }
        if(cache == null) {
            start();
            synchronized(entries) {
                cache = observedCache;
            }
        }
        if(cache != null)
            refresh(cache);
    }

    private void refresh(OCache cache) {
        List<Gob> gobs = new ArrayList<>();
        synchronized(cache) {
            for(Gob gob : cache)
                gobs.add(gob);
        }
        Set<Long> present = new HashSet<>();
        for(Gob gob : gobs) {
            if(observePresence(gob))
                present.add(gob.id);
        }
        synchronized(entries) {
            Iterator<Map.Entry<Long, WorldActivityEntry>> iterator = entries.entrySet().iterator();
            while(iterator.hasNext()) {
                Map.Entry<Long, WorldActivityEntry> mapEntry = iterator.next();
                WorldActivityEntry entry = mapEntry.getValue();
                if(present.contains(entry.gobId()))
                    continue;
                if(entry.hasTimer()) {
                    if(entry.visible())
                        mapEntry.setValue(copy(entry, false));
                } else {
                    iterator.remove();
                }
            }
        }
    }

    /** Records a world-object target from a normal interaction or Inspect cursor. */
    public boolean noteActivityGob(Gob gob) {
        if(!isStarter(gob))
            return(false);
        observePresence(gob);
        synchronized(entries) {
            WorldActivityEntry entry = entries.get(gob.id);
            if(entry != null && !entry.visible())
                entries.put(gob.id, copy(entry, true));
        }
        return(true);
    }

    /** Records a quality, timer, or explicit fuel signal from a server notice. */
    public boolean noteInspection(Gob gob, String message) {
        WorldActivityType type = WorldActivityDetector.classify(gob);
        if(!WorldActivityDetector.isStarterType(type) || message == null)
            return(false);
        WorldActivityTimingParser.ParsedTiming parsed = WorldActivityTimingParser.parse(message, type);
        Double quality = WorldActivityTimingParser.parseQuality(message);
        boolean explicitFuel = parsed.fuelState() != WorldActivityFuelState.UNKNOWN;
        if(!parsed.hasDuration() && quality == null && !explicitFuel)
            return(false);

        long now = System.currentTimeMillis();
        WorldActivityEntry old;
        synchronized(entries) {
            old = entries.get(gob.id);
        }
        String resourceName = WorldActivityDetector.resourceName(gob);
        String label = WorldActivityDetector.displayName(gob);
        long dueAt = parsed.hasDuration()
                ? safeAdd(now, parsed.durationMillis())
                : (old == null ? -1L : old.dueAtMillis());
        Double resolvedQuality = quality != null ? quality : old == null ? quality(gob) : old.quality();
        WorldActivityFuelState fuelState = explicitFuel
                ? parsed.fuelState()
                : old == null ? defaultFuelState(type) : old.fuelState();
        long observedAt = old == null ? now : old.observedAtMillis();
        entriesPut(new WorldActivityEntry(gob.id, type, resourceName, label, resolvedQuality,
                observedAt, dueAt, fuelState, !gob.isHidden));
        return(true);
    }

    /** Keeps a quality-only response associated with the same Gob for a later timer notice. */
    public boolean shouldAwaitFollowup(Gob gob) {
        if(gob == null)
            return(false);
        synchronized(entries) {
            WorldActivityEntry entry = entries.get(gob.id);
            return(entry != null && entry.quality() != null && !entry.hasTimer());
        }
    }

    public List<WorldActivityEntry> snapshot() {
        long now = System.currentTimeMillis();
        List<WorldActivityEntry> snapshot;
        synchronized(entries) {
            snapshot = new ArrayList<>(entries.values());
        }
        snapshot.sort(Comparator
                .comparingInt((WorldActivityEntry entry) -> stateOrder(entry.state(now)))
                .thenComparingLong(entry -> entry.hasTimer() ? entry.dueAtMillis() : Long.MAX_VALUE)
                .thenComparing(entry -> entry.label().toLowerCase(Locale.ROOT)));
        return(snapshot);
    }

    public int trackedCount() {
        synchronized(entries) {
            return(entries.size());
        }
    }

    public int dueCount() {
        long now = System.currentTimeMillis();
        int count = 0;
        synchronized(entries) {
            for(WorldActivityEntry entry : entries.values()) {
                if(entry.state(now) == WorldActivityState.DUE)
                    count++;
            }
        }
        return(count);
    }

    @Override
    public void added(Gob gob) {
        observePresence(gob);
    }

    @Override
    public void removed(Gob gob) {
        if(gob == null)
            return;
        synchronized(entries) {
            WorldActivityEntry entry = entries.get(gob.id);
            if(entry == null)
                return;
            if(entry.hasTimer())
                entries.put(gob.id, copy(entry, false));
            else
                entries.remove(gob.id);
        }
    }

    private boolean observePresence(Gob gob) {
        WorldActivityType type = WorldActivityDetector.classify(gob);
        if(!WorldActivityDetector.isStarterType(type))
            return(false);
        String resourceName = WorldActivityDetector.resourceName(gob);
        String label = WorldActivityDetector.displayName(gob);
        LocalizedResourceTimerInfo timer = gob.localizedResourceTimer();
        WorldActivityEntry old;
        synchronized(entries) {
            old = entries.get(gob.id);
        }
        long dueAt = timer != null ? timer.dueAtMillis() : old == null ? -1L : old.dueAtMillis();
        long observedAt = timer != null ? timer.observedAtMillis() : old == null
                ? System.currentTimeMillis() : old.observedAtMillis();
        Double quality = quality(gob);
        if(quality == null && old != null)
            quality = old.quality();
        WorldActivityFuelState fuel = old == null ? defaultFuelState(type) : old.fuelState();
        entriesPut(new WorldActivityEntry(gob.id, type, resourceName, label, quality,
                observedAt, dueAt, fuel, !gob.isHidden));
        return(true);
    }

    private boolean isStarter(Gob gob) {
        return(WorldActivityDetector.isStarterType(WorldActivityDetector.classify(gob)));
    }

    private static Double quality(Gob gob) {
        if(gob == null)
            return(null);
        GobQualityInfo info = gob.getattr(GobQualityInfo.class);
        if(info == null || info.qualityValue() == 0)
            return(null);
        return((double)info.qualityValue());
    }

    private static WorldActivityFuelState defaultFuelState(WorldActivityType type) {
        return(type == WorldActivityType.PYRE
                ? WorldActivityFuelState.UNKNOWN : WorldActivityFuelState.NOT_REQUIRED);
    }

    private void entriesPut(WorldActivityEntry entry) {
        synchronized(entries) {
            entries.put(entry.gobId(), entry);
        }
    }

    private static WorldActivityEntry copy(WorldActivityEntry entry, boolean visible) {
        return(new WorldActivityEntry(entry.gobId(), entry.type(), entry.resourceName(), entry.label(),
                entry.quality(), entry.observedAtMillis(), entry.dueAtMillis(), entry.fuelState(), visible));
    }

    private static int stateOrder(WorldActivityState state) {
        return(state == WorldActivityState.DUE ? 0 : state == WorldActivityState.RUNNING ? 1 : 2);
    }

    private static long safeAdd(long left, long right) {
        if(right > 0L && Long.MAX_VALUE - left < right)
            return(Long.MAX_VALUE);
        return(left + right);
    }

    private OCache currentCache() {
        try {
            if(gui != null && gui.ui != null && gui.ui.sess != null
                    && gui.ui.sess.glob != null)
                return(gui.ui.sess.glob.oc);
        } catch(RuntimeException ignored) {
        }
        return(null);
    }
}
