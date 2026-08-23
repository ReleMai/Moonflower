package haven.combat;

import haven.Glob;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Session-scoped combat-number state shared by damage text and health estimates.
 * The weak registry prevents a closed Glob from becoming a permanent static root.
 */
public class CombatDamageTracker {
    private static final int MAX_GOBS = 256;
    private static final int MAX_RECENT_EVENTS = 32;
    private static final Map<Glob, CombatDamageTracker> BY_GLOB = new WeakHashMap<>();

    private final LinkedHashMap<Long, State> states = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, State> eldest) {
            return(size() > MAX_GOBS);
        }
    };

    public static CombatDamageTracker forGlob(Glob glob) {
        if(glob == null)
            throw(new IllegalArgumentException("glob must not be null"));
        synchronized(BY_GLOB) {
            return(BY_GLOB.computeIfAbsent(glob, ignored -> new CombatDamageTracker()));
        }
    }

    public synchronized void beginCombat(long gobId, String resourceName, long nowMillis) {
        State state = state(gobId, resourceName, true);
        if(!state.inCombat) {
            state.inCombat = true;
            state.combatSoftBaseline = state.totalSoftHp;
            state.estimateReliable = true;
            state.hasCombatSoftHpEvent = false;
            state.lastSoftEventAt = 0;
            state.combatStartedAt = nowMillis;
        }
    }

    public synchronized void endCombat(long gobId) {
        State state = states.get(gobId);
        if(state != null)
            state.inCombat = false;
    }

    public synchronized boolean record(long gobId, String resourceName, long eventKey,
                                       CombatDamageEvent event, long nowMillis) {
        State state = state(gobId, resourceName, true);
        if(!state.recentEvents.add(eventKey))
            return(false);
        trimRecentEvents(state);

        switch(event.type()) {
            case SOFT_HP -> {
                state.hasSoftHpEvent = true;
                if(state.inCombat)
                    state.hasCombatSoftHpEvent = true;
                state.totalSoftHp = nonnegativeAdd(state.totalSoftHp, event.amount());
                state.lastSoftEventAt = nowMillis;
                if(event.amount() < 0)
                    state.estimateReliable = false;
            }
            case HARD_HP -> {
                state.hasHardHpEvent = true;
                state.totalHardHp = nonnegativeAdd(state.totalHardHp, event.amount());
            }
            case ARMOR -> {
                state.hasArmorEvent = true;
                state.totalArmor = nonnegativeAdd(state.totalArmor, event.amount());
            }
        }
        return(true);
    }

    public synchronized CombatDamageSnapshot snapshot(long gobId, String resourceName, long nowMillis) {
        State state = state(gobId, resourceName, false);
        if(state == null)
            return(CombatDamageSnapshot.EMPTY);
        long combatSoft = Math.max(0, state.totalSoftHp - state.combatSoftBaseline);
        long age = state.lastSoftEventAt <= 0 ? Long.MAX_VALUE : Math.max(0, nowMillis - state.lastSoftEventAt);
        return(new CombatDamageSnapshot(
                state.totalSoftHp,
                state.totalHardHp,
                state.totalArmor,
                combatSoft,
                state.hasSoftHpEvent,
                state.hasCombatSoftHpEvent,
                state.hasHardHpEvent,
                state.hasArmorEvent,
                state.inCombat,
                state.estimateReliable,
                age));
    }

    public synchronized void removeGob(long gobId) {
        states.remove(gobId);
    }

    public synchronized void clear() {
        states.clear();
    }

    private State state(long gobId, String resourceName, boolean create) {
        State state = states.get(gobId);
        if(state != null && resourceName != null && state.resourceName != null &&
                !state.resourceName.equals(resourceName)) {
            states.remove(gobId);
            state = null;
        }
        if(state == null && create) {
            state = new State();
            state.resourceName = resourceName;
            states.put(gobId, state);
        } else if(state != null && state.resourceName == null && resourceName != null) {
            state.resourceName = resourceName;
        }
        return(state);
    }

    private static long nonnegativeAdd(long current, int amount) {
        if(amount > 0 && current > Long.MAX_VALUE - amount)
            return(Long.MAX_VALUE);
        return(Math.max(0, current + amount));
    }

    private static void trimRecentEvents(State state) {
        while(state.recentEvents.size() > MAX_RECENT_EVENTS) {
            Iterator<Long> iterator = state.recentEvents.iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private static final class State {
        String resourceName;
        long totalSoftHp;
        long totalHardHp;
        long totalArmor;
        long combatSoftBaseline;
        long combatStartedAt;
        long lastSoftEventAt;
        boolean hasSoftHpEvent;
        boolean hasCombatSoftHpEvent;
        boolean hasHardHpEvent;
        boolean hasArmorEvent;
        boolean inCombat;
        boolean estimateReliable = true;
        final LinkedHashSet<Long> recentEvents = new LinkedHashSet<>();
    }
}
