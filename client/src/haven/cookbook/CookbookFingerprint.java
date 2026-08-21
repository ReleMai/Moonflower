package haven.cookbook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Builds stable deduplication keys without including transient display order. */
final class CookbookFingerprint {
    private CookbookFingerprint() {
    }

    static String recipe(CookbookFood food) {
        List<String> ingredients = new ArrayList<>();
        for(CookbookFood.Ingredient ingredient : food.ingredients) {
            ingredients.add(ingredient.kind + "|" + ingredient.name + "|" + number(ingredient.percentage));
        }
        ingredients.sort(Comparator.naturalOrder());
        List<String> modifiers = new ArrayList<>(food.modifiers);
        modifiers.sort(String.CASE_INSENSITIVE_ORDER);
        return(hash(String.join("\n",
                food.worldId,
                food.resourceName,
                food.itemName,
                String.join(";", ingredients),
                String.join(";", modifiers))));
    }

    static String observation(CookbookFood food) {
        List<String> feps = new ArrayList<>();
        for(CookbookFood.Fep fep : food.feps) {
            feps.add(fep.attribute + "|" + number(fep.amount) + "|" + number(fep.normalizedAmount));
        }
        feps.sort(Comparator.naturalOrder());
        return(hash(String.join("\n",
                food.recipeKey,
                number(food.quality),
                number(food.energyPercent),
                number(food.hungerPermille),
                number(food.normalizedHungerPermille),
                String.join(";", feps))));
    }

    private static String number(double value) {
        return(Long.toString(Math.round(value * 1_000_000d)));
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return(HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8))));
        } catch(NoSuchAlgorithmException e) {
            throw(new IllegalStateException("SHA-256 is unavailable", e));
        }
    }
}
