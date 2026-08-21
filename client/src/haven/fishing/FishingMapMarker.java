package haven.fishing;

import haven.Coord;
import haven.MapFile;
import haven.Resource;
import haven.UID;

/** Display-only fish icon linking a mapped fishing tile back to its journal entries. */
public final class FishingMapMarker extends MapFile.SMarker {
    public final long gridId;
    public final int gridTileX;
    public final int gridTileY;
    public final long latestObservationId;
    public final int observationCount;

    FishingMapMarker(MapFile file, long segmentId, Coord mapTile, String name, String fishResource,
                     long gridId, int gridTileX, int gridTileY, long latestObservationId,
                     int observationCount) {
        super(file, segmentId, mapTile, name, UID.nil,
                new Resource.Saved(Resource.remote(), fishResource, -1), new byte[0]);
        this.gridId = gridId;
        this.gridTileX = gridTileX;
        this.gridTileY = gridTileY;
        this.latestObservationId = latestObservationId;
        this.observationCount = observationCount;
    }
}
