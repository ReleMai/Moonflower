package haven.foraging;

import haven.Coord2d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Immutable, world-scoped Phase 1 configuration. */
public final class ForagingProfile {
    public final String worldId;
    public final Set<String> selectedResources;
    public final List<Coord2d> route;
    public final int reserveCells;
    public final int corridorTiles;
    public final ForagingDirection direction;

    public ForagingProfile(String worldId, Set<String> selectedResources, List<Coord2d> route,
                           int reserveCells, int corridorTiles, ForagingDirection direction) {
        this.worldId = worldId == null ? "" : worldId;
        this.selectedResources = Collections.unmodifiableSet(new LinkedHashSet<>(selectedResources));
        this.route = Collections.unmodifiableList(new ArrayList<>(route));
        this.reserveCells = Math.max(1, reserveCells);
        this.corridorTiles = Math.max(2, corridorTiles);
        this.direction = direction == null ? ForagingDirection.NORTH : direction;
    }

    public ForagingProfile withSelection(Set<String> resources) {
        return(new ForagingProfile(worldId, resources, route, reserveCells, corridorTiles, direction));
    }

    public ForagingProfile withDirection(ForagingDirection value) {
        return(new ForagingProfile(worldId, selectedResources, route, reserveCells, corridorTiles, value));
    }
}
