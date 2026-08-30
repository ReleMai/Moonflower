package haven.fishing;

import haven.Utils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Stores a small per-world rack of user-named pole presets. */
final class FishingPresetStore {
    static final int MAX_PRESETS = 8;
    private final String preference;

    FishingPresetStore(String worldId) {
        preference = "fishing-pole-presets-v1/" + (worldId == null ? "" : worldId);
    }

    List<FishingPolePreset> load() {
        List<FishingPolePreset> presets = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(Utils.getpref(preference, "[]"));
            for(int index = 0; index < array.length() && presets.size() < MAX_PRESETS; index++) {
                JSONObject object = array.optJSONObject(index);
                if(object == null)
                    continue;
                FishingPolePreset preset = new FishingPolePreset(
                        object.optString("name", ""), object.optString("pole", ""),
                        object.optString("line", ""), object.optString("hook", ""),
                        object.optString("kind", "bait"), object.optString("consumable", ""));
                if(preset.complete())
                    presets.add(preset);
            }
        } catch(RuntimeException ignored) {
        }
        return(List.copyOf(presets));
    }

    List<FishingPolePreset> save(List<FishingPolePreset> source, FishingPolePreset wanted) {
        List<FishingPolePreset> presets = new ArrayList<>();
        if(source != null) {
            for(FishingPolePreset preset : source) {
                if(preset != null && !preset.name.equalsIgnoreCase(wanted.name))
                    presets.add(preset);
            }
        }
        presets.add(0, wanted);
        if(presets.size() > MAX_PRESETS)
            presets.subList(MAX_PRESETS, presets.size()).clear();
        persist(presets);
        return(List.copyOf(presets));
    }

    List<FishingPolePreset> delete(List<FishingPolePreset> source, FishingPolePreset unwanted) {
        List<FishingPolePreset> presets = new ArrayList<>();
        if(source != null) {
            for(FishingPolePreset preset : source) {
                if(preset != null && preset != unwanted &&
                        (unwanted == null || !preset.name.equalsIgnoreCase(unwanted.name)))
                    presets.add(preset);
            }
        }
        persist(presets);
        return(List.copyOf(presets));
    }

    private void persist(List<FishingPolePreset> presets) {
        JSONArray array = new JSONArray();
        for(FishingPolePreset preset : presets) {
            array.put(new JSONObject()
                    .put("name", preset.name).put("pole", preset.pole)
                    .put("line", preset.line).put("hook", preset.hook)
                    .put("kind", preset.consumableKind).put("consumable", preset.consumable));
        }
        Utils.setpref(preference, array.toString());
    }
}
