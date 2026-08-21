package haven.automated;

import haven.Astronomy;
import haven.Coord;
import haven.Coord2d;
import haven.GameUI;
import haven.Glob;
import haven.Gob;
import haven.Loading;
import haven.Locked;
import haven.MCache;
import haven.MapFile;
import haven.Resource;
import haven.fishing.FishingObservation;
import haven.resutil.WaterTile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static haven.MCache.cmaps;
import static haven.MCache.tilesz;

/** Resolves nearby cast water and captures reproducible location/time context. */
final class FishingEnvironment {
    private static final String[] SEASONS = {"Spring", "Summer", "Autumn", "Winter"};

    private FishingEnvironment() {
    }

    static Target findNearbyWater(GameUI gui, int radius, double maxDistance) {
        Gob player = gui.map == null ? null : gui.map.player();
        if(player == null)
            return(null);
        Coord playerTile = player.rc.floor(tilesz);
        List<Target> candidates = new ArrayList<>();
        for(int x = -radius; x <= radius; x++) {
            for(int y = -radius; y <= radius; y++) {
                if(x == 0 && y == 0)
                    continue;
                Coord tile = playerTile.add(x, y);
                try {
                    int tileId = gui.ui.sess.glob.map.gettile(tile);
                    if(!(gui.ui.sess.glob.map.tiler(tileId) instanceof WaterTile))
                        continue;
                    Coord2d center = tile.mul(tilesz).add(tilesz.div(2));
                    double distance = center.dist(player.rc);
                    if(distance > maxDistance)
                        continue;
                    Resource resource = gui.ui.sess.glob.map.tilesetr(tileId);
                    candidates.add(new Target(center, resource == null ? "" : resource.name,
                            cardinalWaterNeighbors(gui, tile), distance));
                } catch(Loading ignored) {
                }
            }
        }
        return(candidates.stream()
                .sorted(Comparator.comparingInt((Target target) -> -target.waterNeighbors)
                        .thenComparingDouble(target -> target.distance))
                .findFirst().orElse(null));
    }

    static FishingObservation capture(GameUI gui, Target target, FishingEquipment.Snapshot tackle,
                                      String choiceRowsJson, long observedAt) {
        Gob player = gui.map.player();
        long segmentId = -1;
        long gridId = -1;
        double offsetX = 0;
        double offsetY = 0;
        try {
            Coord tile = target.coordinate.floor(tilesz);
            MCache.Grid grid = gui.ui.sess.glob.map.getgrid(tile.div(cmaps));
            gridId = grid.id;
            offsetX = target.coordinate.x - grid.gc.x * cmaps.x * tilesz.x;
            offsetY = target.coordinate.y - grid.gc.y * cmaps.y * tilesz.y;
            if(gui.mapfile != null) {
                try(Locked ignored = new Locked(gui.mapfile.file.lock.readLock())) {
                    MapFile.GridInfo info = gui.mapfile.file.gridinfo.get(grid.id);
                    if(info != null)
                        segmentId = info.seg;
                }
            }
        } catch(RuntimeException ignored) {
        }

        Glob glob = gui.ui.sess.glob;
        long gameSeconds = (long)glob.globtime();
        int gameDay = (int)Math.floorDiv(gameSeconds, 24 * 60 * 60);
        int secondOfDay = (int)Math.floorMod(gameSeconds, 24 * 60 * 60);
        Astronomy astronomy = glob.ast;
        boolean night = astronomy != null && astronomy.night;
        String moon = astronomy == null ? "" : Astronomy.phase[
                Math.floorMod((int)Math.round(astronomy.mp * Astronomy.phase.length), Astronomy.phase.length)];
        String season = astronomy == null || astronomy.is < 0 || astronomy.is >= SEASONS.length
                ? "" : SEASONS[astronomy.is];
        Coord2d playerCoordinate = player == null ? Coord2d.z : player.rc;

        return(new FishingObservation.Builder()
                .worldId(gui.genus)
                .location(segmentId, gridId, offsetX, offsetY, target.coordinate.x, target.coordinate.y,
                        playerCoordinate.x, playerCoordinate.y, target.resourceName)
                .observedAt(observedAt)
                .gameTime(gameSeconds, gameDay, secondOfDay, night, moon, season)
                .pole(tackle.pole.resourceName, tackle.pole.displayName, tackle.pole.quality)
                .line(tackle.line.resourceName, tackle.line.displayName, tackle.line.quality)
                .hook(tackle.hook.resourceName, tackle.hook.displayName, tackle.hook.quality)
                .consumable(tackle.consumableKind, tackle.consumable.resourceName,
                        tackle.consumable.displayName, tackle.consumable.quality)
                .choiceRowsJson(choiceRowsJson)
                .stats(attribute(gui, "survive"), attribute(gui, "will"))
                .outcome("caught")
                .confidence("candidate")
                .build());
    }

    private static int cardinalWaterNeighbors(GameUI gui, Coord tile) {
        int count = 0;
        for(Coord direction : new Coord[]{Coord.of(1, 0), Coord.of(-1, 0), Coord.of(0, 1), Coord.of(0, -1)}) {
            int tileId = gui.ui.sess.glob.map.gettile(tile.add(direction));
            if(gui.ui.sess.glob.map.tiler(tileId) instanceof WaterTile)
                count++;
        }
        return(count);
    }

    private static Integer attribute(GameUI gui, String name) {
        try {
            Glob.CAttr attribute = gui.ui.sess.glob.getcattr(name);
            return(attribute == null ? null : attribute.comp);
        } catch(RuntimeException e) {
            return(null);
        }
    }

    static final class Target {
        final Coord2d coordinate;
        final String resourceName;
        final int waterNeighbors;
        final double distance;

        Target(Coord2d coordinate, String resourceName, int waterNeighbors, double distance) {
            this.coordinate = coordinate;
            this.resourceName = resourceName;
            this.waterNeighbors = waterNeighbors;
            this.distance = distance;
        }
    }
}
