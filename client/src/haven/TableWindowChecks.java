package haven;

/** Focused offline checks for table-window routing and native action identity. */
public final class TableWindowChecks {
    private TableWindowChecks() {
    }

    public static void main(String[] args) {
        check(TableWindowDetector.classify("Table", Coord.of(4, 2)) ==
                        TableWindowDetector.Kind.ALCHEMIST_TABLE,
                "4x2 Table inventory should route to the Alchemist's Table control");
        check(TableWindowDetector.classify("Alchemist's Table", Coord.of(6, 6)) ==
                        TableWindowDetector.Kind.ALCHEMIST_TABLE,
                "explicit Alchemist's Table caption should remain alchemy-specific");
        check(TableWindowDetector.classify("Table", Coord.of(6, 6)) ==
                        TableWindowDetector.Kind.FEASTING_TABLE,
                "large Table inventory should retain the Feasting Helper");
        check(TableWindowDetector.classify("Table", Coord.of(3, 3)) ==
                        TableWindowDetector.Kind.TABLEWARE,
                "3x3 tableware inventory should not receive the helper");
        check(TableWindowDetector.classify("Table", Coord.of(1, 2)) ==
                        TableWindowDetector.Kind.TABLEWARE,
                "1x2 tableware inventory should not receive the helper");
        check(TableWindowDetector.classify("Cupboard", Coord.of(6, 6)) ==
                        TableWindowDetector.Kind.NOT_TABLE,
                "non-table windows should not receive table integrations");
        check(AlchemyBookAction.isBookActionResource("paginae/act/alchbook"),
                "native Alchemy Book action resource identity");
        check(!AlchemyBookAction.isBookActionResource("paginae/craft/alchemy"),
                "alchemy category must not be mistaken for the Book action");
        System.out.println("Table window checks passed.");
    }

    private static void check(boolean condition, String message) {
        if(!condition)
            throw(new AssertionError(message));
    }
}
