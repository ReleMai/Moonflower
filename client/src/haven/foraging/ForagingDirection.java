package haven.foraging;

import haven.Coord;

/** User-selected travel mode. ROUTE preserves Checkpoint Manager behavior. */
public enum ForagingDirection {
    ROUTE("Route", 0, 0),
    NORTH("N", 0, -1),
    NORTH_EAST("NE", 1, -1),
    EAST("E", 1, 0),
    SOUTH_EAST("SE", 1, 1),
    SOUTH("S", 0, 1),
    SOUTH_WEST("SW", -1, 1),
    WEST("W", -1, 0),
    NORTH_WEST("NW", -1, -1);

    public final String label;
    public final Coord tileVector;

    ForagingDirection(String label, int x, int y) {
        this.label = label;
        this.tileVector = Coord.of(x, y);
    }

    public boolean usesCheckpointRoute() {
        return(this == ROUTE);
    }

    public static ForagingDirection parse(String value) {
        if(value != null) {
            try {
                return(valueOf(value));
            } catch(IllegalArgumentException ignored) {
            }
        }
        return(NORTH);
    }
}
