package haven.cookbook;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Owns the versioned local SQLite cookbook schema and queries. */
final class CookbookRepository {
    private static final int SCHEMA_VERSION = 4;
    private static final String SHADOWED_PLACEHOLDER_FILTER =
            " NOT (NOT EXISTS (SELECT 1 FROM cookbook_ingredients pi WHERE pi.recipe_id = r.id)" +
            " AND r.modifiers = '' AND EXISTS (SELECT 1 FROM cookbook_recipes documented" +
            " WHERE documented.id <> r.id AND documented.world_id = r.world_id" +
            " AND documented.resource_name = r.resource_name" +
            " AND lower(documented.item_name) = lower(r.item_name)" +
            " AND (documented.modifiers <> '' OR EXISTS (SELECT 1 FROM cookbook_ingredients di" +
            " WHERE di.recipe_id = documented.id))))";
    private final String databaseUrl;

    CookbookRepository(String databaseUrl) {
        this.databaseUrl = databaseUrl;
    }

    void initialize() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch(ClassNotFoundException e) {
            throw(new SQLException("SQLite JDBC driver is unavailable", e));
        }
        try(Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("CREATE TABLE IF NOT EXISTS cookbook_meta (" +
                    "key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS cookbook_recipes (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, world_id TEXT NOT NULL, " +
                    "recipe_key TEXT UNIQUE NOT NULL, resource_name TEXT NOT NULL, " +
                    "item_name TEXT NOT NULL, ingredients_summary TEXT NOT NULL, " +
                    "modifiers TEXT NOT NULL, first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS cookbook_recipes_world_name " +
                    "ON cookbook_recipes(world_id, item_name)");
            statement.execute("CREATE TABLE IF NOT EXISTS cookbook_ingredients (" +
                    "recipe_id INTEGER NOT NULL REFERENCES cookbook_recipes(id) ON DELETE CASCADE, " +
                    "kind TEXT NOT NULL, name TEXT NOT NULL, percentage REAL NOT NULL, position INTEGER, " +
                    "UNIQUE(recipe_id, kind, name, percentage))");
            statement.execute("CREATE TABLE IF NOT EXISTS cookbook_observations (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, recipe_id INTEGER NOT NULL " +
                    "REFERENCES cookbook_recipes(id) ON DELETE CASCADE, observation_key TEXT UNIQUE NOT NULL, " +
                    "character_id TEXT NOT NULL, quality REAL NOT NULL, energy_percent REAL NOT NULL, " +
                    "hunger_permille REAL NOT NULL, normalized_hunger_permille REAL NOT NULL, " +
                    "first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL, seen_count INTEGER NOT NULL DEFAULT 1)");
            statement.execute("CREATE INDEX IF NOT EXISTS cookbook_observations_recipe " +
                    "ON cookbook_observations(recipe_id, last_seen)");
            statement.execute("CREATE TABLE IF NOT EXISTS cookbook_feps (" +
                    "observation_id INTEGER NOT NULL REFERENCES cookbook_observations(id) ON DELETE CASCADE, " +
                    "attribute TEXT NOT NULL, amount REAL NOT NULL, normalized_amount REAL NOT NULL, " +
                    "UNIQUE(observation_id, attribute))");
            statement.execute("CREATE TABLE IF NOT EXISTS cookbook_item_resources (" +
                    "world_id TEXT NOT NULL, normalized_name TEXT NOT NULL, item_name TEXT NOT NULL, " +
                    "resource_name TEXT NOT NULL, last_seen INTEGER NOT NULL, " +
                    "PRIMARY KEY(world_id, normalized_name))");
            ensureColumn(connection, "cookbook_observations", "captured_fep_efficiency_percent",
                    "REAL");
            ensureColumn(connection, "cookbook_observations", "captured_hunger_efficiency_percent",
                    "REAL");
            ensureColumn(connection, "cookbook_ingredients", "position", "INTEGER");
            statement.execute("INSERT INTO cookbook_meta(key, value) VALUES " +
                    "('schema_version', '" + SCHEMA_VERSION + "') ON CONFLICT(key) DO UPDATE SET " +
                    "value = excluded.value");
        }
    }

    boolean saveItem(CookbookItem item) throws SQLException {
        if(item.normalizedName.isEmpty() || item.resourceName.isEmpty())
            return(false);
        String previous = null;
        try(Connection connection = connect()) {
            try(PreparedStatement statement = connection.prepareStatement(
                    "SELECT resource_name FROM cookbook_item_resources " +
                            "WHERE world_id = ? AND normalized_name = ?")) {
                statement.setString(1, item.worldId);
                statement.setString(2, item.normalizedName);
                try(ResultSet result = statement.executeQuery()) {
                    if(result.next())
                        previous = result.getString(1);
                }
            }
            try(PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO cookbook_item_resources(world_id, normalized_name, item_name, " +
                            "resource_name, last_seen) VALUES (?, ?, ?, ?, ?) " +
                            "ON CONFLICT(world_id, normalized_name) DO UPDATE SET " +
                            "item_name = excluded.item_name, resource_name = excluded.resource_name, " +
                            "last_seen = excluded.last_seen")) {
                statement.setString(1, item.worldId);
                statement.setString(2, item.normalizedName);
                statement.setString(3, item.name);
                statement.setString(4, item.resourceName);
                statement.setLong(5, item.observedAt);
                statement.executeUpdate();
            }
        }
        return(previous == null || !previous.equals(item.resourceName));
    }

    boolean save(CookbookFood food) throws SQLException {
        try(Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                long recipeId = findRecipe(connection, food.recipeKey);
                boolean newRecipe = recipeId < 0;
                if(newRecipe)
                    recipeId = insertRecipe(connection, food);
                else
                    updateRecipe(connection, recipeId, food);
                boolean ingredientsChanged = replaceIngredients(connection, recipeId,
                        food.ingredients);

                long observationId = findObservation(connection, food.observationKey);
                boolean newObservation = observationId < 0;
                boolean updatedObservation = false;
                if(newObservation) {
                    observationId = insertObservation(connection, recipeId, food);
                    insertFeps(connection, observationId, food.feps);
                } else {
                    updatedObservation = touchObservation(connection, observationId, food);
                }
                connection.commit();
                return(newRecipe || ingredientsChanged || newObservation || updatedObservation);
            } catch(SQLException e) {
                connection.rollback();
                throw(e);
            }
        }
    }

    List<CookbookEntry> list(String worldId, String attribute, String search) throws SQLException {
        String sql = "WITH latest AS (" +
                " SELECT o.* FROM cookbook_observations o WHERE o.id = (" +
                "  SELECT o2.id FROM cookbook_observations o2 WHERE o2.recipe_id = o.recipe_id" +
                "  ORDER BY o2.last_seen DESC, o2.id DESC LIMIT 1))," +
                " scores AS (" +
                " SELECT r.id recipe_id, r.item_name, r.resource_name, r.ingredients_summary, r.modifiers," +
                " o.id latest_observation_id, o.quality latest_quality," +
                " o.energy_percent latest_energy_percent," +
                " o.normalized_hunger_permille latest_normalized_hunger," +
                " COALESCE(SUM(CASE WHEN lower(f.attribute) LIKE ? THEN f.normalized_amount ELSE 0 END), 0) target_fep," +
                " COALESCE(SUM(f.normalized_amount), 0) total_fep" +
                " FROM cookbook_recipes r JOIN latest o ON o.recipe_id = r.id" +
                " LEFT JOIN cookbook_feps f ON f.observation_id = o.id" +
                " WHERE r.world_id = ? AND" + SHADOWED_PLACEHOLDER_FILTER +
                " AND (? = '' OR lower(r.item_name) LIKE ?" +
                " OR lower(r.ingredients_summary) LIKE ? OR lower(r.modifiers) LIKE ?)" +
                " GROUP BY r.id, r.item_name, r.resource_name, r.ingredients_summary, r.modifiers," +
                " o.id, o.quality, o.energy_percent, o.normalized_hunger_permille)" +
                " SELECT s.*, o.id observation_id, o.last_seen observed_last_seen," +
                " o.quality observed_quality, o.energy_percent observed_energy_percent," +
                " o.hunger_permille observed_hunger_permille," +
                " o.normalized_hunger_permille observed_normalized_hunger," +
                " o.captured_fep_efficiency_percent observed_fep_efficiency," +
                " o.captured_hunger_efficiency_percent observed_hunger_efficiency," +
                " o.seen_count observed_seen_count, f.attribute fep_attribute," +
                " f.amount fep_amount, f.normalized_amount fep_normalized_amount" +
                " FROM scores s JOIN cookbook_observations o ON o.recipe_id = s.recipe_id" +
                " LEFT JOIN cookbook_feps f ON f.observation_id = o.id" +
                " ORDER BY CASE WHEN s.latest_normalized_hunger > 0 THEN" +
                " s.target_fep / s.latest_normalized_hunger ELSE s.target_fep END DESC," +
                " s.target_fep DESC, s.item_name COLLATE NOCASE," +
                " o.last_seen DESC, o.id DESC," +
                " CASE WHEN lower(f.attribute) LIKE ? THEN 0 ELSE 1 END," +
                " f.attribute COLLATE NOCASE";
        String normalizedSearch = (search == null) ? "" : search.trim().toLowerCase(Locale.ROOT);
        String searchPattern = "%" + normalizedSearch + "%";
        String attributePattern = attribute.toLowerCase(Locale.ROOT) + "%";
        Map<Long, EntryBuilder> builders = new LinkedHashMap<>();
        try(Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, attributePattern);
            statement.setString(2, worldId);
            statement.setString(3, normalizedSearch);
            statement.setString(4, searchPattern);
            statement.setString(5, searchPattern);
            statement.setString(6, searchPattern);
            statement.setString(7, attributePattern);
            try(ResultSet result = statement.executeQuery()) {
                while(result.next()) {
                    long recipeId = result.getLong("recipe_id");
                    EntryBuilder entry = builders.get(recipeId);
                    if(entry == null) {
                        entry = new EntryBuilder(recipeId, result);
                        builders.put(recipeId, entry);
                    }
                    entry.add(result);
                }
            }
            hydrateRecipeDisplays(connection, worldId, builders);
        }
        List<CookbookEntry> entries = new ArrayList<>(builders.size());
        for(EntryBuilder builder : builders.values())
            entries.add(builder.build(attribute));
        return(entries);
    }

    List<CookbookIngredientEntry> listIngredients(String worldId,
                                                  CookbookIngredientCategory selectedCategory,
                                                  String search) throws SQLException {
        String sql = "WITH latest AS (" +
                " SELECT o.* FROM cookbook_observations o WHERE o.id = (" +
                "  SELECT o2.id FROM cookbook_observations o2 WHERE o2.recipe_id = o.recipe_id" +
                "  ORDER BY o2.last_seen DESC, o2.id DESC LIMIT 1))" +
                " SELECT r.id recipe_id, r.item_name, r.resource_name, r.modifiers," +
                " i.kind ingredient_kind, i.name ingredient_name, i.percentage ingredient_percentage," +
                " f.attribute fep_attribute, f.normalized_amount fep_amount" +
                " FROM cookbook_recipes r JOIN latest o ON o.recipe_id = r.id" +
                " LEFT JOIN cookbook_ingredients i ON i.recipe_id = r.id" +
                " LEFT JOIN cookbook_feps f ON f.observation_id = o.id" +
                " WHERE r.world_id = ? AND" + SHADOWED_PLACEHOLDER_FILTER +
                " ORDER BY r.id, i.name COLLATE NOCASE, f.attribute COLLATE NOCASE";
        Map<Long, RecipeProfile> profiles = new LinkedHashMap<>();
        Map<String, String> itemResources;
        try(Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, worldId);
            try(ResultSet result = statement.executeQuery()) {
                while(result.next()) {
                    long recipeId = result.getLong("recipe_id");
                    RecipeProfile profile = profiles.get(recipeId);
                    if(profile == null) {
                        profile = new RecipeProfile(recipeId, result.getString("item_name"),
                                result.getString("resource_name"), result.getString("modifiers"));
                        profiles.put(recipeId, profile);
                    }
                    String ingredientName = result.getString("ingredient_name");
                    if(ingredientName != null) {
                        profile.addIngredient(result.getString("ingredient_kind"), ingredientName,
                                result.getDouble("ingredient_percentage"));
                    }
                    String attribute = result.getString("fep_attribute");
                    if(attribute != null)
                        profile.feps.put(attribute, result.getDouble("fep_amount"));
                }
            }
            itemResources = loadItemResources(connection, worldId);
        }

        for(RecipeProfile profile : profiles.values())
            profile.addModifierIngredients();

        Map<String, IngredientBuilder> ingredients = new LinkedHashMap<>();
        for(RecipeProfile profile : profiles.values()) {
            for(IngredientSample ingredient : profile.ingredients.values()) {
                String key = CookbookIngredientCatalog.normalize(ingredient.name);
                String observedResource = itemResources.get(key);
                IngredientBuilder builder = ingredients.computeIfAbsent(key,
                        ignored -> new IngredientBuilder(ingredient.name, observedResource));
                builder.addRecipe(profile);
            }
        }

        Map<String, List<RecipeProfile>> unspiced = new HashMap<>();
        for(RecipeProfile profile : profiles.values()) {
            if(profile.spices().isEmpty())
                unspiced.computeIfAbsent(profile.baseKey(), ignored -> new ArrayList<>()).add(profile);
        }
        for(RecipeProfile profile : profiles.values()) {
            List<IngredientSample> spices = profile.spices();
            if(spices.isEmpty())
                continue;
            List<RecipeProfile> baselines = unspiced.get(profile.baseKey());
            if(baselines == null || baselines.isEmpty())
                continue;
            Map<String, Double> baselineFeps = averageFeps(baselines);
            for(IngredientSample spice : spices) {
                IngredientBuilder builder = ingredients.get(
                        CookbookIngredientCatalog.normalize(spice.name));
                if(builder != null)
                    builder.addSpiceComparison(profile.feps, baselineFeps);
            }
        }

        CookbookIngredientCategory category = selectedCategory == null ?
                CookbookIngredientCategory.ALL : selectedCategory;
        String normalizedSearch = CookbookIngredientCatalog.normalize(search);
        List<CookbookIngredientEntry> entries = new ArrayList<>();
        for(IngredientBuilder builder : ingredients.values()) {
            CookbookIngredientEntry entry = builder.build();
            if(!category.matches(entry.category))
                continue;
            if(!normalizedSearch.isEmpty() && !ingredientMatches(entry, normalizedSearch))
                continue;
            entries.add(entry);
        }
        entries.sort(Comparator.comparingInt((CookbookIngredientEntry entry) -> entry.category.ordinal())
                .thenComparing(entry -> entry.name, String.CASE_INSENSITIVE_ORDER));
        return(entries);
    }

    private static void hydrateRecipeDisplays(Connection connection, String worldId,
                                               Map<Long, EntryBuilder> builders) throws SQLException {
        if(builders.isEmpty())
            return;
        String sql = "SELECT i.recipe_id, i.kind, i.name, i.percentage, i.position " +
                "FROM cookbook_ingredients i " +
                "JOIN cookbook_recipes r ON r.id = i.recipe_id WHERE r.world_id = ? " +
                "ORDER BY i.recipe_id, COALESCE(i.position, 2147483647), i.rowid";
        Map<Long, List<IngredientSample>> ingredients = new HashMap<>();
        try(PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, worldId);
            try(ResultSet result = statement.executeQuery()) {
                while(result.next()) {
                    long recipeId = result.getLong(1);
                    if(builders.containsKey(recipeId)) {
                        ingredients.computeIfAbsent(recipeId, ignored -> new ArrayList<>()).add(
                                new IngredientSample(result.getString(2), result.getString(3),
                                        result.getDouble(4), nullableInt(result, 5)));
                    }
                }
            }
        }
        for(Map.Entry<Long, EntryBuilder> value : builders.entrySet()) {
            List<String> regular = new ArrayList<>();
            List<String> modifiers = splitModifiers(value.getValue().modifiers);
            List<IngredientSample> ordered = new ArrayList<>(ingredients.getOrDefault(
                    value.getKey(), Collections.emptyList()));
            ordered.sort(Comparator
                    .comparingInt((IngredientSample ingredient) ->
                            CookbookIngredientOrder.mainPriority(value.getValue().itemName,
                                    ingredient.name))
                    .thenComparingInt(ingredient -> ingredient.position)
                    .thenComparing(ingredient -> ingredient.name,
                            String.CASE_INSENSITIVE_ORDER));
            for(IngredientSample ingredient : ordered) {
                String display = String.format(Locale.ROOT, "%s %.2f%%", ingredient.name,
                        ingredient.percentage);
                if(CookbookIngredientCatalog.isSpice(ingredient.name)) {
                    modifiers.add(display);
                } else {
                    String prefix = ingredient.kind.equals("smoke") ? "Smoke: " : "";
                    regular.add(prefix + display);
                }
            }
            modifiers = distinctSorted(modifiers);
            value.getValue().ingredients = String.join("  •  ", regular);
            value.getValue().modifiers = String.join(", ", modifiers);
        }
    }

    private static Map<String, String> loadItemResources(Connection connection, String worldId)
            throws SQLException {
        Map<String, String> resources = new HashMap<>();
        try(PreparedStatement statement = connection.prepareStatement(
                "SELECT normalized_name, resource_name FROM cookbook_item_resources WHERE world_id = ?")) {
            statement.setString(1, worldId);
            try(ResultSet result = statement.executeQuery()) {
                while(result.next())
                    resources.put(result.getString(1), result.getString(2));
            }
        }
        return(resources);
    }

    private static boolean ingredientMatches(CookbookIngredientEntry entry, String search) {
        if(CookbookIngredientCatalog.normalize(entry.name).contains(search) ||
                CookbookIngredientCatalog.normalize(entry.category.label).contains(search))
            return(true);
        for(String recipe : entry.recipes) {
            if(CookbookIngredientCatalog.normalize(recipe).contains(search))
                return(true);
        }
        return(false);
    }

    private static Map<String, Double> averageFeps(List<RecipeProfile> profiles) {
        Map<String, Double> totals = new HashMap<>();
        for(RecipeProfile profile : profiles) {
            for(Map.Entry<String, Double> fep : profile.feps.entrySet())
                totals.merge(fep.getKey(), fep.getValue(), Double::sum);
        }
        for(Map.Entry<String, Double> total : totals.entrySet())
            total.setValue(total.getValue() / profiles.size());
        return(totals);
    }

    private static List<String> splitModifiers(String modifiers) {
        List<String> values = new ArrayList<>();
        if(modifiers == null || modifiers.isBlank())
            return(values);
        for(String modifier : modifiers.split(",\\s*")) {
            if(!modifier.isBlank())
                values.add(modifier.trim());
        }
        return(values);
    }

    private static List<String> distinctSorted(List<String> values) {
        Set<String> normalized = new HashSet<>();
        List<String> result = new ArrayList<>();
        for(String value : values) {
            if(normalized.add(CookbookIngredientCatalog.normalize(value)))
                result.add(value);
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return(result);
    }

    private static final class EntryBuilder {
        final long recipeId;
        final String itemName;
        final String resourceName;
        String ingredients;
        String modifiers;
        final long latestObservationId;
        final double latestQuality;
        final double latestEnergyPercent;
        final double latestNormalizedHunger;
        final double targetFep;
        final double totalFep;
        final Map<Long, ObservationBuilder> observations = new LinkedHashMap<>();

        EntryBuilder(long recipeId, ResultSet result) throws SQLException {
            this.recipeId = recipeId;
            this.itemName = result.getString("item_name");
            this.resourceName = result.getString("resource_name");
            this.ingredients = result.getString("ingredients_summary");
            this.modifiers = result.getString("modifiers");
            this.latestObservationId = result.getLong("latest_observation_id");
            this.latestQuality = result.getDouble("latest_quality");
            this.latestEnergyPercent = result.getDouble("latest_energy_percent");
            this.latestNormalizedHunger = result.getDouble("latest_normalized_hunger");
            this.targetFep = result.getDouble("target_fep");
            this.totalFep = result.getDouble("total_fep");
        }

        void add(ResultSet result) throws SQLException {
            long observationId = result.getLong("observation_id");
            ObservationBuilder observation = observations.get(observationId);
            if(observation == null) {
                observation = new ObservationBuilder(observationId, result);
                observations.put(observationId, observation);
            }
            String attribute = result.getString("fep_attribute");
            if(attribute != null) {
                observation.feps.add(new CookbookEntry.FepValue(attribute,
                        result.getDouble("fep_amount"), result.getDouble("fep_normalized_amount")));
            }
        }

        CookbookEntry build(String selectedAttribute) {
            List<CookbookEntry.Observation> values = new ArrayList<>();
            for(ObservationBuilder observation : observations.values())
                values.add(observation.build(selectedAttribute));
            return(new CookbookEntry(recipeId, itemName, resourceName, ingredients, modifiers,
                    latestQuality, latestEnergyPercent, latestNormalizedHunger, targetFep,
                    totalFep, latestObservationId, values));
        }
    }

    private static final class IngredientSample {
        final String kind;
        final String name;
        final double percentage;
        final int position;

        IngredientSample(String kind, String name, double percentage) {
            this(kind, name, percentage, Integer.MAX_VALUE);
        }

        IngredientSample(String kind, String name, double percentage, int position) {
            this.kind = kind == null ? "ingredient" : kind;
            this.name = name;
            this.percentage = percentage;
            this.position = position;
        }

        String key() {
            return(kind + "|" + CookbookIngredientCatalog.normalize(name) + "|" +
                    Math.round(percentage * 100d));
        }
    }

    private static final class RecipeProfile {
        final long recipeId;
        final String itemName;
        final String resourceName;
        final String modifiers;
        final Map<String, IngredientSample> ingredients = new LinkedHashMap<>();
        final Map<String, Double> feps = new LinkedHashMap<>();

        RecipeProfile(long recipeId, String itemName, String resourceName, String modifiers) {
            this.recipeId = recipeId;
            this.itemName = itemName;
            this.resourceName = resourceName;
            this.modifiers = modifiers;
        }

        void addIngredient(String kind, String name, double percentage) {
            IngredientSample value = new IngredientSample(kind, name, percentage);
            ingredients.putIfAbsent(value.key(), value);
        }

        void addModifierIngredients() {
            for(String modifier : splitModifiers(modifiers)) {
                String ingredientName = CookbookIngredientCatalog.modifierIngredient(modifier);
                if(ingredientName != null)
                    addIngredient("modifier", ingredientName, 100d);
            }
        }

        List<IngredientSample> spices() {
            List<IngredientSample> values = new ArrayList<>();
            for(IngredientSample ingredient : ingredients.values()) {
                if(CookbookIngredientCatalog.isSpice(ingredient.name))
                    values.add(ingredient);
            }
            return(values);
        }

        String baseKey() {
            List<String> values = new ArrayList<>();
            for(IngredientSample ingredient : ingredients.values()) {
                if(!CookbookIngredientCatalog.isSpice(ingredient.name))
                    values.add(ingredient.key());
            }
            values.sort(String.CASE_INSENSITIVE_ORDER);
            return(CookbookIngredientCatalog.normalize(resourceName) + "|" +
                    CookbookIngredientCatalog.normalize(itemName) + "|" + String.join(";", values));
        }
    }

    private static final class IngredientBuilder {
        final String name;
        final CookbookIngredientCategory category;
        final String resourceName;
        final Set<Long> recipeIds = new LinkedHashSet<>();
        final Set<String> recipeNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        final Map<String, Double> fepTotals = new HashMap<>();
        final List<CookbookIngredientEntry.RecipeHighlight> recipeHighlights = new ArrayList<>();
        final Map<String, Double> boostTotals = new HashMap<>();
        final Map<String, Double> boostPercentTotals = new HashMap<>();
        final Map<String, Integer> boostPercentCounts = new HashMap<>();
        int spiceComparisons;

        IngredientBuilder(String name, String observedResource) {
            this.name = name;
            this.category = CookbookIngredientCatalog.category(name, observedResource);
            this.resourceName = CookbookIngredientCatalog.iconResource(name, observedResource);
        }

        void addRecipe(RecipeProfile profile) {
            if(!recipeIds.add(profile.recipeId))
                return;
            recipeNames.add(profile.itemName);
            for(Map.Entry<String, Double> fep : profile.feps.entrySet())
                fepTotals.merge(fep.getKey(), fep.getValue(), Double::sum);
            CookbookRecipeStat strongest = CookbookRecipeStat.strongest(profile.feps);
            if(strongest.attribute != null) {
                recipeHighlights.add(new CookbookIngredientEntry.RecipeHighlight(
                        profile.recipeId, profile.itemName, profile.resourceName,
                        strongest.attribute.label, strongest.amount));
            }
        }

        void addSpiceComparison(Map<String, Double> spiced, Map<String, Double> baseline) {
            spiceComparisons++;
            Set<String> attributes = new HashSet<>(spiced.keySet());
            attributes.addAll(baseline.keySet());
            for(String attribute : attributes) {
                double base = baseline.getOrDefault(attribute, 0d);
                double delta = spiced.getOrDefault(attribute, 0d) - base;
                boostTotals.merge(attribute, delta, Double::sum);
                if(Math.abs(base) > 0.00001d) {
                    boostPercentTotals.merge(attribute, (delta / base) * 100d, Double::sum);
                    boostPercentCounts.merge(attribute, 1, Integer::sum);
                }
            }
        }

        CookbookIngredientEntry build() {
            List<CookbookIngredientEntry.AttributeValue> average = new ArrayList<>();
            for(Map.Entry<String, Double> fep : fepTotals.entrySet()) {
                average.add(new CookbookIngredientEntry.AttributeValue(fep.getKey(),
                        fep.getValue() / Math.max(1, recipeIds.size())));
            }
            average.sort(Comparator.comparingDouble(
                            (CookbookIngredientEntry.AttributeValue value) -> value.amount).reversed()
                    .thenComparing(value -> value.attribute, String.CASE_INSENSITIVE_ORDER));
            recipeHighlights.sort(Comparator.comparingDouble(
                            (CookbookIngredientEntry.RecipeHighlight value) -> value.amount)
                    .reversed()
                    .thenComparing(value -> value.foodName, String.CASE_INSENSITIVE_ORDER));

            List<CookbookIngredientEntry.SpiceBoost> boosts = new ArrayList<>();
            if(spiceComparisons > 0) {
                for(Map.Entry<String, Double> boost : boostTotals.entrySet()) {
                    double amount = boost.getValue() / spiceComparisons;
                    if(Math.abs(amount) < 0.005d)
                        continue;
                    int percentCount = boostPercentCounts.getOrDefault(boost.getKey(), 0);
                    double percent = percentCount == 0 ? Double.NaN :
                            boostPercentTotals.getOrDefault(boost.getKey(), 0d) / percentCount;
                    boosts.add(new CookbookIngredientEntry.SpiceBoost(boost.getKey(), amount, percent));
                }
                boosts.sort(Comparator.comparingDouble(
                                (CookbookIngredientEntry.SpiceBoost value) -> Math.abs(value.amount)).reversed()
                        .thenComparing(value -> value.attribute, String.CASE_INSENSITIVE_ORDER));
            }
            return(new CookbookIngredientEntry(name, category, resourceName, recipeIds.size(),
                    new ArrayList<>(recipeNames), average, recipeHighlights, boosts,
                    spiceComparisons));
        }
    }

    private static final class ObservationBuilder {
        final long id;
        final long lastSeen;
        final double quality;
        final double energyPercent;
        final double hungerPermille;
        final double normalizedHungerPermille;
        final double capturedFepEfficiencyPercent;
        final double capturedHungerEfficiencyPercent;
        final int seenCount;
        final List<CookbookEntry.FepValue> feps = new ArrayList<>();

        ObservationBuilder(long id, ResultSet result) throws SQLException {
            this.id = id;
            this.lastSeen = result.getLong("observed_last_seen");
            this.quality = result.getDouble("observed_quality");
            this.energyPercent = result.getDouble("observed_energy_percent");
            this.hungerPermille = result.getDouble("observed_hunger_permille");
            this.normalizedHungerPermille = result.getDouble("observed_normalized_hunger");
            this.capturedFepEfficiencyPercent = nullableDouble(result,
                    "observed_fep_efficiency");
            this.capturedHungerEfficiencyPercent = nullableDouble(result,
                    "observed_hunger_efficiency");
            this.seenCount = result.getInt("observed_seen_count");
        }

        CookbookEntry.Observation build(String selectedAttribute) {
            String normalized = selectedAttribute.toLowerCase(Locale.ROOT);
            feps.sort(Comparator.comparingInt((CookbookEntry.FepValue value) ->
                            value.attribute.toLowerCase(Locale.ROOT).startsWith(normalized) ? 0 : 1)
                    .thenComparing(value -> value.attribute, String.CASE_INSENSITIVE_ORDER));
            return(new CookbookEntry.Observation(id, lastSeen, quality, energyPercent,
                    hungerPermille, normalizedHungerPermille, capturedFepEfficiencyPercent,
                    capturedHungerEfficiencyPercent, seenCount, feps));
        }
    }

    private static double nullableDouble(ResultSet result, String column) throws SQLException {
        double value = result.getDouble(column);
        return(result.wasNull() ? Double.NaN : value);
    }

    private static void ensureColumn(Connection connection, String table, String column,
                                     String declaration) throws SQLException {
        boolean present = false;
        try(Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while(result.next()) {
                if(column.equalsIgnoreCase(result.getString("name"))) {
                    present = true;
                    break;
                }
            }
        }
        if(!present) {
            try(Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " +
                        declaration);
            }
        }
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(databaseUrl);
        try(Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
        }
        return(connection);
    }

    private static long findRecipe(Connection connection, String recipeKey) throws SQLException {
        try(PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM cookbook_recipes WHERE recipe_key = ?")) {
            statement.setString(1, recipeKey);
            try(ResultSet result = statement.executeQuery()) {
                return(result.next() ? result.getLong(1) : -1);
            }
        }
    }

    private static long insertRecipe(Connection connection, CookbookFood food) throws SQLException {
        String sql = "INSERT INTO cookbook_recipes(world_id, recipe_key, resource_name, item_name, " +
                "ingredients_summary, modifiers, first_seen, last_seen) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try(PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, food.worldId);
            statement.setString(2, food.recipeKey);
            statement.setString(3, food.resourceName);
            statement.setString(4, food.itemName);
            statement.setString(5, food.ingredientSummary());
            statement.setString(6, food.modifierSummary());
            statement.setLong(7, food.observedAt);
            statement.setLong(8, food.observedAt);
            statement.executeUpdate();
            try(ResultSet keys = statement.getGeneratedKeys()) {
                if(keys.next())
                    return(keys.getLong(1));
            }
        }
        throw(new SQLException("Could not create cookbook recipe"));
    }

    private static void updateRecipe(Connection connection, long recipeId, CookbookFood food) throws SQLException {
        try(PreparedStatement statement = connection.prepareStatement(
                "UPDATE cookbook_recipes SET item_name = ?, ingredients_summary = ?, modifiers = ?, last_seen = ? WHERE id = ?")) {
            statement.setString(1, food.itemName);
            statement.setString(2, food.ingredientSummary());
            statement.setString(3, food.modifierSummary());
            statement.setLong(4, food.observedAt);
            statement.setLong(5, recipeId);
            statement.executeUpdate();
        }
    }

    private static boolean replaceIngredients(Connection connection, long recipeId,
                                              List<CookbookFood.Ingredient> ingredients) throws SQLException {
        List<StoredIngredient> stored = new ArrayList<>();
        try(PreparedStatement statement = connection.prepareStatement(
                "SELECT kind, name, percentage, position FROM cookbook_ingredients " +
                        "WHERE recipe_id = ? ORDER BY COALESCE(position, 2147483647), rowid")) {
            statement.setLong(1, recipeId);
            try(ResultSet result = statement.executeQuery()) {
                while(result.next())
                    stored.add(new StoredIngredient(result.getString(1), result.getString(2),
                            result.getDouble(3), nullableInt(result, 4)));
            }
        }
        boolean changed = stored.size() != ingredients.size();
        for(int index = 0; !changed && index < ingredients.size(); index++)
            changed = !stored.get(index).matches(ingredients.get(index), index);
        if(!changed)
            return(false);

        try(PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM cookbook_ingredients WHERE recipe_id = ?")) {
            statement.setLong(1, recipeId);
            statement.executeUpdate();
        }
        insertIngredients(connection, recipeId, ingredients);
        return(true);
    }

    private static void insertIngredients(Connection connection, long recipeId,
                                          List<CookbookFood.Ingredient> ingredients) throws SQLException {
        String sql = "INSERT OR IGNORE INTO cookbook_ingredients" +
                "(recipe_id, kind, name, percentage, position) VALUES (?, ?, ?, ?, ?)";
        try(PreparedStatement statement = connection.prepareStatement(sql)) {
            for(int position = 0; position < ingredients.size(); position++) {
                CookbookFood.Ingredient ingredient = ingredients.get(position);
                statement.setLong(1, recipeId);
                statement.setString(2, ingredient.kind);
                statement.setString(3, ingredient.name);
                statement.setDouble(4, ingredient.percentage);
                statement.setInt(5, position);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static int nullableInt(ResultSet result, int column) throws SQLException {
        int value = result.getInt(column);
        return(result.wasNull() ? Integer.MAX_VALUE : value);
    }

    private static final class StoredIngredient {
        final String kind;
        final String name;
        final double percentage;
        final int position;

        StoredIngredient(String kind, String name, double percentage, int position) {
            this.kind = kind;
            this.name = name;
            this.percentage = percentage;
            this.position = position;
        }

        boolean matches(CookbookFood.Ingredient ingredient, int expectedPosition) {
            return(kind.equals(ingredient.kind) && name.equals(ingredient.name) &&
                    Double.compare(percentage, ingredient.percentage) == 0 &&
                    position == expectedPosition);
        }
    }

    private static long findObservation(Connection connection, String observationKey) throws SQLException {
        try(PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM cookbook_observations WHERE observation_key = ?")) {
            statement.setString(1, observationKey);
            try(ResultSet result = statement.executeQuery()) {
                return(result.next() ? result.getLong(1) : -1);
            }
        }
    }

    private static long insertObservation(Connection connection, long recipeId,
                                          CookbookFood food) throws SQLException {
        String sql = "INSERT INTO cookbook_observations(recipe_id, observation_key, character_id, quality, " +
                "energy_percent, hunger_permille, normalized_hunger_permille, " +
                "captured_fep_efficiency_percent, captured_hunger_efficiency_percent, " +
                "first_seen, last_seen, seen_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)";
        try(PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, recipeId);
            statement.setString(2, food.observationKey);
            statement.setString(3, food.characterId);
            statement.setDouble(4, food.quality);
            statement.setDouble(5, food.energyPercent);
            statement.setDouble(6, food.hungerPermille);
            statement.setDouble(7, food.normalizedHungerPermille);
            setNullableDouble(statement, 8, food.capturedFepEfficiencyPercent);
            setNullableDouble(statement, 9, food.capturedHungerEfficiencyPercent);
            statement.setLong(10, food.observedAt);
            statement.setLong(11, food.observedAt);
            statement.executeUpdate();
            try(ResultSet keys = statement.getGeneratedKeys()) {
                if(keys.next())
                    return(keys.getLong(1));
            }
        }
        throw(new SQLException("Could not create cookbook observation"));
    }

    private static boolean touchObservation(Connection connection, long observationId,
                                            CookbookFood food) throws SQLException {
        double previousFep = Double.NaN;
        double previousHunger = Double.NaN;
        try(PreparedStatement statement = connection.prepareStatement(
                "SELECT captured_fep_efficiency_percent, captured_hunger_efficiency_percent " +
                        "FROM cookbook_observations WHERE id = ?")) {
            statement.setLong(1, observationId);
            try(ResultSet result = statement.executeQuery()) {
                if(result.next()) {
                    previousFep = nullableDouble(result, "captured_fep_efficiency_percent");
                    previousHunger = nullableDouble(result,
                            "captured_hunger_efficiency_percent");
                }
            }
        }
        try(PreparedStatement statement = connection.prepareStatement(
                "UPDATE cookbook_observations SET last_seen = ?, seen_count = seen_count + 1, " +
                        "captured_fep_efficiency_percent = ?, " +
                        "captured_hunger_efficiency_percent = ? WHERE id = ?")) {
            statement.setLong(1, food.observedAt);
            setNullableDouble(statement, 2, food.capturedFepEfficiencyPercent);
            setNullableDouble(statement, 3, food.capturedHungerEfficiencyPercent);
            statement.setLong(4, observationId);
            statement.executeUpdate();
        }
        return(!sameNumber(previousFep, food.capturedFepEfficiencyPercent) ||
                !sameNumber(previousHunger, food.capturedHungerEfficiencyPercent));
    }

    private static boolean sameNumber(double left, double right) {
        return((Double.isNaN(left) && Double.isNaN(right)) || Double.compare(left, right) == 0);
    }

    private static void setNullableDouble(PreparedStatement statement, int index, double value)
            throws SQLException {
        if(Double.isFinite(value))
            statement.setDouble(index, value);
        else
            statement.setNull(index, java.sql.Types.REAL);
    }

    private static void insertFeps(Connection connection, long observationId,
                                   List<CookbookFood.Fep> feps) throws SQLException {
        String sql = "INSERT INTO cookbook_feps(observation_id, attribute, amount, normalized_amount) VALUES (?, ?, ?, ?)";
        try(PreparedStatement statement = connection.prepareStatement(sql)) {
            for(CookbookFood.Fep fep : feps) {
                statement.setLong(1, observationId);
                statement.setString(2, fep.attribute);
                statement.setDouble(3, fep.amount);
                statement.setDouble(4, fep.normalizedAmount);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
