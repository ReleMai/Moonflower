package haven.foraging;

import haven.Coord2d;

/** An exact loaded forageable Gob identity. */
public final class ForagingTarget {
    public final long gobId;
    public final String resourceName;
    public final String displayName;
    public final Coord2d coordinate;

    public ForagingTarget(long gobId, String resourceName, String displayName, Coord2d coordinate) {
        this.gobId = gobId;
        this.resourceName = resourceName == null ? "" : resourceName;
        this.displayName = displayName == null ? this.resourceName : displayName;
        this.coordinate = coordinate;
    }
}
