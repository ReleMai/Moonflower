package haven.automated;

import haven.Coord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Pure rectangle-layout helpers shared by the sorter and offline checks. */
final class InventorySortLayout {
    private static final int SEARCH_NODE_LIMIT = 250000;

    private InventorySortLayout() {
    }

    static List<Coord> assignTargets(Coord inventorySize, boolean[][] blocked, List<Coord> itemSizes) {
	boolean[][] grid = copyGrid(blocked, inventorySize);
	int available = 0;
	for (int x = 0; x < inventorySize.x; x++)
	    for (int y = 0; y < inventorySize.y; y++)
		if (!grid[x][y]) available++;
	int required = 0;
	for (Coord slots : itemSizes) {
	    if (slots == null || slots.x <= 0 || slots.y <= 0) return null;
	    required += slots.x * slots.y;
	}
	if (required > available) return null;

	Coord[] targets = new Coord[itemSizes.size()];
	if (!assignTargets(grid, inventorySize, itemSizes, targets, 0, new SearchBudget())) return null;
	return new ArrayList<>(Arrays.asList(targets));
    }

    /* Search in scan order, so the first complete solution preserves the
     * established compact/name-order presentation. Backtracking only occurs
     * when a greedy placement would strand a later rectangle. */
    private static boolean assignTargets(boolean[][] grid, Coord inventorySize, List<Coord> itemSizes,
								Coord[] targets, int index, SearchBudget budget) {
	if (index == itemSizes.size()) return true;
	if (++budget.nodes > SEARCH_NODE_LIMIT) return false;
	Coord slots = itemSizes.get(index);
	for (int y = 0; y <= inventorySize.y - slots.y; y++) {
	    for (int x = 0; x <= inventorySize.x - slots.x; x++) {
		if (!fits(grid, x, y, slots)) continue;
		Coord target = Coord.of(x, y);
		targets[index] = target;
		markGrid(grid, target, slots, true);
		if (assignTargets(grid, inventorySize, itemSizes, targets, index + 1, budget)) return true;
		markGrid(grid, target, slots, false);
		targets[index] = null;
	    }
	}
	return false;
    }

    private static final class SearchBudget {
	int nodes;
    }

    static Coord findFit(boolean[][] grid, Coord inventorySize, Coord slots) {
	for (int y = 0; y <= inventorySize.y - slots.y; y++) {
	    for (int x = 0; x <= inventorySize.x - slots.x; x++) {
		if (fits(grid, x, y, slots)) return Coord.of(x, y);
	    }
	}
	return null;
    }

    static boolean fits(boolean[][] grid, int ox, int oy, Coord slots) {
	if (slots.x <= 0 || slots.y <= 0) return false;
	for (int x = 0; x < slots.x; x++)
	    for (int y = 0; y < slots.y; y++)
		if (grid[ox + x][oy + y]) return false;
	return true;
    }

    static boolean overlaps(Coord apos, Coord aslots, Coord bpos, Coord bslots) {
	return apos.x < bpos.x + bslots.x && apos.x + aslots.x > bpos.x &&
	       apos.y < bpos.y + bslots.y && apos.y + aslots.y > bpos.y;
    }

    static boolean inBounds(Coord inventorySize, Coord pos, Coord slots) {
	return pos.x >= 0 && pos.y >= 0 && slots.x > 0 && slots.y > 0 &&
	       pos.x + slots.x <= inventorySize.x && pos.y + slots.y <= inventorySize.y;
    }

    static int slotsForPixels(int pixels, int cellPixels) {
	return Math.max(1, (pixels + cellPixels / 2) / cellPixels);
    }

    static boolean[][] copyGrid(boolean[][] source, Coord inventorySize) {
	boolean[][] copy = new boolean[inventorySize.x][inventorySize.y];
	for (int x = 0; x < inventorySize.x; x++)
	    copy[x] = Arrays.copyOf(source[x], inventorySize.y);
	return copy;
    }

    static void markGrid(boolean[][] grid, Coord pos, Coord slots, boolean value) {
	for (int x = 0; x < slots.x; x++)
	    for (int y = 0; y < slots.y; y++)
		grid[pos.x + x][pos.y + y] = value;
    }
}
