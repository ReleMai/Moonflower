package haven;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Placement rules for separately backed inventory-container windows. */
final class ContainerWindowPlacement {
    private static final String CREEL_NAME = "Creel";

    private ContainerWindowPlacement() {
    }

    static boolean isEquippedCreel(String contentsName, boolean ownEquipory, int equipmentSlot) {
        return ownEquipory && equipmentSlot >= 0 && contentsName != null &&
                CREEL_NAME.equalsIgnoreCase(contentsName.trim());
    }

    static String positionPreferenceKey(Object contentsId, String contentsName,
                                        boolean ownEquipory, int equipmentSlot) {
        String base = String.format("cont-wndc/%s", contentsId);
        if(!isEquippedCreel(contentsName, ownEquipory, equipmentSlot))
            return base;
        // The equipment slot is stable even if a server-supplied container ID is instance-specific.
        return String.format("cont-wndc/creel/equip/%d", equipmentSlot);
    }

    static Coord avoidOverlap(Coord preferred, Coord windowSize, Coord parentSize,
                              Collection<Area> occupied, int gap) {
        if(preferred == null || windowSize == null || parentSize == null || occupied.isEmpty())
            return preferred;
        if(!overlaps(preferred, windowSize, occupied))
            return preferred;

        Set<Coord> candidates = new LinkedHashSet<>();
        for(Area blocker : occupied) {
            candidates.add(Coord.of(blocker.br.x + gap, preferred.y));
            candidates.add(Coord.of(blocker.ul.x - windowSize.x - gap, preferred.y));
            candidates.add(Coord.of(preferred.x, blocker.br.y + gap));
            candidates.add(Coord.of(preferred.x, blocker.ul.y - windowSize.y - gap));
        }

        List<Coord> fitted = new ArrayList<>();
        for(Coord candidate : candidates) {
            Coord fit = fitInside(candidate, windowSize, parentSize);
            if(!fitted.contains(fit))
                fitted.add(fit);
        }
        for(Coord candidate : fitted) {
            if(!overlaps(candidate, windowSize, occupied))
                return candidate;
        }
        return preferred;
    }

    private static Coord fitInside(Coord position, Coord windowSize, Coord parentSize) {
        int maxX = Math.max(0, parentSize.x - windowSize.x);
        int maxY = Math.max(0, parentSize.y - windowSize.y);
        return Coord.of(Utils.clip(position.x, 0, maxX), Utils.clip(position.y, 0, maxY));
    }

    private static boolean overlaps(Coord position, Coord windowSize, Collection<Area> occupied) {
        Area proposed = Area.sized(position, windowSize);
        for(Area blocker : occupied) {
            if(proposed.isects(blocker))
                return true;
        }
        return false;
    }
}
