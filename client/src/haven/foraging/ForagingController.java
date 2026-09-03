package haven.foraging;

import haven.Coord;
import haven.Coord2d;
import haven.FlowerMenu;
import haven.GameUI;
import haven.Gob;
import haven.IMeter;
import haven.Loading;
import haven.Locked;
import haven.MCache;
import haven.MapFile;
import haven.Resource;
import haven.Widget;

import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static haven.OCache.posres;

/** Single UI-thread logical state machine and the only Phase 1 gameplay dispatcher. */
public final class ForagingController extends Widget {
    private static final long SCAN_INTERVAL_MS = 750;
    private static final long MOVE_TIMEOUT_MS = 14_000;
    private static final long PICK_TIMEOUT_MS = 7_000;
    private static final long BLACKLIST_MS = 30_000;
    private static final int RESERVE_CELLS = 4;
    private static final int CORRIDOR_TILES = 8;
    private static final int DIRECTION_RUN_TILES = 50;
    private static final int DIRECTION_STEP_TILES = 4;
    private static final int MAX_ROUTE_POINTS = 64;
    private static final int MAX_ROUTE_LENGTH_TILES = 500;
    private static final DateTimeFormatter EVENT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final GameUI gui;
    private final ForagingGobScanner scanner;
    private final ForagingInventoryService inventory;
    private final ForagingRoutePlanner planner = new ForagingRoutePlanner();
    private ForagingRepository repository;
    private final Set<String> selected = new LinkedHashSet<>();
    private final List<ForagingGobScanner.HerbResource> catalog = new ArrayList<>();
    private final List<Coord2d> configuredRoute = new ArrayList<>();
    private final java.util.Map<String, String> persistedCatalog = new java.util.HashMap<>();
    private final ArrayDeque<String> events = new ArrayDeque<>();
    private final java.util.Map<Long, Long> blacklist = new java.util.HashMap<>();

    private ForagingSnapshot.State state = ForagingSnapshot.State.IDLE;
    private String reason = "Choose forageables and a travel direction.";
    private List<Coord2d> route = List.of();
    private int routeIndex;
    private ForagingTarget target;
    private List<Coord> path = List.of();
    private Coord2d movementDestination;
    private long deadline;
    private long nextScan;
    private int beforeAmount;
    private int sessionYield;
    private long segmentId = -1;
    private ForagingDirection direction = ForagingDirection.NORTH;
    private int freeCells = -1;
    private ForagingSnapshot snapshot;

    public ForagingController(GameUI gui) {
        super(Coord.z);
        this.gui = gui;
        scanner = new ForagingGobScanner(gui);
        inventory = new ForagingInventoryService(gui);
        try {
            repository = new ForagingRepository();
            selected.addAll(repository.loadSelection(gui.genus));
            direction = repository.loadDirection(gui.genus);
            for(Coord2d point : repository.loadRoute(gui.genus)) {
                if(!validRoutePoint(point) || configuredRoute.size() >= MAX_ROUTE_POINTS)
                    continue;
                List<Coord2d> candidate = new ArrayList<>(configuredRoute);
                candidate.add(point);
                if(routeLength(candidate) <= MAX_ROUTE_LENGTH_TILES * MCache.tilesz.x)
                    configuredRoute.add(point);
            }
        } catch(SQLException e) {
            repository = null;
            record("Profile database unavailable: " + concise(e.getMessage()));
        }
        publish();
    }

    public synchronized ForagingSnapshot snapshot() {
        return(snapshot);
    }

    GameUI gameUI() {
        return(gui);
    }

    public synchronized boolean active() {
        return(snapshot != null && snapshot.active());
    }

    public synchronized List<Coord2d> configuredRoute() {
        return(List.copyOf(configuredRoute));
    }

