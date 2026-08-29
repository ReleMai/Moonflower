package haven.fishing;

import haven.Coord;
import haven.MapFile;
import haven.Resource;
import haven.UID;

import java.util.List;

/** Display-only fish icon linking a mapped fishing tile back to its journal entries. */
public final class FishingMapMarker extends MapFile.SMarker {
    private static final float DETAIL_ZOOM_LIMIT = 0.75f;
    public final long gridId;
    public final int gridTileX;
    public final int gridTileY;
    public final long latestObservationId;
    public final int observationCount;
    /** Summary markers represent a whole map grid; detail markers represent nearby casts. */
    public final boolean summary;
    /** Fish at this spot, de-duplicated and ordered by best recorded final chance. */
    public final List<FishChance> fishChances;
    /** Exact local journal rows represented by this nearby-map cluster. */
    public final List<Long> observationIds;

    FishingMapMarker(MapFile file, long segmentId, Coord mapTile, String name, String fishResource,
                     long gridId, int gridTileX, int gridTileY, long latestObservationId,
                     int observationCount, List<Long> observationIds, List<FishChance> fishChances,
                     boolean summary) {
        super(file, segmentId, mapTile, name, UID.nil,
                new Resource.Saved(Resource.remote(), fishResource == null || fishResource.isBlank() ?
                        "gfx/invobjs/missing" : fishResource, -1), new byte[0]);
        this.gridId = gridId;
        this.gridTileX = gridTileX;
        this.gridTileY = gridTileY;
        this.latestObservationId = latestObservationId;
        this.observationCount = observationCount;
        this.summary = summary;
        this.observationIds = List.copyOf(observationIds == null ? List.of() : observationIds);
        this.fishChances = List.copyOf(fishChances == null ? List.of() : fishChances);
    }

    public String bestPercentageLabel() {
        if(fishChances.isEmpty() || fishChances.get(0).percent == null)
            return("?%");
        return(fishChances.get(0).percent + "%");
    }

    /** Compact and zoomed-out maps show grid summaries; zoomed-in big maps show details. */
    public boolean visibleAt(boolean compactMap, float zoomLevel) {
        boolean showDetails = !compactMap && zoomLevel <= DETAIL_ZOOM_LIMIT;
        return(summary != showDetails);
    }

    public static final class FishChance {
        public final String fishName;
        public final String fishResource;
        public final Integer percent;

        FishChance(String fishName, String fishResource, Integer percent) {
            this.fishName = fishName;
            this.fishResource = fishResource;
            this.percent = percent;
        }
    }
}
