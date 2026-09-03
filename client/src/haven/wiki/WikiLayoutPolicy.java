package haven.wiki;

/** Pure layout thresholds used to keep the Codex readable at narrow sizes. */
final class WikiLayoutPolicy {
    private WikiLayoutPolicy() {
    }

    static boolean relatedShelfAllowed(int width, int minimumWidth) {
        return(width >= minimumWidth);
    }
}
