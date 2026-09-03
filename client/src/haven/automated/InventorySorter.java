package haven.automated;

import haven.*;
import haven.res.ui.tt.q.quality.Quality;

import java.util.*;

import static haven.Inventory.sqsz;

public class InventorySorter implements Defer.Callable<Void> {
    private static final String[] EXCLUDE = {
	"Character Sheet", "Study",
	"Chicken Coop", "Belt", "Pouch", "Purse",
	"Cauldron", "Finery Forge", "Fireplace", "Frame",
	"Herbalist Table", "Kiln", "Ore Smelter", "Smith's Smelter",
	"Oven", "Pane mold", "Rack", "Smoke shed",
	"Stack Furnace", "Steelbox", "Tub"
    };

    private static final Comparator<Entry> ITEM_COMPARATOR = Comparator
	.comparing((Entry e) -> e.identity.sortName())
	.thenComparing(e -> e.identity.resourceName)
	.thenComparing(e -> e.identity.quality != null ? e.identity.quality : 0.0,
		Comparator.reverseOrder());

    /* A sort action is a server-backed drag operation. These values bound each
     * state transition rather than acting as a fixed animation delay. */
    private static final long MOVE_TIMEOUT_MS = 3000;
    private static final long MOVE_POLL_INTERVAL_MS = 25;

    private static final Object lock = new Object();
    private static InventorySorter current;
    private Defer.Future<Void> task;
    private final List<Inventory> inventories;
    private final GameUI gui;

    /* Set only while one item is expected to be on the cursor. It lets a
     * timeout or cancellation put that item back at its source slot even if
     * the server recreated its client-side GItem wrapper. */
    private Inventory activeInventory;
    private Entry activeEntry;
    private Coord activeOrigin;

    private InventorySorter(List<Inventory> inventories, GameUI gui) {
	this.inventories = inventories;
	this.gui = gui;
    }

    public static void sort(Inventory inv) {
	if (inv.ui.gui.vhand != null) {
	    inv.ui.gui.error("Need empty cursor to sort inventory!");
	    return;
	}
	start(new InventorySorter(Collections.singletonList(inv), inv.ui.gui));
    }

    public static void sortAll(GameUI gui) {
	if (gui.vhand != null) {
	    gui.error("Need empty cursor to sort inventory!");
	    return;
	}
	List<Inventory> targets = new ArrayList<>();
	for (Inventory inv : gui.ui.root.children(Inventory.class)) {
	    Window wnd = inv.getparent(Window.class);
	    if (wnd != null && isExcluded(wnd.cap)) continue;
	    targets.add(inv);
	}
	if (!targets.isEmpty()) {
	    start(new InventorySorter(targets, gui));
	}
    }

    private static boolean isExcluded(String cap) {
	if (cap == null) return false;
	for (String ex : EXCLUDE) {
	    if (ex.equals(cap)) return true;
	}
	return false;
    }

    @Override
    public Void call() throws InterruptedException {
	try {
	    for (Inventory inv : inventories) {
		if (inv.parent == null) return null;
		doSort(inv);
	    }
	    gui.ui.sfxrl(sfx_done);
	} catch (SortFailure failure) {
	    reportFailure(failure.getMessage());
	} catch (InterruptedException interrupted) {
	    boolean recovered = false;
	    try {
		recovered = recoverActiveCursor();
	    } catch (InterruptedException ignored) {
		/* The cancellation interrupted recovery as well. */
	    }
	    if (!recovered && gui.vhand != null)
		gui.error("Inventory sort stopped with an item still on the cursor; place it manually.");
	    throw interrupted;
	} catch (RuntimeException failure) {
	    /* Do not leave an automation-held item on the cursor if a UI change
	     * invalidates an assumption between two acknowledged transitions. */
	    try {
		recoverActiveCursor();
	    } catch (InterruptedException ignored) {
		Thread.currentThread().interrupt();
	    }
	    throw failure;
	} finally {
	    clearActiveCursor();
	    synchronized (lock) {
		if (current == this) current = null;
	    }
	}
	return null;
    }

    private static class SortFailure extends Exception {
	SortFailure(String message) {
	    super(message);
	}
    }

