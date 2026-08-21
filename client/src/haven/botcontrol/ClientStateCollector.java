package haven.botcontrol;

import haven.BAttrWnd;
import haven.CharWnd;
import haven.Coord;
import haven.Equipory;
import haven.GameUI;
import haven.Glob;
import haven.IMeter;
import haven.PUtils;
import haven.QuestWnd;
import haven.Resource;
import haven.SAttrWnd;
import haven.SkillWnd;
import haven.WItem;
import haven.Widget;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClientStateCollector {
    private static final Coord TELEMETRY_ICON_SIZE = new Coord(32, 32);
    private final Map<Integer, JSONArray> questConditionsById = new HashMap<>();
    private final Map<String, String> iconDataByResource = new HashMap<>();
    private long lastQuestHydrationAt;
    private int lastHydratedQuestId = -1;

    public JSONObject collect(GameUI gui, String botId, String currentTaskId, String currentAction, boolean automationPaused) {
        return collectSnapshot(gui, botId, currentTaskId, currentAction, automationPaused);
    }

    public JSONObject collectFastUpdate(GameUI gui, String botId, String currentTaskId, String currentAction, boolean automationPaused) {
        JSONObject state = new JSONObject();
        state.put("botId", botId);
        state.put("sessionStatus", sessionStatus(gui));
        state.put("screen", "GAME");
        state.put("automationPaused", automationPaused);
        state.put("capturedAt", Instant.now().toString());
        putPosition(gui, state);
        state.put("health", health(gui));
        state.put("stamina", meter(gui, "stam", 0));
        state.put("energy", meter(gui, "nrj", 0));
        state.put("currentTask", currentTask(currentTaskId, currentAction));
        return state;
    }

    public JSONObject collectSnapshot(GameUI gui, String botId, String currentTaskId, String currentAction, boolean automationPaused) {
        harvestQuestConditions(gui);
        hydrateMissingQuestDetails(gui);

        JSONObject state = new JSONObject();
        state.put("botId", botId);
        state.put("sessionStatus", sessionStatus(gui));
        state.put("screen", "GAME");
        state.put("characterName", gui.chrid == null ? "" : gui.chrid);
        state.put("worldName", gui.genus == null ? "" : gui.genus);
        state.put("automationPaused", automationPaused);
        state.put("capturedAt", Instant.now().toString());

        putPosition(gui, state);

        state.put("health", health(gui));
        state.put("stamina", meter(gui, "stam", 0));
        state.put("energy", meter(gui, "nrj", 0));
        state.put("inventory", inventory(gui));
        state.put("equipment", equipment(gui));
        state.put("currentTask", currentTask(currentTaskId, currentAction));
        state.put("routeNames", routeNames(gui));
        state.put("routeInfo", routeInfo(gui));

        Map<String, Integer> attributes = collectAttributes(gui);
        Map<String, Integer> skills = collectSkillValues(gui);
        JSONArray knownSkills = collectKnownSkills(gui);
        JSONArray credos = collectCredos(gui);

        state.put("learningPoints", gui.chrwdg != null ? gui.chrwdg.exp : 0);
        state.put("experience", gui.chrwdg != null ? gui.chrwdg.enc : 0);
        state.put("attributes", new JSONObject(attributes));
        state.put("attributeDetails", collectAttributeDetails(gui));
        state.put("skills", new JSONObject(skills));
        state.put("skillDetails", collectSkillDetails(gui));
        state.put("knownSkills", knownSkills);
        state.put("knownSkillDetails", collectKnownSkillDetails(gui));
        state.put("credos", credos);
        state.put("credoDetails", collectCredoDetails(gui));
        state.put("activeSkills", compatibleActiveSkills(knownSkills, credos));
        state.put("visibleStats", visibleStats(attributes, skills, gui));
        state.put("selectedQuestId", selectedQuestId(gui));
        state.put("currentQuests", currentQuests(gui));
        state.put("equipmentDetails", equipmentDetails(gui));

        return state;
    }

    private String sessionStatus(GameUI gui) {
        return gui.ui != null && gui.ui.sess != null ? "CONNECTED" : "DISCONNECTED";
    }

    private void putPosition(GameUI gui, JSONObject state) {
        if (gui.map != null && gui.map.player() != null) {
            JSONObject position = new JSONObject();
            position.put("x", gui.map.player().rc.x);
            position.put("y", gui.map.player().rc.y);
            position.put("gridId", gui.map.player().glob != null ? String.valueOf(gui.map.player().glob.hashCode()) : "");
            state.put("position", position);
        }
    }

    private JSONObject inventory(GameUI gui) {
        JSONObject inventory = new JSONObject();
        int itemCount = 0;
        JSONArray inventoryItems = new JSONArray();
        JSONArray inventoryItemDetails = new JSONArray();
        if (gui.maininv != null) {
            for (Widget child = gui.maininv.lchild; child != null; child = child.prev) {
                if (child instanceof WItem) {
                    WItem item = (WItem) child;
                    itemCount++;
                    if (inventoryItems.length() < 24) {
                        try {
                            inventoryItems.put(item.item.getname());
                        } catch (RuntimeException ignored) {
                        }
                    }
                    if (inventoryItemDetails.length() < 24) {
                        JSONObject detail = itemDetail(item, inventoryItemDetails.length());
                        if (detail != null) {
                            inventoryItemDetails.put(detail);
                        }
                    }
                }
            }
            inventory.put("freeSlots", Math.max(0, (gui.maininv.isz.x * gui.maininv.isz.y) - itemCount));
            inventory.put("occupiedSlots", itemCount);
        }
        inventory.put("itemCount", itemCount);
        inventory.put("items", inventoryItems);
        inventory.put("handItem", gui.vhand != null && gui.vhand.item != null ? gui.vhand.item.getname() : "");
        inventory.put("itemDetails", inventoryItemDetails);
        if (gui.vhand != null && gui.vhand.item != null) {
            JSONObject handItemDetail = itemDetail(gui.vhand, -1);
            if (handItemDetail != null) {
                inventory.put("handItemDetail", handItemDetail);
            }
        }
        return inventory;
    }

    private JSONArray equipment(GameUI gui) {
        JSONArray equipmentItems = new JSONArray();
        Equipory equipory = gui.getequipory();
        if (equipory == null || equipory.slots == null) {
            return equipmentItems;
        }
        for (WItem slot : equipory.slots) {
            if (slot != null && slot.item != null) {
                try {
                    equipmentItems.put(slot.item.getname());
                } catch (RuntimeException ignored) {
                }
            }
        }
        return equipmentItems;
    }

    private JSONArray equipmentDetails(GameUI gui) {
        JSONArray details = new JSONArray();
        Equipory equipory = gui.getequipory();
        if (equipory == null || equipory.slots == null) {
            return details;
        }
        for (int slotIndex = 0; slotIndex < equipory.slots.length; slotIndex++) {
            WItem slot = equipory.slots[slotIndex];
            JSONObject detail = itemDetail(slot, slotIndex);
            if (detail != null) {
                details.put(detail);
            }
        }
        return details;
    }

    private JSONObject currentTask(String currentTaskId, String currentAction) {
        JSONObject currentTask = new JSONObject();
        currentTask.put("taskId", currentTaskId == null ? "" : currentTaskId);
        currentTask.put("actionType", currentAction == null ? "" : currentAction);
        return currentTask;
    }

    private JSONArray routeNames(GameUI gui) {
        JSONArray routes = new JSONArray();
        if (gui.map != null && gui.map.checkpointManager != null) {
            routes.put("active-checkpoint-route");
        }
        return routes;
    }

    private JSONObject routeInfo(GameUI gui) {
        JSONObject routeInfo = new JSONObject();
        if (gui.map != null && gui.map.checkpointManager != null) {
            routeInfo.put("checkpointCount", gui.map.checkpointManager.checkpointList.listitems());
            routeInfo.put("distanceRemaining", gui.map.checkpointManager.getWholeDistance());
            routeInfo.put("active", true);
        } else {
            routeInfo.put("checkpointCount", 0);
            routeInfo.put("distanceRemaining", 0);
            routeInfo.put("active", false);
        }
        return routeInfo;
    }

    private JSONObject health(GameUI gui) {
        JSONObject result = meter(gui, "hp", 0);
        IMeter.HealthState state = IMeter.lastHealthState;
        if (state != null) {
            result.put("current", state.shp);
            result.put("max", state.mhp);
            result.put("percentage", state.softPercentage / 100.0);
            result.put("text", state.displayText);
            result.put("shp", state.shp);
            result.put("hhp", state.hhp);
            result.put("mhp", state.mhp);
            result.put("softPercentage", state.softPercentage);
            result.put("hardPercentage", state.hardPercentage);
            result.put("displayText", state.displayText);
        }
        return result;
    }

    private JSONObject meter(GameUI gui, String name, int idx) {
        JSONObject result = new JSONObject();
        IMeter.Meter meter = gui.getmeter(name, idx);
        if (meter == null) {
            return result;
        }
        result.put("current", meter.a);
        result.put("max", 1.0);
        result.put("percentage", meter.a);
        result.put("text", Math.round(meter.a * 100.0) + "%");
        return result;
    }

    private Map<String, Integer> collectAttributes(GameUI gui) {
        Map<String, Integer> attributes = new LinkedHashMap<>();
        if (gui.chrwdg != null && gui.chrwdg.battr != null && gui.chrwdg.battr.attrs != null) {
            for (BAttrWnd.Attr attr : gui.chrwdg.battr.attrs) {
                attributes.put(displayName(attr.res, attr.nm), attr.attr.comp);
            }
            return attributes;
        }

        String[] fallback = {"str", "agi", "int", "con", "prc", "csm", "dex", "wil", "psy"};
        if (gui.ui != null && gui.ui.sess != null && gui.ui.sess.glob != null) {
            for (String attr : fallback) {
                Glob.CAttr cattr = gui.ui.sess.glob.getcattr(attr);
                if (cattr != null && (cattr.base != 0 || cattr.comp != 0)) {
                    attributes.put(fallbackDisplayName(attr), cattr.comp);
                }
            }
        }
        return attributes;
    }

    private JSONArray collectAttributeDetails(GameUI gui) {
        JSONArray details = new JSONArray();
        if (gui.chrwdg != null && gui.chrwdg.battr != null && gui.chrwdg.battr.attrs != null) {
            for (BAttrWnd.Attr attr : gui.chrwdg.battr.attrs) {
                details.put(statDetail(attr.nm, displayName(attr.res, attr.nm), attr.attr.comp, attr.res));
            }
            return details;
        }

        String[] fallback = {"str", "agi", "int", "con", "prc", "csm", "dex", "wil", "psy"};
        if (gui.ui != null && gui.ui.sess != null && gui.ui.sess.glob != null) {
            for (String attr : fallback) {
                Glob.CAttr cattr = gui.ui.sess.glob.getcattr(attr);
                if (cattr != null && (cattr.base != 0 || cattr.comp != 0)) {
                    details.put(statDetail(attr, fallbackDisplayName(attr), cattr.comp, resource(cattr)));
                }
            }
        }
        return details;
    }

    private Map<String, Integer> collectSkillValues(GameUI gui) {
        Map<String, Integer> skills = new LinkedHashMap<>();
        if (gui.chrwdg != null && gui.chrwdg.sattr != null && gui.chrwdg.sattr.attrs != null) {
            for (SAttrWnd.SAttr attr : gui.chrwdg.sattr.attrs) {
                skills.put(displayName(attr.res, attr.nm), attr.attr.comp);
            }
            return skills;
        }

        String[] fallback = {"unarmed", "melee", "ranged", "explore", "stealth", "sewing", "smithing", "masonry", "carpentry", "cooking", "farming", "survive", "lore", "swim", "swimming", "mining"};
        if (gui.ui != null && gui.ui.sess != null && gui.ui.sess.glob != null) {
            for (String attr : fallback) {
                Glob.CAttr cattr = gui.ui.sess.glob.getcattr(attr);
                if (cattr != null && (cattr.base != 0 || cattr.comp != 0)) {
                    skills.put(fallbackDisplayName(attr), cattr.comp);
                }
            }
        }
        return skills;
    }

    private JSONArray collectSkillDetails(GameUI gui) {
        JSONArray details = new JSONArray();
        if (gui.chrwdg != null && gui.chrwdg.sattr != null && gui.chrwdg.sattr.attrs != null) {
            for (SAttrWnd.SAttr attr : gui.chrwdg.sattr.attrs) {
                details.put(statDetail(attr.nm, displayName(attr.res, attr.nm), attr.attr.comp, attr.res));
            }
            return details;
        }

        String[] fallback = {"unarmed", "melee", "ranged", "explore", "stealth", "sewing", "smithing", "masonry", "carpentry", "cooking", "farming", "survive", "lore", "swim", "swimming", "mining"};
        if (gui.ui != null && gui.ui.sess != null && gui.ui.sess.glob != null) {
            for (String attr : fallback) {
                Glob.CAttr cattr = gui.ui.sess.glob.getcattr(attr);
                if (cattr != null && (cattr.base != 0 || cattr.comp != 0)) {
                    details.put(statDetail(attr, fallbackDisplayName(attr), cattr.comp, resource(cattr)));
                }
            }
        }
        return details;
    }

    private JSONArray collectKnownSkills(GameUI gui) {
        JSONArray skills = new JSONArray();
        if (gui.chrwdg == null || gui.chrwdg.skill == null || gui.chrwdg.skill.skg == null || gui.chrwdg.skill.skg.csk == null || gui.chrwdg.skill.skg.csk.items == null) {
            return skills;
        }
        for (SkillWnd.Skill skill : gui.chrwdg.skill.skg.csk.items) {
            skills.put(skill.nm);
        }
        return skills;
    }

    private JSONArray collectKnownSkillDetails(GameUI gui) {
        JSONArray details = new JSONArray();
        if (gui.chrwdg == null || gui.chrwdg.skill == null || gui.chrwdg.skill.skg == null || gui.chrwdg.skill.skg.csk == null || gui.chrwdg.skill.skg.csk.items == null) {
            return details;
        }
        for (SkillWnd.Skill skill : gui.chrwdg.skill.skg.csk.items) {
            Resource resource = resource(skill.res);
            details.put(labeledIconDetail(skill.nm, displayName(resource, skill.nm), resource, false));
        }
        return details;
    }

    private JSONArray collectCredos(GameUI gui) {
        JSONArray credos = new JSONArray();
        if (gui.chrwdg == null || gui.chrwdg.skill == null || gui.chrwdg.skill.credos == null) {
            return credos;
        }
        List<SkillWnd.Credo> acquired = gui.chrwdg.skill.credos.ccr;
        if (acquired != null) {
            for (SkillWnd.Credo credo : acquired) {
                credos.put(credo.nm);
            }
        }
        if (gui.chrwdg.skill.credos.pcr != null) {
            credos.put("Pursuing: " + gui.chrwdg.skill.credos.pcr.nm);
        }
        return credos;
    }

    private JSONArray collectCredoDetails(GameUI gui) {
        JSONArray details = new JSONArray();
        if (gui.chrwdg == null || gui.chrwdg.skill == null || gui.chrwdg.skill.credos == null) {
            return details;
        }
        List<SkillWnd.Credo> acquired = gui.chrwdg.skill.credos.ccr;
        if (acquired != null) {
            for (SkillWnd.Credo credo : acquired) {
                Resource resource = resource(credo.res);
                details.put(labeledIconDetail(credo.nm, displayName(resource, credo.nm), resource, false));
            }
        }
        if (gui.chrwdg.skill.credos.pcr != null) {
            SkillWnd.Credo pursuing = gui.chrwdg.skill.credos.pcr;
            Resource resource = resource(pursuing.res);
            details.put(labeledIconDetail(pursuing.nm, displayName(resource, pursuing.nm), resource, true));
        }
        return details;
    }

    private JSONArray compatibleActiveSkills(JSONArray knownSkills, JSONArray credos) {
        JSONArray combined = new JSONArray();
        for (int index = 0; index < knownSkills.length(); index++) {
            combined.put(knownSkills.get(index));
        }
        for (int index = 0; index < credos.length(); index++) {
            String value = String.valueOf(credos.get(index));
            combined.put(value.startsWith("Pursuing: ") ? value : "Credo: " + value);
        }
        return combined;
    }

    private JSONObject visibleStats(Map<String, Integer> attributes, Map<String, Integer> skills, GameUI gui) {
        JSONObject stats = new JSONObject();
        for (Map.Entry<String, Integer> entry : attributes.entrySet()) {
            stats.put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Integer> entry : skills.entrySet()) {
            stats.put(entry.getKey(), entry.getValue());
        }
        if (gui.chrwdg != null) {
            stats.put("learningPoints", gui.chrwdg.exp);
            stats.put("experience", gui.chrwdg.enc);
        }
        return stats;
    }

    private Integer selectedQuestId(GameUI gui) {
        if (gui.chrwdg == null || gui.chrwdg.quest == null || gui.chrwdg.quest.quest == null) {
            return null;
        }
        return gui.chrwdg.quest.quest.questid();
    }

    private JSONArray currentQuests(GameUI gui) {
        JSONArray quests = new JSONArray();
        if (gui.chrwdg == null || gui.chrwdg.quest == null || gui.chrwdg.quest.cqst == null) {
            return quests;
        }
        for (QuestWnd.Quest quest : gui.chrwdg.quest.cqst.quests) {
            JSONObject data = new JSONObject();
            data.put("id", quest.id);
            data.put("title", safeQuestTitle(quest));
            data.put("done", quest.done);
            data.put("mtime", quest.mtime);
            Resource resource = resource(quest.res);
            putIconMeta(data, resource, safeQuestTitle(quest), "quest", safeQuestTitle(quest), "", summary(resource, safeQuestTitle(quest)));
            JSONArray conditions = questConditionsById.get(quest.id);
            if (conditions != null) {
                data.put("conditions", new JSONArray(conditions.toString()));
            }
            quests.put(data);
        }
        return quests;
    }

    private void harvestQuestConditions(GameUI gui) {
        if (gui.chrwdg == null || gui.chrwdg.quest == null) {
            return;
        }
        for (Widget widget : gui.chrwdg.quest.questbox.children()) {
            if (!(widget instanceof QuestWnd.Quest.Box)) {
                continue;
            }
            QuestWnd.Quest.Box box = (QuestWnd.Quest.Box) widget;
            if (box.cond == null || box.cond.length == 0) {
                continue;
            }
            JSONArray conditions = new JSONArray();
            for (QuestWnd.Quest.Condition condition : box.cond) {
                JSONObject data = new JSONObject();
                data.put("description", condition.desc);
                data.put("done", condition.done);
                data.put("status", condition.status == null ? "" : condition.status);
                conditions.put(data);
            }
            questConditionsById.put(box.id(), conditions);
        }
    }

    private void hydrateMissingQuestDetails(GameUI gui) {
        if (gui.chrwdg == null || gui.chrwdg.quest == null || gui.chrwdg.quest.cqst == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if ((now - lastQuestHydrationAt) < 1000L) {
            return;
        }

        Integer targetQuestId = null;
        for (QuestWnd.Quest quest : gui.chrwdg.quest.cqst.quests) {
            JSONArray existing = questConditionsById.get(quest.id);
            if (existing == null || existing.length() == 0) {
                if (quest.id != lastHydratedQuestId) {
                    targetQuestId = quest.id;
                    break;
                }
                if (targetQuestId == null) {
                    targetQuestId = quest.id;
                }
            }
        }

        if (targetQuestId != null) {
            gui.chrwdg.wdgmsg("qsel", targetQuestId);
            lastHydratedQuestId = targetQuestId;
            lastQuestHydrationAt = now;
        }
    }

    private String safeQuestTitle(QuestWnd.Quest quest) {
        try {
            return quest.title();
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private String displayName(Resource res, String fallback) {
        if (res == null) {
            return fallbackDisplayName(fallback);
        }
        try {
            return res.flayer(Resource.tooltip).t;
        } catch (RuntimeException ex) {
            return fallbackDisplayName(fallback);
        }
    }

    private JSONObject statDetail(String key, String label, int value, Resource resource) {
        JSONObject detail = new JSONObject();
        detail.put("key", key);
        detail.put("label", label);
        detail.put("value", value);
        String kind = isAttributeKey(key) ? "attribute" : "ability";
        String wikiTitle = isAttributeKey(key) ? "Attributes" : "Abilities";
        putIconMeta(detail, resource, label, kind, wikiTitle, label, summary(resource, key));
        return detail;
    }

    private JSONObject labeledIconDetail(String key, String label, Resource resource, boolean pursuing) {
        JSONObject detail = new JSONObject();
        detail.put("key", key);
        detail.put("label", label);
        detail.put("pursuing", pursuing);
        putIconMeta(detail, resource, label, pursuing ? "credo" : "skill", label, "", summary(resource, key));
        return detail;
    }

    private JSONObject itemDetail(WItem item, int slotIndex) {
        if (item == null || item.item == null) {
            return null;
        }
        try {
            Resource resource = item.item.resource();
            String name = item.item.getname();
            JSONObject detail = new JSONObject();
            detail.put("name", name);
            detail.put("slotIndex", slotIndex);
            putIconMeta(detail, resource, name, "item", name, "", summary(resource, name));
            return detail;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void putIconMeta(JSONObject target, Resource resource, String fallbackLabel, String kind, String wikiTitle, String wikiSection, String summary) {
        target.put("kind", kind);
        if (resource != null) {
            target.put("resourceName", resource.name);
            String icon = iconData(resource);
            if (!icon.isBlank()) {
                target.put("icon", icon);
            }
        }
        String label = fallbackLabel == null ? "" : fallbackLabel;
        if (!label.isBlank()) {
            target.put("wikiUrl", wikiUrl(label));
        }
        if (wikiTitle != null && !wikiTitle.isBlank()) {
            target.put("wikiTitle", wikiTitle);
        }
        if (wikiSection != null && !wikiSection.isBlank()) {
            target.put("wikiSection", wikiSection);
        }
        if (summary != null && !summary.isBlank()) {
            target.put("summary", summary);
        }
    }

    private Resource resource(Glob.CAttr attr) {
        try {
            return attr.res().get();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Resource resource(haven.Indir<Resource> resource) {
        if (resource == null) {
            return null;
        }
        try {
            return resource.get();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String iconData(Resource resource) {
        if (resource == null) {
            return "";
        }
        String cached = iconDataByResource.get(resource.name);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        try {
            Resource.Image layer = resource.flayer(Resource.imgc);
            if (layer == null || layer.img == null) {
                return "";
            }
            BufferedImage scaled = PUtils.convolvedown(layer.img, TELEMETRY_ICON_SIZE, CharWnd.iconfilter);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            ImageIO.write(scaled, "png", buffer);
            String encoded = "data:image/png;base64," + Base64.getEncoder().encodeToString(buffer.toByteArray());
            iconDataByResource.put(resource.name, encoded);
            return encoded;
        } catch (RuntimeException | IOException ex) {
            return "";
        }
    }

    private String wikiUrl(String label) {
        return "https://ringofbrodgar.com/wiki/" + label.trim().replace(" ", "_");
    }

    private String summary(Resource resource, String fallbackKey) {
        if (resource != null) {
            try {
                Resource.Pagina pagina = resource.layer(Resource.pagina);
                if (pagina != null && pagina.text != null) {
                    String normalized = normalizeSummary(pagina.text);
                    if (!normalized.isBlank()) {
                        return normalized;
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }
        return fallbackSummary(fallbackKey);
    }

    private String normalizeSummary(String value) {
        String normalized = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        if (normalized.length() > 280) {
            return normalized.substring(0, 277) + "...";
        }
        return normalized;
    }

    private boolean isAttributeKey(String key) {
        return switch (key) {
            case "str", "agi", "int", "con", "prc", "csm", "dex", "wil", "psy",
                 "Strength", "Agility", "Intelligence", "Constitution", "Perception", "Charisma", "Dexterity", "Will", "Psyche" -> true;
            default -> false;
        };
    }

    private String fallbackSummary(String key) {
        return switch (key) {
            case "str", "Strength" -> "Raw physical power used for melee damage, mining, object destruction, and carrying heavy loads.";
            case "agi", "Agility" -> "Movement and dexterity attribute used in combat defense, travel, and animal handling.";
            case "int", "Intelligence" -> "Mental attribute tied to study, planning, and several softcap formulas.";
            case "con", "Constitution" -> "Toughness and survivability attribute that influences health and physical resilience.";
            case "prc", "Perception" -> "Senses and accuracy attribute used for ranging, tracking, spotting, and exploration formulas.";
            case "csm", "Charisma" -> "Social and leadership attribute used in authority and several support formulas.";
            case "dex", "Dexterity" -> "Fine motor control attribute used in crafting and many precision softcaps.";
            case "wil", "Will" -> "Resolve and focus attribute used in mental resistance and several magical or crafting formulas.";
            case "psy", "Psyche" -> "Mystic intuition attribute tied to curiosities, magic-adjacent interactions, and some item formulas.";
            case "unarmed", "Unarmed" -> "Determines the effectiveness of unarmed combat moves.";
            case "melee", "Melee" -> "Determines the effectiveness of melee combat moves.";
            case "ranged", "Marksmanship" -> "Ranged combat ability that improves damage and accuracy and softcaps bows, slings, and arrows.";
            case "explore", "Exploration" -> "Determines foragable visibility and scent tracking using Perception multiplied by Exploration.";
            case "stealth", "Stealth" -> "Affects how hidden your scents and criminal traces are and interacts with tracking formulas.";
            case "sewing", "Sewing" -> "Softcaps leather and fabric crafting, often together with Dexterity.";
            case "smithing", "Smithing" -> "Drives metalworking quality formulas and many forge-related crafts.";
            case "masonry", "Masonry" -> "Softcaps stonework and construction recipes involving brick, stone, and clay.";
            case "carpentry", "Carpentry" -> "Softcaps woodcraft and building recipes involving lumber and boards.";
            case "cooking", "Cooking" -> "Improves cooking quality and several food preparation formulas.";
            case "farming", "Farming" -> "Controls crop and fieldwork quality and many agriculture actions.";
            case "survive", "Survival" -> "Controls butchering, wilderness gathering, fishing support, and several outdoor quality formulas.";
            case "lore", "Lore" -> "General wisdom stat used in magical, curiosity, and many advanced crafting formulas.";
            case "swim", "swimming", "Swimming" -> "Movement ability that governs how effectively the character swims.";
            case "mining", "Mining" -> "Underground work ability used in mining-related tasks and quality formulas.";
            default -> "";
        };
    }

    private String fallbackDisplayName(String key) {
        return switch (key) {
            case "str" -> "Strength";
            case "agi" -> "Agility";
            case "int" -> "Intelligence";
            case "con" -> "Constitution";
            case "prc" -> "Perception";
            case "csm" -> "Charisma";
            case "dex" -> "Dexterity";
            case "wil" -> "Will";
            case "psy" -> "Psyche";
            case "unarmed" -> "Unarmed";
            case "melee" -> "Melee";
            case "ranged" -> "Marksmanship";
            case "explore" -> "Exploration";
            case "stealth" -> "Stealth";
            case "sewing" -> "Sewing";
            case "smithing" -> "Smithing";
            case "masonry" -> "Masonry";
            case "carpentry" -> "Carpentry";
            case "cooking" -> "Cooking";
            case "farming" -> "Farming";
            case "survive" -> "Survival";
            case "lore" -> "Lore";
            case "swim", "swimming" -> "Swimming";
            case "mining" -> "Mining";
            default -> key;
        };
    }
}
