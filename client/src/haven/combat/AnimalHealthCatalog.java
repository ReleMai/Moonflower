package haven.combat;

import java.util.Map;

/** Versioned, offline animal max-HP evidence keyed by stable Gob resources. */
public final class AnimalHealthCatalog {
    public static final String VERSION = "ring-of-brodgar-creatures-oldid-114037-2026-08-23";
    public static final String SOURCE_URL = "https://ringofbrodgar.com/index.php?title=Creatures&oldid=114037";
    public static final String RETRIEVED_ON = "2026-08-23";

    public enum EvidenceKind {
        EXACT,
        APPROXIMATE,
        RANGE,
        LOWER_BOUND,
        UNKNOWN,
        NONE
    }

    public static final class HpEvidence {
        private final EvidenceKind kind;
        private final Integer minimum;
        private final Integer maximum;
        private final String note;

        public HpEvidence(EvidenceKind kind, Integer minimum, Integer maximum, String note) {
            this.kind = kind;
            this.minimum = minimum;
            this.maximum = maximum;
            this.note = note;
        }

        public EvidenceKind kind() { return(kind); }
        public Integer minimum() { return(minimum); }
        public Integer maximum() { return(maximum); }
        public String note() { return(note); }

        public static HpEvidence exact(int hp) { return(new HpEvidence(EvidenceKind.EXACT, hp, hp, "")); }
        public static HpEvidence approximate(int hp) { return(new HpEvidence(EvidenceKind.APPROXIMATE, hp, hp, "")); }
        public static HpEvidence range(int min, int max) { return(new HpEvidence(EvidenceKind.RANGE, min, max, "")); }
        public static HpEvidence lowerBound(int hp) { return(new HpEvidence(EvidenceKind.LOWER_BOUND, hp, null, "")); }
        public static HpEvidence unknown(String note) { return(new HpEvidence(EvidenceKind.UNKNOWN, null, null, note)); }
        public static HpEvidence none() { return(new HpEvidence(EvidenceKind.NONE, null, null, "")); }
    }

    public static final class Entry {
        private final String resourceName;
        private final String displayName;
        private final HpEvidence maxHp;
        private final HpEvidence fleeingHp;
        private final String sourceUrl;
        private final String retrievedOn;

        public Entry(String resourceName, String displayName, HpEvidence maxHp,
                     HpEvidence fleeingHp, String sourceUrl, String retrievedOn) {
            this.resourceName = resourceName;
            this.displayName = displayName;
            this.maxHp = maxHp;
            this.fleeingHp = fleeingHp;
            this.sourceUrl = sourceUrl;
            this.retrievedOn = retrievedOn;
        }

        public String resourceName() { return(resourceName); }
        public String displayName() { return(displayName); }
        public HpEvidence maxHp() { return(maxHp); }
        public HpEvidence fleeingHp() { return(fleeingHp); }
        public String sourceUrl() { return(sourceUrl); }
        public String retrievedOn() { return(retrievedOn); }
    }

