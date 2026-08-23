package haven.cookbook;

import java.util.Locale;

/** Determines which recipe ingredients should be presented as the main ingredients. */
final class CookbookIngredientOrder {
    private CookbookIngredientOrder() {
    }

    static int mainPriority(String foodName, String ingredientName) {
        String food = words(foodName);
        String ingredient = words(ingredientName);
        if(!ingredient.isEmpty() && (" " + food + " ").contains(" " + ingredient + " "))
            return(0);

        CookbookIngredientCategory category = CookbookIngredientCatalog.category(
                ingredientName, "");
        if(containsWord(food, "fish") && category == CookbookIngredientCategory.FISH)
            return(1);
        if((containsWord(food, "fruit") || containsWord(food, "berry")) &&
                category == CookbookIngredientCategory.FRUIT)
            return(1);
        if((containsWord(food, "vegetable") || containsWord(food, "veggie")) &&
                category == CookbookIngredientCategory.VEGETABLE)
            return(1);
        if((containsWord(food, "meat") || containsWord(food, "roast") ||
                containsWord(food, "cutlet") || containsWord(food, "steak") ||
                containsWord(food, "sausage") || containsWord(food, "jerky")) &&
                category == CookbookIngredientCategory.MEAT)
            return(1);
        return(10);
    }

    private static String words(String value) {
        if(value == null)
            return("");
        return(value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim());
    }

    private static boolean containsWord(String words, String word) {
        return((" " + words + " ").contains(" " + word + " "));
    }
}