    public synchronized void addRoutePoint(Coord2d point) {
        if(!validRoutePoint(point)) {
            reason = "Route point rejected: the map coordinate is unavailable.";
            publish();
            return;
        }
        if(runInProgress()) {
            pause("Paused: plotted route changed by the user.", true);
            route = List.of();
            routeIndex = 0;
        }
        if(!configuredRoute.isEmpty() &&
                configuredRoute.get(configuredRoute.size() - 1).dist(point) < MCache.tilesz.x) {
            reason = "Route point ignored: it is too close to the previous point.";
            publish();
            return;
        }
        if(configuredRoute.size() >= MAX_ROUTE_POINTS) {
            reason = "Route point rejected: the bounded path already has " + MAX_ROUTE_POINTS + " points.";
            publish();
            return;
        }
        List<Coord2d> candidate = new ArrayList<>(configuredRoute);
        candidate.add(point);
        if(routeLength(candidate) > MAX_ROUTE_LENGTH_TILES * MCache.tilesz.x) {
            reason = "Route point rejected: the bounded path may not exceed " +
                    MAX_ROUTE_LENGTH_TILES + " tiles.";
            publish();
            return;
        }
        configuredRoute.add(point);
        direction = ForagingDirection.ROUTE;
        persistDirection();
        persistRoute();
        reason = "Route point " + configuredRoute.size() + " added. Add another point or start the run.";
        publish();
    }

    public synchronized void removeLastRoutePoint() {
        if(runInProgress()) {
            pause("Paused: plotted route changed by the user.", true);
            route = List.of();
            routeIndex = 0;
        }
        if(configuredRoute.isEmpty()) {
            reason = "No plotted route point to remove.";
        } else {
            configuredRoute.remove(configuredRoute.size() - 1);
            persistRoute();
            reason = configuredRoute.isEmpty() ?
                    "Plotted route cleared." :
                    "Removed the last plotted route point.";
        }
        publish();
    }

    public synchronized void clearRoute() {
        if(runInProgress())
            pause("Paused: plotted route cleared by the user.", true);
        route = List.of();
        routeIndex = 0;
        configuredRoute.clear();
        persistRoute();
        reason = "Plotted route cleared.";
        publish();
    }

    public synchronized void toggleSelection(String resourceName) {
        if(!ForagingGobScanner.isForageable(resourceName))
            return;
        if(!selected.add(resourceName))
            selected.remove(resourceName);
        persistCatalog();
        if(active())
            pause("Herb selection changed during the run.", true);
        publish();
    }

    public synchronized void setAllSelected(boolean enabled) {
        selected.clear();
        if(enabled) {
            List<ForagingGobScanner.HerbResource> source = catalog.isEmpty() ?
                    ForagingHerbAtlas.entries() : catalog;
            for(ForagingGobScanner.HerbResource herb : source)
                selected.add(herb.resourceName);
        }
        persistedCatalog.clear();
        persistCatalog();
        if(active())
            pause("Herb selection changed during the run.", true);
        publish();
    }

    public synchronized void selectDirection(ForagingDirection next) {
        if(next == null || next == direction)
            return;
        direction = next;
        persistDirection();
        if(active())
            pause("Travel direction changed to " + direction.label + ".", true);
        else
            reason = direction.usesCheckpointRoute() ?
                    "Plotted route mode selected." :
                    "Direction " + direction.label + " selected for a bounded 50-tile run.";
        publish();
    }

    public synchronized void startOrResume() {
        if(active())
            return;
        boolean newRun = state == ForagingSnapshot.State.IDLE ||
                state == ForagingSnapshot.State.COMPLETE || state == ForagingSnapshot.State.FAILED;
        transition(ForagingSnapshot.State.PREFLIGHT, "Checking route, vitals, inventory, and selected herbs.");
        String failure = preflight();
        if(failure != null) {
            transition(ForagingSnapshot.State.PAUSED, failure);
            return;
        }
        Gob player = gui.map.player();
        route = direction.usesCheckpointRoute() ? List.copyOf(configuredRoute) :
                directionalRoute(player.rc, direction);
        segmentId = currentSegment(player.rc);
        if(segmentId < 0) {
            transition(ForagingSnapshot.State.PAUSED,
                    "Start blocked: current map segment identity is unreadable.");
            return;
        }
        if(direction.usesCheckpointRoute() && !routeMatchesSegment(segmentId)) {
            route = List.of();
            transition(ForagingSnapshot.State.PAUSED,
                    "Start blocked: the plotted route belongs to another or unreadable map segment.");
            return;
        }
        routeIndex = direction.usesCheckpointRoute() ? nearestRoutePoint(player.rc, route) : 1;
        if(newRun)
            sessionYield = 0;
        target = null;
        path = List.of();
        blacklist.clear();
        transition(ForagingSnapshot.State.SCANNING,
                direction.usesCheckpointRoute() ?
                        "Plotted route locked; scanning its bounded corridor." :
                        "Direction " + direction.label + " locked; scanning its bounded corridor.");
        nextScan = 0;
    }

