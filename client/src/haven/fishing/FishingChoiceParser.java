package haven.fishing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses a fishing row by meaning first and checked percentage order second. */
public final class FishingChoiceParser {
    private static final Pattern PERCENT = Pattern.compile("(-?\\d+)\\s*%");

    private FishingChoiceParser() {
    }

    public static FishingChoice parse(List<String> labels) {
        if(labels == null || labels.isEmpty())
            return(null);
        String fishName = "";
        Integer gear = null;
        Integer lure = null;
        Integer result = null;
        List<Integer> unlabelled = new ArrayList<>();
        for(String raw : labels) {
            String text = raw == null ? "" : raw.trim();
            if(text.isEmpty())
                continue;
            Matcher matcher = PERCENT.matcher(text);
            Integer percent = matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
            if(percent == null) {
                if(fishName.isEmpty())
                    fishName = text;
                continue;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            if(lower.contains("gear") || lower.contains("hook") || lower.contains("line"))
                gear = percent;
            else if(lower.contains("lure") || lower.contains("bait"))
                lure = percent;
            else if(lower.contains("final") || lower.contains("chance") || lower.contains("total"))
                result = percent;
            else
                unlabelled.add(percent);
        }
        int next = 0;
        if(gear == null && next < unlabelled.size())
            gear = unlabelled.get(next++);
        if(lure == null && next < unlabelled.size())
            lure = unlabelled.get(next++);
        if(result == null && !unlabelled.isEmpty())
            result = unlabelled.get(unlabelled.size() - 1);
        if(fishName.isEmpty() || result == null)
            return(null);
        return(new FishingChoice(fishName, gear, lure, result));
    }
}
