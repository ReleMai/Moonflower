package haven.fishing;

import haven.MCache;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Builds local, evidence-based rig and spot knowledge from catches and choice surveys. */
public final class FishingAnalytics {
    private static final int SPOT_RADIUS_TILES = 12;

    private FishingAnalytics() {
    }

    public static Snapshot analyze(List<FishingObservation> source) {
        List<FishingObservation> observations = source == null ? List.of() : List.copyOf(source);
        Map<RigKey, RigSummary> rigs = new LinkedHashMap<>();
        List<SpotSummary> spots = new ArrayList<>();
        int catches = 0;
        int surveys = 0;
        for(FishingObservation observation : observations) {
            if(observation == null)
                continue;
            RigKey rig = RigKey.from(observation);
            rigs.computeIfAbsent(rig, ignored -> new RigSummary(rig, observation)).add(observation);
            if(isCatch(observation))
                catches++;
            if(isSurvey(observation))
                surveys++;
            if(observation.gridId != -1 && !FishingChanceTable.parse(observation.choiceRowsJson).isEmpty()) {
                int tileX = (int)Math.floor(observation.gridOffsetX / MCache.tilesz.x);
                int tileY = (int)Math.floor(observation.gridOffsetY / MCache.tilesz.y);
                SpotSummary spot = nearest(spots, observation.gridId, tileX, tileY);
                if(spot == null) {
                    spot = new SpotSummary(observation.gridId, tileX, tileY);
                    spots.add(spot);
                }
                spot.add(observation, rig);
            }
        }
        List<RigSummary> orderedRigs = new ArrayList<>(rigs.values());
        orderedRigs.sort(Comparator
                .comparingInt((RigSummary rig) -> value(rig.bestChance)).reversed()
                .thenComparing(Comparator.comparingInt((RigSummary rig) -> rig.catchCount).reversed())
                .thenComparing(rig -> rig.label, String.CASE_INSENSITIVE_ORDER));
        spots.sort(Comparator
                .comparingInt((SpotSummary spot) -> value(spot.bestChance)).reversed()
                .thenComparing(Comparator.comparingInt((SpotSummary spot) -> spot.samples).reversed()));
        return(new Snapshot(observations, List.copyOf(orderedRigs), List.copyOf(spots), catches, surveys));
    }

    public static boolean isCatch(FishingObservation observation) {
        return(observation != null && "caught".equalsIgnoreCase(observation.outcome));
    }

    public static boolean isSurvey(FishingObservation observation) {
        return(observation != null && "surveyed".equalsIgnoreCase(observation.outcome));
    }

    private static SpotSummary nearest(List<SpotSummary> spots, long gridId, int tileX, int tileY) {
        SpotSummary nearest = null;
        long bestDistance = Long.MAX_VALUE;
        for(SpotSummary spot : spots) {
            if(spot.gridId != gridId)
                continue;
            long dx = tileX - spot.tileX();
            long dy = tileY - spot.tileY();
            long distance = dx * dx + dy * dy;
            if(distance <= SPOT_RADIUS_TILES * SPOT_RADIUS_TILES && distance < bestDistance) {
                nearest = spot;
                bestDistance = distance;
            }
        }
        return(nearest);
    }

    private static int value(Integer chance) {
        return(chance == null ? Integer.MIN_VALUE : chance);
    }

    private static String clean(String value) {
        return(value == null || value.isBlank() ? "Unknown" : value.trim());
    }

    private static String normalized(String value) {
        return(value == null ? "" : value.trim().toLowerCase(Locale.ROOT));
    }

    private static String quality(Double value) {
        return(value == null ? "?" : String.format(Locale.ROOT, "%.1f", value));
    }

    public static final class Snapshot {
        private final List<FishingObservation> observations;
        public final List<RigSummary> rigs;
        public final List<SpotSummary> spots;
        public final int catchCount;
        public final int surveyCount;

        Snapshot(List<FishingObservation> observations, List<RigSummary> rigs, List<SpotSummary> spots,
                 int catchCount, int surveyCount) {
            this.observations = observations;
            this.rigs = rigs;
            this.spots = spots;
            this.catchCount = catchCount;
            this.surveyCount = surveyCount;
        }

