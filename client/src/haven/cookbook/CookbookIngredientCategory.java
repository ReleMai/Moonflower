package haven.cookbook;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Broad ingredient groups used by the cookbook planner. */
public enum CookbookIngredientCategory {
    ALL("All ingredients"),
    SPICE("Spices"),
    FISH("Fish"),
    MEAT("Meat"),
    VEGETABLE("Vegetables"),
    CROP("Crops"),
    HERB("Herbs"),
    FRUIT("Fruit"),
    MUSHROOM("Mushrooms"),
    DAIRY("Dairy"),
    OTHER("Other");

    public static final List<CookbookIngredientCategory> ALL_VALUES =
            Collections.unmodifiableList(Arrays.asList(values()));

    public final String label;

    CookbookIngredientCategory(String label) {
        this.label = label;
    }

    public boolean matches(CookbookIngredientCategory category) {
        return(this == ALL || this == category);
    }
}
