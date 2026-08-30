package haven.fishing;

import haven.MCache;
import haven.automated.helpers.FishingAtlas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure evidence model for fish-specific locations and selected-rig results. */
public final class FishingNavigatorModel {
    private static final int SPOT_RADIUS_TILES = 12;

    private FishingNavigatorModel() {
    }

    public static Snapshot build(List<FishingObservation> source, String selectedFishKey, RigSpec rig) {
        List<FishingObservation> observations = source == null ? List.of() : List.copyOf(source);
        Map<String, MutableFish> fishByKey = new LinkedHashMap<>();
        for(FishingObservation observation : observations) {
            if(observation == null)
                continue;
            if(FishingAnalytics.isCatch(observation) && !observation.fishName.isBlank())
                fishByKey.computeIfAbsent(fishKey(observation.fishName), ignored ->
                        new MutableFish(fishLabel(observation.fishName), observation.fishResource)).addCatch(observation);
            for(FishingChoice choice : FishingChanceTable.parse(observation.choiceRowsJson))
                fishByKey.computeIfAbsent(fishKey(choice.fishName), ignored ->
                        new MutableFish(fishLabel(choice.fishName), resourceFor(observations, choice.fishName)))
                        .addOffer(choice.finalPercent, observation.observedAt);
        }
        List<FishSummary> fish = new ArrayList<>();
        for(Map.Entry<String, MutableFish> entry : fishByKey.entrySet())
            fish.add(entry.getValue().freeze(entry.getKey()));
        fish.sort(Comparator.comparingInt((FishSummary value) -> value.bestChance == null ? -1 : value.bestChance)
                .reversed().thenComparing(Comparator.comparingInt((FishSummary value) -> value.catchCount).reversed())
                .thenComparing(value -> value.name, String.CASE_INSENSITIVE_ORDER));

        String wanted = selectedFishKey == null || fish.stream().noneMatch(value -> value.key.equals(selectedFishKey)) ?
                (fish.isEmpty() ? "" : fish.get(0).key) : selectedFishKey;
        FishSummary selected = fish.stream().filter(value -> value.key.equals(wanted)).findFirst().orElse(null);
        List<SpotSummary> spots = selected == null ? List.of() : spots(observations, selected.name);
        List<RigFishResult> rigResults = rig == null || !rig.complete() ? List.of() : rigResults(observations, rig);
        return(new Snapshot(List.copyOf(fish), selected, spots, rigResults, rig));
    }

    private static List<SpotSummary> spots(List<FishingObservation> observations, String fishName) {
        List<MutableSpot> clusters = new ArrayList<>();
        for(FishingObservation observation : observations) {
            if(observation == null || observation.gridId == -1)
                continue;
            FishingChoice choice = choiceFor(observation, fishName);
            if(choice == null)
                continue;
            int tileX = (int)Math.floor(observation.gridOffsetX / MCache.tilesz.x);
            int tileY = (int)Math.floor(observation.gridOffsetY / MCache.tilesz.y);
            MutableSpot cluster = nearest(clusters, observation.gridId, tileX, tileY);
            if(cluster == null) {
                cluster = new MutableSpot(observation.gridId, tileX, tileY);
                clusters.add(cluster);
            }
            cluster.add(observation, choice);
        }
        List<SpotSummary> result = new ArrayList<>();
        for(MutableSpot cluster : clusters)
            result.add(cluster.freeze());
        result.sort(Comparator.comparingInt((SpotSummary value) -> value.bestChance).reversed()
                .thenComparing(Comparator.comparingInt((SpotSummary value) -> value.samples).reversed())
                .thenComparing(Comparator.comparingLong((SpotSummary value) -> value.latestObservedAt).reversed()));
        return(List.copyOf(result));
    }

    private static MutableSpot nearest(List<MutableSpot> spots, long gridId, int tileX, int tileY) {
        MutableSpot result = null;
        long best = Long.MAX_VALUE;
        for(MutableSpot spot : spots) {
            if(spot.gridId != gridId)
                continue;
            long dx = tileX - spot.tileX();
            long dy = tileY - spot.tileY();
            long distance = dx * dx + dy * dy;
            if(distance <= SPOT_RADIUS_TILES * SPOT_RADIUS_TILES && distance < best) {
                result = spot;
                best = distance;
            }
        }
        return(result);
    }

