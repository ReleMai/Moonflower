package haven.inventoryqol;

import haven.LocalizedResourceTimerInfo;
import haven.Area;
import haven.Coord;
import haven.Inventory;
import haven.UI;
import haven.Window;

/** Deterministic checks for option routing, sharp tools, timer parsing, and title art. */
public final class InventoryQolChecks {
    public static void main(String[] args) {
        check(SharpToolSwapper.isSupportedSharpTool("Stone Axe", "gfx/invobjs/stoneaxe"),
                "stone axe is a supported one-hand sharp tool");
        check(SharpToolSwapper.isSupportedSharpTool("Butcher's Cleaver", "gfx/invobjs/butcherscleaver"),
                "butcher cleaver is supported");
        check(SharpToolSwapper.isSupportedSharpTool("Ceramic Knife", "gfx/invobjs/ceramicknife"),
                "knife is supported");
        check(!SharpToolSwapper.isSupportedSharpTool("Battleaxe of the Twelfth Bay", "gfx/invobjs/b12axe"),
                "two-hand battleaxe is excluded from guarded swapping");
        check(!SharpToolSwapper.isSupportedSharpTool("Pickaxe", "gfx/invobjs/pickaxe"),
                "pickaxe is not classified as a sharp tool");
		check(SharpToolSwapper.chooseHandSlot(true, false, true, true, true, false) == 6,
				"a sword hand is selected instead of evicting the off-hand shield");
		check(SharpToolSwapper.chooseHandSlot(true, true, false, true, false, true) == 7,
				"a cutting hand in slot seven is selected when slot six is a shield");
		check(SharpToolSwapper.chooseHandSlot(true, true, false, true, true, false) < 0,
				"two protected shields fail closed without an equipment swap");

        check(SharpToolAutoManager.isProcessingOption("Collect Bones"),
                "bone gathering receives sharp-tool preparation");
        check(SharpToolAutoManager.isProcessingOption("Flay"),
                "flaying receives sharp-tool preparation");
        check(!SharpToolAutoManager.isProcessingOption("Eat"),
                "unrelated flower actions remain native");
		check(InventoryBulkActionController.candidateText(
				InventoryBulkActionController.Action.BUTCHER_ALL, "Dead Rabbit gfx/invobjs/rabbit-dead"),
				"dead animal items are probed for processing options");
		check(!InventoryBulkActionController.candidateText(
				InventoryBulkActionController.Action.BUTCHER_ALL, "Raw Rabbit Meat gfx/invobjs/meat"),
				"ordinary food is not right-click probed");
		check(InventoryBulkActionController.candidateText(
				InventoryBulkActionController.Action.CRACK_ALL, "Walnut gfx/invobjs/walnut"),
				"nuts are probed for crack options");
		check(InventoryBulkActionController.Action.BUTCHER_ALL.options.indexOf("Skin") == 0 &&
				InventoryBulkActionController.Action.BUTCHER_ALL.options.indexOf("Clean") == 1 &&
				InventoryBulkActionController.Action.BUTCHER_ALL.options.indexOf("Butcher") == 2,
				"butchering enforces Skin then Clean then Butcher priority");

        long parsed = LocalizedResourceTimerInfo.parseDurationMillis(
                "This resource will refill in 2 days, 3 hours and 4 minutes.");
        check(parsed == ((2L * 86_400L + 3L * 3_600L + 4L * 60L) * 1000L),
                "compound refill duration parses exactly");
        check(LocalizedResourceTimerInfo.parseDurationMillis("Quality: 42") < 0,
                "unrelated Inspect quality does not create a timer");
        check("1d 02:03:04".equals(LocalizedResourceTimerInfo.formatRemaining(
                        (86_400L + 2L * 3_600L + 3L * 60L + 4L) * 1000L)),
                "countdown formatting is stable");

		Window inventoryWindow = new Window(Coord.of(100, 100), "Inventory");
		inventoryWindow.add(new Inventory(Coord.of(4, 4)), Coord.z);
		Window.DefaultDeco decoration = inventoryWindow.getchild(Window.DefaultDeco.class);
		check(decoration != null && decoration.inventorycontrolbtn != null,
				"inventory attachment creates the inventory-actions title control");
		check(decoration.inventorycontrolbtn.visible,
				"inventory-actions title control is visible on inventory windows");
		inventoryWindow.resize(Coord.of(100, 100));
		check(decoration.inventorycontrolbtn.c.x >= 0 &&
				decoration.inventorycontrolbtn.c.x < decoration.cbtn.c.x,
				"inventory-actions title control stays visible beside Close on narrow inventories");
		check(decoration.inventorycontrolbtn.c.y >= 0,
				"inventory-tools control stays inside the title frame");
		check(decoration.inventorycontrolbtn.sz.equals(UI.scale(34, 18)) &&
				decoration.inventorycontrolbtn.c.y + decoration.inventorycontrolbtn.sz.y <= UI.scale(30),
				"engraved inventory-tools tab scales inside the title rail");
		checkLayout(InventoryControlPanel.layout(false));
		checkLayout(InventoryControlPanel.layout(true));
        System.out.println("Inventory QoL checks passed.");
    }

	private static void checkLayout(InventoryControlPanel.Layout layout) {
		Area[] actions = {layout.sort, layout.stack, layout.unstack, layout.lock, layout.extended,
				layout.butcher, layout.crack, layout.stop};
		for(Area action : actions) {
			if(action == null)
				continue;
			check(action.ul.x >= 0 && action.ul.y >= 0 && action.br.x <= layout.panelSize.x &&
					action.br.y <= layout.panelSize.y,
					"inventory-tools action remains inside the slide-out panel");
		}
		check(layout.panelSize.x <= UI.scale(224) && layout.panelSize.y <= UI.scale(270),
				"inventory-tools panel respects its maximum scaled footprint");
	}

    private static void check(boolean condition, String message) {
        if(!condition)
            throw new AssertionError(message);
    }
}
