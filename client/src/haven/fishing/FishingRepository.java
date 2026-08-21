package haven.fishing;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Owns the append-only local fishing observation schema. */
final class FishingRepository {
    private static final int SCHEMA_VERSION = 1;
    private final String databaseUrl;

    FishingRepository(String databaseUrl) {
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
            statement.execute("CREATE TABLE IF NOT EXISTS fishing_meta (" +
                    "key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)");
            statement.execute("INSERT OR IGNORE INTO fishing_meta(key, value) VALUES " +
                    "('schema_version', '" + SCHEMA_VERSION + "')");
            statement.execute("CREATE TABLE IF NOT EXISTS fishing_observations (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, world_id TEXT NOT NULL, " +
                    "segment_id INTEGER NOT NULL, grid_id INTEGER NOT NULL, " +
                    "grid_offset_x REAL NOT NULL, grid_offset_y REAL NOT NULL, " +
                    "cast_x REAL NOT NULL, cast_y REAL NOT NULL, " +
                    "player_x REAL NOT NULL, player_y REAL NOT NULL, " +
                    "water_resource TEXT NOT NULL, observed_at INTEGER NOT NULL, " +
                    "game_time_seconds INTEGER NOT NULL, game_day INTEGER NOT NULL, " +
                    "game_second_of_day INTEGER NOT NULL, night INTEGER NOT NULL, " +
                    "moon_phase TEXT NOT NULL, season TEXT NOT NULL, " +
                    "fish_resource TEXT NOT NULL, fish_name TEXT NOT NULL, fish_quality REAL, " +
                    "pole_resource TEXT NOT NULL, pole_name TEXT NOT NULL, pole_quality REAL, " +
                    "line_resource TEXT NOT NULL, line_name TEXT NOT NULL, line_quality REAL, " +
                    "hook_resource TEXT NOT NULL, hook_name TEXT NOT NULL, hook_quality REAL, " +
                    "consumable_kind TEXT NOT NULL, consumable_resource TEXT NOT NULL, " +
                    "consumable_name TEXT NOT NULL, consumable_quality REAL, " +
                    "choice_rows_json TEXT NOT NULL, survival INTEGER, will INTEGER, " +
                    "outcome TEXT NOT NULL, confidence TEXT NOT NULL, schema_version INTEGER NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS fishing_recent " +
                    "ON fishing_observations(world_id, observed_at DESC)");
            statement.execute("CREATE INDEX IF NOT EXISTS fishing_spot_fish " +
                    "ON fishing_observations(world_id, segment_id, grid_id, fish_resource)");
        }
    }

    long save(FishingObservation observation) throws SQLException {
        String sql = "INSERT INTO fishing_observations(" +
                "world_id, segment_id, grid_id, grid_offset_x, grid_offset_y, cast_x, cast_y, " +
                "player_x, player_y, water_resource, observed_at, game_time_seconds, game_day, " +
                "game_second_of_day, night, moon_phase, season, fish_resource, fish_name, fish_quality, " +
                "pole_resource, pole_name, pole_quality, line_resource, line_name, line_quality, " +
                "hook_resource, hook_name, hook_quality, consumable_kind, consumable_resource, " +
                "consumable_name, consumable_quality, choice_rows_json, survival, will, outcome, " +
                "confidence, schema_version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?," +
                "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try(Connection connection = connect();
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int index = 1;
            statement.setString(index++, observation.worldId);
            statement.setLong(index++, observation.segmentId);
            statement.setLong(index++, observation.gridId);
            statement.setDouble(index++, observation.gridOffsetX);
            statement.setDouble(index++, observation.gridOffsetY);
            statement.setDouble(index++, observation.castX);
            statement.setDouble(index++, observation.castY);
            statement.setDouble(index++, observation.playerX);
            statement.setDouble(index++, observation.playerY);
            statement.setString(index++, observation.waterResource);
            statement.setLong(index++, observation.observedAt);
            statement.setLong(index++, observation.gameTimeSeconds);
            statement.setInt(index++, observation.gameDay);
            statement.setInt(index++, observation.gameSecondOfDay);
            statement.setInt(index++, observation.night ? 1 : 0);
            statement.setString(index++, observation.moonPhase);
            statement.setString(index++, observation.season);
            statement.setString(index++, observation.fishResource);
            statement.setString(index++, observation.fishName);
            setNullableDouble(statement, index++, observation.fishQuality);
            statement.setString(index++, observation.poleResource);
            statement.setString(index++, observation.poleName);
            setNullableDouble(statement, index++, observation.poleQuality);
            statement.setString(index++, observation.lineResource);
            statement.setString(index++, observation.lineName);
            setNullableDouble(statement, index++, observation.lineQuality);
            statement.setString(index++, observation.hookResource);
            statement.setString(index++, observation.hookName);
            setNullableDouble(statement, index++, observation.hookQuality);
            statement.setString(index++, observation.consumableKind);
            statement.setString(index++, observation.consumableResource);
            statement.setString(index++, observation.consumableName);
            setNullableDouble(statement, index++, observation.consumableQuality);
            statement.setString(index++, observation.choiceRowsJson);
            setNullableInteger(statement, index++, observation.survival);
            setNullableInteger(statement, index++, observation.will);
            statement.setString(index++, observation.outcome);
            statement.setString(index++, observation.confidence);
            statement.setInt(index, observation.schemaVersion);
            statement.executeUpdate();
            try(ResultSet keys = statement.getGeneratedKeys()) {
                if(keys.next())
                    return(keys.getLong(1));
            }
        }
        throw(new SQLException("Could not create fishing observation"));
    }

    List<FishingObservation> recent(String worldId, int limit) throws SQLException {
        List<FishingObservation> observations = new ArrayList<>();
        try(Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM fishing_observations WHERE world_id = ? " +
                        "ORDER BY observed_at DESC, id DESC LIMIT ?")) {
            statement.setString(1, worldId == null ? "" : worldId);
            statement.setInt(2, Math.max(1, limit));
            try(ResultSet result = statement.executeQuery()) {
                while(result.next())
                    observations.add(read(result));
            }
        }
        return(observations);
    }

    List<FishingObservation> spot(String worldId, long gridId, double minX, double maxX,
                                  double minY, double maxY) throws SQLException {
        List<FishingObservation> observations = new ArrayList<>();
        try(Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM fishing_observations WHERE world_id = ? AND grid_id = ? " +
                        "AND grid_offset_x >= ? AND grid_offset_x < ? " +
                        "AND grid_offset_y >= ? AND grid_offset_y < ? " +
                        "ORDER BY observed_at DESC, id DESC")) {
            statement.setString(1, worldId == null ? "" : worldId);
            statement.setLong(2, gridId);
            statement.setDouble(3, minX);
            statement.setDouble(4, maxX);
            statement.setDouble(5, minY);
            statement.setDouble(6, maxY);
            try(ResultSet result = statement.executeQuery()) {
                while(result.next())
                    observations.add(read(result));
            }
        }
        return(observations);
    }

    int count(String worldId) throws SQLException {
        try(Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM fishing_observations WHERE world_id = ?")) {
            statement.setString(1, worldId == null ? "" : worldId);
            try(ResultSet result = statement.executeQuery()) {
                return(result.next() ? result.getInt(1) : 0);
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

    private static FishingObservation read(ResultSet result) throws SQLException {
        return(new FishingObservation.Builder()
                .id(result.getLong("id"))
                .worldId(result.getString("world_id"))
                .location(result.getLong("segment_id"), result.getLong("grid_id"),
                        result.getDouble("grid_offset_x"), result.getDouble("grid_offset_y"),
                        result.getDouble("cast_x"), result.getDouble("cast_y"),
                        result.getDouble("player_x"), result.getDouble("player_y"),
                        result.getString("water_resource"))
                .observedAt(result.getLong("observed_at"))
                .gameTime(result.getLong("game_time_seconds"), result.getInt("game_day"),
                        result.getInt("game_second_of_day"), result.getInt("night") != 0,
                        result.getString("moon_phase"), result.getString("season"))
                .fish(result.getString("fish_resource"), result.getString("fish_name"),
                        nullableDouble(result, "fish_quality"))
                .pole(result.getString("pole_resource"), result.getString("pole_name"),
                        nullableDouble(result, "pole_quality"))
                .line(result.getString("line_resource"), result.getString("line_name"),
                        nullableDouble(result, "line_quality"))
                .hook(result.getString("hook_resource"), result.getString("hook_name"),
                        nullableDouble(result, "hook_quality"))
                .consumable(result.getString("consumable_kind"),
                        result.getString("consumable_resource"), result.getString("consumable_name"),
                        nullableDouble(result, "consumable_quality"))
                .choiceRowsJson(result.getString("choice_rows_json"))
                .stats(nullableInteger(result, "survival"), nullableInteger(result, "will"))
                .outcome(result.getString("outcome"))
                .confidence(result.getString("confidence"))
                .schemaVersion(result.getInt("schema_version"))
                .build());
    }

    private static void setNullableDouble(PreparedStatement statement, int index, Double value)
            throws SQLException {
        if(value == null)
            statement.setNull(index, Types.REAL);
        else
            statement.setDouble(index, value);
    }

    private static void setNullableInteger(PreparedStatement statement, int index, Integer value)
            throws SQLException {
        if(value == null)
            statement.setNull(index, Types.INTEGER);
        else
            statement.setInt(index, value);
    }

    private static Double nullableDouble(ResultSet result, String column) throws SQLException {
        double value = result.getDouble(column);
        return(result.wasNull() ? null : value);
    }

    private static Integer nullableInteger(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        return(result.wasNull() ? null : value);
    }
}
