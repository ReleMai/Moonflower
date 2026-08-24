package haven;

import java.util.Arrays;
import java.util.Collections;

/** Offline checks for equipped-Creel preference identity and non-overlap placement. */
public final class ContainerWindowPlacementChecks {
    private ContainerWindowPlacementChecks() {
    }

    public static void main(String[] args) {
        require(ContainerWindowPlacement.positionPreferenceKey("creel", "Creel", true, 3)
                        .equals("cont-wndc/creel/equip/3"),
                "equipped Creel should have a slot-specific position key");
        require(ContainerWindowPlacement.positionPreferenceKey("instance-42", "Creel", true, 4)
                        .equals("cont-wndc/creel/equip/4"),
                "second equipped Creel should have a slot key independent of its server ID");
        require(ContainerWindowPlacement.positionPreferenceKey("toolbelt", "Belt", true, 3)
                        .equals("cont-wndc/toolbelt"),
                "non-Creel containers should retain their legacy position key");

        Coord window = Coord.of(200, 220);
        Coord parent = Coord.of(800, 600);
        Area first = Area.sized(Coord.of(20, 20), window);
        require(ContainerWindowPlacement.avoidOverlap(Coord.of(20, 20), window, parent,
                        Collections.singletonList(first), 4).equals(Coord.of(224, 20)),
                "overlapping Creels should tile to the right when room is available");

        Area rightEdge = Area.sized(Coord.of(600, 20), window);
        require(ContainerWindowPlacement.avoidOverlap(Coord.of(600, 20), window, parent,
                        Collections.singletonList(rightEdge), 4).equals(Coord.of(396, 20)),
                "right-edge Creels should tile to the left");

        Coord unchanged = ContainerWindowPlacement.avoidOverlap(Coord.of(300, 300), window, parent,
                Arrays.asList(first, rightEdge), 4);
        require(unchanged.equals(Coord.of(300, 300)),
                "a non-overlapping saved position should remain unchanged");

        System.out.println("Container window placement checks passed.");
    }

    private static void require(boolean condition, String description) {
        if(!condition)
            throw new AssertionError("Unexpected " + description);
    }
}
