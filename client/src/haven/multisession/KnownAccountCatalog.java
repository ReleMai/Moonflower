package haven.multisession;

import haven.Utils;
import org.json.JSONArray;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads only display names from MoonFlower's legacy saved-account preference.
 * The password portion is deliberately neither returned nor retained.
 */
public final class KnownAccountCatalog {
    private static final String LEGACY_SEPARATOR = "(ಠ‿ಠ)";

    private KnownAccountCatalog() {
    }

    public static List<String> labels() {
        String encoded = Utils.getpref("savedAccounts", null);
        if(encoded == null || encoded.isBlank())
            return(List.of());
        try {
            JSONArray stored = new JSONArray(encoded);
            String[] records = new String[stored.length()];
            for(int index = 0; index < records.length; index++)
                records[index] = stored.getString(index);
            return(labelsFrom(records));
        } catch(RuntimeException malformedPreference) {
            return(List.of());
        }
    }

    static List<String> labelsFrom(String[] records) {
        if(records == null || records.length == 0)
            return(List.of());
        Set<String> labels = new LinkedHashSet<>();
        for(String record : records) {
            if(record == null)
                continue;
            int split = record.indexOf(LEGACY_SEPARATOR);
            String label = ((split < 0) ? record : record.substring(0, split)).trim();
            if(!label.isEmpty())
                labels.add(label);
        }
        return(List.copyOf(new ArrayList<>(labels)));
    }
}
