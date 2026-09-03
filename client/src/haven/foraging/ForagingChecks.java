package haven.foraging;

import haven.Coord;
import haven.Coord2d;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Account-free deterministic checks for the supervised foraging contracts. */
public final class ForagingChecks {
    private ForagingChecks() {
    }

    public static void main(String[] args) throws Exception {
        classificationChecks();
        inventoryChecks();
        routeChecks();
        persistenceChecks();
        layoutChecks();
        System.out.println("ForagingChecks: PASS");
    }

    private static void classificationChecks() {
        check(ForagingGobScanner.isForageable("gfx/terobjs/herbs/chantrelle"),
                "normal herb identity should classify");
        check(ForagingGobScanner.isForageable("gfx/terobjs/items/precioussnowflake"),
                "reviewed exception should classify");
        check(!ForagingGobScanner.isForageable("gfx/terobjs/herbsfake/chantrelle"),
                "prefix boundary must be exact");
        check(!ForagingGobScanner.isForageable("gfx/terobjs/trees/oak"),
                "unreviewed resources must not classify");
        check(ForagingInventoryService.basename("gfx/terobjs/herbs/chantrelle")
                        .equals("chantrelle"),
                "ground and inventory compatibility should use exact basename");
        check(ForagingHerbIconCache.inventoryIconResourceName("gfx/terobjs/herbs/yarrow")
                        .equals("gfx/invobjs/herbs/yarrow"),
                "ordinary forageables should use their inventory icon path");
        check(ForagingHerbIconCache.inventoryIconResourceName("gfx/terobjs/items/precioussnowflake")
                        .equals("gfx/invobjs/precioussnowflake"),
                "reviewed item forageables should use their item icon path");
        check(ForagingHerbAtlas.entries().size() >= 80,
                "the guide catalog should expose the complete reviewed forageable list");
        Set<String> resources = new LinkedHashSet<>();
        for(ForagingGobScanner.HerbResource herb : ForagingHerbAtlas.entries()) {
            check(ForagingGobScanner.isForageable(herb.resourceName),
                    "every guide entry must remain inside the exact targeting boundary");
            check(resources.add(herb.resourceName), "guide resource identities must be unique");
        }
    }

    private static void inventoryChecks() {
        boolean[] mask = {false, true, false, false, false, false};
        int free = ForagingInventoryService.countFree(Coord.of(3, 2), mask,
                Set.of(Coord.of(0, 1)), Set.of(Coord.of(0, 0), Coord.of(2, 1)));
        check(free == 2, "masks, locks, and occupied cells must all reduce capacity");
    }

    private static void routeChecks() {
        ForagingRoutePlanner planner = new ForagingRoutePlanner();
        SyntheticSafety open = new SyntheticSafety();
        ForagingRoutePlanner.Plan first = planner.plan(Coord.z, Set.of(Coord.of(3, 2)), open);
        ForagingRoutePlanner.Plan second = planner.plan(Coord.z, Set.of(Coord.of(3, 2)), open);
        check(first.found() && first.tiles.equals(second.tiles), "planning must be deterministic");
        check(first.tiles.get(0).equals(Coord.z) && first.tiles.get(first.tiles.size() - 1).equals(Coord.of(3, 2)),
                "safe path endpoints were not preserved");

        SyntheticSafety corner = new SyntheticSafety();
        corner.blocked.add(Coord.of(1, 0));
        corner.blocked.add(Coord.of(0, 1));
        ForagingRoutePlanner.Plan diagonal = planner.plan(Coord.z, Set.of(Coord.of(1, 1)), corner);
        check(diagonal.found() && diagonal.cost() > Math.sqrt(2),
                "diagonal corner cutting must be rejected even when a longer route exists");

        SyntheticSafety ridge = new SyntheticSafety();
        ridge.blockedEdges.add(edge(Coord.of(0, 0), Coord.of(1, 0)));
        ForagingRoutePlanner.Plan aroundRidge = planner.plan(Coord.z, Set.of(Coord.of(2, 0)), ridge);
        check(aroundRidge.found() && aroundRidge.cost() > 2.0,
                "a forbidden ridge edge must change safe-path distance");

        double corridor = ForagingController.distanceToRoute(new Coord2d(5, 4),
                List.of(new Coord2d(0, 0), new Coord2d(10, 0)));
        check(Math.abs(corridor - 4.0) < 0.0001, "corridor distance should project onto route segments");
        List<Coord2d> northEast = ForagingController.directionalRoute(new Coord2d(100, 100),
                ForagingDirection.NORTH_EAST);
        check(northEast.size() > 2 && northEast.get(1).x > 100 && northEast.get(1).y < 100,
                "north-east direction should create a bounded forward corridor");
        check(ForagingController.directionalRoute(new Coord2d(100, 100),
                ForagingDirection.ROUTE).isEmpty(), "Route mode must not fabricate direction points");
        List<Coord2d> plotted = List.of(new Coord2d(0, 0), new Coord2d(30, 40), new Coord2d(30, 60));
        check(Math.abs(ForagingController.routeLength(plotted) - 70.0) < 0.0001,
                "plotted route length should be the sum of its segments");
    }

