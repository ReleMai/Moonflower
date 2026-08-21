package haven.fishing;

import haven.Coord;
import haven.Locked;
import haven.MapFile;
import haven.MapWnd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import static haven.MCache.cmaps;
import static haven.MCache.tilesz;

/** Projects journal observations onto the map as transient, clickable fish icons. */
public final class FishingMapMarkers {
    private static final int MAP_OBSERVATION_LIMIT = 2000;
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
        Map<SpotKey, Spot> spots = new LinkedHashMap<>();
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
                SpotKey key = new SpotKey(observation.gridId, tileX, tileY);
                Spot spot = spots.get(key);
                if(spot == null)
                    spots.put(key, new Spot(info.seg, mapTile, observation));
                else
                    spot.count++;
            }
        }

        List<FishingMapMarker> markers = new ArrayList<>();
        for(Map.Entry<SpotKey, Spot> entry : spots.entrySet()) {
            SpotKey key = entry.getKey();
            Spot spot = entry.getValue();
            String fish = spot.latest.fishName.isBlank() ? "Fish" : spot.latest.fishName;
            String name = "Fishing spot: " + fish + (spot.count > 1 ? " +" + (spot.count - 1) : "");
            markers.add(new FishingMapMarker(file, spot.segmentId, spot.mapTile, name,
                    spot.latest.fishResource, key.gridId, key.tileX, key.tileY,
                    spot.latest.id, spot.count));
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

    private static final class SpotKey {
        final long gridId;
        final int tileX;
        final int tileY;

        SpotKey(long gridId, int tileX, int tileY) {
            this.gridId = gridId;
            this.tileX = tileX;
            this.tileY = tileY;
        }

        @Override
        public boolean equals(Object other) {
            if(!(other instanceof SpotKey))
                return(false);
            SpotKey key = (SpotKey)other;
            return(gridId == key.gridId && tileX == key.tileX && tileY == key.tileY);
        }

        @Override
        public int hashCode() {
            return(java.util.Objects.hash(gridId, tileX, tileY));
        }
    }

    private static final class Spot {
        final long segmentId;
        final Coord mapTile;
        final FishingObservation latest;
        int count = 1;

        Spot(long segmentId, Coord mapTile, FishingObservation latest) {
            this.segmentId = segmentId;
            this.mapTile = mapTile;
            this.latest = latest;
        }
    }
}
