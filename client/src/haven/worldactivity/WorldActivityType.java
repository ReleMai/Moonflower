package haven.worldactivity;

/**
 * The activity families that can eventually appear on the World Activity
 * Board. Only the first two are wired to server observations in this slice.
 */
public enum WorldActivityType {
    PYRE("Pyre", true),
    LOCALIZED_RESOURCE("Localized resource", true),
    DRYING_RACK("Drying rack", false),
    HERBALIST_TABLE("Herbalist table", false),
    KILN("Kiln", false),
    OVEN("Oven", false),
    SMELTER("Smelter", false),
    GARDEN_POT("Garden pot", false),
    FIELD("Field", false),
    CURIOSITY("Curiosity", false);

    private final String label;
    private final boolean starterSupported;

    WorldActivityType(String label, boolean starterSupported) {
        this.label = label;
        this.starterSupported = starterSupported;
    }

    public String label() {
        return(label);
    }

    public boolean starterSupported() {
        return(starterSupported);
    }
}