    private static void persistenceChecks() throws Exception {
        Path database = Files.createTempFile("haven-foraging-checks-", ".db");
        try(ForagingRepository repository = new ForagingRepository("jdbc:sqlite:" + database)) {
            ForagingGobScanner.HerbResource herb = new ForagingGobScanner.HerbResource(
                    "gfx/terobjs/herbs/chantrelle", "Chantrelle");
            repository.observe("world-a", herb, true);
            check(repository.loadSelection("world-a").equals(Set.of(herb.resourceName)),
                    "selected resource should persist per world");
            check(repository.loadSelection("world-b").isEmpty(),
                    "profile selection must remain world scoped");
            repository.observe("world-a", herb, false);
            check(repository.loadSelection("world-a").isEmpty(),
                    "deselection should persist without deleting the observation");
            repository.saveDirection("world-a", ForagingDirection.SOUTH_WEST);
            check(repository.loadDirection("world-a") == ForagingDirection.SOUTH_WEST,
                    "travel direction should persist per world");
            check(repository.loadDirection("world-b") == ForagingDirection.NORTH,
                    "new worlds should receive the safe default direction");
            List<Coord2d> route = List.of(new Coord2d(10.5, 20.5), new Coord2d(50.5, 20.5));
            repository.saveRoute("world-a", route);
            check(repository.loadRoute("world-a").equals(route),
                    "plotted route should persist in point order per world");
            check(repository.loadRoute("world-b").isEmpty(),
                    "plotted routes must remain world scoped");
            repository.saveRoute("world-a", List.of());
            check(repository.loadRoute("world-a").isEmpty(),
                    "clearing a plotted route should remove its saved points");
        } finally {
            Files.deleteIfExists(database);
        }
    }

    private static void layoutChecks() {
        Coord screen = Coord.of(1280, 720);
        Coord fitted = ForagingWindow.fittedSize(screen);
        check(fitted.x <= screen.x && fitted.y <= screen.y,
                "themed window must fit a 1280x720 screen at the active UI scale");
        check(fitted.x >= 320 && fitted.y >= 280,
                "compact layout must retain its absolute readable floor");
    }

    private static String edge(Coord a, Coord b) {
        return(a.x + "," + a.y + ">" + b.x + "," + b.y);
    }

    private static final class SyntheticSafety implements ForagingRoutePlanner.SafetyRaster {
        final Set<Coord> blocked = new HashSet<>();
        final Set<String> blockedEdges = new HashSet<>();

        @Override
        public boolean passable(Coord tile) {
            return(Math.abs(tile.x) <= 8 && Math.abs(tile.y) <= 8 && !blocked.contains(tile));
        }

        @Override
        public boolean canStep(Coord from, Coord to) {
            Coord delta = to.sub(from);
            if(delta.x != 0 && delta.y != 0 &&
                    (!passable(from.add(delta.x, 0)) || !passable(from.add(0, delta.y))))
                return(false);
            return(!blockedEdges.contains(edge(from, to)) && !blockedEdges.contains(edge(to, from)));
        }
    }

    private static void check(boolean condition, String message) {
        if(!condition)
            throw(new AssertionError(message));
    }
}
