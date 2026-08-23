package haven.fishing;

import haven.Coord;
import haven.Locked;
import haven.MapFile;
import haven.MapWnd;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import static haven.MCache.cmaps;
import static haven.MCache.tilesz;

/** Projects journal observations onto the map as transient, clickable fish icons. */
public final class FishingMapMarkers {
    private static final int MAP_OBSERVATION_LIMIT = 2000;
    /* Three Haven tiles (33 world units) keep one shoreline fishing area readable. */
    private static final int NEARBY_CLUSTER_RADIUS_TILES = 3;
    private static final long RETRY_INTERVAL_MS = 10_000;

    private final FishingJournalService service;
    private MapFile installedFile;
    private Future<List<FishingObservation>> query;
    private long displayedGeneration = -1;
    private long nextRefreshAt;
    private boolean needsRetry;
    private volatile int markerCount;
    private volatile int unresolvedCount;
    private volatile String lastError;

    public FishingMapMarkers(FishingJournalService service) {
        this.service = service;
    }

    public void tick(MapWnd mapWindow) {
        MapFile current = mapWindow == null ? null : mapWindow.file;
        if(current != installedFile) {
            if(installedFile != null)
                installedFile.replaceEphemeralMarkers(List.of());
            installedFile = current;
            displayedGeneration = -1;
            nextRefreshAt = 0;
            needsRetry = true;
        }
        if(query != null && query.isDone())
            finishQuery();
        long now = System.currentTimeMillis();
        if(installedFile != null && query == null &&
                (displayedGeneration != service.generation() || needsRetry && now >= nextRefreshAt)) {
            query = service.recent(MAP_OBSERVATION_LIMIT);
            nextRefreshAt = now + RETRY_INTERVAL_MS;
        }
    }

    public void close() {
        if(installedFile != null)
            installedFile.replaceEphemeralMarkers(List.of());
        installedFile = null;
        if(query != null)
            query.cancel(false);
        query = null;
    }

    public int markerCount() {
        return(markerCount);
    }

    public int unresolvedCount() {
        return(unresolvedCount);
    }

    public String lastError() {
        return(lastError);
    }

    private void finishQuery() {
        try {
            List<FishingObservation> observations = query.get();
            if(installedFile != null) {
                BuildResult result = build(installedFile, observations);
                installedFile.replaceEphemeralMarkers(result.markers);
                markerCount = result.markers.size();
                unresolvedCount = result.unresolved;
                needsRetry = result.unresolved > 0;
                lastError = null;
            }
            displayedGeneration = service.generation();
        } catch(Exception failure) {
            needsRetry = true;
            String detail = failure.getMessage();
            String message = detail == null || detail.isBlank() ?
                    "Could not project fishing spots onto the map." :
                    "Could not project fishing spots onto the map: " + detail;
            if(!message.equals(lastError))
                new haven.Warning(failure, message).level(haven.Warning.ERROR).issue();
            lastError = message;
        } finally {
            query = null;
        }
    }

    private static BuildResult build(MapFile file, List<FishingObservation> observations) {
        Map<Long, List<Spot>> spotsBySegment = new LinkedHashMap<>();
        int unresolved = 0;
        try(Locked ignored = new Locked(file.lock.readLock())) {
            for(FishingObservation observation : observations) {
                // Haven IDs are signed 64-bit hashes, so negative grid IDs are valid.
                // Only the observation builder's explicit -1 sentinel means unknown.
                if(observation == null || observation.gridId == -1 || observation.fishResource.isBlank())
                    continue;
                MapFile.GridInfo info = file.gridinfo.get(observation.gridId);
                if(info == null) {
                    unresolved++;
                    continue;
                }
                int tileX = (int)Math.floor(observation.gridOffsetX / tilesz.x);
                int tileY = (int)Math.floor(observation.gridOffsetY / tilesz.y);
                Coord mapTile = info.sc.mul(cmaps).add(tileX, tileY);
                List<Spot> segmentSpots = spotsBySegment.computeIfAbsent(info.seg,
                        ignoredKey -> new ArrayList<>());
                Spot spot = nearestSpot(segmentSpots, mapTile);
                if(spot == null) {
                    spot = new Spot(info.seg, mapTile, tileX, tileY, observation);
                    segmentSpots.add(spot);
                } else {
                    spot.add(mapTile, observation);
                }
            }
        }

        List<FishingMapMarker> markers = new ArrayList<>();
        for(List<Spot> segmentSpots : spotsBySegment.values()) {
            for(Spot spot : segmentSpots) {
                String fish = spot.latest.fishName.isBlank() ? "Fish" : spot.latest.fishName;
                String name = "Fishing spot: " + fish +
                        (spot.observations.size() > 1 ? " +" + (spot.observations.size() - 1) : "");
                markers.add(new FishingMapMarker(file, spot.segmentId, spot.mapTile(), name,
                        spot.latest.fishResource, spot.latest.gridId, spot.representativeTileX,
                        spot.representativeTileY, spot.latest.id, spot.observations.size(),
                        spot.observationIds()));
            }
        }
        return(new BuildResult(markers, unresolved));
    }

    static List<FishingMapMarker> projectForChecks(MapFile file, List<FishingObservation> observations) {
        return(build(file, observations).markers);
    }

    private static final class BuildResult {
        final List<FishingMapMarker> markers;
        final int unresolved;

        BuildResult(List<FishingMapMarker> markers, int unresolved) {
            this.markers = markers;
            this.unresolved = unresolved;
        }
    }

    private static Spot nearestSpot(List<Spot> spots, Coord mapTile) {
        Spot nearest = null;
        long nearestDistance = Long.MAX_VALUE;
        for(Spot spot : spots) {
            long distance = spot.distanceSquared(mapTile);
            if(distance <= NEARBY_CLUSTER_RADIUS_TILES * NEARBY_CLUSTER_RADIUS_TILES &&
                    distance < nearestDistance) {
                nearest = spot;
                nearestDistance = distance;
            }
        }
        return(nearest);
    }

    private static final class Spot {
        final long segmentId;
        final List<FishingObservation> observations = new ArrayList<>();
        long mapTileX;
        long mapTileY;
        int representativeTileX;
        int representativeTileY;
        FishingObservation latest;

        Spot(long segmentId, Coord mapTile, int tileX, int tileY, FishingObservation observation) {
            this.segmentId = segmentId;
            representativeTileX = tileX;
            representativeTileY = tileY;
            add(mapTile, observation);
        }

        void add(Coord mapTile, FishingObservation observation) {
            observations.add(observation);
            mapTileX += mapTile.x;
            mapTileY += mapTile.y;
            if(latest == null || observation.observedAt > latest.observedAt ||
                    observation.observedAt == latest.observedAt && observation.id > latest.id) {
                latest = observation;
                representativeTileX = (int)Math.floor(observation.gridOffsetX / tilesz.x);
                representativeTileY = (int)Math.floor(observation.gridOffsetY / tilesz.y);
            }
        }

        Coord mapTile() {
            return(Coord.of((int)Math.round(mapTileX / (double)observations.size()),
                    (int)Math.round(mapTileY / (double)observations.size())));
        }

        long distanceSquared(Coord candidate) {
            Coord center = mapTile();
            long dx = candidate.x - center.x;
            long dy = candidate.y - center.y;
            return(dx * dx + dy * dy);
        }

        List<Long> observationIds() {
            List<Long> ids = new ArrayList<>();
            for(FishingObservation observation : observations) {
                if(observation.id > 0)
                    ids.add(observation.id);
            }
            ids.sort(Comparator.reverseOrder());
            return(ids);
        }
    }
}
