package haven;

/** Formatting and hit-selection helpers for the interactive vitality display. */
public final class MoonFlowerVitalInfo {
    public static final int HEALTH = 0;
    public static final int STAMINA = 1;
    public static final int ENERGY = 2;

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
        return String.format("%s\n%s: %d%%\nMissing: %d%%", name, currentLabel, current, 100 - current);
    }

    public static String speedTooltip(double speed) {
        return String.format("Movement speed\nCurrent: %.2f units/second", Math.max(0, speed));
    }

    private static int percent(double value) {
        return(int)Math.round(Math.max(0, Math.min(1, value)) * 100);
    }
}