    private static class Entry {
	/* Both WItem and GItem wrappers can be recreated as the server reparents
	 * an item. Keep the current object as a fast path, but resolve it through
	 * this value identity before every take and acknowledgement. */
	GItem item;
	final InventorySortIdentity identity;
	final Coord slots;
	Coord current;
	Coord target;

	Entry(WItem w, Coord slots, Coord current, InventorySortIdentity identity) {
	    this.item = w.item;
	    this.identity = identity;
	    this.slots = slots;
	    this.current = current;
	    this.target = current;
	}
    }

    private void doSort(Inventory inv) throws InterruptedException, SortFailure {
	// Build mask grid (permanently blocked cells).
	boolean[][] maskGrid = new boolean[inv.isz.x][inv.isz.y];
	if (inv.sqmask != null) {
	    int mo = 0;
	    for (int y = 0; y < inv.isz.y; y++)
		for (int x = 0; x < inv.isz.x; x++)
		    maskGrid[x][y] = mo < inv.sqmask.length && inv.sqmask[mo++];
	}
	Set<Coord> lockedSlots = inv.lockedSlots();
	for (Coord locked : lockedSlots) {
	    if (locked.x >= 0 && locked.y >= 0 && locked.x < inv.isz.x && locked.y < inv.isz.y)
		maskGrid[locked.x][locked.y] = true;
	}

	// Collect all visible items whose sprites are ready. The sprite dimensions
	// are authoritative here; WItem.tick may not have resized the wrapper yet.
	List<Entry> entries = new ArrayList<>();
	for (Widget wdg = inv.lchild; wdg != null; wdg = wdg.prev) {
	    if (!wdg.visible || !(wdg instanceof WItem)) continue;
	    WItem w = (WItem) wdg;
	    GSprite sprite = w.item.spr();
	    if (sprite == null)
		throw new SortFailure("Sorting stopped until all visible item sprites finish loading; try again shortly.");
	    InventorySortIdentity identity = identityOf(w.item);
	    if (!identity.hasDescription())
		throw new SortFailure("Sorting stopped until the visible item names finish loading; try again shortly.");
	    Coord slots = slotsFor(sprite);
	    Coord current = w.c.sub(1, 1).div(sqsz);
	    if (!InventorySortLayout.inBounds(inv.isz, current, slots))
		throw new SortFailure("Sorting stopped because the inventory changed while it was being read.");
	    if (inv.itemTouchesLockedSlot(current, slots, lockedSlots)) {
		InventorySortLayout.markGrid(maskGrid, current, slots, true);
		continue;
	    }
	    entries.add(new Entry(w, slots, current, identity));
	}

	// Preserve the existing name ordering whenever it can fit. If early 1x1
	// items fragment the grid, retry with the largest rectangles first so a
	// valid large-item layout is not lost to a greedy scan.
	entries.sort(ITEM_COMPARATOR);
	List<Entry> nameOrder = new ArrayList<>(entries);
	List<Entry> largeFirstOrder = new ArrayList<>();
	for (Entry e : entries)
	    if (isMulti(e)) largeFirstOrder.add(e);
	largeFirstOrder.sort((left, right) -> {
	    int byArea = itemArea(right) - itemArea(left);
	    return byArea != 0 ? byArea : ITEM_COMPARATOR.compare(left, right);
	});
	for (Entry e : entries)
	    if (!isMulti(e)) largeFirstOrder.add(e);

	List<Entry> placementOrder = nameOrder;
	List<Coord> sizes = sizesFor(nameOrder);
	List<Coord> targets = InventorySortLayout.assignTargets(inv.isz, maskGrid, sizes);
	if (targets == null) {
	    placementOrder = largeFirstOrder;
	    targets = InventorySortLayout.assignTargets(inv.isz, maskGrid, sizesFor(largeFirstOrder));
	}
	if (targets == null)
	    throw new SortFailure("Could not find a size-safe layout for every movable item; no items were taken.");
	for (int i = 0; i < placementOrder.size(); i++)
	    placementOrder.get(i).target = targets.get(i);

	boolean needsSort = false;
	for (Entry e : entries) {
	    if (!same(e.current, e.target)) {
		needsSort = true;
		break;
	    }
	}
	if (!needsSort) return;

	/* First settle multi-slot items. A target is never used as a drop point
	 * until every current occupant has been moved to a verified empty fit. */
	for (Entry moving : placementOrder) {
	    if (!isMulti(moving) || same(moving.current, moving.target)) continue;
	    List<Entry> blockers = occupants(entries, moving.target, moving.slots, moving);
	    for (Entry blocker : blockers) {
		Coord free = findFreePlacement(inv.isz, maskGrid, entries, blocker.slots,
			blocker, moving.target, moving.slots);
		if (free == null)
		    throw new SortFailure("Could not make room for a large item without dropping onto another item.");
		moveToEmpty(inv, blocker, free);
		blocker.current = free;
	    }
	    if (!occupants(entries, moving.target, moving.slots, moving).isEmpty())
		throw new SortFailure("Inventory changed before a large item could be placed.");
	    moveToEmpty(inv, moving, moving.target);
	    moving.current = moving.target;
	}

	/* Then solve the 1x1 permutation with one empty cell as a buffer. Every
	 * drop goes into a known-empty cell, so this never depends on an unverified
	 * client-side swap or a stale WItem wrapper. */
	Coord free = findFreeCell(inv.isz, maskGrid, entries);
	for (Entry desired : placementOrder) {
	    if (isMulti(desired) || same(desired.current, desired.target)) continue;
	    if (free == null)
		throw new SortFailure("Sorting stopped safely: no empty 1x1 staging slot is available for the remaining items.");

	    List<Entry> blockers = occupants(entries, desired.target, Coord.of(1, 1), desired);
	    if (blockers.size() > 1 || (!blockers.isEmpty() && isMulti(blockers.get(0))))
		throw new SortFailure("Inventory changed while sorting single-slot items; the target is occupied by a large item.");
	    if (!blockers.isEmpty()) {
		Entry blocker = blockers.get(0);
		moveToEmpty(inv, blocker, free);
		blocker.current = free;
		free = desired.target;
	    }

	    Coord old = desired.current;
	    moveToEmpty(inv, desired, desired.target);
	    desired.current = desired.target;
	    free = old;
	}
    }