    private static final Map<String, Entry> ENTRIES = Map.ofEntries(
            entry("gfx/kritter/adder/adder", "Adder", HpEvidence.exact(70), HpEvidence.exact(30)),
            entry("gfx/kritter/ant/ant", "Ants", HpEvidence.exact(50), HpEvidence.none()),
            entry("gfx/kritter/ants/ants", "Ants", HpEvidence.exact(50), HpEvidence.none()),
            entry("gfx/kritter/aurochs/aurochs", "Aurochs", HpEvidence.exact(350), HpEvidence.exact(250)),
            entry("gfx/kritter/badger/badger", "Badger", HpEvidence.exact(250), HpEvidence.exact(150)),
            entry("gfx/kritter/bat/bat", "Bat", HpEvidence.exact(90), HpEvidence.exact(60)),
            entry("gfx/kritter/bear/bear", "Bear", HpEvidence.exact(850), HpEvidence.approximate(500)),
            entry("gfx/kritter/bear/polarbear", "Polar Bear", HpEvidence.unknown("No current table row"), HpEvidence.none()),
            entry("gfx/kritter/beaver/beaver", "Beaver", HpEvidence.approximate(100), HpEvidence.approximate(75)),
            entry("gfx/kritter/boar/boar", "Boar", HpEvidence.exact(450), HpEvidence.approximate(275)),
            entry("gfx/kritter/bogturtle/bogturtle", "Bog Turtle", HpEvidence.exact(1), HpEvidence.exact(1)),
            entry("gfx/kritter/boreworm/boreworm", "Boreworm", HpEvidence.exact(1200), HpEvidence.exact(800)),
            entry("gfx/kritter/caveangler/caveangler", "Cave Angler", HpEvidence.range(1200, 1800), HpEvidence.range(850, 1300)),
            entry("gfx/kritter/cavelouse/cavelouse", "Cave Louse", HpEvidence.exact(1000), HpEvidence.exact(300)),
            entry("gfx/kritter/rat/caverat", "Caverat", HpEvidence.exact(120), HpEvidence.exact(75)),
            entry("gfx/kritter/chasmconch/chasmconch", "Chasm Conch", HpEvidence.exact(20000), HpEvidence.none()),
            entry("gfx/kritter/chicken/chicken", "Chicken", HpEvidence.exact(10), HpEvidence.exact(10)),
            entry("gfx/kritter/chicken/hen", "Chicken Hen", HpEvidence.exact(10), HpEvidence.exact(10)),
            entry("gfx/kritter/chicken/rooster", "Chicken Cock", HpEvidence.exact(10), HpEvidence.exact(10)),
            entry("gfx/kritter/eagleowl/eagleowl", "Eagle Owl", HpEvidence.approximate(180), HpEvidence.approximate(100)),
            entry("gfx/kritter/fox/fox", "Fox", HpEvidence.exact(110), HpEvidence.exact(75)),
            entry("gfx/kritter/goldeneagle/goldeneagle", "Golden Eagle", HpEvidence.approximate(250), HpEvidence.approximate(80)),
            entry("gfx/kritter/goat/billy", "Billygoat", HpEvidence.exact(200), HpEvidence.exact(100)),
            entry("gfx/kritter/goat/nanny", "Nanny Goat", HpEvidence.exact(200), HpEvidence.exact(100)),
            entry("gfx/kritter/ooze/greenooze", "Green Ooze", HpEvidence.range(60, 500), HpEvidence.range(30, 100)),
            entry("gfx/kritter/greyseal/greyseal", "Grey Seal", HpEvidence.approximate(320), HpEvidence.approximate(240)),
            entry("gfx/kritter/hedgehog/hedgehog", "Hedgehog", HpEvidence.exact(40), HpEvidence.exact(40)),
            entry("gfx/kritter/lynx/lynx", "Lynx", HpEvidence.exact(400), HpEvidence.exact(150)),
            entry("gfx/kritter/mallard/mallard", "Mallard", HpEvidence.exact(10), HpEvidence.exact(10)),
            entry("gfx/kritter/mammoth/mammoth", "Mammoth", HpEvidence.exact(4000), HpEvidence.exact(2800)),
            entry("gfx/kritter/mallard/mallard-f", "Mallard Hen", HpEvidence.exact(10), HpEvidence.exact(10)),
            entry("gfx/kritter/mallard/mallard-m", "Mallard Drake", HpEvidence.exact(10), HpEvidence.exact(10)),
            entry("gfx/kritter/mole/mole", "Mole", HpEvidence.exact(30), HpEvidence.exact(30)),
            entry("gfx/kritter/moose/moose", "Moose", HpEvidence.exact(800), HpEvidence.exact(250)),
            entry("gfx/kritter/goat/wildgoat", "Wildgoat", HpEvidence.exact(300), HpEvidence.exact(200)),
            entry("gfx/kritter/mouflon/mouflon", "Mouflon", HpEvidence.exact(200), HpEvidence.exact(120)),
            entry("gfx/kritter/orca/orca", "Orca", HpEvidence.exact(20000), HpEvidence.exact(14000)),
            entry("gfx/kritter/otter/otter", "Otter", HpEvidence.exact(100), HpEvidence.exact(60)),
            entry("gfx/kritter/pelican/pelican", "Pelican", HpEvidence.exact(130), HpEvidence.approximate(80)),
            entry("gfx/kritter/pig/hog", "Hog", HpEvidence.exact(150), HpEvidence.none()),
            entry("gfx/kritter/pig/sow", "Sow", HpEvidence.exact(150), HpEvidence.none()),
            entry("gfx/kritter/ptarmigan/ptarmigan", "Ptarmigan", HpEvidence.unknown("Current HP is unknown"), HpEvidence.unknown("Current fleeing HP is unknown")),
            entry("gfx/kritter/quail/quail", "Quail", HpEvidence.exact(10), HpEvidence.exact(10)),
            entry("gfx/kritter/reddeer/reddeer", "Red Deer", HpEvidence.exact(200), HpEvidence.approximate(150)),
            entry("gfx/kritter/reindeer/reindeer", "Reindeer", HpEvidence.exact(200), HpEvidence.exact(110)),
            entry("gfx/kritter/rockdove/rockdove", "Rock Dove", HpEvidence.exact(10), HpEvidence.exact(10)),
            entry("gfx/kritter/seagull/seagull", "Seagull", HpEvidence.exact(10), HpEvidence.exact(10)),
            entry("gfx/kritter/sheep/sheep", "Sheep", HpEvidence.exact(200), HpEvidence.exact(100)),
            entry("gfx/kritter/spermwhale/spermwhale", "Cachalot", HpEvidence.exact(50000), HpEvidence.unknown("Varies")),
            entry("gfx/kritter/stoat/stoat", "Stoat", HpEvidence.exact(90), HpEvidence.exact(60)),
            entry("gfx/kritter/squirrel/squirrel", "Squirrel", HpEvidence.exact(10), HpEvidence.none()),
            entry("gfx/kritter/swan/swan", "Swan", HpEvidence.approximate(150), HpEvidence.approximate(80)),
            entry("gfx/kritter/troll/troll", "Troll", HpEvidence.lowerBound(1000), HpEvidence.unknown("About 40% of health")),
            entry("gfx/kritter/walrus/walrus", "Walrus", HpEvidence.exact(900), HpEvidence.exact(600)),
            entry("gfx/kritter/wildbees/beeswarm", "Wild Bees", HpEvidence.exact(50), HpEvidence.none()),
            entry("gfx/kritter/horse/horse", "Wild Horse", HpEvidence.approximate(320), HpEvidence.exact(200)),
            entry("gfx/kritter/narwhal/narwhal", "Narwhal", HpEvidence.unknown("No current table row"), HpEvidence.none()),
            entry("gfx/kritter/nidbane/nidbane", "Nidbane", HpEvidence.unknown("Current HP is unknown"), HpEvidence.none()),
            entry("gfx/kritter/roedeer/roedeer", "Roe Deer", HpEvidence.unknown("No current table row"), HpEvidence.none()),
            entry("gfx/kritter/wolf/wolf", "Wolf", HpEvidence.exact(500), HpEvidence.exact(400)),
            entry("gfx/kritter/wolverine/wolverine", "Wolverine", HpEvidence.exact(300), HpEvidence.exact(200)),
            entry("gfx/kritter/woodgrouse/woodgrouse-m", "Wood Grouse Cock", HpEvidence.approximate(120), HpEvidence.exact(100)),
            entry("gfx/kritter/woodgrouse/woodgrouse-f", "Wood Grouse Hen", HpEvidence.exact(10), HpEvidence.exact(10))
    );

    private AnimalHealthCatalog() {
    }

    public static Entry find(String resourceName) {
        return(resourceName == null ? null : ENTRIES.get(resourceName));
    }

    public static Map<String, Entry> entries() {
        return(ENTRIES);
    }

    private static Map.Entry<String, Entry> entry(String resourceName, String name,
                                                   HpEvidence maxHp, HpEvidence fleeingHp) {
        return(Map.entry(resourceName, new Entry(resourceName, name, maxHp, fleeingHp,
                SOURCE_URL, RETRIEVED_ON)));
    }
}
