package haven.worldactivity;

/** Focused deterministic checks for the first World Activity Board slice. */
public final class WorldActivityChecks {
    private WorldActivityChecks() {
    }

    public static void main(String[] args) {
        check(WorldActivityTimingParser.parseDurationMillis(
                        "This resource will refill in 2 days, 3 hours and 4 minutes.",
                        WorldActivityType.LOCALIZED_RESOURCE)
                        == ((2L * 86_400L + 3L * 3_600L + 4L * 60L) * 1_000L),
                "localized-resource duration");
        check(WorldActivityTimingParser.parseDurationMillis(
                        "The mine pyre is lit and will burn for 7 real days.",
                        WorldActivityType.PYRE) == 7L * 86_400L * 1_000L,
                "pyre duration");
        check(WorldActivityTimingParser.parseDurationMillis(
                        "Quality: 42 and the message has 3 hours.",
                        WorldActivityType.LOCALIZED_RESOURCE) < 0L,
                "unrelated duration rejected");
        check(WorldActivityTimingParser.parseQuality("Quality: 42.5") == 42.5d,
                "quality parsing");
        check(WorldActivityTimingParser.parseFuelState("The pyre is unlit and has no fuel.")
                        == WorldActivityFuelState.NO_FUEL,
                "fuel fail-closed state");
        check(WorldActivityTimingParser.parseFuelState("The pyre is burning.")
                        == WorldActivityFuelState.LIT,
                "lit state");
        check("1d 01:01:01".equals(WorldActivityTimingParser.formatRemaining(90_061_000L)),
                "remaining format");
        check(WorldActivityDetector.classifyResourceName("gfx/terobjs/pyre")
                        == WorldActivityType.PYRE, "pyre classification");
        check(WorldActivityDetector.classifyResourceName("gfx/terobjs/mm/saltbasin")
                        == WorldActivityType.LOCALIZED_RESOURCE, "localized classification");
        check(WorldActivityDetector.classifyResourceName("gfx/terobjs/gardenpot")
                        == WorldActivityType.GARDEN_POT, "future garden-pot classification");
        check(WorldActivityDetector.classifyResourceName("gfx/terobjs/dframe")
                        == WorldActivityType.DRYING_RACK, "future drying-rack classification");
        check(WorldActivityDetector.classifyResourceName("gfx/terobjs/herbalisttable")
                        == WorldActivityType.HERBALIST_TABLE, "future herbalist-table classification");
        check(WorldActivityDetector.classifyResourceName("gfx/terobjs/kiln")
                        == WorldActivityType.KILN, "future kiln classification");
        check(WorldActivityDetector.classifyResourceName("gfx/terobjs/oven")
                        == WorldActivityType.OVEN, "future oven classification");
        check(WorldActivityDetector.classifyResourceName("gfx/terobjs/oresmelter")
                        == WorldActivityType.SMELTER, "future smelter classification");
        check(WorldActivityDetector.classifyResourceName("gfx/terobjs/field")
                        == WorldActivityType.FIELD, "future field classification");
        check(WorldActivityType.KILN.starterSupported() == false
                        && WorldActivityType.PYRE.starterSupported(),
                "starter scope");

        long now = System.currentTimeMillis();
        WorldActivityEntry running = new WorldActivityEntry(1L, WorldActivityType.PYRE,
                "gfx/terobjs/pyre", "Mine Pyre", 50.0d, now,
                now + 60_000L, WorldActivityFuelState.LIT, true);
        WorldActivityEntry due = new WorldActivityEntry(2L, WorldActivityType.PYRE,
                "gfx/terobjs/pyre", "Mine Pyre", null, now,
                now - 1L, WorldActivityFuelState.UNKNOWN, false);
        check(running.state(now) == WorldActivityState.RUNNING, "running state");
        check(due.state(now) == WorldActivityState.DUE, "due state");
        System.out.println("World activity checks passed.");
    }

    private static void check(boolean condition, String name) {
        if(!condition)
            throw(new AssertionError("World activity check failed: " + name));
    }
}
