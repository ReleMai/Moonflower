package haven.foraging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Reviewed GUIDE catalog of forageable ground resources. Live OCache resources
 * always override display text and are the only objects that can become targets.
 */
public final class ForagingHerbAtlas {
    private static final String H = ForagingGobScanner.HERB_PREFIX;
    private static final List<ForagingGobScanner.HerbResource> ENTRIES = build();

    private ForagingHerbAtlas() {
    }

    public static List<ForagingGobScanner.HerbResource> entries() {
        return(ENTRIES);
    }

    private static List<ForagingGobScanner.HerbResource> build() {
        List<ForagingGobScanner.HerbResource> herbs = new ArrayList<>();
        add(herbs, "Bay Bolete", "baybolete", "Mushrooms");
        add(herbs, "Black Trumpets", "blacktrumpets", "Mushrooms");
        add(herbs, "Bloated Bolete", "bloatedbolete", "Mushrooms");
        add(herbs, "Blood Stern", "bloodstern", "Plants");
        add(herbs, "Blueberries", "blueberry", "Berries");
        add(herbs, "Brown Kelp", "brownkelp", "Waterside");
        add(herbs, "Button Mushroom", "buttonmushroom", "Mushrooms");
        add(herbs, "Camomile", "camomile", "Plants");
        add(herbs, "Candleberry", "candleberry", "Berries");
        add(herbs, "Cattail", "cattail", "Waterside");
        add(herbs, "Cave Clay", "caveclay", "Materials");
        add(herbs, "Cave Coral", "cavecoral", "Cave");
        add(herbs, "Cave Lantern", "cavelantern", "Cave");
        add(herbs, "Cavebulb", "cavebulb", "Cave");
        add(herbs, "Chantrelles", "chantrelle", "Mushrooms");
        add(herbs, "Chiming Bluebell", "chimingbluebell", "Curiosities");
        add(herbs, "Chives", "chives", "Plants");
        add(herbs, "Clover", "clover", "Plants");
        add(herbs, "Coltsfoot", "coltsfoot", "Plants");
        add(herbs, "Common Starfish", "starfish", "Waterside");
        add(herbs, "Cremini Mushroom", "cremini", "Mushrooms");
        add(herbs, "Dandelion", "dandelion", "Plants");
        add(herbs, "Dewy Lady's Mantle", "dewyladysmantle", "Curiosities");
        add(herbs, "Dill", "dill", "Plants");
        add(herbs, "Dusk Fern", "duskfern", "Plants");
        add(herbs, "Edelweiss", "edelweiss", "Curiosities");
        add(herbs, "Elven Lights", "elvenlights", "Curiosities");
        add(herbs, "Field Blewits", "fieldblewits", "Mushrooms");
        add(herbs, "Four-Leaf Clover", "fourleafclover", "Curiosities");
        add(herbs, "Frog's Crown", "frogscrown", "Curiosities");
        add(herbs, "Frogspawn", "frogspawn", "Waterside");
        add(herbs, "Giant Puffball", "giantpuffball", "Mushrooms");
        add(herbs, "Glimmermoss", "glimmermoss", "Cave");
        add(herbs, "Gooseneck Barnacle", "gooseneckbarnacle", "Waterside");
        add(herbs, "Gray Clay", "clay-gray", "Materials");
        add(herbs, "Green Kelp", "greenkelp", "Waterside");
        add(herbs, "Heartsease", "heartsease", "Plants");
        add(herbs, "Indigo Cap", "indigocap", "Mushrooms");
        add(herbs, "Kvann", "kvann", "Plants");
        add(herbs, "Lady's Mantle", "ladysmantle", "Plants");
        add(herbs, "Lamp Stalk", "lampstalk", "Cave");
        add(herbs, "Liberty Caps", "libertycap", "Mushrooms");
        add(herbs, "Lingonberries", "lingon", "Berries");
        add(herbs, "Mandrake Root", "mandrakeroot", "Plants");
        add(herbs, "Marsh-Mallow", "marshmallow", "Plants");
        add(herbs, "Mistletoe", "mistletoe", "Plants");
        add(herbs, "Morels", "morel", "Mushrooms");
        add(herbs, "Oyster", "oyster", "Waterside");
        add(herbs, "Oyster Mushroom", "oystermushroom", "Mushrooms");
        add(herbs, "Parasol Mushroom", "parasol", "Mushrooms");
        add(herbs, "Peculiar Flotsam", "flotsam", "Waterside");
        add(herbs, "Perfect Autumn Leaf", "perfectautumnleaf", "Curiosities");
        add(herbs, "Portobello Mushroom", "portobello", "Mushrooms");
        herbs.add(new ForagingGobScanner.HerbResource(
                "gfx/terobjs/items/precioussnowflake", "Precious Snowflake", "Curiosities", false));
        add(herbs, "Rabbit Frost", "rabbitfrost", "Curiosities");
        add(herbs, "Rainbow Shell", "rainbowshell", "Waterside");
        add(herbs, "Razor Clam", "razorclam", "Waterside");
        add(herbs, "River Pearl Mussel", "mussel", "Waterside");
        add(herbs, "Round Clam", "roundclam", "Waterside");
        add(herbs, "Royal Toadstool", "royaltoadstool", "Mushrooms");
        add(herbs, "Ruby Bolete", "rubybolete", "Mushrooms");
        add(herbs, "Rustroot", "rustroot", "Plants");
        add(herbs, "Sage", "sage", "Plants");
        add(herbs, "Seasponge", "seasponge", "Waterside");
        add(herbs, "Sleighbell", "sleighbell", "Curiosities");
        add(herbs, "Snowtop", "snowtop", "Mushrooms");
        add(herbs, "Spindly Taproot", "spindlytaproot", "Plants");
        add(herbs, "Spirited Mandrake Root", "spiritedmandrakeroot", "Curiosities");
        add(herbs, "Stalagoom", "stalagoom", "Cave");
        add(herbs, "Standing Grass", "standinggrass", "Plants");
        add(herbs, "Stinging Nettle", "stingingnettle", "Plants");
        add(herbs, "Strawberry", "strawberry", "Berries");
        add(herbs, "Swamplily", "swamplily", "Waterside");
        add(herbs, "Tangled Bramble", "tangledbramble", "Plants");
        add(herbs, "Tansy", "tansy", "Plants");
        add(herbs, "Thorny Thistle", "thornythistle", "Plants");
        add(herbs, "Thyme", "thyme", "Plants");
        add(herbs, "Uncommon Snapdragon", "uncommonsnapdragon", "Curiosities");
        add(herbs, "Washed-up Bladderwrack", "washedupbladderwrack", "Waterside");
        add(herbs, "Waybroad", "waybroad", "Plants");
        add(herbs, "Whale Barnacles", "whalebarnacle", "Waterside");
        add(herbs, "Wild Windsown Weed", "wwweed", "Plants");
        add(herbs, "Wintergreen", "wintergreen", "Berries");
        add(herbs, "Yarrow", "yarrow", "Plants");
        add(herbs, "Yellowfeet", "yellowfeet", "Mushrooms");
        herbs.sort(Comparator.comparing((ForagingGobScanner.HerbResource herb) -> herb.displayName)
                .thenComparing(herb -> herb.resourceName));
        return(Collections.unmodifiableList(herbs));
    }

    private static void add(List<ForagingGobScanner.HerbResource> herbs, String name,
                            String basename, String category) {
        herbs.add(new ForagingGobScanner.HerbResource(H + basename, name, category, false));
    }
}
