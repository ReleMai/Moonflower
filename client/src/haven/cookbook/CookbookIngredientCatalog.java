package haven.cookbook;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Small, explicit classification layer for ingredient presentation.
 * Resource-backed observations take precedence over these name fallbacks.
 */
final class CookbookIngredientCatalog {
    private static final Set<String> SPICES = names(
            "Tansy", "Pepper", "Black Pepper", "Ground Black Pepper",
            "White Truffle", "Black Truffle", "Salt");
    private static final Set<String> FISH = names(
            "A Talking Whale", "Abyss Gazer", "Asp", "Bass", "Bream", "Brill",
            "Burbot", "Carp", "Catfish", "Cave Sculpin", "Cavelacanth", "Chub",
            "Cod", "Eel", "Grayling", "Haddock", "Herring", "Ide", "Lavaret",
            "Mackerel", "Mullet", "Pale Ghostfish", "Perch", "Pike", "Plaice",
            "Pomfret", "Roach", "Rose Fish", "Ruffe", "Saithe", "Salmon",
            "Seahorse", "Silver Bream", "Smelt", "Sturgeon", "Tench", "Trout",
            "Whiting", "Zander", "Zope");
    private static final Set<String> VEGETABLES = names(
            "Beetroot", "Cabbage", "Carrot", "Cucumber", "Leek", "Lettuce",
            "Onion", "Peas", "Pumpkin", "Turnip", "Yellow Onion", "Red Onion",
            "White Onion");
    private static final Set<String> CROPS = names(
            "Barley", "Beans", "Flax", "Hemp", "Hops", "Millet", "Oats",
            "Pipeweed", "Poppy", "Rye", "Tea", "Wheat");
    private static final Set<String> FRUIT = names(
            "Apple", "Cherry", "Grape", "Lemon", "Orange", "Pear", "Persimmon",
            "Plum", "Quince");
    private static final Map<String, String> KNOWN_RESOURCES;

    static {
        Map<String, String> resources = new HashMap<>();
        resources.put(normalize("Tansy"), "gfx/invobjs/herbs/tansy");
        resources.put(normalize("Stinging Nettle"), "gfx/invobjs/herbs/stingingnettle");
        resources.put(normalize("Pepper"), "gfx/invobjs/pepper");
        resources.put(normalize("Black Pepper"), "gfx/invobjs/pepper");
        KNOWN_RESOURCES = Collections.unmodifiableMap(resources);
    }

    private CookbookIngredientCatalog() {
    }

    static boolean isSpice(String name) {
        return(SPICES.contains(normalize(name)));
    }

    static CookbookIngredientCategory category(String name, String resourceName) {
        String normalizedName = normalize(name);
        String normalizedResource = normalize(resourceName).replace(' ', '/');
        if(normalizedResource.isEmpty())
            normalizedResource = normalize(KNOWN_RESOURCES.get(normalizedName));
        if(SPICES.contains(normalizedName))
            return(CookbookIngredientCategory.SPICE);
        if(FISH.contains(normalizedName) || normalizedResource.contains("/fish-"))
            return(CookbookIngredientCategory.FISH);
        if(isGenericMeatResource(normalizedResource) ||
                normalizedResource.contains("/meat-") || containsAny(normalizedName,
                        " meat", "beef", "mutton", "pork", "venison", "poultry"))
            return(CookbookIngredientCategory.MEAT);
        if(VEGETABLES.contains(normalizedName) || containsAny(normalizedName,
                "beetroot", "cabbage", "carrot", "cucumber", "leek", "lettuce",
                "onion", "peas", "pumpkin", "turnip"))
            return(CookbookIngredientCategory.VEGETABLE);
        if(normalizedResource.contains("/seed-") || CROPS.contains(normalizedName) ||
                containsAny(normalizedName, "barley", "beans", "flax", "hemp", "hops",
                        "millet", "oats", "pipeweed", "poppy", "rye", "tea", "wheat"))
            return(CookbookIngredientCategory.CROP);
        if(normalizedResource.contains("/herbs/"))
            return(CookbookIngredientCategory.HERB);
        if(FRUIT.contains(normalizedName) || containsAny(normalizedName,
                "apple", "cherry", "grape", "lemon", "orange", "pear", "persimmon",
                "plum", "quince", "berry"))
            return(CookbookIngredientCategory.FRUIT);
        if(containsAny(normalizedName, "mushroom", "shroom", "bolete", "chantrelle",
                "toadstool", "morel") || normalizedResource.contains("mushroom"))
            return(CookbookIngredientCategory.MUSHROOM);
        if(containsAny(normalizedName, "milk", "cream", "butter", "cheese", "curd"))
            return(CookbookIngredientCategory.DAIRY);
        return(CookbookIngredientCategory.OTHER);
    }

