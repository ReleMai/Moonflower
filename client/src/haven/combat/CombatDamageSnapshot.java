package haven.combat;

/** Immutable observed-damage state for one Gob lifecycle. */
public final class CombatDamageSnapshot {
    private final long totalSoftHp;
    private final long totalHardHp;
    private final long totalArmor;
    private final long combatSoftHp;
    private final boolean hasSoftHpEvent;
    private final boolean hasCombatSoftHpEvent;
    private final boolean hasHardHpEvent;
    private final boolean hasArmorEvent;
    private final boolean inCombat;
    private final boolean estimateReliable;
    private final long lastSoftEventAgeMillis;

    public static final CombatDamageSnapshot EMPTY = new CombatDamageSnapshot(
            0, 0, 0, 0, false, false, false, false, false, true, Long.MAX_VALUE);

    public CombatDamageSnapshot(long totalSoftHp, long totalHardHp, long totalArmor,
                                long combatSoftHp, boolean hasSoftHpEvent,
                                boolean hasCombatSoftHpEvent, boolean hasHardHpEvent,
                                boolean hasArmorEvent, boolean inCombat,
                                boolean estimateReliable, long lastSoftEventAgeMillis) {
        this.totalSoftHp = totalSoftHp;
        this.totalHardHp = totalHardHp;
        this.totalArmor = totalArmor;
        this.combatSoftHp = combatSoftHp;
        this.hasSoftHpEvent = hasSoftHpEvent;
        this.hasCombatSoftHpEvent = hasCombatSoftHpEvent;
        this.hasHardHpEvent = hasHardHpEvent;
        this.hasArmorEvent = hasArmorEvent;
        this.inCombat = inCombat;
        this.estimateReliable = estimateReliable;
        this.lastSoftEventAgeMillis = lastSoftEventAgeMillis;
    }

    public long totalSoftHp() { return(totalSoftHp); }
    public long totalHardHp() { return(totalHardHp); }
    public long totalArmor() { return(totalArmor); }
    public long combatSoftHp() { return(combatSoftHp); }
    public boolean hasSoftHpEvent() { return(hasSoftHpEvent); }
    public boolean hasCombatSoftHpEvent() { return(hasCombatSoftHpEvent); }
    public boolean hasHardHpEvent() { return(hasHardHpEvent); }
    public boolean hasArmorEvent() { return(hasArmorEvent); }
    public boolean inCombat() { return(inCombat); }
    public boolean estimateReliable() { return(estimateReliable); }
    public long lastSoftEventAgeMillis() { return(lastSoftEventAgeMillis); }

    public boolean hasAnyEvent() {
        return(hasSoftHpEvent || hasHardHpEvent || hasArmorEvent);
    }

    public boolean hasCombatSoftHpObservation() {
        return(inCombat && hasCombatSoftHpEvent);
    }
}
