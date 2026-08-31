package haven.foraging;

import haven.Coord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** Bounded deterministic A* that never mutates the client's global pathfinder. */
public final class ForagingRoutePlanner {
    private static final Coord[] DIRECTIONS = {
            Coord.of(0, -1), Coord.of(1, 0), Coord.of(0, 1), Coord.of(-1, 0),
            Coord.of(1, -1), Coord.of(1, 1), Coord.of(-1, 1), Coord.of(-1, -1)
    };
    private static final int MAX_VISITED = 8_000;

    public interface SafetyRaster {
        boolean passable(Coord tile);
        boolean canStep(Coord from, Coord to);
    }

    public Plan plan(Coord start, Set<Coord> goals, SafetyRaster safety) {
        if(start == null || goals == null || goals.isEmpty())
            return(Plan.failed("No safe destination was provided."));
        if(!safety.passable(start))
            return(Plan.failed("The current tile is unknown or unsafe."));
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator
                .comparingDouble((Node node) -> node.f)
                .thenComparingDouble(node -> node.g)
                .thenComparingInt(node -> node.tile.y)
                .thenComparingInt(node -> node.tile.x));
        Map<Coord, Double> costs = new HashMap<>();
        Map<Coord, Coord> previous = new HashMap<>();
        Set<Coord> closed = new HashSet<>();
        costs.put(start, 0.0);
        open.add(new Node(start, 0.0, heuristic(start, goals)));
        while(!open.isEmpty() && closed.size() < MAX_VISITED) {
            Node current = open.poll();
            if(!closed.add(current.tile))
                continue;
            if(goals.contains(current.tile))
                return(new Plan(reconstruct(previous, current.tile), "Safe path found."));
            for(Coord direction : DIRECTIONS) {
                Coord next = current.tile.add(direction);
                if(closed.contains(next) || !safety.passable(next) || !safety.canStep(current.tile, next))
                    continue;
                double nextCost = current.g + ((direction.x != 0 && direction.y != 0) ? Math.sqrt(2) : 1.0);
                if(nextCost >= costs.getOrDefault(next, Double.POSITIVE_INFINITY))
                    continue;
                costs.put(next, nextCost);
                previous.put(next, current.tile);
                open.add(new Node(next, nextCost, nextCost + heuristic(next, goals)));
            }
        }
        return(Plan.failed(closed.size() >= MAX_VISITED ?
                "Safe-path search reached its bounded limit." : "No safe path is currently loaded."));
    }

    private static double heuristic(Coord tile, Set<Coord> goals) {
        double best = Double.POSITIVE_INFINITY;
        for(Coord goal : goals) {
            int dx = Math.abs(tile.x - goal.x);
            int dy = Math.abs(tile.y - goal.y);
            best = Math.min(best, Math.max(dx, dy) + (Math.sqrt(2) - 1.0) * Math.min(dx, dy));
        }
        return(best);
    }

    private static List<Coord> reconstruct(Map<Coord, Coord> previous, Coord end) {
        List<Coord> path = new ArrayList<>();
        for(Coord cursor = end; cursor != null; cursor = previous.get(cursor))
            path.add(cursor);
        Collections.reverse(path);
        return(path);
    }

    private static final class Node {
        final Coord tile;
        final double g;
        final double f;

        Node(Coord tile, double g, double f) {
            this.tile = tile;
            this.g = g;
            this.f = f;
        }
    }

    public static final class Plan {
        public final List<Coord> tiles;
        public final String reason;

        Plan(List<Coord> tiles, String reason) {
            this.tiles = Collections.unmodifiableList(new ArrayList<>(tiles));
            this.reason = reason;
        }

        static Plan failed(String reason) {
            return(new Plan(List.of(), reason));
        }

        public boolean found() {
            return(!tiles.isEmpty());
        }

        public double cost() {
            double cost = 0;
            for(int index = 1; index < tiles.size(); index++) {
                Coord delta = tiles.get(index).sub(tiles.get(index - 1));
                cost += delta.x != 0 && delta.y != 0 ? Math.sqrt(2) : 1.0;
            }
            return(cost);
        }
    }
}
