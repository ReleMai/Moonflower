package haven.cookbook;

/** Name-to-resource observation used to display native ingredient icons. */
final class CookbookItem {
    final String worldId;
    final String name;
    final String normalizedName;
    final String resourceName;
    final long observedAt;

    CookbookItem(String worldId, String name, String resourceName, long observedAt) {
        this.worldId = worldId == null ? "" : worldId.trim();
        this.name = name == null ? "" : name.trim();
        this.normalizedName = CookbookIngredientCatalog.normalize(this.name);
        this.resourceName = resourceName == null ? "" : resourceName.trim();
        this.observedAt = observedAt;
    }
}