    public synchronized void pause(String why, boolean haltMovement) {
        if(state == ForagingSnapshot.State.IDLE || state == ForagingSnapshot.State.PAUSED)
            return;
        cancelArmedSelection();
        if(haltMovement)
            haltMovement();
        target = null;
        path = List.of();
        transition(ForagingSnapshot.State.PAUSED, why);
    }

    public synchronized void emergencyStop(String why) {
        cancelArmedSelection();
        haltMovement();
        target = null;
        path = List.of();
        route = List.of();
        routeIndex = 0;
        if(state != ForagingSnapshot.State.IDLE || !reason.equals(why))
            transition(ForagingSnapshot.State.IDLE, why);
    }

    public synchronized void noteManualMapInput(int button) {
        if(active() && (button == 1 || button == 3))
            pause("Paused: manual map input took control.", false);
    }

    @Override
    public synchronized void tick(double dt) {
        super.tick(dt);
        long now = System.currentTimeMillis();
        if(now >= nextScan && !active()) {
            refreshCatalog();
            nextScan = now + SCAN_INTERVAL_MS;
        }
        if(!active()) {
            publish();
            return;
        }
        String unsafe = runtimeSafetyReason();
        if(unsafe != null) {
            pause(unsafe, true);
            return;
        }
        switch(state) {
        case SCANNING:
            if(now >= nextScan) {
                scanAndPlan();
                nextScan = now + SCAN_INTERVAL_MS;
            }
            break;
        case TRAVELING:
            advanceTravel(now);
            break;
        case APPROACHING:
            beginPick();
            break;
        case ACKNOWLEDGING:
            acknowledgePick(now);
            break;
        default:
            break;
        }
        publish();
    }

    private String preflight() {
        if(gui.ui == null || gui.ui.sess == null || gui.map == null || gui.map.player() == null)
            return("Start blocked: no loaded player/map session.");
        if(selected.isEmpty())
            return("Start blocked: select at least one forageable.");
        if(direction.usesCheckpointRoute() && configuredRoute.size() < 2)
            return("Start blocked: plot at least two points on the Wayfinder map.");
        if(currentSegment(gui.map.player().rc) < 0)
            return("Start blocked: current map segment identity is unreadable.");
        if(gui.maininv == null)
            return("Start blocked: main inventory is unavailable.");
        if(!gui.hand.isEmpty() || gui.vhand != null)
            return("Start blocked: place the cursor item safely first.");
        if(gui.fv != null && gui.fv.current != null)
            return("Start blocked: combat is active.");
        String vital = vitalReason();
        if(vital != null)
            return(vital);
        freeCells = inventory.freeCells();
        if(freeCells < RESERVE_CELLS)
            return("Start blocked: keep at least " + RESERVE_CELLS + " unlocked inventory cells free.");
        return(null);
    }