    private static List<Coord> sizesFor(List<Entry> entries) {
	List<Coord> sizes = new ArrayList<>(entries.size());
	for (Entry e : entries) sizes.add(e.slots);
	return sizes;
    }

    private static boolean isMulti(Entry e) {
	return itemArea(e) > 1;
    }

    private static int itemArea(Entry e) {
	return e.slots.x * e.slots.y;
    }

    private static Coord slotsFor(GSprite sprite) {
	Coord pixels = sprite.sz();
	return Coord.of(
	    InventorySortLayout.slotsForPixels(pixels.x, sqsz.x),
	    InventorySortLayout.slotsForPixels(pixels.y, sqsz.y));
    }

    private static boolean same(Coord left, Coord right) {
	return left != null && left.equals(right);
    }

    private static List<Entry> occupants(List<Entry> entries, Coord pos, Coord slots, Entry excluded) {
	List<Entry> found = new ArrayList<>();
	for (Entry e : entries) {
	    if (e == excluded) continue;
	    if (InventorySortLayout.overlaps(e.current, e.slots, pos, slots)) found.add(e);
	}
	return found;
    }

    private static Coord findFreePlacement(Coord isz, boolean[][] maskGrid, List<Entry> entries,
								Coord slots, Entry moving, Coord avoid, Coord avoidSlots) {
	for (int y = 0; y <= isz.y - slots.y; y++) {
	    for (int x = 0; x <= isz.x - slots.x; x++) {
		Coord pos = Coord.of(x, y);
		if (moving != null && same(moving.current, pos)) continue;
		if (avoid != null && InventorySortLayout.overlaps(pos, slots, avoid, avoidSlots)) continue;
		if (!InventorySortLayout.fits(maskGrid, x, y, slots)) continue;
		boolean occupied = false;
		for (Entry e : entries) {
		    if (e == moving) continue;
		    if (InventorySortLayout.overlaps(e.current, e.slots, pos, slots)) {
			occupied = true;
			break;
		    }
		}
		if (!occupied) return pos;
	    }
	}
	return null;
    }

