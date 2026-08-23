package haven.cookbook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Aggregated, evidence-backed ingredient view for planning recipes. */
public final class CookbookIngredientEntry {
    public final String name;
    public final CookbookIngredientCategory category;
    public final String resourceName;
    public final int recipeCount;
    public final List<String> recipes;
    public final List<AttributeValue> averageFeps;
    public final List<RecipeHighlight> recipeHighlights;
    public final List<SpiceBoost> spiceBoosts;
    public final int matchedSpiceComparisons;

    CookbookIngredientEntry(String name, CookbookIngredientCategory category, String resourceName,
                            int recipeCount, List<String> recipes,
                            List<AttributeValue> averageFeps,
                            List<RecipeHighlight> recipeHighlights,
                            List<SpiceBoost> spiceBoosts,
                            int matchedSpiceComparisons) {
        this.name = name;
        this.category = category;
        this.resourceName = resourceName;
        this.recipeCount = recipeCount;
        this.recipes = immutableCopy(recipes);
        this.averageFeps = immutableCopy(averageFeps);
        this.recipeHighlights = immutableCopy(recipeHighlights);
        this.spiceBoosts = immutableCopy(spiceBoosts);
        this.matchedSpiceComparisons = matchedSpiceComparisons;
    }

    public boolean spice() {
        return(category == CookbookIngredientCategory.SPICE);
    }

    public CookbookAttribute mainAttribute() {
        if(averageFeps.isEmpty())
            return(null);
        return(CookbookAttribute.forEvent(averageFeps.get(0).attribute));
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        return(Collections.unmodifiableList(new ArrayList<>(values)));
    }

    public static final class AttributeValue {
        public final String attribute;
        public final double amount;

        AttributeValue(String attribute, double amount) {
            this.attribute = attribute;
            this.amount = amount;
        }
    }

    public static final class SpiceBoost {
        public final String attribute;
        public final double amount;
        public final double percent;

        SpiceBoost(String attribute, double amount, double percent) {
            this.attribute = attribute;
            this.amount = amount;
            this.percent = percent;
        }
    }

    /** Strongest normalized attribute observed for one recipe containing this ingredient. */
    public static final class RecipeHighlight {
        public final long recipeId;
        public final String foodName;
        public final String resourceName;
        public final String attribute;
        public final double amount;

        RecipeHighlight(long recipeId, String foodName, String resourceName,
                        String attribute, double amount) {
            this.recipeId = recipeId;
            this.foodName = foodName;
            this.resourceName = resourceName;
            this.attribute = attribute;
            this.amount = amount;
        }
    }
}
