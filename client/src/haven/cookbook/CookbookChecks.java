package haven.cookbook;

import haven.BAttrWnd;
import haven.Resource;
import haven.Utils;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Focused, offline checks for cookbook deduplication, persistence, and ranking. */
public final class CookbookChecks {
    private CookbookChecks() {
    }

    public static void main(String[] args) throws Exception {
        Path database = Files.createTempFile("haven-cookbook-checks-", ".db");
        try {
            CookbookRepository repository = new CookbookRepository("jdbc:sqlite:" + database);
            repository.initialize();

            CookbookFood meatPie = food("world-a", "Meat Pie", "gfx/invobjs/meatpie", 40,
                    2, 4, "Beef", 60);
            check(repository.save(meatPie), "first observation should be new");
            check(!repository.save(meatPie), "identical observation should deduplicate");
            check(repository.save(food("world-a", "Meat Pie", "gfx/invobjs/meatpie", 90,
                    2, 4, "Beef", 60)), "new quality should create an observed outcome");

            CookbookFood berryPie = food("world-a", "Berry Pie", "gfx/invobjs/berrypie", 20,
                    4, 2, "Blueberry", 80);
            check(repository.save(berryPie), "second recipe should be new");
            check(repository.save(food("world-b", "World B Pie", "gfx/invobjs/worldbpie", 10,
                    1, 9, "Apple", 100)), "other-world recipe should be new");

            List<CookbookEntry> strength = repository.list("world-a", "Strength", "");
            check(strength.size() == 2, "world filter should return two recipes");
            check(strength.get(0).itemName.equals("Meat Pie"), "strength per hunger ranking is incorrect");
            check(strength.get(0).seenCount == 3, "all observation counts should be included");
            check(close(strength.get(0).targetFep, 4), "strength FEP total is incorrect");
            check(strength.get(0).observations.size() == 2,
                    "specific observed qualities should be retained");
            check(close(strength.get(0).quality, 90), "latest quality is incorrect");
            check(strength.get(0).feps.get(0).attribute.startsWith("Strength"),
                    "selected attribute should be first in the FEP list");
            check(close(strength.get(0).observations.get(0).feps.get(0).amount, 12),
                    "actual FEP outcome for the observed quality is incorrect");

            List<CookbookEntry> filtered = repository.list("world-a", "Agility", "blueberry");
            check(filtered.size() == 1 && filtered.get(0).itemName.equals("Berry Pie"),
                    "ingredient search is incorrect");

            List<CookbookEntry> allRecipes = repository.list("world-a", "All recipes", "");
            check(allRecipes.size() == 2, "all-recipes view should retain every recipe");
            check(allRecipes.get(0).itemName.equals("Berry Pie"),
                    "all-recipes view should be alphabetized");

            CookbookFood panSearedFish = recipe("world-spice", false, 80, 70);
            CookbookFood panSearedFishWithTansy = recipe("world-spice", true, 80, 70);
            check(repository.save(panSearedFish), "unspiced comparison recipe should be new");
            check(repository.save(panSearedFishWithTansy), "spiced comparison recipe should be new");
            check(repository.save(recipe("world-spice", true, 75, 65)),
                    "changed character modifiers should refresh an existing observation");
            check(repository.saveItem(new CookbookItem("world-spice", "Tansy",
                    "gfx/invobjs/herbs/tansy", System.currentTimeMillis())),
                    "ingredient resource should be learned");
            check(panSearedFishWithTansy.ingredientSummary().equals(
                            "Stinging Nettle 100.00%, Zander 100.00%"),
                    "spices should not remain in the regular ingredient summary");
            check(panSearedFishWithTansy.modifierSummary().equals("Tansy 100.00%"),
                    "Tansy should appear as a modifier");

            List<CookbookEntry> spiceRecipes = repository.list(
                    "world-spice", "Strength", "tansy");
            check(spiceRecipes.size() == 1, "Tansy recipe search should return one recipe");
            check(spiceRecipes.get(0).modifiers.contains("Tansy 100.00%"),
                    "existing ingredient records should be presented as modifiers");
            check(!spiceRecipes.get(0).ingredients.contains("Tansy"),
                    "Tansy should be removed from the displayed ingredient list");
            check(spiceRecipes.get(0).observations.size() == 1,
                    "character modifiers should not duplicate the crafted outcome");
            check(close(spiceRecipes.get(0).observations.get(0).capturedFepEfficiencyPercent, 75),
                    "latest captured FEP efficiency is incorrect");
            check(close(spiceRecipes.get(0).observations.get(0).capturedHungerEfficiencyPercent, 65),
                    "latest captured hunger efficiency is incorrect");

            List<CookbookIngredientEntry> ingredientEntries = repository.listIngredients(
                    "world-spice", CookbookIngredientCategory.SPICE, "");
            check(ingredientEntries.size() == 1, "spice category should contain Tansy");
            CookbookIngredientEntry tansy = ingredientEntries.get(0);
            check(tansy.name.equals("Tansy") && tansy.spice(),
                    "Tansy should be classified as a spice");
            check(tansy.resourceName.equals("gfx/invobjs/herbs/tansy"),
                    "observed native ingredient icon should be retained");
            check(tansy.matchedSpiceComparisons == 1,
                    "matching unspiced recipe should provide one boost comparison");
            CookbookIngredientEntry.SpiceBoost agilityBoost = tansy.spiceBoosts.stream()
                    .filter(value -> value.attribute.startsWith("Agility"))
                    .findFirst().orElseThrow();
            check(close(agilityBoost.amount, 0.5), "measured Tansy agility boost is incorrect");
            check(close(agilityBoost.percent, 50), "measured Tansy boost percentage is incorrect");
            check(CookbookIngredientCatalog.category("Stinging Nettle", "") ==
                            CookbookIngredientCategory.HERB,
                    "known herb fallback should classify Stinging Nettle");
            check(CookbookIngredientCatalog.category("Zander", "") ==
                            CookbookIngredientCategory.FISH,
                    "fish name fallback should classify Zander");
            check(CookbookIngredientCatalog.category("Boar Meat", "gfx/invobjs/meat-boar") ==
                            CookbookIngredientCategory.MEAT,
                    "meat resource should classify as meat");
            check(CookbookIngredientCatalog.category("Swan", "gfx/invobjs/meat") ==
                            CookbookIngredientCategory.MEAT,
                    "generic dynamic meat resource should classify as meat");
            check("gfx/invobjs/meat-swan".equals(CookbookIngredientCatalog.meatIconOverlay(
                            "Sizzling Roast Swan", "gfx/invobjs/meat")),
                    "roast Swan should use the native Swan meat badge");
            check("gfx/invobjs/meat-chicken".equals(CookbookIngredientCatalog.meatIconOverlay(
                            "Sizzling Spitroast Chicken", "gfx/invobjs/meat")),
                    "spitroast Chicken should use the native Chicken meat badge");
            check("gfx/invobjs/meat-ide".equals(CookbookIngredientCatalog.meatIconOverlay(
                            "Spitroast Ide", "gfx/invobjs/meat")),
                    "spitroast fish should retain its native source badge");
            check("gfx/invobjs/meat-fox".equals(CookbookIngredientCatalog.meatIconOverlay(
                            "Raw Fox, stack of", "gfx/invobjs/meat")),
                    "stacked raw meat should retain its native source badge");
            check(CookbookIngredientCatalog.meatIconOverlay(
                            "Opened Oyster", "gfx/invobjs/oyster-opened") == null,
                    "non-meat foods should retain their existing icon");
            check(CookbookIngredientCatalog.category("Carrot", "gfx/invobjs/carrot") ==
                            CookbookIngredientCategory.VEGETABLE,
                    "vegetable name should classify as a vegetable");
            check(CookbookIngredientCatalog.category("Wheat", "gfx/invobjs/seed-cereal") ==
                            CookbookIngredientCategory.CROP,
                    "crop name should classify as a crop");
            CookbookFood incompleteStew = documentedFood("world-documentation",
                    Collections.emptyList());
            CookbookFood documentedStew = documentedFood("world-documentation",
                    Collections.singletonList(new CookbookFood.Ingredient(
                            "ingredient", "Carrot", 100)));
            check(repository.save(incompleteStew), "ingredient-less placeholder should save");
            check(repository.save(documentedStew), "resolved recipe documentation should save");
            List<CookbookEntry> documented = repository.list(
                    "world-documentation", "All recipes", "");
            check(documented.size() == 1 && documented.get(0).ingredients.contains("Carrot"),
                    "resolved documentation should shadow its incomplete placeholder");
            for(CookbookAttribute attribute : CookbookAttribute.ALL) {
                if(!attribute.allRecipes() && attribute != CookbookAttribute.WILL)
                    checkNativeAttributePresentation(attribute);
            }
            check(CookbookAttribute.WILL.resourceName.equals("gfx/hud/chr/fev/wil"),
                    "Will should use the native live FEP artwork");
            System.out.println("Cookbook checks passed.");
        } finally {
            Files.deleteIfExists(database);
            Files.deleteIfExists(Path.of(database + "-wal"));
            Files.deleteIfExists(Path.of(database + "-shm"));
        }
    }

