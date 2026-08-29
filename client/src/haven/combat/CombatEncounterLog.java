package haven.combat;

import haven.Buff;
import haven.Bufflist;
import haven.Equipory;
import haven.Fightview;
import haven.GItem;
import haven.GameUI;
import haven.Glob;
import haven.Gob;
import haven.IMeter;
import haven.Loading;
import haven.Resource;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Records authoritative combat events and explicitly labelled observations.
 * Opponent attributes which the protocol does not expose are never inferred.
 */
public final class CombatEncounterLog {
    public static final int SCHEMA_VERSION = 1;
    private static final long SAMPLE_INTERVAL_MILLIS = 250;
    private static final Map<Glob, CombatEncounterLog> BY_GLOB = new WeakHashMap<>();

    private final Map<Long, EncounterState> encounters = new HashMap<>();

    public static CombatEncounterLog forGlob(Glob glob) {
        if(glob == null)
            throw new IllegalArgumentException("glob must not be null");
        synchronized(BY_GLOB) {
            return BY_GLOB.computeIfAbsent(glob, ignored -> new CombatEncounterLog());
        }
    }

    public synchronized void begin(GameUI gui, Fightview.Relation relation, String resourceName,
                                   long nowMillis) {
        if(relation == null)
            return;
        EncounterState state = encounters.get(relation.gobid);
        if(state != null) {
            if(state.resourceName == null && resourceName != null)
                state.resourceName = resourceName;
            return;
        }
        state = new EncounterState(UUID.randomUUID().toString(), relation.gobid, nowMillis, resourceName);
        encounters.put(relation.gobid, state);
        JSONObject detail = new JSONObject();
        detail.put("player", playerSnapshot(gui));
        detail.put("opponent", opponentSnapshot(gui, relation, resourceName, nowMillis));
        emit(gui, state, relation.gobid, "encounter_started", detail, nowMillis);
    }

    public synchronized void end(GameUI gui, Fightview.Relation relation, long nowMillis) {
        if(relation == null)
            return;
        EncounterState state = encounters.remove(relation.gobid);
        if(state == null)
            return;
        JSONObject detail = new JSONObject();
        detail.put("durationMillis", Math.max(0, nowMillis - state.startedAt));
        detail.put("player", playerMeters(gui));
        detail.put("opponent", opponentSnapshot(gui, relation, state.resourceName, nowMillis));
        emit(gui, state, relation.gobid, "encounter_ended", detail, nowMillis);
    }

    public synchronized void relationUpdated(GameUI gui, Fightview.Relation relation, long nowMillis) {
        EncounterState state = state(gui, relation, nowMillis);
        if(state == null)
            return;
        emit(gui, state, relation.gobid, "initiative_updated",
                relationState(relation), nowMillis);
    }

    public synchronized void targetChanged(GameUI gui, Fightview.Relation relation, long nowMillis) {
        if(relation == null)
            return;
        EncounterState state = state(gui, relation, nowMillis);
        if(state != null)
            emit(gui, state, relation.gobid, "target_selected", relationState(relation), nowMillis);
    }

    public synchronized void action(GameUI gui, Fightview.Relation relation, String actor,
                                    String actionResource, long nowMillis) {
        if(relation == null)
            return;
        EncounterState state = state(gui, relation, nowMillis);
        if(state == null)
            return;
        JSONObject detail = relationState(relation);
        detail.put("actor", actor);
        putNullable(detail, "actionResource", actionResource);
        detail.put("player", playerMeters(gui));
        emit(gui, state, relation.gobid, "action_used", detail, nowMillis);
    }

    public synchronized void cooldown(GameUI gui, Fightview.Relation relation,
                                      double ticks, double seconds, long nowMillis) {
        if(relation == null)
            return;
        EncounterState state = state(gui, relation, nowMillis);
        if(state == null)
            return;
        JSONObject detail = relationState(relation);
        detail.put("ticks", ticks);
        detail.put("seconds", seconds);
        emit(gui, state, relation.gobid, "player_attack_cooldown", detail, nowMillis);
    }