    private static Coord findFreeCell(Coord isz, boolean[][] maskGrid, List<Entry> entries) {
	return findFreePlacement(isz, maskGrid, entries, Coord.of(1, 1), null, null, null);
    }

    private static InventorySortIdentity identityOf(GItem item) {
	int widgetId = InventorySortIdentity.UNKNOWN_WIDGET_ID;
	String resourceName = "";
	String displayName = "";
	Double quality = null;
	int amount = InventorySortIdentity.UNKNOWN_AMOUNT;
	if (item == null) return new InventorySortIdentity(widgetId, resourceName, displayName, quality, amount);
	try {
	    widgetId = item.wdgid();
	} catch (RuntimeException ignored) {
	    /* A transiently detached widget simply falls back to value matching. */
	}
	try {
	    Resource resource = item.getres();
	    if (resource != null) resourceName = resource.name;
	} catch (RuntimeException ignored) {
	    /* Resource loading is allowed to settle while the exact object remains a fast path. */
	}
	try {
	    String name = item.getname();
	    if (name != null && !name.isBlank() && !"it's null".equals(name) && !"exception".equals(name))
		displayName = name;
	} catch (RuntimeException ignored) {
	    /* Keep the resource name when tooltip data is still loading. */
	}
	try {
	    Quality q = ItemInfo.find(Quality.class, item.info());
	    if (q != null) quality = q.q;
	} catch (RuntimeException ignored) {
	    /* Quality is an optional tie-breaker, never the only identity field. */
	}
	if (item.num >= 0) amount = item.num;
	return new InventorySortIdentity(widgetId, resourceName, displayName, quality, amount);
    }

	private WItem findLiveItem(Inventory inv, Entry entry) {
	WItem semanticCandidate = null;
	WItem mapped = entry.item == null ? null : inv.wmap.get(entry.item);
	if (mapped != null && mapped.parent == inv && mapped.visible) return mapped;
	for (Widget wdg = inv.lchild; wdg != null; wdg = wdg.prev) {
	    if (!(wdg instanceof WItem) || !wdg.visible) continue;
	    WItem candidate = (WItem) wdg;
	    if (candidate.item == entry.item) return candidate;
	    InventorySortIdentity observed = identityOf(candidate.item);
	    if (!entry.identity.matches(observed)) continue;
	    if (entry.identity.sameWidget(observed)) return candidate;
	    /* Prefer the expected slot when duplicate stacks share a resource/name.
	     * If that is unavailable, refuse an ambiguous arbitrary choice. */
	    if (same(entry.current, positionOf(candidate))) return candidate;
	    if (semanticCandidate != null) return null;
	    semanticCandidate = candidate;
	}
	return semanticCandidate;
    }

    private WItem findLiveItemAt(Inventory inv, Entry entry, Coord position) {
	for (Widget wdg = inv.lchild; wdg != null; wdg = wdg.prev) {
	    if (!(wdg instanceof WItem) || !wdg.visible) continue;
	    WItem candidate = (WItem) wdg;
	    if (!same(position, positionOf(candidate))) continue;
	    if (candidate.item == entry.item) return candidate;
	    InventorySortIdentity observed = identityOf(candidate.item);
	    if (entry.identity.sameWidget(observed) || entry.identity.matches(observed)) return candidate;
	}
	return null;
    }

    private static Coord positionOf(WItem item) {
	return item == null ? null : item.c.sub(1, 1).div(sqsz);
    }

    private WItem cursorItem(Entry entry) {
	if (gui.vhand == null || gui.vhand.item == null) return null;
	if (gui.vhand.item == entry.item) return gui.vhand;
	return entry.identity.matches(identityOf(gui.vhand.item)) ? gui.vhand : null;
    }

