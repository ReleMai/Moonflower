package haven.fishing;

/** A locally saved tackle recipe; applying it still uses verified helper preparation. */
public final class FishingPolePreset {
    public final String name;
    public final String pole;
    public final String line;
    public final String hook;
    public final String consumableKind;
    public final String consumable;

    public FishingPolePreset(String name, String pole, String line, String hook,
                             String consumableKind, String consumable) {
        this.name = clean(name);
        this.pole = clean(pole);
        this.line = clean(line);
        this.hook = clean(hook);
        this.consumableKind = "lure".equalsIgnoreCase(consumableKind) ? "lure" : "bait";
        this.consumable = clean(consumable);
    }

    public boolean complete() {
        return(!name.isBlank() && !pole.isBlank() && !line.isBlank() &&
                !hook.isBlank() && !consumable.isBlank());
    }

    public FishingNavigatorModel.RigSpec rig() {
        return(new FishingNavigatorModel.RigSpec(pole, line, hook, consumableKind, consumable));
    }

    public String summary() {
        return(pole + " | " + line + " | " + hook + " | " +
                capitalize(consumableKind) + ": " + consumable);
    }

    private static String clean(String value) {
        return(value == null ? "" : value.trim());
    }

    private static String capitalize(String value) {
        return(value.isBlank() ? "Tackle" : Character.toUpperCase(value.charAt(0)) + value.substring(1));
    }
}