    private static List<RigFishResult> rigResults(List<FishingObservation> observations, RigSpec rig) {
        Map<String, MutableRigFish> results = new LinkedHashMap<>();
        for(FishingObservation observation : observations) {
            if(observation == null || !rig.matches(observation))
                continue;
            for(FishingChoice choice : FishingChanceTable.parse(observation.choiceRowsJson))
                results.computeIfAbsent(fishKey(choice.fishName), ignored ->
                        new MutableRigFish(fishLabel(choice.fishName), resourceFor(observations, choice.fishName)))
                        .addOffer(choice.finalPercent);
            if(FishingAnalytics.isCatch(observation) && !observation.fishName.isBlank())
                results.computeIfAbsent(fishKey(observation.fishName), ignored ->
                        new MutableRigFish(fishLabel(observation.fishName), observation.fishResource)).catches++;
        }
        List<RigFishResult> result = new ArrayList<>();
        for(MutableRigFish value : results.values())
            result.add(value.freeze());
        result.sort(Comparator.comparingInt((RigFishResult value) -> value.bestChance == null ? -1 : value.bestChance)
                .reversed().thenComparing(Comparator.comparingInt((RigFishResult value) -> value.catches).reversed())
                .thenComparing(value -> value.name, String.CASE_INSENSITIVE_ORDER));
        return(List.copyOf(result));
    }

    private static FishingChoice choiceFor(FishingObservation observation, String fishName) {
        String wanted = fishKey(fishName);
        for(FishingChoice choice : FishingChanceTable.parse(observation.choiceRowsJson)) {
            if(fishKey(choice.fishName).equals(wanted))
                return(choice);
        }
        return(null);
    }

    private static String resourceFor(List<FishingObservation> observations, String fishName) {
        String wanted = fishKey(fishName);
        for(FishingObservation observation : observations) {
            if(observation != null && fishKey(observation.fishName).equals(wanted) &&
                    !observation.fishResource.isBlank())
                return(observation.fishResource);
        }
        return("");
    }

    static String fishKey(String name) {
        return(fishLabel(name).toLowerCase(Locale.ROOT));
    }

    /** Server item labels can describe a fish stack; the guide is a fish-type rail. */
    static String fishLabel(String name) {
        String value = name == null ? "" : name.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if(lower.startsWith("a stack of "))
            value = value.substring("a stack of ".length()).trim();
        else if(lower.startsWith("stack of "))
            value = value.substring("stack of ".length()).trim();
        lower = value.toLowerCase(Locale.ROOT);
        int marker = lower.indexOf(", stack of");
        if(marker >= 0)
            value = value.substring(0, marker).trim();
        return(value);
    }

    private static String clean(String value) {
        return(value == null ? "" : value.trim());
    }

    public static final class Snapshot {
        public final List<FishSummary> fish;
        public final FishSummary selectedFish;
        public final List<SpotSummary> spots;
        public final List<RigFishResult> rigResults;
        public final RigSpec rig;

        Snapshot(List<FishSummary> fish, FishSummary selectedFish, List<SpotSummary> spots,
                 List<RigFishResult> rigResults, RigSpec rig) {
            this.fish = fish;
            this.selectedFish = selectedFish;
            this.spots = spots;
            this.rigResults = rigResults;
            this.rig = rig;
        }
    }

    public static final class FishSummary {
        public final String key;
        public final String name;
        public final String resource;
        public final int catchCount;
        public final int offerCount;
        public final Integer bestChance;
        public final long latestObservedAt;

        FishSummary(String key, String name, String resource, int catchCount, int offerCount,
                    Integer bestChance, long latestObservedAt) {
            this.key = key;
            this.name = name;
            this.resource = resource;
            this.catchCount = catchCount;
            this.offerCount = offerCount;
            this.bestChance = bestChance;
            this.latestObservedAt = latestObservedAt;
        }
    }

    public static final class SpotSummary {
        public final long gridId;
        public final int tileX;
        public final int tileY;
        public final int samples;
        public final int catches;
        public final int bestChance;
        public final long latestObservedAt;
        public final String waterResource;
        public final RigSpec bestRig;

        SpotSummary(long gridId, int tileX, int tileY, int samples, int catches, int bestChance,
                    long latestObservedAt, String waterResource, RigSpec bestRig) {
            this.gridId = gridId;
            this.tileX = tileX;
            this.tileY = tileY;
            this.samples = samples;
            this.catches = catches;
            this.bestChance = bestChance;
            this.latestObservedAt = latestObservedAt;
            this.waterResource = clean(waterResource);
            this.bestRig = bestRig;
        }
    }

    public static final class RigFishResult {
        public final String name;
        public final String resource;
        public final int observations;
        public final int catches;
        public final Integer bestChance;

