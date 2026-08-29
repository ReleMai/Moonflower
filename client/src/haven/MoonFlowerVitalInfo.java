package haven;

/** Formatting and hit-selection helpers for the interactive vitality display. */
public final class MoonFlowerVitalInfo {
    public static final int HEALTH = 0;
    public static final int STAMINA = 1;
    public static final int ENERGY = 2;
    public static final double HEALING_ENERGY_THRESHOLD = 0.80;
    public static final double STARVING_ENERGY_THRESHOLD = 0.20;

    public enum EnergyState {
        HEALING,
        BELOW_HEALING,
        STARVING
    }

    private MoonFlowerVitalInfo() {
    }

    public static int nearestRing(double distance, int healthRadius, int staminaRadius,
                                  int energyRadius, int tolerance) {
        double healthDistance = Math.abs(distance - healthRadius);
        double staminaDistance = Math.abs(distance - staminaRadius);
        double energyDistance = Math.abs(distance - energyRadius);
        double nearest = Math.min(healthDistance, Math.min(staminaDistance, energyDistance));
        if(nearest > tolerance)
            return -1;
        if(nearest == healthDistance)
            return HEALTH;
        return(nearest == staminaDistance) ? STAMINA : ENERGY;
    }

    public static String healthTooltip(IMeter.HealthState health, double fallback) {
        if(health == null) {
            int current = percent(fallback);
            return String.format("Health\nCurrent: %d%%\nMissing: %d%%", current, 100 - current);
        }
        int recoverable = Math.max(0, health.hhp - health.shp);
        int wounds = Math.max(0, health.mhp - health.hhp);
        int missing = Math.max(0, health.mhp - health.shp);
        return String.format("Health\nSoft health: %d / %d (%d%%)\nHard-health ceiling: %d / %d (%d%%)\n" +
                        "Recoverable damage: %d\nWound damage: %d\nTotal missing: %d",
                health.shp, health.mhp, percent(health.softPercentage / 100.0),
                health.hhp, health.mhp, percent(health.hardPercentage / 100.0),
                recoverable, wounds, missing);
    }

    public static String percentageTooltip(String name, double value, String currentLabel) {
        int current = percent(value);
        String state = "Energy".equals(name) ? "\nState: " + energyStateLabel(value) : "";
        return String.format("%s\n%s: %d%%\nMissing: %d%%%s", name, currentLabel, current, 100 - current, state);
    }

    public static boolean starving(double energy) {
        return energy <= STARVING_ENERGY_THRESHOLD;
    }

    public static boolean healing(double energy) {
        return energy >= HEALING_ENERGY_THRESHOLD;
    }

    public static EnergyState energyState(double energy) {
        if(starving(energy))
            return EnergyState.STARVING;
        return healing(energy) ? EnergyState.HEALING : EnergyState.BELOW_HEALING;
    }

    public static String energyStateLabel(double energy) {
        return switch(energyState(energy)) {
            case HEALING -> "Healing";
            case BELOW_HEALING -> "Below healing threshold";
            case STARVING -> "Starving";
        };
    }

    public static double softHealthFraction(IMeter.HealthState health, double fallback) {
        if(health == null)
            return clip(fallback);
        return clip(health.softPercentage / 100.0);
    }

    public static double hardHealthFraction(IMeter.HealthState health, double fallback) {
        if(health == null)
            return clip(fallback);
        return clip(health.hardPercentage / 100.0);
    }

    public static double recoverableHealthFraction(IMeter.HealthState health) {
        if(health == null || health.mhp <= 0)
            return 0;
        return clip((health.hhp - health.shp) / (double)health.mhp);
    }

    public static String starvationLabel(double energy) {
        return String.format("STARVING · %d%%", percent(energy));
    }

    public static String movementModeName(int mode) {
        String[] names = {"Crawl", "Walk", "Run", "Sprint"};
        return names[Math.max(0, Math.min(names.length - 1, mode))];
    }

    public static String speedTooltip(double speed) {
        return String.format("Movement speed\nCurrent: %.2f units/second", Math.max(0, speed));
    }

    private static int percent(double value) {
        return(int)Math.round(clip(value) * 100);
    }

    private static double clip(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
