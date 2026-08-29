package haven.fishing;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Reads the server-provided fish percentages saved with one fishing attempt. */
public final class FishingChanceTable {
    private FishingChanceTable() {
    }

    public static List<FishingChoice> parse(String rowsJson) {
        if(rowsJson == null || rowsJson.isBlank() || "[]".equals(rowsJson.trim()))
            return(List.of());
        try {
            JSONArray rows = new JSONArray(rowsJson);
            List<FishingChoice> choices = new ArrayList<>();
            for(int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i);
                if(row == null)
                    continue;
                String fish = row.optString("fish", "").trim();
                Integer result = percent(row, "final");
                if(fish.isEmpty() || result == null)
                    continue;
                choices.add(new FishingChoice(fish, percent(row, "gear"),
                        percent(row, "lure"), result));
            }
            choices.sort(highestFirst());
            return(List.copyOf(choices));
        } catch(RuntimeException malformed) {
            return(List.of());
        }
    }

    public static FishingChoice forFish(FishingObservation observation) {
        if(observation == null)
            return(null);
        String wanted = normalize(observation.fishName);
        for(FishingChoice choice : parse(observation.choiceRowsJson)) {
            if(normalize(choice.fishName).equals(wanted))
                return(choice);
        }
        return(null);
    }

    public static Integer finalPercent(FishingObservation observation) {
        FishingChoice choice = forFish(observation);
        return(choice == null ? null : choice.finalPercent);
    }

    public static Comparator<FishingChoice> highestFirst() {
        return(Comparator.comparingInt((FishingChoice choice) ->
                        choice.finalPercent == null ? Integer.MIN_VALUE : choice.finalPercent)
                .reversed().thenComparing(choice -> choice.fishName,
                        String.CASE_INSENSITIVE_ORDER));
    }

    public static String compact(List<FishingChoice> choices, int limit) {
        if(choices == null || choices.isEmpty())
            return("chance unavailable");
        StringBuilder text = new StringBuilder();
        int shown = Math.min(Math.max(limit, 1), choices.size());
        for(int i = 0; i < shown; i++) {
            if(i > 0)
                text.append(" | ");
            FishingChoice choice = choices.get(i);
            text.append(choice.fishName).append(' ').append(choice.finalPercent).append('%');
        }
        if(choices.size() > shown)
            text.append(" | +").append(choices.size() - shown).append(" more");
        return(text.toString());
    }

    private static Integer percent(JSONObject row, String key) {
        Object value = row.opt(key);
        return(value instanceof Number ? ((Number)value).intValue() : null);
    }

    private static String normalize(String value) {
        return(value == null ? "" : value.trim().toLowerCase(Locale.ROOT));
    }
}
