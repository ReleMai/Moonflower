package haven.worldactivity;

import haven.Gob;
import haven.Resource;

import java.util.Locale;

/** Maps loaded world resources to board activity families. */
public final class WorldActivityDetector {
    private WorldActivityDetector() {
    }

    public static WorldActivityType classify(Gob gob) {
        return(classifyResourceName(resourceName(gob)));
    }

    public static WorldActivityType classifyResourceName(String resourceName) {
        if(resourceName == null || resourceName.isEmpty())
            return(null);
        String normalized = resourceName.toLowerCase(Locale.ROOT);
        String basename = normalized.substring(normalized.lastIndexOf('/') + 1);
        if(basename.contains("pyre"))
            return(WorldActivityType.PYRE);
        if(normalized.contains("/terobjs/mm/")
                || normalized.equals("gfx/terobjs/map/jotunclam")
                || normalized.equals("gfx/terobjs/map/cavepuddle"))
            return(WorldActivityType.LOCALIZED_RESOURCE);

        if(basename.contains("dframe") || basename.contains("dryingrack"))
            return(WorldActivityType.DRYING_RACK);
        if(basename.contains("herbalist"))
            return(WorldActivityType.HERBALIST_TABLE);
        if(basename.contains("kiln") || basename.contains("smokeshed"))
            return(WorldActivityType.KILN);
        if(basename.contains("oven"))
            return(WorldActivityType.OVEN);
        if(basename.contains("smelter"))
            return(WorldActivityType.SMELTER);
        if(basename.contains("gardenpot"))
            return(WorldActivityType.GARDEN_POT);
        if(basename.contains("field") || basename.contains("farmland"))
            return(WorldActivityType.FIELD);
        if(basename.contains("cheeserack") || basename.equals("ttub"))
            return(WorldActivityType.DRYING_RACK);
        return(null);
    }

    public static boolean isStarterType(WorldActivityType type) {
        return(type != null && type.starterSupported());
    }

    public static String resourceName(Gob gob) {
        if(gob == null)
            return(null);
        try {
            Resource resource = gob.getres();
            return(resource == null ? null : resource.name);
        } catch(RuntimeException ignored) {
            return(null);
        }
    }

    public static String displayName(Gob gob) {
        String resourceName = resourceName(gob);
        String fallback = prettyResourceName(resourceName);
        if(gob == null)
            return(fallback);
        try {
            Resource resource = gob.getres();
            Resource.Tooltip tooltip = resource == null ? null : resource.layer(Resource.tooltip);
            if(tooltip != null && tooltip.t != null && !tooltip.t.trim().isEmpty())
                return(tooltip.t);
        } catch(RuntimeException ignored) {
        }
        return(fallback);
    }

    private static String prettyResourceName(String resourceName) {
        if(resourceName == null || resourceName.isEmpty())
            return("World activity");
        String basename = resourceName.substring(resourceName.lastIndexOf('/') + 1);
        String known = switch(basename.toLowerCase(Locale.ROOT)) {
            case "saltbasin" -> "Salt Basin";
            case "jotunmussel" -> "Jotun Mussel";
            case "jotunclam" -> "Jotun Clam";
            case "cavepuddle" -> "Cave Puddle";
            case "algaeblob" -> "Algae Blob";
            case "lilypadlotus" -> "Lily Pad Lotus";
            case "crystalpatch" -> "Crystal Patch";
            default -> null;
        };
        if(known != null)
            return(known);
        String simple = basename.replace('-', ' ').replace('_', ' ').trim();
        if(simple.isEmpty())
            return("World activity");
        return(Character.toUpperCase(simple.charAt(0)) + simple.substring(1));
    }
}