        /** Returns exact-rig and all-rig evidence for one nearby cast coordinate. */
        public TargetScore score(double castX, double castY, RigKey wanted) {
            Integer rigBest = null;
            Integer anyBest = null;
            int rigSamples = 0;
            int anySamples = 0;
            double radius = Math.max(MCache.tilesz.x, MCache.tilesz.y) * 1.5;
            for(FishingObservation observation : observations) {
                if(observation == null || Math.hypot(observation.castX - castX,
                        observation.castY - castY) > radius)
                    continue;
                List<FishingChoice> choices = FishingChanceTable.parse(observation.choiceRowsJson);
                if(choices.isEmpty())
                    continue;
                int best = choices.get(0).finalPercent;
                anyBest = anyBest == null ? best : Math.max(anyBest, best);
                anySamples++;
                if(wanted != null && wanted.equals(RigKey.from(observation))) {
                    rigBest = rigBest == null ? best : Math.max(rigBest, best);
                    rigSamples++;
                }
            }
            return(new TargetScore(rigBest, anyBest, rigSamples, anySamples));
        }
    }

    public static final class TargetScore {
        public final Integer rigBestChance;
        public final Integer anyRigBestChance;
        public final int rigSamples;
        public final int anyRigSamples;

        TargetScore(Integer rigBestChance, Integer anyRigBestChance, int rigSamples, int anyRigSamples) {
            this.rigBestChance = rigBestChance;
            this.anyRigBestChance = anyRigBestChance;
            this.rigSamples = rigSamples;
            this.anyRigSamples = anyRigSamples;
        }

        public int rankingChance() {
            return(rigBestChance != null ? rigBestChance : anyRigBestChance != null ? anyRigBestChance : -1);
        }
    }

    public static final class RigKey {
        private final String pole;
        private final String poleQuality;
        private final String line;
        private final String lineQuality;
        private final String hook;
        private final String hookQuality;
        private final String consumableKind;
        private final String consumable;
        private final String consumableQuality;

        private RigKey(String pole, Double poleQuality, String line, Double lineQuality,
                       String hook, Double hookQuality, String consumableKind,
                       String consumable, Double consumableQuality) {
            this.pole = normalized(pole);
            this.poleQuality = quality(poleQuality);
            this.line = normalized(line);
            this.lineQuality = quality(lineQuality);
            this.hook = normalized(hook);
            this.hookQuality = quality(hookQuality);
            this.consumableKind = normalized(consumableKind);
            this.consumable = normalized(consumable);
            this.consumableQuality = quality(consumableQuality);
        }

        public static RigKey of(String pole, Double poleQuality, String line, Double lineQuality,
                                String hook, Double hookQuality, String consumableKind,
                                String consumable, Double consumableQuality) {
            return(new RigKey(pole, poleQuality, line, lineQuality, hook, hookQuality,
                    consumableKind, consumable, consumableQuality));
        }

        static RigKey from(FishingObservation observation) {
            return(of(observation.poleName, observation.poleQuality, observation.lineName,
                    observation.lineQuality, observation.hookName, observation.hookQuality,
                    observation.consumableKind, observation.consumableName,
                    observation.consumableQuality));
        }

        @Override
        public boolean equals(Object other) {
            if(this == other)
                return(true);
            if(!(other instanceof RigKey))
                return(false);
            RigKey that = (RigKey)other;
            return(pole.equals(that.pole) && poleQuality.equals(that.poleQuality) &&
                    line.equals(that.line) && lineQuality.equals(that.lineQuality) &&
                    hook.equals(that.hook) && hookQuality.equals(that.hookQuality) &&
                    consumableKind.equals(that.consumableKind) && consumable.equals(that.consumable) &&
                    consumableQuality.equals(that.consumableQuality));
        }

        @Override
        public int hashCode() {
            return(Objects.hash(pole, poleQuality, line, lineQuality, hook, hookQuality,
                    consumableKind, consumable, consumableQuality));
        }
    }

