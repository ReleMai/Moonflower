package haven.foraging;

import haven.Coord2d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** UI-safe immutable view of controller state. */
public final class ForagingSnapshot {
    public enum State {
        IDLE, PREFLIGHT, SCANNING, PLANNING, TRAVELING, APPROACHING,
        PICKING, ACKNOWLEDGING, PAUSED, STOPPING, FAILED, COMPLETE
    }

    public final State state;
    public final String reason;
    public final ForagingTarget target;
    public final int routeIndex;
    public final int routeSize;
    public final int freeCells;
    public final int reserveCells;
    public final int yield;
    public final double targetDistance;
    public final double targetBearing;
    public final ForagingDirection direction;
    public final Set<String> selectedResources;
    public final List<ForagingGobScanner.HerbResource> catalog;
    public final List<Coord2d> route;
    public final List<String> events;

    public ForagingSnapshot(State state, String reason, ForagingTarget target,
                            int routeIndex, int routeSize, int freeCells, int reserveCells,
                            int yield, double targetDistance, double targetBearing,
                            ForagingDirection direction,
                            Set<String> selectedResources,
                            List<ForagingGobScanner.HerbResource> catalog,
                            List<Coord2d> route, List<String> events) {
        this.state = state;
        this.reason = reason == null ? "" : reason;
        this.target = target;
        this.routeIndex = routeIndex;
        this.routeSize = routeSize;
        this.freeCells = freeCells;
        this.reserveCells = reserveCells;
        this.yield = yield;
        this.targetDistance = targetDistance;
        this.targetBearing = targetBearing;
        this.direction = direction == null ? ForagingDirection.NORTH : direction;
        this.selectedResources = Collections.unmodifiableSet(new LinkedHashSet<>(selectedResources));
        this.catalog = Collections.unmodifiableList(new ArrayList<>(catalog));
        this.route = Collections.unmodifiableList(new ArrayList<>(route));
        this.events = Collections.unmodifiableList(new ArrayList<>(events));
    }

    public boolean active() {
        return(state != State.IDLE && state != State.PAUSED && state != State.FAILED && state != State.COMPLETE);
    }
}
