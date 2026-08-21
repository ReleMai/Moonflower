package haven.cookbook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable food data captured from one resolved in-game item tooltip.
 */
public final class CookbookFood {
    public final String worldId;
    public final String characterId;
    public final String itemName;
    public final String resourceName;
    public final double quality;
    public final double energyPercent;
    public final double hungerPermille;
    public final double normalizedHungerPermille;
    public final double capturedFepEfficiencyPercent;
    public final double capturedHungerEfficiencyPercent;
    public final List<Ingredient> ingredients;
    public final List<String> modifiers;
    public final List<Fep> feps;
    public final long observedAt;
    public final String recipeKey;
    public final String observationKey;

    public CookbookFood(String worldId, String characterId, String itemName, String resourceName,
                        double quality, double energyPercent, double hungerPermille,
                        double normalizedHungerPermille, List<Ingredient> ingredients,
                        List<String> modifiers, List<Fep> feps, long observedAt) {
        this(worldId, characterId, itemName, resourceName, quality, energyPercent,
                hungerPermille, normalizedHungerPermille, Double.NaN, Double.NaN,
                ingredients, modifiers, feps, observedAt);
    }

    public CookbookFood(String worldId, String characterId, String itemName, String resourceName,
                        double quality, double energyPercent, double hungerPermille,
                        double normalizedHungerPermille, double capturedFepEfficiencyPercent,
                        double capturedHungerEfficiencyPercent, List<Ingredient> ingredients,
                        List<String> modifiers, List<Fep> feps, long observedAt) {
        this.worldId = clean(worldId);
        this.characterId = clean(characterId);
        this.itemName = clean(itemName);
        this.resourceName = clean(resourceName);
        this.quality = quality;
        this.energyPercent = energyPercent;
        this.hungerPermille = hungerPermille;
        this.normalizedHungerPermille = normalizedHungerPermille;
        this.capturedFepEfficiencyPercent = capturedFepEfficiencyPercent;
        this.capturedHungerEfficiencyPercent = capturedHungerEfficiencyPercent;
        this.ingredients = immutableCopy(ingredients);
        this.modifiers = immutableCopy(modifiers);
        this.feps = immutableCopy(feps);
        this.observedAt = observedAt;
        this.recipeKey = CookbookFingerprint.recipe(this);
        this.observationKey = CookbookFingerprint.observation(this);
    }

    public String ingredientSummary() {
        List<String> parts = new ArrayList<>();
        for(Ingredient ingredient : ingredients) {
            if(CookbookIngredientCatalog.isSpice(ingredient.name))
                continue;
            String prefix = ingredient.kind.equals("smoke") ? "Smoke: " : "";
            parts.add(String.format("%s%s %.2f%%", prefix, ingredient.name, ingredient.percentage));
        }
        return(String.join(", ", parts));
    }

    public String modifierSummary() {
        List<String> parts = new ArrayList<>(modifiers);
        for(Ingredient ingredient : ingredients) {
            if(CookbookIngredientCatalog.isSpice(ingredient.name)) {
                String spice = String.format("%s %.2f%%", ingredient.name, ingredient.percentage);
                if(parts.stream().noneMatch(value -> value.equalsIgnoreCase(spice)))
                    parts.add(spice);
            }
        }
        parts.sort(String.CASE_INSENSITIVE_ORDER);
        return(String.join(", ", parts));
    }

    private static String clean(String value) {
        return(value == null ? "" : value.trim());
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        return(Collections.unmodifiableList(new ArrayList<>(values)));
    }

    public static final class Ingredient {
        public final String kind;
        public final String name;
        public final double percentage;

        public Ingredient(String kind, String name, double percentage) {
            this.kind = clean(kind).toLowerCase();
            this.name = clean(name);
            this.percentage = percentage;
        }
    }

    public static final class Fep {
        public final String attribute;
        public final double amount;
        public final double normalizedAmount;

        public Fep(String attribute, double amount, double normalizedAmount) {
            this.attribute = clean(attribute);
            this.amount = amount;
            this.normalizedAmount = normalizedAmount;
        }
    }
}
