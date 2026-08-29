package haven.fishing;

/** Immutable evidence captured from a fishing chance survey or completed catch. */
public final class FishingObservation {
    public final long id;
    public final String worldId;
    public final long segmentId;
    public final long gridId;
    public final double gridOffsetX;
    public final double gridOffsetY;
    public final double castX;
    public final double castY;
    public final double playerX;
    public final double playerY;
    public final String waterResource;
    public final long observedAt;
    public final long gameTimeSeconds;
    public final int gameDay;
    public final int gameSecondOfDay;
    public final boolean night;
    public final String moonPhase;
    public final String season;
    public final String fishResource;
    public final String fishName;
    public final Double fishQuality;
    public final String poleResource;
    public final String poleName;
    public final Double poleQuality;
    public final String lineResource;
    public final String lineName;
    public final Double lineQuality;
    public final String hookResource;
    public final String hookName;
    public final Double hookQuality;
    public final String consumableKind;
    public final String consumableResource;
    public final String consumableName;
    public final Double consumableQuality;
    public final String choiceRowsJson;
    public final Integer survival;
    public final Integer will;
    public final String outcome;
    public final String confidence;
    public final int schemaVersion;

    private FishingObservation(Builder builder) {
        this.id = builder.id;
        this.worldId = clean(builder.worldId);
        this.segmentId = builder.segmentId;
        this.gridId = builder.gridId;
        this.gridOffsetX = builder.gridOffsetX;
        this.gridOffsetY = builder.gridOffsetY;
        this.castX = builder.castX;
        this.castY = builder.castY;
        this.playerX = builder.playerX;
        this.playerY = builder.playerY;
        this.waterResource = clean(builder.waterResource);
        this.observedAt = builder.observedAt;
        this.gameTimeSeconds = builder.gameTimeSeconds;
        this.gameDay = builder.gameDay;
        this.gameSecondOfDay = builder.gameSecondOfDay;
        this.night = builder.night;
        this.moonPhase = clean(builder.moonPhase);
        this.season = clean(builder.season);
        this.fishResource = clean(builder.fishResource);
        this.fishName = clean(builder.fishName);
        this.fishQuality = builder.fishQuality;
        this.poleResource = clean(builder.poleResource);
        this.poleName = clean(builder.poleName);
        this.poleQuality = builder.poleQuality;
        this.lineResource = clean(builder.lineResource);
        this.lineName = clean(builder.lineName);
        this.lineQuality = builder.lineQuality;
        this.hookResource = clean(builder.hookResource);
        this.hookName = clean(builder.hookName);
        this.hookQuality = builder.hookQuality;
        this.consumableKind = clean(builder.consumableKind);
        this.consumableResource = clean(builder.consumableResource);
        this.consumableName = clean(builder.consumableName);
        this.consumableQuality = builder.consumableQuality;
        this.choiceRowsJson = clean(builder.choiceRowsJson);
        this.survival = builder.survival;
        this.will = builder.will;
        this.outcome = clean(builder.outcome);
        this.confidence = clean(builder.confidence);
        this.schemaVersion = builder.schemaVersion;
    }

    public Builder copy() {
        return(new Builder(this));
    }

    private static String clean(String value) {
        return(value == null ? "" : value.trim());
    }

    public static final class Builder {
        private long id = -1;
        private String worldId;
        private long segmentId = -1;
        private long gridId = -1;
        private double gridOffsetX;
        private double gridOffsetY;
        private double castX;
        private double castY;
        private double playerX;
        private double playerY;
        private String waterResource;
        private long observedAt = System.currentTimeMillis();
        private long gameTimeSeconds;
        private int gameDay;
        private int gameSecondOfDay;
        private boolean night;
        private String moonPhase;
        private String season;
        private String fishResource;
        private String fishName;
        private Double fishQuality;
        private String poleResource;
        private String poleName;
        private Double poleQuality;
        private String lineResource;
        private String lineName;
        private Double lineQuality;
        private String hookResource;
        private String hookName;
        private Double hookQuality;
        private String consumableKind;
        private String consumableResource;
        private String consumableName;
        private Double consumableQuality;
        private String choiceRowsJson;
        private Integer survival;
        private Integer will;
        private String outcome = "caught";
        private String confidence = "candidate";
        private int schemaVersion = 1;

