package haven.fishing;

import java.util.Collections;
import java.util.List;

/** Immutable view of tackle currently reachable by the fishing helper. */
public final class FishingTackleCatalog {
    public final List<String> poles;
    public final List<String> lines;
    public final List<String> hooks;
    public final List<String> baits;
    public final List<String> lures;
    public final String pole;
    public final String line;
    public final String hook;
    public final String consumable;
    public final boolean lure;

    public FishingTackleCatalog(List<String> poles, List<String> lines, List<String> hooks,
                                List<String> baits, List<String> lures, String pole,
                                String line, String hook, String consumable, boolean lure) {
        this.poles = copy(poles);
        this.lines = copy(lines);
        this.hooks = copy(hooks);
        this.baits = copy(baits);
        this.lures = copy(lures);
        this.pole = clean(pole);
        this.line = clean(line);
        this.hook = clean(hook);
        this.consumable = clean(consumable);
        this.lure = lure;
    }

    public List<String> consumables() {
        return(lure ? lures : baits);
    }

    public FishingNavigatorModel.RigSpec rig() {
        return(new FishingNavigatorModel.RigSpec(pole, line, hook,
                lure ? "lure" : "bait", consumable));
    }

    public boolean complete() {
        return(!pole.isBlank() && !line.isBlank() && !hook.isBlank() && !consumable.isBlank());
    }

    private static List<String> copy(List<String> values) {
        return(values == null ? Collections.emptyList() : List.copyOf(values));
    }

    private static String clean(String value) {
        return(value == null ? "" : value.trim());
    }
}