    public synchronized void damage(GameUI gui, long gobId, String resourceName,
                                    CombatDamageEvent event, CombatDamageSnapshot total,
                                    long nowMillis) {
        EncounterState state = encounters.get(gobId);
        boolean playerDamage = gui != null && gui.plid == gobId;
        if(state == null && !playerDamage)
            return;
        if(state != null && state.resourceName == null && resourceName != null)
            state.resourceName = resourceName;
        JSONObject detail = new JSONObject();
        detail.put("damageType", event.type().name());
        detail.put("amount", event.amount());
        detail.put("combatSoftTotal", total.combatSoftHp());
        detail.put("softTotal", total.totalSoftHp());
        detail.put("hardTotal", total.totalHardHp());
        detail.put("armorTotal", total.totalArmor());
        if(playerDamage) {
            detail.put("recipient", "PLAYER");
            putNullable(detail, "recipientResource", resourceName);
            for(EncounterState encounter : encounters.values())
                emit(gui, encounter, encounter.gobId, "player_damage_observed",
                        new JSONObject(detail.toString()), nowMillis);
        } else {
            detail.put("recipient", "OPPONENT");
            putNullable(detail, "opponentResource", resourceName);
            emit(gui, state, gobId, "damage_observed", detail, nowMillis);
        }
    }

    /** Samples native meters/openings, but emits only when the stable state changes. */
    public synchronized void sample(GameUI gui, Fightview.Relation relation, long nowMillis) {
        EncounterState state = state(gui, relation, nowMillis);
        if(state == null || nowMillis < state.nextSampleAt)
            return;
        state.nextSampleAt = nowMillis + SAMPLE_INTERVAL_MILLIS;
        JSONObject detail = new JSONObject();
        detail.put("relation", relationState(relation));
        detail.put("player", playerMeters(gui));
        detail.put("opponent", opponentSnapshot(gui, relation, state.resourceName, nowMillis));
        String signature = detail.toString();
        if(signature.equals(state.lastSampleSignature))
            return;
        state.lastSampleSignature = signature;
        emit(gui, state, relation.gobid, "combat_state", detail, nowMillis);
    }

    private EncounterState state(GameUI gui, Fightview.Relation relation, long nowMillis) {
        if(relation == null)
            return null;
        EncounterState state = encounters.get(relation.gobid);
        if(state == null) {
            String resourceName = resourceName(gui, relation.gobid);
            begin(gui, relation, resourceName, nowMillis);
            state = encounters.get(relation.gobid);
        } else if(state.resourceName == null) {
            state.resourceName = resourceName(gui, relation.gobid);
        }
        return state;
    }

    private void emit(GameUI gui, EncounterState state, long gobId, String type,
                      JSONObject detail, long nowMillis) {
        Instant capturedAt = Instant.ofEpochMilli(nowMillis);
        JSONObject record = new JSONObject();
        record.put("schemaVersion", SCHEMA_VERSION);
        record.put("encounterId", state.id);
        record.put("sequence", ++state.sequence);
        record.put("eventType", type);
        record.put("capturedAt", capturedAt.toString());
        record.put("elapsedMillis", Math.max(0, nowMillis - state.startedAt));
        record.put("opponentGobId", Long.toUnsignedString(gobId));
        if(gui != null) {
            putNullable(record, "characterId", gui.chrid);
            putNullable(record, "world", gui.genus);
        }
        record.put("detail", detail == null ? new JSONObject() : detail);
        CombatLogWriter.append(record, capturedAt);
    }

    private static JSONObject relationState(Fightview.Relation relation) {
        JSONObject state = new JSONObject();
        state.put("giveState", relation.gst);
        state.put("playerInitiative", relation.ip);
        state.put("opponentInitiative", relation.oip);
        state.put("opponentAgilityMinimumMultiplier", relation.minAgi);
        state.put("opponentAgilityMaximumMultiplier", relation.maxAgi);
        state.put("opponentExactAttributesAvailable", false);
        state.put("opponentOpenings", openings(relation.buffs));
        return state;
    }

    private static JSONObject playerSnapshot(GameUI gui) {
        JSONObject player = playerMeters(gui);
        JSONObject stats = new JSONObject();
        putAttribute(stats, gui, "strength", "str");
        putAttribute(stats, gui, "agility", "agi");
        putAttribute(stats, gui, "constitution", "con");
        putAttribute(stats, gui, "perception", "prc");
        putAttribute(stats, gui, "unarmed", "unarmed");
        putAttribute(stats, gui, "melee", "melee");
        player.put("stats", stats);
        String weapon = weaponResource(gui);
        putNullable(player, "weaponResource", weapon);
        return player;
    }

