package haven.foraging;

import haven.Coord;
import haven.Coord2d;
import haven.GameUI;
import haven.Gob;
import haven.Loading;
import haven.MCache;
import haven.Resource;
import haven.Tiler;
import haven.automated.pathfinder.Pathfinder;
import haven.automated.helpers.HitBoxes;
import haven.resutil.Ridges;
import haven.resutil.WaterTile;

/** Conservative live safety raster. Unknown data, water, ridge tiles, and hitboxes are blocked. */
public final class ForagingMapSafety implements ForagingRoutePlanner.SafetyRaster {
    private static final Coord[] CARDINALS = {
            Coord.of(0, -1), Coord.of(1, 0), Coord.of(0, 1), Coord.of(-1, 0)
    };
    private final GameUI gui;
    private final long ignoredTargetId;

    public ForagingMapSafety(GameUI gui, long ignoredTargetId) {
        this.gui = gui;
        this.ignoredTargetId = ignoredTargetId;
    }

    @Override
    public boolean passable(Coord tile) {
        try {
            MCache map = gui.ui.sess.glob.map;
            Tiler tiler = map.tiler(map.gettile(tile));
            Resource tileResource = map.tilesetr(map.gettile(tile));
            if(tiler == null || tileResource == null || tiler instanceof WaterTile)
                return(false);
            String name = tileResource.name == null ? "" : tileResource.name;
            if(name.contains("cave") || name.contains("nil") || name.contains("void"))
                return(false);
            // Require a loaded one-tile margin and block all broken-ridge tiles conservatively.
            for(Coord direction : CARDINALS)
                map.gettile(tile.add(direction));
            if(Ridges.brokenp(map, tile))
                return(false);
            return(!blockedByGob(tileCenter(tile)));
        } catch(RuntimeException e) {
            return(false);
        }
    }

    @Override
    public boolean canStep(Coord from, Coord to) {
        Coord delta = to.sub(from);
        if(Math.abs(delta.x) > 1 || Math.abs(delta.y) > 1)
            return(false);
        try {
            MCache map = gui.ui.sess.glob.map;
            if(Ridges.brokenp(map, from) || Ridges.brokenp(map, to))
                return(false);
            if(delta.x != 0 && delta.y != 0) {
                Coord horizontal = from.add(delta.x, 0);
                Coord vertical = from.add(0, delta.y);
                return(passable(horizontal) && passable(vertical) &&
                        canStep(from, horizontal) && canStep(horizontal, to) &&
                        canStep(from, vertical) && canStep(vertical, to));
            }
            return(true);
        } catch(RuntimeException e) {
            return(false);
        }
    }

    private boolean blockedByGob(Coord2d point) {
        synchronized(gui.ui.sess.glob.oc) {
            for(Gob gob : gui.ui.sess.glob.oc) {
                if(gob.id == gui.plid || gob.id == ignoredTargetId || gob.rc == null)
                    continue;
                try {
                    Resource resource = gob.getres();
                    if(resource == null || ForagingGobScanner.isForageable(resource.name))
                        continue;
                    if(Pathfinder.isInsideBoundBox(gob.rc.floor(), gob.a, resource.name, point.floor()))
                        return(true);
                    if(!HitBoxes.collisionBoxMap.containsKey(resource.name) &&
                            gob.rc.dist(point) <= MCache.tilesz.x * 0.75)
                        return(true);
                } catch(Loading ignored) {
                    return(true);
                }
            }
        }
        return(false);
    }

    public static Coord2d tileCenter(Coord tile) {
        return(new Coord2d((tile.x + 0.5) * MCache.tilesz.x, (tile.y + 0.5) * MCache.tilesz.y));
    }
}
