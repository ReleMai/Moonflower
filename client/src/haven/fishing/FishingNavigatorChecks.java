package haven.fishing;

import haven.MCache;

import java.util.List;

/** Deterministic proof for per-fish ranking and selected-rig evidence. */
public final class FishingNavigatorChecks {
    private FishingNavigatorChecks() {
    }

    public static void main(String[] args) {
        check(FishingNavigatorAssets.complete(),
                "all three transparent Tideglass production assets must load at UI scale");
        check(FishingNavigatorUi.safe("angler ‹ tide — ★").equals("angler ? tide ? ?"),
                "FastText labels must sanitize glyphs outside its 256-entry atlas");
        FishingObservation pikeLow = observation(1, 10, 10, "Pike", 20,
                "Fine Fishline", "Bone Hook", "Rock Lobster", "lure");
        FishingObservation pikeBest = observation(2, 40, 20, "Pike", 44,
                "Fine Fishline", "Chitin Hook", "Rock Lobster", "lure");
        FishingObservation pikeNearby = observation(3, 43, 21, "Pike", 38,
                "Fine Fishline", "Chitin Hook", "Rock Lobster", "lure");
        FishingObservation aspGlobalBest = observation(4, 11, 10, "Asp", 72,
                "Fine Fishline", "Bone Hook", "Rock Lobster", "lure");
        FishingObservation unknownMap = observation(5, 60, 20, "Pike", 90,
                "Fine Fishline", "Chitin Hook", "Rock Lobster", "lure").copy()
                .location(1, -1, 60 * MCache.tilesz.x, 20 * MCache.tilesz.y,
                        0, 0, 0, 0, "gfx/tiles/water").build();

        FishingNavigatorModel.RigSpec rig = new FishingNavigatorModel.RigSpec(
                "Primitive Casting-Rod", "Fine Fishline", "Chitin Hook", "lure", "Rock Lobster");
        FishingNavigatorModel.Snapshot snapshot = FishingNavigatorModel.build(
                List.of(pikeLow, pikeBest, pikeNearby, aspGlobalBest, unknownMap), "pike", rig);

        check(snapshot.selectedFish != null && "Pike".equals(snapshot.selectedFish.name),
                "the selected fish must remain Pike even when Asp has the global best chance");
        check(snapshot.spots.size() == 2,
                "nearby Pike observations must cluster while a distant Pike location remains separate");
        check(snapshot.spots.get(0).bestChance == 44 && snapshot.spots.get(0).samples == 2,
                "Pike locations must rank by Pike chance and preserve cluster evidence");
        check(snapshot.spots.stream().noneMatch(spot -> spot.gridId == -1),
                "the explicit unknown map sentinel must not become a navigation target");
        check(snapshot.rigResults.size() == 1 && "Pike".equals(snapshot.rigResults.get(0).name) &&
                        snapshot.rigResults.get(0).observations == 3,
                "rig evidence must include only observations matching all selected component types");

        FishingPolePreset preview = new FishingPolePreset("Pike Scout", "Primitive Casting-Rod",
                "Fine Fishline", "Bone Hook", "lure", "Rock Lobster");
        FishingNavigatorModel.Snapshot previewSnapshot = FishingNavigatorModel.build(
                List.of(pikeLow, pikeBest, pikeNearby, aspGlobalBest), "pike", preview.rig());
        check(previewSnapshot.rigResults.size() == 2 &&
                        "Asp".equals(previewSnapshot.rigResults.get(0).name) &&
                        Integer.valueOf(72).equals(previewSnapshot.rigResults.get(0).bestChance) &&
                        "Pike".equals(previewSnapshot.rigResults.get(1).name) &&
                        Integer.valueOf(20).equals(previewSnapshot.rigResults.get(1).bestChance),
                "preset overview must list exact-rig catches by their highest learned percentages");

        FishingObservation signedGrid = observation(6, 80, 20, "Pike", 31,
                "Fine Fishline", "Chitin Hook", "Rock Lobster", "lure").copy()
                .location(1, -22, 80 * MCache.tilesz.x, 20 * MCache.tilesz.y,
                        0, 0, 0, 0, "gfx/tiles/water").build();
        FishingNavigatorModel.Snapshot signed = FishingNavigatorModel.build(List.of(signedGrid), "pike", rig);
        check(signed.spots.size() == 1 && signed.spots.get(0).gridId == -22,
                "signed negative Haven grid IDs other than -1 must remain valid");

        System.out.println("Fishing navigator checks passed.");
    }

    private static FishingObservation observation(long id, int tileX, int tileY, String fish,
                                                  int chance, String line, String hook,
                                                  String consumable, String kind) {
        String rows = "[{\"fish\":\"" + fish + "\",\"final\":" + chance + "}]";
        return(new FishingObservation.Builder().id(id).worldId("test")
                .location(1, 10, tileX * MCache.tilesz.x, tileY * MCache.tilesz.y,
                        0, 0, 0, 0, "gfx/tiles/water")
                .observedAt(1_000L + id).fish("gfx/invobjs/" + fish.toLowerCase(), fish, 10.0)
                .pole("gfx/invobjs/fishingrod", "Primitive Casting-Rod", 10.0)
                .line("gfx/invobjs/fishline", line, 10.0)
                .hook("gfx/invobjs/fishhook", hook, 10.0)
                .consumable(kind, "gfx/invobjs/lure", consumable, 10.0)
                .choiceRowsJson(rows).outcome("caught").confidence("candidate").build());
    }

    private static void check(boolean condition, String message) {
        if(!condition)
            throw(new AssertionError(message));
    }
}
