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
            List<Integer> percentages = new ArrayList<>();
            int firstPercentAt = -1;
            while(matcher.find()) {
                if(firstPercentAt < 0)
                    firstPercentAt = matcher.start();
                percentages.add(Integer.parseInt(matcher.group(1)));
            }
            if(percentages.isEmpty()) {
                if(fishName.isEmpty())
                    fishName = trimFishName(text);
                continue;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            if(fishName.isEmpty() && !semanticLabel(lower)) {
                String prefix = trimFishName(text.substring(0, firstPercentAt));
                if(!prefix.isEmpty())
                    fishName = prefix;
            }
            if(lower.contains("gear") || lower.contains("hook") || lower.contains("line"))
                gear = percentages.get(0);
            else if(lower.contains("lure") || lower.contains("bait"))
                lure = percentages.get(0);
            else if(lower.contains("final") || lower.contains("chance") || lower.contains("total"))
                result = percentages.get(percentages.size() - 1);
            else
                unlabelled.addAll(percentages);
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

    private static boolean semanticLabel(String lower) {
        return(lower.contains("gear") || lower.contains("hook") || lower.contains("line") ||
                lower.contains("lure") || lower.contains("bait") || lower.contains("final") ||
                lower.contains("chance") || lower.contains("total"));
    }

    private static String trimFishName(String value) {
        return(value == null ? "" : value.trim().replaceFirst("[:=\\-]+$", "").trim());
    }
}
