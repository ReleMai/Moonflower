package haven.cookbook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** All locally known ingredient recipes that produce the same named food. */
final class CookbookRecipeGroup {
    final String key;
    final String itemName;
    final String resourceName;
    final List<CookbookEntry> recipes;

    private CookbookRecipeGroup(String key, List<CookbookEntry> recipes) {
        this.key = key;
        this.recipes = Collections.unmodifiableList(new ArrayList<>(recipes));
        CookbookEntry first = recipes.get(0);
        this.itemName = first.itemName;
        this.resourceName = first.resourceName;
    }

    static List<CookbookRecipeGroup> group(List<CookbookEntry> entries) {
        Map<String, List<CookbookEntry>> grouped = new LinkedHashMap<>();
        for(CookbookEntry entry : entries) {
            String key = entry.itemName.trim().replaceAll("\\s+", " ")
                    .toLowerCase(Locale.ROOT);
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
        }
        List<CookbookRecipeGroup> result = new ArrayList<>();
        for(Map.Entry<String, List<CookbookEntry>> value : grouped.entrySet()) {
            value.getValue().sort(Comparator
                    .comparing((CookbookEntry entry) -> entry.ingredients,
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(entry -> entry.modifiers, String.CASE_INSENSITIVE_ORDER)
                    .thenComparingLong(entry -> entry.recipeId));
            result.add(new CookbookRecipeGroup(value.getKey(), value.getValue()));
        }
        return(result);
    }

    CookbookEntry representative(CookbookRecipeSort sort, boolean allRecipes) {
        return(recipes.stream().max(Comparator
                .comparingDouble((CookbookEntry entry) -> value(entry, sort, allRecipes))
                .thenComparingDouble(entry -> entry.targetFep)
                .thenComparingDouble(entry -> entry.totalFep)
                .thenComparingLong(entry -> entry.recipeId)).orElse(recipes.get(0)));
    }

    double sortValue(CookbookRecipeSort sort, boolean allRecipes) {
        double value = Double.NEGATIVE_INFINITY;
        for(CookbookEntry recipe : recipes)
            value = Math.max(value, value(recipe, sort, allRecipes));
        return(value);
    }

    int seenCount() {
        int total = 0;
        for(CookbookEntry recipe : recipes)
            total += recipe.seenCount;
        return(total);
    }

    private static double value(CookbookEntry entry, CookbookRecipeSort sort,
                                boolean allRecipes) {
        return(switch(sort) {
            case SELECTED_STAT -> allRecipes ? entry.totalFep : entry.targetFep;
            case TOTAL_FEPS -> entry.totalFep;
            case FEP_PER_HUNGER -> {
                double fep = allRecipes ? entry.totalFep : entry.targetFep;
                yield entry.hungerPermille > 0 ? fep / entry.hungerPermille : fep;
            }
            case LATEST_QUALITY -> entry.quality;
            case NAME -> 0d;
        });
    }
}
