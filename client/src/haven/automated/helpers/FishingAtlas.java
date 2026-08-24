package haven.automated.helpers;

import java.util.*;

public class FishingAtlas {

    public enum Part {
        POLE, LINE, HOOK, BAIT, LURE, FISH, UNKNOWN
    }

    public static final List<String> fishingPoles = new ArrayList<>(Arrays.asList(
            "Bushcraft Fishingpole", "Primitive Casting-Rod"
    ));

    public static final Set<String> fishingHooks = new LinkedHashSet<>(Arrays.asList(
            "Bone Hook", "Chitin Hook", "Metal Hook", "Gold Hook"
    ));

    public static final Set<String> fishingLines = new LinkedHashSet<>(Arrays.asList(
            "Bushcraft Fishline", "Farmer's Fishline", "Fine Fishline", "Macabre Fishline",
            "Shepherd's Fishline", "Shoreline Fishline", "Tanner's Fishline", "Woodsman's Fishline"
    ));

    public static final Set<String> fishingLures = new LinkedHashSet<>(Arrays.asList(
            "Copper Comet", "Copperbrush Snapper", "Feather Fly", "Gold Spoon-Lure", "Pinecone Plug",
            "Poppy Wobbler", "Rock Lobster", "Steelbrush Plunger", "Tin Fly", "Woodfish"
    ));

    public static final Set<String> fishingBaits = new LinkedHashSet<>(Arrays.asList(
            "Woodworm", "Entrails", "Earthworm", "Ant Empress", "Ant Larvae", "Ant Pupae",
            "Ant Queen", "Ant Soldiers", "Aphids", "Bay Shrimp", "Bee Larvae",
            "Brimstone Butterfly", "Cave Moth", "Chum Bait", "Crane Fly", "Dumbledore",
            "Emerald Dragonfly", "Firefly",
            "Grasshopper", "Grub", "Ladybug", "Leech", "Monarch Butterfly", "Moonmoth",
            "Raw Crab", "Raw Lobster", "Ruby Dragonfly", "Sand Flea", "Silkmoth", "Silkworm",
            "Silkworm Egg", "Springtime Bumblebee", "Stag Beetle", "Bloated Tick",
            "Upside-Downbeldore", "Waterstrider"
    ));

    public static final Set<String> fishingOptions = new LinkedHashSet<>(Arrays.asList(
            "A Talking Whale", "Abyss Gazer", "Asp", "Bass", "Bream", "Brill",
            "Burbot", "Carp", "Catfish", "Cave Sculpin", "Cavelacanth", "Chub",
            "Cod", "Eel", "Grayling", "Haddock", "Herring", "Ide", "Lavaret",
            "Mackerel", "Mullet", "Pale Ghostfish", "Perch", "Pike", "Plaice", "Pomfret",
            "Roach", "Rose Fish", "Ruffe", "Saithe", "Salmon", "Seahorse", "Silver Bream",
            "Smelt", "Sturgeon", "Tench", "Trout", "Whiting", "Zander", "Zope"
    ));

    public static Part classify(String displayName) {
        String name = displayName(displayName);
        if(name.isEmpty())
            return(Part.UNKNOWN);
        if(matchesDisplayName(name, fishingPoles))
            return(Part.POLE);
        if(matchesDisplayName(name, fishingLines))
            return(Part.LINE);
        if(matchesDisplayName(name, fishingHooks))
            return(Part.HOOK);
        if(matchesDisplayName(name, fishingBaits))
            return(Part.BAIT);
        if(matchesDisplayName(name, fishingLures))
            return(Part.LURE);
        if(matchesDisplayName(name, fishingOptions))
            return(Part.FISH);
        return(Part.UNKNOWN);
    }

    /** Converts stack labels into the same item names shown by the helper's selectors. */
    public static String displayName(String value) {
        if(value == null)
            return("");
        String normalized = value.trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if(lower.startsWith("a stack of "))
            return(normalized.substring("a stack of ".length()).trim());
        if(lower.startsWith("stack of "))
            return(normalized.substring("stack of ".length()).trim());
        return(normalized);
    }

    public static boolean sameDisplayName(String first, String second) {
        return(normalizeDisplayName(first).equals(normalizeDisplayName(second)));
    }

    public static Part classify(String displayName, String resourceName) {
        Part named = classify(displayName);
        if(named != Part.UNKNOWN)
            return(named);
        String base = normalizedResourceBase(resourceName);
        if(base.isEmpty())
            return(Part.UNKNOWN);
        if(matchesResource(base, fishingPoles)) return(Part.POLE);
        if(matchesResource(base, fishingLines)) return(Part.LINE);
        if(matchesResource(base, fishingHooks)) return(Part.HOOK);
        if(matchesResource(base, fishingBaits)) return(Part.BAIT);
        if(matchesResource(base, fishingLures)) return(Part.LURE);
        if(matchesResource(base, fishingOptions)) return(Part.FISH);
        return(Part.UNKNOWN);
    }

    public static boolean isFish(String displayName, String resourceName) {
        if(classify(displayName) == Part.FISH)
            return(true);
        if(resourceName == null)
            return(false);
        String base = normalizedResourceBase(resourceName);
        for(String fish : fishingOptions) {
            String normalized = fish.replace(" ", "").replace("-", "").toLowerCase(Locale.ROOT);
            if(base.equals(normalized) || base.equals("fish" + normalized) || base.equals(normalized + "fish"))
                return(true);
        }
        return(false);
    }

    public static boolean isCreel(String displayName, String resourceName) {
        if(displayName != null && "Creel".equalsIgnoreCase(displayName.trim()))
            return(true);
        String base = normalizedResourceBase(resourceName);
        return(base.equals("creel") || base.endsWith("creel"));
    }

    public static boolean isFishingAction(String resourceName) {
        return("paginae/act/fish".equals(resourceName));
    }

    private static boolean matchesResource(String base, Collection<String> names) {
        for(String name : names) {
            String normalized = normalize(name);
            if(base.equals(normalized) || base.equals("fish" + normalized) ||
                    base.equals(normalized + "fish"))
                return(true);
        }
        return(false);
    }

    private static boolean matchesDisplayName(String name, Collection<String> choices) {
        String normalized = normalizeDisplayName(name);
        for(String choice : choices) {
            if(normalized.equals(normalizeDisplayName(choice)))
                return(true);
        }
        return(false);
    }

    private static String normalizeDisplayName(String value) {
        String normalized = normalize(displayName(value));
        return(normalized.endsWith("s") && normalized.length() > 1 ?
                normalized.substring(0, normalized.length() - 1) : normalized);
    }

    private static String normalizedResourceBase(String resourceName) {
        if(resourceName == null)
            return("");
        return(normalize(resourceName.substring(resourceName.lastIndexOf('/') + 1)));
    }

    private static String normalize(String value) {
        return(value.replace(" ", "").replace("-", "").replace("_", "")
                .replace("'", "").toLowerCase(Locale.ROOT));
    }
}