    public static final class RigSummary {
        public final RigKey key;
        public final String label;
        public final String poleName;
        public final String lineName;
        public final String hookName;
        public final String consumableKind;
        public final String consumableName;
        public final List<FishResult> fish = new ArrayList<>();
        public int catchCount;
        public int surveyCount;
        public Integer bestChance;
        public String bestFish = "";
        private final Map<String, FishResult> fishByKey = new LinkedHashMap<>();

        RigSummary(RigKey key, FishingObservation example) {
            this.key = key;
            poleName = clean(example.poleName);
            lineName = clean(example.lineName);
            hookName = clean(example.hookName);
            consumableKind = clean(example.consumableKind);
            consumableName = clean(example.consumableName);
            label = poleName + " | " + lineName + " | " + hookName + " | " +
                    consumableKind + ": " + consumableName;
        }

        void add(FishingObservation observation) {
            if(isSurvey(observation))
                surveyCount++;
            for(FishingChoice choice : FishingChanceTable.parse(observation.choiceRowsJson)) {
                if(bestChance == null || choice.finalPercent > bestChance) {
                    bestChance = choice.finalPercent;
                    bestFish = choice.fishName;
                }
                FishResult result = fishByKey.computeIfAbsent(normalized(choice.fishName),
                        ignored -> new FishResult(choice.fishName));
                if(result.bestOfferedChance == null || choice.finalPercent > result.bestOfferedChance)
                    result.bestOfferedChance = choice.finalPercent;
            }
            if(isCatch(observation)) {
                catchCount++;
                String name = clean(observation.fishName);
                FishResult result = fishByKey.computeIfAbsent(normalized(name),
                        ignored -> new FishResult(name));
                result.catches++;
                if(observation.fishQuality != null) {
                    result.qualityCount++;
                    result.qualityTotal += observation.fishQuality;
                }
                Integer chance = FishingChanceTable.finalPercent(observation);
                if(chance != null && (result.bestCaughtChance == null || chance > result.bestCaughtChance))
                    result.bestCaughtChance = chance;
            }
            fish.clear();
            fish.addAll(fishByKey.values());
            fish.sort(Comparator.comparingInt((FishResult result) -> result.catches).reversed()
                    .thenComparing(Comparator.comparingInt(
                            (FishResult result) -> value(result.bestOfferedChance)).reversed())
                    .thenComparing(result -> result.fishName, String.CASE_INSENSITIVE_ORDER));
        }
    }

    public static final class FishResult {
        public final String fishName;
        public int catches;
        public int qualityCount;
        public double qualityTotal;
        public Integer bestCaughtChance;
        public Integer bestOfferedChance;

        FishResult(String fishName) {
            this.fishName = clean(fishName);
        }

        public Double averageQuality() {
            return(qualityCount == 0 ? null : qualityTotal / qualityCount);
        }
    }

    public static final class SpotSummary {
        public final long gridId;
        public int samples;
        public Integer bestChance;
        public String bestFish = "";
        public String bestRig = "";
        public long latestObservedAt;
        private long tileXTotal;
        private long tileYTotal;

        SpotSummary(long gridId, int tileX, int tileY) {
            this.gridId = gridId;
            tileXTotal = tileX;
            tileYTotal = tileY;
        }

        void add(FishingObservation observation, RigKey rig) {
            int tileX = (int)Math.floor(observation.gridOffsetX / MCache.tilesz.x);
            int tileY = (int)Math.floor(observation.gridOffsetY / MCache.tilesz.y);
            if(samples > 0) {
                tileXTotal += tileX;
                tileYTotal += tileY;
            }
            samples++;
            latestObservedAt = Math.max(latestObservedAt, observation.observedAt);
            RigSummary label = new RigSummary(rig, observation);
            for(FishingChoice choice : FishingChanceTable.parse(observation.choiceRowsJson)) {
                if(bestChance == null || choice.finalPercent > bestChance) {
                    bestChance = choice.finalPercent;
                    bestFish = choice.fishName;
                    bestRig = label.label;
                }
            }
        }

        public int tileX() {
            return((int)Math.round(tileXTotal / (double)Math.max(1, samples)));
        }

        public int tileY() {
            return((int)Math.round(tileYTotal / (double)Math.max(1, samples)));
        }
    }
}