    static String iconResource(String name, String observedResource) {
        if(observedResource != null && !observedResource.isBlank())
            return(observedResource);
        String normalized = normalize(name);
        String known = KNOWN_RESOURCES.get(normalized);
        if(known != null)
            return(known);
        if(FISH.contains(normalized))
            return("gfx/invobjs/fish-" + resourceSlug(name));
        return(switch(category(name, "")) {
            case SPICE -> "gfx/invobjs/herbs/tansy";
            case FISH -> "gfx/invobjs/fish-pike";
            case MEAT -> "gfx/invobjs/meat-raw";
            case VEGETABLE -> "gfx/invobjs/onion";
            case CROP -> "gfx/invobjs/seed-cereal";
            case HERB -> "gfx/invobjs/herbs/stingingnettle";
            case FRUIT -> "gfx/invobjs/apple";
            case MUSHROOM -> "gfx/invobjs/herbs/chantrelle";
            case DAIRY -> "gfx/invobjs/milk";
            default -> "gfx/invobjs/missing";
        });
    }

    /**
     * Returns the native species badge layered over the generic meat sprite in game.
     * Meat cooking state is encoded dynamically, so the persisted base resource alone
     * cannot distinguish, for example, roast Swan from spitroast Chicken.
     */
    static String meatIconOverlay(String name, String observedResource) {
        if(!isGenericMeatResource(observedResource))
            return(null);
        String source = normalize(name);
        if(source.endsWith(", stack of"))
            source = source.substring(0, source.length() - ", stack of".length()).trim();
        boolean stripped;
        do {
            stripped = false;
            for(String prefix : new String[] {
                    "sizzling ", "spitroast ", "roasted ", "roast ", "raw ",
                    "smoked ", "dried "}) {
                if(source.startsWith(prefix)) {
                    source = source.substring(prefix.length()).trim();
                    stripped = true;
                    break;
                }
            }
        } while(stripped);
        String slug = resourceSlug(source);
        if(slug.isEmpty() || slug.equals("meat"))
            return(null);
        return("gfx/invobjs/meat-" + slug);
    }

    static String modifierIngredient(String modifier) {
        String normalized = normalize(modifier);
        if(normalized.equals("peppered"))
            return("Pepper");
        if(normalized.equals("salted"))
            return("Salt");
        if(normalized.equals("white-truffled"))
            return("White Truffle");
        if(normalized.equals("black-truffled"))
            return("Black Truffle");
        return(null);
    }

    static String normalize(String value) {
        if(value == null)
            return("");
        return(value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT));
    }

    private static Set<String> names(String... values) {
        Set<String> normalized = new HashSet<>();
        Arrays.stream(values).map(CookbookIngredientCatalog::normalize).forEach(normalized::add);
        return(Collections.unmodifiableSet(normalized));
    }

    private static boolean containsAny(String value, String... fragments) {
        for(String fragment : fragments) {
            if(value.contains(fragment))
                return(true);
        }
        return(false);
    }

    private static boolean isGenericMeatResource(String resourceName) {
        String resource = normalize(resourceName).replace(' ', '/');
        return(resource.equals("gfx/invobjs/meat") || resource.equals("gfx/invobjs/food/meat"));
    }

    private static String resourceSlug(String name) {
        return(normalize(name).replaceAll("[^a-z0-9]", ""));
    }
}
