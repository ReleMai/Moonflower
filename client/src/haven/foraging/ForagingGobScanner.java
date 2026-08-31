package haven.foraging;

import haven.GameUI;
import haven.Gob;
import haven.Loading;
import haven.Resource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads only currently loaded OCache Gobs and classifies exact resource identities. */
public final class ForagingGobScanner {
    public static final String HERB_PREFIX = "gfx/terobjs/herbs/";
    private static final Set<String> REVIEWED_EXCEPTIONS = Set.of(
            "gfx/terobjs/items/precioussnowflake");

    private final GameUI gui;

    public ForagingGobScanner(GameUI gui) {
        this.gui = gui;
    }

    public static boolean isForageable(String resourceName) {
        return(resourceName != null &&
                (resourceName.startsWith(HERB_PREFIX) || REVIEWED_EXCEPTIONS.contains(resourceName)));
    }

    public Scan scan() {
        Map<String, HerbResource> catalog = new LinkedHashMap<>();
        for(HerbResource herb : ForagingHerbAtlas.entries())
            catalog.put(herb.resourceName, herb);
        List<ForagingTarget> targets = new ArrayList<>();
        if(gui.ui == null || gui.ui.sess == null)
            return(new Scan(sortedCatalog(catalog), List.of()));
        synchronized(gui.ui.sess.glob.oc) {
            for(Gob gob : gui.ui.sess.glob.oc) {
                try {
                    Resource resource = gob.getres();
                    if(resource == null || !isForageable(resource.name) || gob.rc == null)
                        continue;
                    String display = displayName(resource);
                    HerbResource guide = catalog.get(resource.name);
                    catalog.put(resource.name, new HerbResource(resource.name, display,
                            guide == null ? "Observed" : guide.category, true));
                    targets.add(new ForagingTarget(gob.id, resource.name, display, gob.rc));
                } catch(Loading ignored) {
                    // A partially loaded Gob is unknown and therefore cannot become a target.
                }
            }
        }
        List<HerbResource> resources = sortedCatalog(catalog);
        targets.sort(Comparator.comparingLong(target -> target.gobId));
        return(new Scan(resources, targets));
    }

    private static List<HerbResource> sortedCatalog(Map<String, HerbResource> catalog) {
        List<HerbResource> resources = new ArrayList<>(catalog.values());
        resources.sort(Comparator.comparing((HerbResource herb) -> herb.displayName)
                .thenComparing(herb -> herb.resourceName));
        return(resources);
    }

    private static String displayName(Resource resource) {
        try {
            Resource.Tooltip tooltip = resource.layer(Resource.tooltip);
            if(tooltip != null && tooltip.t != null && !tooltip.t.isEmpty())
                return(tooltip.t);
        } catch(RuntimeException ignored) {
        }
        String base = resource.basename();
        return(base == null || base.isEmpty() ? resource.name : base);
    }

    public static final class HerbResource {
        public final String resourceName;
        public final String displayName;
        public final String category;
        public final boolean live;

        public HerbResource(String resourceName, String displayName) {
            this(resourceName, displayName, "Observed", true);
        }

        public HerbResource(String resourceName, String displayName, String category, boolean live) {
            this.resourceName = resourceName;
            this.displayName = displayName;
            this.category = category == null ? "Other" : category;
            this.live = live;
        }
    }

    public static final class Scan {
        public final List<HerbResource> catalog;
        public final List<ForagingTarget> targets;

        Scan(List<HerbResource> catalog, List<ForagingTarget> targets) {
            this.catalog = catalog;
            this.targets = targets;
        }
    }
}
