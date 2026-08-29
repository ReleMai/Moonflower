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
    /* A wider shoreline radius keeps repeated casts from becoming a row of near-identical blips. */
    private static final int NEARBY_CLUSTER_RADIUS_TILES = 12;
    private static final long RETRY_INTERVAL_MS = 10_000;

    private final FishingJournalService service;
    private MapFile installedFile;
    private Future<List<FishingObservation>> query;
    private long displayedGeneration = -1;
    private long nextRefreshAt;
    private boolean needsRetry;
    private volatile int markerCount;
    private volatile int summaryCount;
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

    public int summaryCount() {
        return(summaryCount);
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
                markerCount = result.detailCount;
                summaryCount = result.summaryCount;
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
        Map<Long, Spot> summariesByGrid = new LinkedHashMap<>();
        int unresolved = 0;
        try(Locked ignored = new Locked(file.lock.readLock())) {
            for(FishingObservation observation : observations) {
                // Haven IDs are signed 64-bit hashes, so negative grid IDs are valid.
                // Only the observation builder's explicit -1 sentinel means unknown.
                if(observation == null || observation.gridId == -1 ||
                        FishingChanceTable.parse(observation.choiceRowsJson).isEmpty() &&
                                observation.fishName.isBlank())
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
                Spot summary = summariesByGrid.get(observation.gridId);
                if(summary == null)
                    summariesByGrid.put(observation.gridId,
                            new Spot(info.seg, mapTile, tileX, tileY, observation));
                else
                    summary.add(mapTile, observation);
            }
        }

        List<FishingMapMarker> markers = new ArrayList<>();
        int detailCount = 0;
        for(List<Spot> segmentSpots : spotsBySegment.values()) {
            for(Spot spot : segmentSpots) {
                markers.add(marker(file, spot, false));
                detailCount++;
            }
        }
        for(Spot gridSummary : summariesByGrid.values())
            markers.add(marker(file, gridSummary, true));
        return(new BuildResult(markers, detailCount, summariesByGrid.size(), unresolved));
    }

    static List<FishingMapMarker> projectForChecks(MapFile file, List<FishingObservation> observations) {
        List<FishingMapMarker> details = new ArrayList<>();
        for(FishingMapMarker marker : build(file, observations).markers) {
            if(!marker.summary)
                details.add(marker);
        }
        return(details);
    }

    static List<FishingMapMarker> projectSummariesForChecks(MapFile file,
                                                            List<FishingObservation> observations) {
        List<FishingMapMarker> summaries = new ArrayList<>();
        for(FishingMapMarker marker : build(file, observations).markers) {
            if(marker.summary)
                summaries.add(marker);
        }
        return(summaries);
    }

    private static FishingMapMarker marker(MapFile file, Spot spot, boolean summary) {
        List<FishingMapMarker.FishChance> chances = spot.fishChances();
        FishingMapMarker.FishChance best = chances.get(0);
        String name = (summary ? "Fishing area: " : "Fishing spot (best recorded): ") +
                chanceSummary(chances, 3) +
                (summary ? " | " + spot.observations.size() + " observations" : "");
        return(new FishingMapMarker(file, spot.segmentId, spot.mapTile(), name,
                best.fishResource, spot.latest.gridId, spot.representativeTileX,
                spot.representativeTileY, spot.latest.id, spot.observations.size(),
                spot.observationIds(), chances, summary));
    }

    private static final class BuildResult {
        final List<FishingMapMarker> markers;
        final int detailCount;
        final int summaryCount;
        final int unresolved;

        BuildResult(List<FishingMapMarker> markers, int detailCount, int summaryCount, int unresolved) {
            this.markers = markers;
            this.detailCount = detailCount;
            this.summaryCount = summaryCount;
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

        List<FishingMapMarker.FishChance> fishChances() {
            Map<String, FishingMapMarker.FishChance> best = new LinkedHashMap<>();
            for(FishingObservation observation : observations) {
                List<FishingChoice> choices = FishingChanceTable.parse(observation.choiceRowsJson);
                if(choices.isEmpty()) {
                    addChance(best, observation.fishName, observation.fishResource, null);
                } else {
                    for(FishingChoice choice : choices)
                        addChance(best, choice.fishName, resourceFor(choice.fishName), choice.finalPercent);
                }
            }
            List<FishingMapMarker.FishChance> ordered = new ArrayList<>(best.values());
            ordered.sort(Comparator
                    .comparingInt((FishingMapMarker.FishChance chance) ->
                            chance.percent == null ? Integer.MIN_VALUE : chance.percent)
                    .reversed().thenComparing(chance -> chance.fishName,
                            String.CASE_INSENSITIVE_ORDER));
            if(ordered.isEmpty())
                ordered.add(new FishingMapMarker.FishChance("Fish", latest.fishResource, null));
            return(ordered);
        }

        private void addChance(Map<String, FishingMapMarker.FishChance> best, String fishName,
                               String fishResource, Integer percent) {
            String name = fishName == null || fishName.isBlank() ? "Fish" : fishName.trim();
            String key = name.toLowerCase(java.util.Locale.ROOT);
            FishingMapMarker.FishChance current = best.get(key);
            if(current == null || current.percent == null && percent != null ||
                    current.percent != null && percent != null && percent > current.percent)
                best.put(key, new FishingMapMarker.FishChance(name, fishResource, percent));
        }

        private String resourceFor(String fishName) {
            for(FishingObservation observation : observations) {
                if(observation.fishName.equalsIgnoreCase(fishName) && !observation.fishResource.isBlank())
                    return(observation.fishResource);
            }
            return(latest.fishResource.isBlank() ? "gfx/invobjs/missing" : latest.fishResource);
        }
    }

    private static String chanceSummary(List<FishingMapMarker.FishChance> chances, int limit) {
        StringBuilder text = new StringBuilder();
        int shown = Math.min(limit, chances.size());
        for(int i = 0; i < shown; i++) {
            if(i > 0)
                text.append(" | ");
            FishingMapMarker.FishChance chance = chances.get(i);
            text.append(chance.fishName).append(' ')
                    .append(chance.percent == null ? "?%" : chance.percent + "%");
        }
        if(chances.size() > shown)
            text.append(" | +").append(chances.size() - shown).append(" more");
        return(text.toString());
    }
}
