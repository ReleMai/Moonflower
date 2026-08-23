package haven.fishing;

import haven.automated.helpers.FishingAtlas;
import haven.Coord;
import haven.Locked;
import haven.MapFile;
import haven.ResCache;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Focused offline checks for fishing persistence, repeated evidence, and row parsing. */
public final class FishingChecks {
    private FishingChecks() {
    }

    public static void main(String[] args) throws Exception {
        Path database = Files.createTempFile("haven-fishing-checks-", ".db");
        try {
            FishingRepository repository = new FishingRepository("jdbc:sqlite:" + database);
            repository.initialize();

            FishingObservation first = observation("world-a", "Trout", 42.5, 1000);
            long firstId = repository.save(first);
            long secondId = repository.save(first.copy().observedAt(1100).build());
            repository.save(observation("world-b", "Perch", null, 1200));

            check(firstId > 0 && secondId > firstId, "repeated catches must remain separate observations");
            check(repository.count("world-a") == 2, "world-a should retain both repeated observations");
            check(repository.count("world-b") == 1, "world filtering should isolate observations");

            List<FishingObservation> recent = repository.recent("world-a", 10);
            check(recent.size() == 2, "recent query should return both observations");
            check(recent.get(0).observedAt == 1100, "recent observations should be newest first");
            check(recent.get(0).fishName.equals("Trout"), "fish name did not survive persistence");
            check(close(recent.get(0).gridOffsetX, 121.5), "grid offset did not survive persistence");
            check(recent.get(0).moonPhase.equals("Full Moon"), "moon phase did not survive persistence");
            check(recent.get(0).lineQuality != null && close(recent.get(0).lineQuality, 31.0),
                    "tackle quality did not survive persistence");
            List<FishingObservation> spot = repository.spot("world-a", 11,
                    11 * 11.0, 12 * 11.0, 19 * 11.0, 20 * 11.0);
            check(spot.size() == 2 && spot.get(0).id == secondId,
                    "map-tile fishing spot query should preserve all catches newest first");
            check(repository.spot("world-b", 11, 11 * 11.0, 12 * 11.0,
                    19 * 11.0, 20 * 11.0).size() == 1,
                    "map-tile fishing spot query should remain world scoped");
            List<FishingObservation> selected = repository.observations("world-a",
                    List.of(firstId, secondId));
            check(selected.size() == 2 && selected.get(0).id == secondId,
                    "cluster observation IDs should retrieve the newest matching catches first");

            MapFile map = new MapFile(new ResCache.TestCache(), "fishing-check");
            try(Locked ignored = new Locked(map.lock.writeLock())) {
                map.gridinfo.put(11L, new MapFile.GridInfo(11L, 7L, Coord.of(2, 3)));
            }
            List<FishingMapMarker> markers = FishingMapMarkers.projectForChecks(map, recent);
            check(markers.size() == 1 && markers.get(0).observationCount == 2,
                    "repeated catches on one tile should form one grouped map marker");
            check(markers.get(0).tc.equals(Coord.of(211, 319)) &&
                            markers.get(0).gridTileX == 11 && markers.get(0).gridTileY == 19,
                    "fishing marker should resolve grid offsets into segment tile coordinates");
            check(markers.get(0).observationIds.size() == 2 &&
                            markers.get(0).observationIds.contains(firstId) &&
                            markers.get(0).observationIds.contains(secondId),
                    "fishing marker should retain the exact journal rows it represents");

            FishingObservation nearby = recent.get(0).copy().id(9001)
                    .location(7, 11, 13 * 11.0, 21 * 11.0, 3421.5, 5212.25, 3400, 5200,
                            "gfx/tiles/water").build();
            List<FishingMapMarker> nearbyMarkers = FishingMapMarkers.projectForChecks(map,
                    List.of(recent.get(0), recent.get(1), nearby));
            check(nearbyMarkers.size() == 1 && nearbyMarkers.get(0).observationCount == 3 &&
                            nearbyMarkers.get(0).observationIds.contains(9001L),
                    "nearby fishing tiles should merge into one clickable map spot");
            FishingObservation distant = nearby.copy().id(9002)
                    .location(7, 11, 19 * 11.0, 19 * 11.0, 3421.5, 5212.25, 3400, 5200,
                            "gfx/tiles/water").build();
            check(FishingMapMarkers.projectForChecks(map,
                    List.of(recent.get(0), recent.get(1), nearby, distant)).size() == 2,
                    "distant fishing areas must remain separate map spots");
            map.replaceEphemeralMarkers(markers);
            try(Locked ignored = new Locked(map.lock.readLock())) {
                check(map.displayMarkers().size() == 1 && map.markers.isEmpty(),
                        "fishing markers should display without entering the persisted marker collection");
            }

            long signedGridId = -194824002688929936L;
            MapFile signedMap = new MapFile(new ResCache.TestCache(), "signed-fishing-check");
            try(Locked ignored = new Locked(signedMap.lock.writeLock())) {
                signedMap.gridinfo.put(signedGridId,
                        new MapFile.GridInfo(signedGridId, -994835730057969304L, Coord.of(5, 6)));
            }
            FishingObservation signedObservation = first.copy().location(-994835730057969304L,
                    signedGridId, 940.5, 203.5, 0, 0, 0, 0, "gfx/tiles/water").build();
            List<FishingMapMarker> signedMarkers =
                    FishingMapMarkers.projectForChecks(signedMap, List.of(signedObservation));
            check(signedMarkers.size() == 1 && signedMarkers.get(0).gridId == signedGridId,
                    "negative signed grid IDs must remain valid fishing-map locations");

            FishingQualityAnalysis.Result quality = FishingQualityAnalysis.analyze(first);
            check(quality.tackleAverage != null && close(quality.tackleAverage, 26.5),
                    "tackle quality average should include pole, line, hook, and bait or lure");
            check(quality.weakestQuality != null && close(quality.weakestQuality, 10.0) &&
                            quality.weakestFactors.equals(List.of("Bait")),
                    "quality analysis should identify the weakest tackle component");

            FishingChoice labelled = FishingChoiceParser.parse(Arrays.asList(
                    "Trout", "Final chance: 73%", "Lure: 80%", "Gear: 92%"));
            check(labelled != null && labelled.finalPercent == 73 && labelled.gearPercent == 92 &&
                    labelled.lurePercent == 80, "labelled choice row parsing is incorrect");
            FishingChoice fallback = FishingChoiceParser.parse(Arrays.asList(
                    "Salmon", "85%", "70%", "60%"));
            check(fallback != null && fallback.gearPercent == 85 && fallback.lurePercent == 70 &&
                    fallback.finalPercent == 60, "checked percentage-order fallback is incorrect");
            check(FishingChoiceParser.parse(Arrays.asList("Not a complete row", "25%")) != null,
                    "minimal valid row should parse");
            check(FishingChoiceParser.parse(Arrays.asList("Trout", "loading")) == null,
                    "row without a final percentage should be rejected");

            check(FishingAtlas.classify("Crane Fly") == FishingAtlas.Part.BAIT,
                    "current bait atlas entry is missing");
            check(FishingAtlas.classify("Tick") == FishingAtlas.Part.UNKNOWN &&
                            FishingAtlas.classify("Bloated Tick") == FishingAtlas.Part.BAIT,
                    "only a bloated tick should be classified as current bait");
            check(FishingAtlas.classify("A Talking Whale") == FishingAtlas.Part.FISH,
                    "current fish atlas entry is missing");
            check(FishingAtlas.isFish("", "gfx/invobjs/trout"),
                    "resource-backed fish fallback is incorrect");
            check(FishingAtlas.classify("", "gfx/invobjs/fine-fishline") == FishingAtlas.Part.LINE,
                    "resource-backed tackle classification is incorrect");
            check(FishingAtlas.isCreel("", "gfx/invobjs/creel") &&
                            FishingAtlas.isCreel("Creel", "") &&
                            !FishingAtlas.isCreel("Wicker Basket", "gfx/invobjs/wbasket"),
                    "equipped Creel classification is incorrect");
            check(FishingAtlas.isFishingAction("paginae/act/fish") &&
                            !FishingAtlas.isFishingAction("paginae/act/swim"),
                    "normal Fishing action routing is incorrect");
            System.out.println("Fishing checks passed.");
        } finally {
            Files.deleteIfExists(database);
            Files.deleteIfExists(Path.of(database + "-wal"));
            Files.deleteIfExists(Path.of(database + "-shm"));
        }
    }

    private static FishingObservation observation(String world, String fish, Double quality, long observedAt) {
        return(new FishingObservation.Builder()
                .worldId(world)
                .location(7, 11, 121.5, 212.25, 3421.5, 5212.25, 3400, 5200,
                        "gfx/tiles/water")
                .observedAt(observedAt)
                .gameTime(172900, 2, 100, false, "Full Moon", "Summer")
                .fish("gfx/invobjs/" + fish.toLowerCase(), fish, quality)
                .pole("gfx/invobjs/fishpole", "Bushcraft Fishingpole", 40.0)
                .line("gfx/invobjs/fishline", "Fine Fishline", 31.0)
                .hook("gfx/invobjs/hook", "Bone Hook", 25.0)
                .consumable("bait", "gfx/invobjs/earthworm", "Earthworm", 10.0)
                .choiceRowsJson("[]")
                .stats(55, 28)
                .outcome("caught")
                .confidence("candidate")
                .build());
    }

    private static boolean close(double actual, double expected) {
        return(Math.abs(actual - expected) < 0.0001);
    }

    private static void check(boolean condition, String message) {
        if(!condition)
            throw(new AssertionError(message));
    }
}
