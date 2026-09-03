package haven.foraging;

import haven.Indir;
import haven.Loading;
import haven.Resource;
import haven.Tex;
import haven.WItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Resolves the inventory icon for a forageable without making it a target. */
public final class ForagingHerbIconCache {
    private final Map<String, List<Indir<Resource>>> resources = new HashMap<>();

    public Tex texture(ForagingGobScanner.HerbResource herb) {
        if(herb == null)
            return(missing());
        List<Indir<Resource>> candidates = resources.computeIfAbsent(herb.resourceName,
                key -> candidates(key));
        for(Indir<Resource> candidate : candidates) {
            try {
                Resource.Image image = candidate.get().layer(Resource.imgc);
                if(image != null)
                    return(image.tex());
            } catch(Loading ignored) {
                // The resource may finish loading before the next frame.
            } catch(RuntimeException ignored) {
                // Try the ground resource or the missing icon below.
            }
        }
        return(missing());
    }

    static String inventoryIconResourceName(String groundResourceName) {
        if(groundResourceName == null)
            return("gfx/invobjs/missing");
        if(groundResourceName.startsWith(ForagingGobScanner.HERB_PREFIX))
            return("gfx/invobjs/herbs/" + ForagingInventoryService.basename(groundResourceName));
        if(groundResourceName.startsWith("gfx/terobjs/items/"))
            return("gfx/invobjs/" + ForagingInventoryService.basename(groundResourceName));
        return("gfx/invobjs/" + ForagingInventoryService.basename(groundResourceName));
    }

    private static List<Indir<Resource>> candidates(String groundResourceName) {
        List<Indir<Resource>> result = new ArrayList<>();
        String inventory = inventoryIconResourceName(groundResourceName);
        result.add(Resource.remote().load(inventory));
        if(!inventory.equals(groundResourceName))
            result.add(Resource.remote().load(groundResourceName));
        return(result);
    }

    private static Tex missing() {
        return(WItem.missing.flayer(Resource.imgc).tex());
    }
}
