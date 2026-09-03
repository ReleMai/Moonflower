package haven.automated;

/**
 * Stable, value-based identity used while the server reparents inventory
 * items between an inventory and the cursor. The client may recreate the
 * Java GItem/WItem wrappers during that transition, so object identity is
 * only a fast path and never the sole acknowledgement signal.
 */
final class InventorySortIdentity {
    static final int UNKNOWN_WIDGET_ID = -1;
    static final int UNKNOWN_AMOUNT = -1;

    final int widgetId;
    final String resourceName;
    final String displayName;
    final Double quality;
    final int amount;

    InventorySortIdentity(int widgetId, String resourceName, String displayName,
                          Double quality, int amount) {
        this.widgetId = widgetId;
        this.resourceName = clean(resourceName);
        this.displayName = clean(displayName);
        this.quality = quality != null && !quality.isNaN() && !quality.isInfinite() ? quality : null;
        this.amount = amount >= 0 ? amount : UNKNOWN_AMOUNT;
    }

    String sortName() {
        return !displayName.isEmpty() ? displayName : resourceName;
    }

    boolean hasDescription() {
        return !resourceName.isEmpty() || !displayName.isEmpty();
    }

    boolean sameWidget(InventorySortIdentity other) {
        return other != null && widgetId >= 0 && widgetId == other.widgetId;
    }

    /**
     * Match an item after a server-side reparent. Optional fields are only
     * compared when both sides have them; this allows a freshly created
     * cursor GItem whose tooltip has not arrived yet to be acknowledged by
     * its resource and/or display name.
     */
    boolean matches(InventorySortIdentity actual) {
        if (actual == null) return false;
        if (sameWidget(actual)) return true;

        boolean compared = false;
        if (!resourceName.isEmpty() && !actual.resourceName.isEmpty()) {
            compared = true;
            if (!resourceName.equals(actual.resourceName)) return false;
        }
        if (!displayName.isEmpty() && !actual.displayName.isEmpty()) {
            compared = true;
            if (!displayName.equals(actual.displayName)) return false;
        }
        if (!compared) return false;
        if (quality != null && actual.quality != null && Double.compare(quality, actual.quality) != 0)
            return false;
        if (amount >= 0 && actual.amount >= 0 && amount != actual.amount)
            return false;
        return true;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