    private static JSONObject playerMeters(GameUI gui) {
        JSONObject player = new JSONObject();
        if(gui == null)
            return player;
        IMeter.HealthState health = IMeter.lastHealthState;
        if(health != null) {
            JSONObject hp = new JSONObject();
            hp.put("soft", health.shp);
            hp.put("hard", health.hhp);
            hp.put("max", health.mhp);
            player.put("health", hp);
        } else {
            putMeter(player, gui, "health", "hp");
        }
        putMeter(player, gui, "stamina", "stam");
        putMeter(player, gui, "energy", "nrj");
        if(gui.fv != null)
            player.put("openings", openings(gui.fv.buffs));
        return player;
    }

    private static JSONObject opponentSnapshot(GameUI gui, Fightview.Relation relation,
                                               String resourceName, long nowMillis) {
        JSONObject opponent = relationState(relation);
        putNullable(opponent, "resource", resourceName);
        Gob gob = gob(gui, relation.gobid);
        if(gob != null)
            putNullable(opponent, "observedWeapon", gob.currentWeapon);
        if(gui != null && gui.ui != null && gui.ui.sess != null) {
            CombatDamageSnapshot damage = CombatDamageTracker.forGlob(gui.ui.sess.glob).snapshot(
                    relation.gobid, resourceName, nowMillis);
            JSONObject observed = new JSONObject();
            observed.put("soft", damage.combatSoftHp());
            observed.put("hard", damage.totalHardHp());
            observed.put("armor", damage.totalArmor());
            observed.put("reliable", damage.estimateReliable());
            opponent.put("observedDamage", observed);
            AnimalHealthCatalog.Entry animal = AnimalHealthCatalog.find(resourceName);
            if(animal != null) {
                AnimalHealthEstimate estimate = AnimalHealthEstimator.estimate(animal, damage);
                JSONObject health = new JSONObject();
                health.put("status", estimate.status().name());
                health.put("label", estimate.label());
                if(estimate.fraction() != null)
                    health.put("fraction", estimate.fraction());
                opponent.put("healthEstimate", health);
            }
        }
        return opponent;
    }

    private static JSONArray openings(Bufflist list) {
        JSONArray openings = new JSONArray();
        if(list == null)
            return openings;
        for(Buff buff : list.children(Buff.class)) {
            try {
                Resource resource = buff.res == null ? null : buff.res.get();
                if(resource == null)
                    continue;
                JSONObject opening = new JSONObject();
                opening.put("resource", resource.name);
                Double meter = buff.ameteri.get();
                if(meter != null)
                    opening.put("percentage", (int)Math.round(meter * 100));
                openings.put(opening);
            } catch(Loading ignored) {
            }
        }
        return openings;
    }

    private static void putAttribute(JSONObject stats, GameUI gui, String label, String key) {
        if(gui == null || gui.ui == null || gui.ui.sess == null)
            return;
        Glob.CAttr attribute = gui.ui.sess.glob.getcattr(key);
        if(attribute == null || (attribute.base == 0 && attribute.comp == 0))
            return;
        JSONObject value = new JSONObject();
        value.put("base", attribute.base);
        value.put("effective", attribute.comp);
        stats.put(label, value);
    }

    private static void putMeter(JSONObject player, GameUI gui, String label, String key) {
        IMeter.Meter meter = gui.getmeter(key, 0);
        if(meter != null)
            player.put(label, meter.a);
    }

    private static String weaponResource(GameUI gui) {
        try {
            Equipory equipory = gui == null ? null : gui.getequipory();
            GItem weapon = equipory == null ? null : equipory.getWeapon();
            Resource resource = weapon == null ? null : weapon.getres();
            return resource == null ? null : resource.name;
        } catch(RuntimeException ignored) {
            return null;
        }
    }

    private static String resourceName(GameUI gui, long gobId) {
        Gob gob = gob(gui, gobId);
        if(gob == null)
            return null;
        try {
            Resource resource = gob.getres();
            return resource == null ? null : resource.name;
        } catch(Loading ignored) {
            return null;
        }
    }

    private static Gob gob(GameUI gui, long gobId) {
        return (gui == null || gui.ui == null || gui.ui.sess == null) ? null :
                gui.ui.sess.glob.oc.getgob(gobId);
    }

    private static void putNullable(JSONObject object, String key, String value) {
        if(value != null && !value.isBlank())
            object.put(key, value);
    }

    private static final class EncounterState {
        final String id;
        final long gobId;
        final long startedAt;
        String resourceName;
        long sequence;
        long nextSampleAt;
        String lastSampleSignature;

        EncounterState(String id, long gobId, long startedAt, String resourceName) {
            this.id = id;
            this.gobId = gobId;
            this.startedAt = startedAt;
            this.resourceName = resourceName;
        }
    }
}