        RigFishResult(String name, String resource, int observations, int catches, Integer bestChance) {
            this.name = name;
            this.resource = resource;
            this.observations = observations;
            this.catches = catches;
            this.bestChance = bestChance;
        }
    }

    public static final class RigSpec {
        public final String pole;
        public final String line;
        public final String hook;
        public final String consumableKind;
        public final String consumable;

        public RigSpec(String pole, String line, String hook, String consumableKind, String consumable) {
            this.pole = clean(pole);
            this.line = clean(line);
            this.hook = clean(hook);
            this.consumableKind = "lure".equalsIgnoreCase(consumableKind) ? "lure" : "bait";
            this.consumable = clean(consumable);
        }

        public boolean complete() {
            return(!pole.isBlank() && !line.isBlank() && !hook.isBlank() && !consumable.isBlank());
        }

        public boolean matches(FishingObservation observation) {
            return(observation != null && FishingAtlas.sameDisplayName(pole, observation.poleName) &&
                    FishingAtlas.sameDisplayName(line, observation.lineName) &&
                    FishingAtlas.sameDisplayName(hook, observation.hookName) &&
                    consumableKind.equalsIgnoreCase(observation.consumableKind) &&
                    FishingAtlas.sameDisplayName(consumable, observation.consumableName));
        }

        public String summary() {
            return(pole + " | " + line + " | " + hook + " | " + consumableKind + ": " + consumable);
        }
    }

    private static final class MutableFish {
        final String name;
        String resource;
        int catches;
        int offers;
        Integer bestChance;
        long latest;

        MutableFish(String name, String resource) {
            this.name = clean(name).isBlank() ? "Unknown fish" : clean(name);
            this.resource = clean(resource);
        }

        void addCatch(FishingObservation observation) {
            catches++;
            latest = Math.max(latest, observation.observedAt);
            if(resource.isBlank())
                resource = observation.fishResource;
        }

        void addOffer(Integer chance, long observedAt) {
            offers++;
            latest = Math.max(latest, observedAt);
            if(chance != null && (bestChance == null || chance > bestChance))
                bestChance = chance;
        }

        FishSummary freeze(String key) {
            return(new FishSummary(key, name, resource, catches, offers, bestChance, latest));
        }
    }

    private static final class MutableSpot {
        final long gridId;
        long tileXTotal;
        long tileYTotal;
        int samples;
        int catches;
        int bestChance = -1;
        long latest;
        String waterResource = "";
        RigSpec bestRig;

        MutableSpot(long gridId, int tileX, int tileY) {
            this.gridId = gridId;
            tileXTotal = tileX;
            tileYTotal = tileY;
        }

        void add(FishingObservation observation, FishingChoice choice) {
            int tileX = (int)Math.floor(observation.gridOffsetX / MCache.tilesz.x);
            int tileY = (int)Math.floor(observation.gridOffsetY / MCache.tilesz.y);
            if(samples > 0) {
                tileXTotal += tileX;
                tileYTotal += tileY;
            }
            samples++;
            latest = Math.max(latest, observation.observedAt);
            if(FishingAnalytics.isCatch(observation) &&
                    fishKey(observation.fishName).equals(fishKey(choice.fishName)))
                catches++;
            if(waterResource.isBlank())
                waterResource = observation.waterResource;
            if(choice.finalPercent != null && choice.finalPercent > bestChance) {
                bestChance = choice.finalPercent;
                bestRig = new RigSpec(observation.poleName, observation.lineName,
                        observation.hookName, observation.consumableKind, observation.consumableName);
            }
        }

        int tileX() { return((int)Math.round(tileXTotal / (double)Math.max(1, samples))); }
        int tileY() { return((int)Math.round(tileYTotal / (double)Math.max(1, samples))); }

        SpotSummary freeze() {
            return(new SpotSummary(gridId, tileX(), tileY(), samples, catches,
                    Math.max(0, bestChance), latest, waterResource, bestRig));
        }
    }

    private static final class MutableRigFish {
        final String name;
        final String resource;
        int observations;
        int catches;
        Integer bestChance;

        MutableRigFish(String name, String resource) {
            this.name = clean(name);
            this.resource = clean(resource);
        }

        void addOffer(Integer chance) {
            observations++;
            if(chance != null && (bestChance == null || chance > bestChance))
                bestChance = chance;
        }

        RigFishResult freeze() {
            return(new RigFishResult(name, resource, observations, catches, bestChance));
        }
    }
}