    private String runtimeSafetyReason() {
        if(gui.ui == null || gui.ui.sess == null || gui.map == null || gui.map.player() == null)
            return("Paused: player or map session became unavailable.");
        if(gui.fv != null && gui.fv.current != null)
            return("Paused: combat began; automation will not fight.");
        if(!gui.hand.isEmpty() || gui.vhand != null)
            return("Paused: an unexpected cursor item appeared.");
        String vital = vitalReason();
        if(vital != null)
            return(vital.replace("Start blocked:", "Paused:"));
        freeCells = inventory.freeCells();
        if(freeCells < RESERVE_CELLS)
            return("Paused: inventory reserve reached " + freeCells + " free cells.");
        if(!route.isEmpty() && distanceToRoute(gui.map.player().rc, route) > CORRIDOR_TILES * MCache.tilesz.x)
            return("Paused: player diverged outside the copied route corridor.");
        long currentSegment = currentSegment(gui.map.player().rc);
        if(currentSegment < 0)
            return("Paused: current map segment identity became unreadable.");
        if(segmentId >= 0 && currentSegment != segmentId)
            return("Paused: a map segment transition invalidated the copied route.");
        return(null);
    }

    private String vitalReason() {
        IMeter.Meter hp = gui.getmeter("hp", 0);
        IMeter.Meter stamina = gui.getmeter("stam", 0);
        IMeter.Meter energy = gui.getmeter("nrj", 0);
        if(hp == null || stamina == null || energy == null)
            return("Start blocked: required live vitals are unreadable.");
        if(hp.a < 0.02)
            return("Start blocked: health is critical.");
        if(stamina.a < 0.20)
            return("Start blocked: stamina is below the Phase 1 safe threshold; drinking is not enabled yet.");
        if(energy.a < 0.25)
            return("Start blocked: energy is below the safe threshold.");
        return(null);
    }

    private void scanAndPlan() {
        transition(ForagingSnapshot.State.PLANNING, "Evaluating deterministic safe-path distance.");
        ForagingGobScanner.Scan scan = refreshCatalog();
        Gob player = gui.map.player();
        Coord start = player.rc.floor(MCache.tilesz);
        long now = System.currentTimeMillis();
        blacklist.entrySet().removeIf(entry -> entry.getValue() <= now);
        List<ForagingTarget> candidates = new ArrayList<>();
        for(ForagingTarget candidate : scan.targets) {
            if(selected.contains(candidate.resourceName) && !blacklist.containsKey(candidate.gobId) &&
                    distanceToRoute(candidate.coordinate, route) <= CORRIDOR_TILES * MCache.tilesz.x)
                candidates.add(candidate);
        }
        candidates.sort(Comparator.comparingDouble((ForagingTarget candidate) -> player.rc.dist(candidate.coordinate))
                .thenComparing(candidate -> candidate.resourceName).thenComparingLong(candidate -> candidate.gobId));
        CandidatePlan best = null;
        for(ForagingTarget candidate : candidates.subList(0, Math.min(24, candidates.size()))) {
            ForagingRoutePlanner.Plan candidatePath = planner.plan(start, adjacentGoals(candidate),
                    new ForagingMapSafety(gui, candidate.gobId));
            if(!candidatePath.found()) {
                blacklist.put(candidate.gobId, now + BLACKLIST_MS);
                continue;
            }
            CandidatePlan evaluated = new CandidatePlan(candidate, candidatePath);
            if(best == null || evaluated.compareTo(best) < 0)
                best = evaluated;
        }
        if(best != null) {
            target = best.target;
            beginTravel(best.plan.tiles, true);
            return;
        }
        planRouteProgress(start);
    }

    private void planRouteProgress(Coord start) {
        while(routeIndex < route.size() && gui.map.player().rc.dist(route.get(routeIndex)) < MCache.tilesz.x)
            routeIndex++;
        if(routeIndex >= route.size()) {
            target = null;
            transition(ForagingSnapshot.State.COMPLETE,
                    "Route complete. Gathered " + sessionYield + " acknowledged herb(s).");
            return;
        }
        Coord goal = route.get(routeIndex).floor(MCache.tilesz);
        ForagingRoutePlanner.Plan routePath = planner.plan(start, Set.of(goal), new ForagingMapSafety(gui, -1));
        if(!routePath.found()) {
            pause("Paused: route point " + (routeIndex + 1) + " is unsafe. " + routePath.reason, true);
            return;
        }
        target = null;
        beginTravel(routePath.tiles, false);
    }

