package haven.automated;

import haven.Coord;

import java.util.Arrays;
import java.util.List;

/** Offline checks for size-aware packing and rectangle occupancy rules. */
public final class InventorySorterChecks {
    private InventorySorterChecks() {
    }

    public static void main(String[] args) {
	List<Coord> packed = InventorySortLayout.assignTargets(Coord.of(4, 2),
	    new boolean[4][2], Arrays.asList(
		Coord.of(2, 2), Coord.of(1, 1), Coord.of(1, 1), Coord.of(1, 1), Coord.of(1, 1)));
	require(packed != null && packed.size() == 5 && packed.get(0).equals(Coord.of(0, 0)),
	    "large rectangle should reserve space in a complete plan");
	require(packed.get(1).equals(Coord.of(2, 0)) && packed.get(4).equals(Coord.of(3, 1)),
	    "packed 1x1 targets should remain non-overlapping");
	List<Coord> recovered = InventorySortLayout.assignTargets(Coord.of(4, 2), new boolean[4][2],
	    Arrays.asList(Coord.of(1, 1), Coord.of(1, 1), Coord.of(1, 1), Coord.of(2, 2), Coord.of(1, 1)));
	require(recovered != null && recovered.get(3).equals(Coord.of(2, 0)),
	    "backtracking should recover a large rectangle after greedy fragmentation");

	boolean[][] blocked = new boolean[3][2];
	blocked[0][0] = true;
	List<Coord> masked = InventorySortLayout.assignTargets(Coord.of(3, 2), blocked,
	    Arrays.asList(Coord.of(2, 1), Coord.of(1, 1)));
	require(masked != null && masked.get(0).equals(Coord.of(1, 0)),
	    "rectangle placement should honor blocked cells");
	require(InventorySortLayout.assignTargets(Coord.of(3, 2), new boolean[3][2],
	    Arrays.asList(Coord.of(4, 1))) == null,
	    "an item larger than the inventory should fail the plan");

	require(InventorySortLayout.overlaps(Coord.of(1, 1), Coord.of(2, 2), Coord.of(2, 2), Coord.of(1, 1)),
	    "overlapping rectangles");
	require(!InventorySortLayout.overlaps(Coord.of(0, 0), Coord.of(1, 1), Coord.of(1, 0), Coord.of(1, 1)),
	    "adjacent rectangles should not overlap");
	require(InventorySortLayout.slotsForPixels(66, 33) == 2 &&
		InventorySortLayout.slotsForPixels(49, 33) == 1,
	    "sprite dimensions should use the client grid rounding");

	System.out.println("Inventory sorter checks passed.");
    }

    private static void require(boolean condition, String description) {
	if (!condition) throw new AssertionError("Unexpected " + description);
    }
}