        public Builder() {
        }

        private Builder(FishingObservation source) {
            id = source.id;
            worldId = source.worldId;
            segmentId = source.segmentId;
            gridId = source.gridId;
            gridOffsetX = source.gridOffsetX;
            gridOffsetY = source.gridOffsetY;
            castX = source.castX;
            castY = source.castY;
            playerX = source.playerX;
            playerY = source.playerY;
            waterResource = source.waterResource;
            observedAt = source.observedAt;
            gameTimeSeconds = source.gameTimeSeconds;
            gameDay = source.gameDay;
            gameSecondOfDay = source.gameSecondOfDay;
            night = source.night;
            moonPhase = source.moonPhase;
            season = source.season;
            fishResource = source.fishResource;
            fishName = source.fishName;
            fishQuality = source.fishQuality;
            poleResource = source.poleResource;
            poleName = source.poleName;
            poleQuality = source.poleQuality;
            lineResource = source.lineResource;
            lineName = source.lineName;
            lineQuality = source.lineQuality;
            hookResource = source.hookResource;
            hookName = source.hookName;
            hookQuality = source.hookQuality;
            consumableKind = source.consumableKind;
            consumableResource = source.consumableResource;
            consumableName = source.consumableName;
            consumableQuality = source.consumableQuality;
            choiceRowsJson = source.choiceRowsJson;
            survival = source.survival;
            will = source.will;
            outcome = source.outcome;
            confidence = source.confidence;
            schemaVersion = source.schemaVersion;
        }

        public Builder id(long value) { id = value; return(this); }
        public Builder worldId(String value) { worldId = value; return(this); }
        public Builder location(long segment, long grid, double offsetX, double offsetY,
                                double castX, double castY, double playerX, double playerY,
                                String waterResource) {
            segmentId = segment;
            gridId = grid;
            gridOffsetX = offsetX;
            gridOffsetY = offsetY;
            this.castX = castX;
            this.castY = castY;
            this.playerX = playerX;
            this.playerY = playerY;
            this.waterResource = waterResource;
            return(this);
        }
        public Builder observedAt(long value) { observedAt = value; return(this); }
        public Builder gameTime(long seconds, int day, int secondOfDay, boolean night,
                                String moonPhase, String season) {
            gameTimeSeconds = seconds;
            gameDay = day;
            gameSecondOfDay = secondOfDay;
            this.night = night;
            this.moonPhase = moonPhase;
            this.season = season;
            return(this);
        }
        public Builder fish(String resource, String name, Double quality) {
            fishResource = resource;
            fishName = name;
            fishQuality = quality;
            return(this);
        }
        public Builder pole(String resource, String name, Double quality) {
            poleResource = resource;
            poleName = name;
            poleQuality = quality;
            return(this);
        }
        public Builder line(String resource, String name, Double quality) {
            lineResource = resource;
            lineName = name;
            lineQuality = quality;
            return(this);
        }
        public Builder hook(String resource, String name, Double quality) {
            hookResource = resource;
            hookName = name;
            hookQuality = quality;
            return(this);
        }
        public Builder consumable(String kind, String resource, String name, Double quality) {
            consumableKind = kind;
            consumableResource = resource;
            consumableName = name;
            consumableQuality = quality;
            return(this);
        }
        public Builder choiceRowsJson(String value) { choiceRowsJson = value; return(this); }
        public Builder stats(Integer survival, Integer will) {
            this.survival = survival;
            this.will = will;
            return(this);
        }
        public Builder outcome(String value) { outcome = value; return(this); }
        public Builder confidence(String value) { confidence = value; return(this); }
        public Builder schemaVersion(int value) { schemaVersion = value; return(this); }
        public FishingObservation build() { return(new FishingObservation(this)); }
    }
}