    private static CookbookFood food(String world, String name, String resource, double quality,
                                     double hunger, double strength, String ingredient,
                                     double ingredientPercentage) {
        double qualityFactor = Math.sqrt(quality / 10d);
        return(new CookbookFood(world, "character", name, resource, quality, 80, hunger, hunger,
                Collections.singletonList(new CookbookFood.Ingredient("ingredient", ingredient,
                        ingredientPercentage)), Collections.emptyList(), Arrays.asList(
                        new CookbookFood.Fep("Strength +1", strength * qualityFactor, strength),
                        new CookbookFood.Fep("Agility +1", qualityFactor, 1)),
                System.currentTimeMillis()));
    }

    private static CookbookFood recipe(String world, boolean withTansy, double fepEfficiency,
                                       double hungerEfficiency) {
        List<CookbookFood.Ingredient> ingredients = new java.util.ArrayList<>();
        ingredients.add(new CookbookFood.Ingredient("ingredient", "Stinging Nettle", 100));
        if(withTansy)
            ingredients.add(new CookbookFood.Ingredient("ingredient", "Tansy", 100));
        ingredients.add(new CookbookFood.Ingredient("ingredient", "Zander", 100));
        return(new CookbookFood(world, "character", "Pan-Seared Fish",
                "gfx/invobjs/pansearedfish", 10, 400, 2, 2, fepEfficiency,
                hungerEfficiency, ingredients, Collections.emptyList(), Arrays.asList(
                        new CookbookFood.Fep("Strength +1", 4, 4),
                        new CookbookFood.Fep("Agility +1", withTansy ? 1.5 : 1,
                                withTansy ? 1.5 : 1)), System.currentTimeMillis()));
    }

    private static CookbookFood documentedFood(String world,
                                                List<CookbookFood.Ingredient> ingredients) {
        return(new CookbookFood(world, "character", "Vegetable Stew",
                "gfx/invobjs/vegetablestew", 10, 100, 1, 1,
                ingredients, Collections.emptyList(), Collections.singletonList(
                        new CookbookFood.Fep("Constitution +1", 2, 2)),
                System.currentTimeMillis()));
    }

    private static boolean close(double actual, double expected) {
        return(Math.abs(actual - expected) < 0.0001);
    }

    private static void checkNativeAttributePresentation(CookbookAttribute attribute) {
        Resource resource = Resource.remote().loadwait(attribute.resourceName);
        BAttrWnd.FoodMeter.Event event = resource.flayer(BAttrWnd.FoodMeter.Event.class);
        Color expected = Utils.blendcol(event.col, Color.WHITE, 0.5);
        check(attribute.color.equals(expected),
                attribute.label + " color should match the in-game FEP event color");
        check(attribute.icon() != null,
                attribute.label + " should use the in-game FEP event icon");
    }

    private static void check(boolean condition, String message) {
        if(!condition)
            throw(new AssertionError(message));
    }
}