    private void beginTravel(List<Coord> planned, boolean approachingTarget) {
        path = planned;
        if(path.size() <= 1) {
            transition(approachingTarget ? ForagingSnapshot.State.APPROACHING : ForagingSnapshot.State.SCANNING,
                    approachingTarget ? "At a safe adjacent tile; revalidating Pick." : "Route point reached.");
            return;
        }
        int waypointIndex = Math.min(path.size() - 1, 4);
        movementDestination = ForagingMapSafety.tileCenter(path.get(waypointIndex));
        // Revalidate every outgoing edge immediately before the single movement click.
        ForagingMapSafety safety = new ForagingMapSafety(gui, target == null ? -1 : target.gobId);
        for(int index = 0; index <= waypointIndex; index++) {
            if(!safety.passable(path.get(index)) ||
                    (index > 0 && !safety.canStep(path.get(index - 1), path.get(index)))) {
                pause("Paused: an outgoing path edge became unsafe before dispatch.", true);
                return;
            }
        }
        gui.map.wdgmsg("click", Coord.z, movementDestination.floor(posres), 1, 0);
        deadline = System.currentTimeMillis() + MOVE_TIMEOUT_MS;
        transition(ForagingSnapshot.State.TRAVELING,
                approachingTarget ? "Moving one acknowledged segment toward the selected herb." :
                        "Moving one acknowledged segment along the copied route.");
    }

    private void advanceTravel(long now) {
        Gob player = gui.map.player();
        if(player.rc.dist(movementDestination) <= MCache.tilesz.x * 0.60) {
            if(target != null && player.rc.dist(target.coordinate) <= MCache.tilesz.x * 1.8)
                transition(ForagingSnapshot.State.APPROACHING, "Safe approach complete; revalidating exact target.");
            else
                transition(ForagingSnapshot.State.SCANNING, "Movement acknowledged; rescanning before the next input.");
        } else if(now >= deadline) {
            if(target != null)
                blacklist.put(target.gobId, now + BLACKLIST_MS);
            pause("Paused: movement acknowledgement timed out; no blind retry was sent.", true);
        }
    }

    private void beginPick() {
        Gob gob = exactTargetGob();
        if(gob == null) {
            if(target != null)
                blacklist.put(target.gobId, System.currentTimeMillis() + BLACKLIST_MS);
            target = null;
            transition(ForagingSnapshot.State.SCANNING, "Target changed or disappeared before Pick; rescanning.");
            return;
        }
        beforeAmount = inventory.compatibleResourceAmount(target.resourceName);
        if(beforeAmount < 0) {
            pause("Paused: compatible inventory count is unreadable before Pick.", true);
            return;
        }
        transition(ForagingSnapshot.State.PICKING, "Arming exact Pick for one revalidated Gob.");
        FlowerMenu.setNextSelection("Pick");
        gui.map.wdgmsg("click", Coord.z, gob.rc.floor(posres), 3, 0, 0,
                (int)gob.id, gob.rc.floor(posres), 0, -1);
        deadline = System.currentTimeMillis() + PICK_TIMEOUT_MS;
        transition(ForagingSnapshot.State.ACKNOWLEDGING,
                "Waiting for target removal and compatible main-inventory increase.");
    }

    private void acknowledgePick(long now) {
        int afterAmount = inventory.compatibleResourceAmount(target.resourceName);
        Gob remaining = gui.ui.sess.glob.oc.getgob(target.gobId);
        if(remaining == null && afterAmount > beforeAmount) {
            sessionYield += afterAmount - beforeAmount;
            record("Harvest acknowledged: " + target.displayName + " +" + (afterAmount - beforeAmount));
            target = null;
            transition(ForagingSnapshot.State.SCANNING, "Harvest acknowledged; rescanning safely.");
            nextScan = 0;
        } else if(now >= deadline) {
            long failedId = target.gobId;
            cancelArmedSelection();
            blacklist.put(failedId, now + BLACKLIST_MS);
            pause("Paused: Pick acknowledgement timed out or inventory evidence was ambiguous.", true);
        }
    }