	private void moveToEmpty(Inventory inv, Entry entry, Coord destination)
	    throws InterruptedException, SortFailure {
	if (gui.vhand != null)
	    throw new SortFailure("Sorting stopped because the cursor was no longer empty.");
	WItem source = findLiveItem(inv, entry);
	if (source == null)
	    throw new SortFailure("Sorting stopped because an item moved or disappeared before it could be taken.");
	if (!destinationIsEmpty(inv, source, destination, entry.slots))
	    throw new SortFailure("Sorting stopped because the destination is no longer empty.");

	entry.item = source.item;
	Coord origin = new Coord(positionOf(source));
	entry.current = origin;
	activeInventory = inv;
	activeEntry = entry;
	activeOrigin = origin;
	source.item.wdgmsg("take", source.sz.div(2));
	if (!await(() -> cursorItem(entry) != null, MOVE_TIMEOUT_MS))
	    throw new SortFailure("Timed out waiting for " + itemName(entry) + " to reach the cursor.");
	entry.item = gui.vhand.item;

	inv.wdgmsg("drop", destination);
	if (!await(() -> {
	    if (gui.vhand != null) return false;
	    WItem landed = findLiveItemAt(inv, entry, destination);
	    if (landed == null) return false;
	    entry.item = landed.item;
	    return true;
	}, MOVE_TIMEOUT_MS))
	    throw new SortFailure("Timed out waiting for " + itemName(entry) + " to land in its destination.");
	clearActiveCursor();
    }

	private boolean destinationIsEmpty(Inventory inv, WItem moving, Coord destination, Coord slots) {
	for (Widget wdg = inv.lchild; wdg != null; wdg = wdg.prev) {
	    if (!wdg.visible || !(wdg instanceof WItem)) continue;
	    WItem w = (WItem) wdg;
	    if (w == moving) continue;
	    GSprite sprite = w.item.spr();
	    if (sprite == null) return false;
	    Coord otherSlots = slotsFor(sprite);
	    Coord otherPosition = w.c.sub(1, 1).div(sqsz);
	    if (InventorySortLayout.overlaps(otherPosition, otherSlots, destination, slots)) return false;
	}
	return true;
    }

    private static String itemName(Entry entry) {
	if (!entry.identity.displayName.isEmpty()) return entry.identity.displayName;
	if (!entry.identity.resourceName.isEmpty()) return entry.identity.resourceName;
	return "an item";
    }

    private boolean recoverActiveCursor() throws InterruptedException {
	if (activeEntry == null || activeInventory == null || activeOrigin == null)
	    return gui.vhand == null;
	if (gui.vhand == null && findLiveItem(activeInventory, activeEntry) == null) {
	    /* A cancellation can interrupt the wait just before the server's take
	     * message is reflected in the UI. Give that message one bounded chance
	     * to settle before deciding whether recovery is needed. */
	    await(() -> gui.vhand != null || findLiveItem(activeInventory, activeEntry) != null,
		MOVE_TIMEOUT_MS);
	}
	if (gui.vhand == null) return true;
	if (cursorItem(activeEntry) == null)
	    return false;
	activeEntry.item = gui.vhand.item;
	activeInventory.wdgmsg("drop", activeOrigin);
	return await(() -> gui.vhand == null, MOVE_TIMEOUT_MS);
    }

    private void reportFailure(String message) throws InterruptedException {
	boolean recovered = recoverActiveCursor();
	if (!recovered && gui.vhand != null)
	    message += " The cursor could not be recovered; place the item manually.";
	gui.error(message);
    }

    private void clearActiveCursor() {
	activeInventory = null;
	activeEntry = null;
	activeOrigin = null;
    }

    private interface Condition {
	boolean satisfied();
    }

    private static boolean await(Condition condition, long timeoutMs) throws InterruptedException {
	long deadline = System.currentTimeMillis() + timeoutMs;
	while (!condition.satisfied()) {
	    if (System.currentTimeMillis() >= deadline) return false;
	    Thread.sleep(MOVE_POLL_INTERVAL_MS);
	}
	return true;
    }

    public static void cancel() {
	synchronized (lock) {
	    if (current != null) {
		if (current.task != null) current.task.cancel();
		current = null;
	    }
	}
    }

    private static final Audio.Clip sfx_done = Audio.resclip(Resource.remote().loadwait("sfx/hud/on"));

    private static void start(InventorySorter sorter) {
	cancel();
	synchronized (lock) { current = sorter; }
	sorter.task = Defer.later(sorter);
    }
}
