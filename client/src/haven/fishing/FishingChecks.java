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
                map.gridinfo.put(12L, new MapFile.GridInfo(12L, 7L, Coord.of(3, 3)));
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

            String chanceRows = "[{\"fish\":\"Perch\",\"gear\":70,\"lure\":80,\"final\":24}," +
                    "{\"fish\":\"Trout\",\"gear\":90,\"lure\":95,\"final\":73}]";
            FishingObservation chanceObservation = recent.get(0).copy().id(8001)
                    .choiceRowsJson(chanceRows).build();
            List<FishingMapMarker> chanceMarkers = FishingMapMarkers.projectForChecks(map,
                    List.of(chanceObservation));
            check(chanceMarkers.size() == 1 && chanceMarkers.get(0).fishChances.size() == 2 &&
                            chanceMarkers.get(0).fishChances.get(0).fishName.equals("Trout") &&
                            chanceMarkers.get(0).fishChances.get(0).percent == 73 &&
                            chanceMarkers.get(0).bestPercentageLabel().equals("73%"),
                    "map spot fish should be de-duplicated and ordered by highest percentage");
            List<FishingMapMarker> summaries = FishingMapMarkers.projectSummariesForChecks(map,
                    List.of(chanceObservation));
            check(summaries.size() == 1 && summaries.get(0).summary &&
                            summaries.get(0).visibleAt(true, 0.25f) &&
                            summaries.get(0).visibleAt(false, 1.0f) &&
                            !summaries.get(0).visibleAt(false, 0.5f) &&
                            !chanceMarkers.get(0).visibleAt(true, 0.5f) &&
                            chanceMarkers.get(0).visibleAt(false, 0.5f),
                    "compact and zoomed-out maps should show summaries while zoomed-in big maps show details");

            String surveyRows = "[{\"fish\":\"Perch\",\"gear\":94,\"lure\":96,\"final\":88}]";
            FishingObservation survey = chanceObservation.copy().id(8002).observedAt(1300)
                    .location(7, 11, 30 * 11.0, 19 * 11.0, 3500, 5200, 3400, 5200,
                            "gfx/tiles/water")
                    .fish("", "", null).choiceRowsJson(surveyRows)
                    .outcome("surveyed").confidence("server-choice").build();
            FishingAnalytics.Snapshot analytics = FishingAnalytics.analyze(
                    List.of(chanceObservation, survey));
            check(analytics.catchCount == 1 && analytics.surveyCount == 1,
                    "analytics must distinguish chance surveys from completed catches");
            check(analytics.rigs.size() == 1 && analytics.rigs.get(0).bestChance == 88 &&
                            analytics.rigs.get(0).catchCount == 1 &&
                            analytics.rigs.get(0).fish.get(0).fishName.equals("Trout"),
                    "rig history must retain catches while ranking its best offered chance");
            check(analytics.spots.size() == 2 && analytics.spots.get(0).bestChance == 88,
                    "known fishing spots must be ranked by their best observed chance");
            FishingAnalytics.TargetScore targetScore = analytics.score(3500, 5200,
                    FishingAnalytics.RigKey.from(survey));
            check(targetScore.rigBestChance == 88 && targetScore.rigSamples == 1,
                    "nearby-target scoring must prefer exact-rig evidence");
            List<FishingMapMarker> surveyMarkers = FishingMapMarkers.projectForChecks(map,
                    List.of(survey));
            check(surveyMarkers.size() == 1 && surveyMarkers.get(0).bestPercentageLabel().equals("88%"),
                    "chance-only surveys must remain visible as fishing-map evidence");

            FishingObservation nearby = recent.get(0).copy().id(9001)
                    .location(7, 11, 13 * 11.0, 21 * 11.0, 3421.5, 5212.25, 3400, 5200,
                            "gfx/tiles/water").build();
            List<FishingMapMarker> nearbyMarkers = FishingMapMarkers.projectForChecks(map,
                    List.of(recent.get(0), recent.get(1), nearby));
            check(nearbyMarkers.size() == 1 && nearbyMarkers.get(0).observationCount == 3 &&
                            nearbyMarkers.get(0).observationIds.contains(9001L),
                    "nearby fishing tiles should merge into one clickable map spot");
            FishingObservation expandedRadius = nearby.copy().id(9002)
                    .location(7, 11, 19 * 11.0, 19 * 11.0, 3421.5, 5212.25, 3400, 5200,
                            "gfx/tiles/water").build();
            check(FishingMapMarkers.projectForChecks(map,
                    List.of(recent.get(0), recent.get(1), nearby, expandedRadius)).size() == 1,
                    "casts within the expanded shoreline radius should share one detailed spot");
            FishingObservation distant = nearby.copy().id(9003)
                    .location(7, 11, 27 * 11.0, 19 * 11.0, 3421.5, 5212.25, 3400, 5200,
                            "gfx/tiles/water").build();
            check(FishingMapMarkers.projectForChecks(map,
                    List.of(recent.get(0), recent.get(1), nearby, expandedRadius, distant)).size() == 2,
                    "distant fishing areas must remain separate map spots");
            FishingObservation nextGrid = nearby.copy().id(9004)
                    .location(7, 12, 10 * 11.0, 10 * 11.0, 0, 0, 0, 0,
                            "gfx/tiles/water").build();
            check(FishingMapMarkers.projectSummariesForChecks(map,
                    List.of(recent.get(0), nearby, nextGrid)).size() == 2,
                    "each mapped grid should retain one zoomed-out fishing summary");
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
            FishingChoice combined = FishingChoiceParser.parse(List.of(
                    "Perch: 34% x 95% = 32%"));
            check(combined != null && combined.fishName.equals("Perch") &&
                            combined.gearPercent == 34 && combined.lurePercent == 95 &&
                            combined.finalPercent == 32,
                    "combined primitive-pole percentage row parsing is incorrect");
            check(FishingChoiceParser.parse(Arrays.asList("Not a complete row", "25%")) != null,
                    "minimal valid row should parse");
            check(FishingChoiceParser.parse(Arrays.asList("Trout", "loading")) == null,
                    "row without a final percentage should be rejected");
            List<FishingChoice> savedChoices = FishingChanceTable.parse(chanceRows);
            check(savedChoices.size() == 2 && savedChoices.get(0).fishName.equals("Trout") &&
                            FishingChanceTable.forFish(chanceObservation).finalPercent == 73 &&
                            FishingChanceTable.compact(savedChoices, 2)
                                    .equals("Trout 73% | Perch 24%"),
                    "saved fishing percentages should remain readable and highest first");
            check(FishingChanceTable.parse("not-json").isEmpty(),
                    "malformed legacy fishing rows should fail closed");

            check(FishingAtlas.classify("Crane Fly") == FishingAtlas.Part.BAIT,
                    "current bait atlas entry is missing");
            check(FishingAtlas.classify("A stack of Earthworms") == FishingAtlas.Part.BAIT &&
                            FishingAtlas.sameDisplayName("Stack of Grasshoppers", "Grasshopper"),
                    "stacked fishing tackle names must normalize to their selectable items");
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