    private Gob exactTargetGob() {
        if(target == null || gui.ui == null || gui.ui.sess == null)
            return(null);
        Gob gob = gui.ui.sess.glob.oc.getgob(target.gobId);
        if(gob == null || gob.rc == null)
            return(null);
        try {
            Resource resource = gob.getres();
            return(resource != null && target.resourceName.equals(resource.name) ? gob : null);
        } catch(Loading ignored) {
            return(null);
        }
    }

    private ForagingGobScanner.Scan refreshCatalog() {
        ForagingGobScanner.Scan scan = scanner.scan();
        catalog.clear();
        catalog.addAll(scan.catalog);
        persistCatalog();
        return(scan);
    }

    private void persistCatalog() {
        if(repository == null)
            return;
        try {
            for(ForagingGobScanner.HerbResource herb : catalog) {
                String signature = herb.displayName + "\u0000" + selected.contains(herb.resourceName);
                if(signature.equals(persistedCatalog.get(herb.resourceName)))
                    continue;
                repository.observe(gui.genus, herb, selected.contains(herb.resourceName));
                persistedCatalog.put(herb.resourceName, signature);
            }
        } catch(SQLException e) {
            record("Profile save failed: " + concise(e.getMessage()));
        }
    }

    private void persistDirection() {
        if(repository == null)
            return;
        try {
            repository.saveDirection(gui.genus, direction);
        } catch(SQLException e) {
            record("Direction save failed: " + concise(e.getMessage()));
        }
    }

    private void persistRoute() {
        if(repository == null)
            return;
        try {
            repository.saveRoute(gui.genus, configuredRoute);
        } catch(SQLException e) {
            record("Route save failed: " + concise(e.getMessage()));
        }
    }

    private boolean runInProgress() {
        return(active() || state == ForagingSnapshot.State.PAUSED);
    }

    private boolean routeMatchesSegment(long expectedSegment) {
        for(Coord2d point : configuredRoute)
            if(currentSegment(point) != expectedSegment)
                return(false);
        return(true);
    }

    private Set<Coord> adjacentGoals(ForagingTarget candidate) {
        Coord center = candidate.coordinate.floor(MCache.tilesz);
        Set<Coord> goals = new HashSet<>();
        for(int y = -1; y <= 1; y++)
            for(int x = -1; x <= 1; x++)
                if(x != 0 || y != 0)
                    goals.add(center.add(x, y));
        return(goals);
    }

    private void cancelArmedSelection() {
        FlowerMenu.setNextSelection(null);
    }

    private void haltMovement() {
        if(gui.map != null && gui.map.player() != null)
            gui.map.wdgmsg("click", Coord.z, gui.map.player().rc.floor(posres), 1, 0);
    }

    private void transition(ForagingSnapshot.State next, String nextReason) {
        if(state != next || !reason.equals(nextReason))
            record(next.name() + ": " + nextReason);
        state = next;
        reason = nextReason;
        publish();
    }

    private void record(String message) {
        events.addFirst(LocalTime.now().format(EVENT_TIME) + "  " + message);
        while(events.size() > 8)
            events.removeLast();
    }

    private void publish() {
        Gob player = gui.map == null ? null : gui.map.player();
        double distance = -1;
        double bearing = 0;
        if(player != null && target != null && target.coordinate != null) {
            distance = player.rc.dist(target.coordinate);
            bearing = Math.atan2(target.coordinate.y - player.rc.y, target.coordinate.x - player.rc.x);
        }
        List<Coord2d> displayRoute = route.isEmpty() ? configuredRoute : route;
        int displayRouteSize = displayRoute.size();
        int displayRouteIndex = route.isEmpty() ? 0 : routeIndex;
        snapshot = new ForagingSnapshot(state, reason, target, displayRouteIndex, displayRouteSize,
                freeCells, RESERVE_CELLS, sessionYield, distance, bearing, direction, selected,
                catalog, displayRoute, new ArrayList<>(events));
    }

    static List<Coord2d> directionalRoute(Coord2d start, ForagingDirection direction) {
        if(start == null || direction == null || direction.usesCheckpointRoute())
            return(List.of());
        Coord vector = direction.tileVector;
        double vectorLength = Math.sqrt((vector.x * vector.x) + (vector.y * vector.y));
        List<Coord2d> points = new ArrayList<>();
        points.add(start);
        for(int distance = DIRECTION_STEP_TILES; distance < DIRECTION_RUN_TILES;
            distance += DIRECTION_STEP_TILES) {
            points.add(start.add((vector.x / vectorLength) * distance * MCache.tilesz.x,
                    (vector.y / vectorLength) * distance * MCache.tilesz.y));
        }
        points.add(start.add((vector.x / vectorLength) * DIRECTION_RUN_TILES * MCache.tilesz.x,
                (vector.y / vectorLength) * DIRECTION_RUN_TILES * MCache.tilesz.y));
        return(List.copyOf(points));
    }

    private static int nearestRoutePoint(Coord2d point, List<Coord2d> points) {
        int best = 0;
        double distance = Double.POSITIVE_INFINITY;
        for(int index = 0; index < points.size(); index++) {
            double candidate = point.dist(points.get(index));
            if(candidate < distance) {
                distance = candidate;
                best = index;
            }
        }
        return(best);
    }

    static double distanceToRoute(Coord2d point, List<Coord2d> points) {
        if(points.isEmpty())
            return(Double.POSITIVE_INFINITY);
        if(points.size() == 1)
            return(point.dist(points.get(0)));
        double best = Double.POSITIVE_INFINITY;
        for(int index = 1; index < points.size(); index++)
            best = Math.min(best, pointSegmentDistance(point, points.get(index - 1), points.get(index)));
        return(best);
    }

    static double routeLength(List<Coord2d> points) {
        double length = 0;
        for(int index = 1; index < points.size(); index++)
            length += points.get(index - 1).dist(points.get(index));
        return(length);
    }

    private static boolean finite(double value) {
        return(!Double.isNaN(value) && !Double.isInfinite(value));
    }

    private static boolean validRoutePoint(Coord2d point) {
        return(point != null && finite(point.x) && finite(point.y));
    }

    private static double pointSegmentDistance(Coord2d point, Coord2d a, Coord2d b) {
        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double lengthSquared = dx * dx + dy * dy;
        if(lengthSquared == 0)
            return(point.dist(a));
        double t = Math.max(0, Math.min(1, ((point.x - a.x) * dx + (point.y - a.y) * dy) / lengthSquared));
        return(point.dist(new Coord2d(a.x + t * dx, a.y + t * dy)));
    }

    private static String concise(String value) {
        if(value == null || value.isEmpty())
            return("unknown error");
        return(value.length() <= 120 ? value : value.substring(0, 117) + "...");
    }

    private long currentSegment(Coord2d coordinate) {
        if(gui.mapfile == null || gui.ui == null || gui.ui.sess == null || coordinate == null)
            return(-1);
        try {
            MCache.Grid grid = gui.ui.sess.glob.map.getgrid(coordinate.floor(MCache.tilesz).div(MCache.cmaps));
            try(Locked ignored = new Locked(gui.mapfile.file.lock.readLock())) {
                MapFile.GridInfo info = gui.mapfile.file.gridinfo.get(grid.id);
                return(info == null ? -1 : info.seg);
            }
        } catch(RuntimeException e) {
            return(-1);
        }
    }

    public void close() {
        emergencyStop("Foraging controller closed.");
        if(repository != null)
            repository.close();
    }

    private static final class CandidatePlan implements Comparable<CandidatePlan> {
        final ForagingTarget target;
        final ForagingRoutePlanner.Plan plan;

        CandidatePlan(ForagingTarget target, ForagingRoutePlanner.Plan plan) {
            this.target = target;
            this.plan = plan;
        }

        @Override
        public int compareTo(CandidatePlan other) {
            int cost = Double.compare(plan.cost(), other.plan.cost());
            if(cost != 0)
                return(cost);
            int resource = target.resourceName.compareTo(other.target.resourceName);
            return(resource != 0 ? resource : Long.compare(target.gobId, other.target.gobId));
        }
    }
}
