package haven.foraging;

import haven.ClientData;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;

/** Persists only world-scoped configuration and observations, never active state. */
public final class ForagingRepository implements AutoCloseable {
    private final String databaseUrl;

    public ForagingRepository() throws SQLException {
        this(ClientData.sqlite("foraging.db"));
    }

    ForagingRepository(String databaseUrl) throws SQLException {
        this.databaseUrl = databaseUrl;
        try {
            Class.forName("org.sqlite.JDBC");
        } catch(ClassNotFoundException e) {
            throw(new SQLException("SQLite JDBC driver is unavailable", e));
        }
        try(Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("CREATE TABLE IF NOT EXISTS foraging_profile_resources (" +
                    "world_id TEXT NOT NULL, resource_name TEXT NOT NULL, selected INTEGER NOT NULL, " +
                    "display_name TEXT NOT NULL, last_seen INTEGER NOT NULL, " +
                    "PRIMARY KEY(world_id, resource_name))");
            statement.execute("CREATE TABLE IF NOT EXISTS foraging_profiles (" +
                    "world_id TEXT PRIMARY KEY NOT NULL, direction TEXT NOT NULL)");
        }
    }

    public Set<String> loadSelection(String worldId) throws SQLException {
        Set<String> result = new LinkedHashSet<>();
        try(Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "SELECT resource_name FROM foraging_profile_resources " +
                        "WHERE world_id = ? AND selected = 1 ORDER BY resource_name")) {
            statement.setString(1, clean(worldId));
            try(ResultSet rows = statement.executeQuery()) {
                while(rows.next())
                    result.add(rows.getString(1));
            }
        }
        return(result);
    }

    public void observe(String worldId, ForagingGobScanner.HerbResource herb,
                        boolean selected) throws SQLException {
        try(Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO foraging_profile_resources(world_id, resource_name, selected, display_name, last_seen) " +
                        "VALUES(?,?,?,?,?) ON CONFLICT(world_id, resource_name) DO UPDATE SET " +
                        "selected=excluded.selected, display_name=excluded.display_name, last_seen=excluded.last_seen")) {
            statement.setString(1, clean(worldId));
            statement.setString(2, herb.resourceName);
            statement.setInt(3, selected ? 1 : 0);
            statement.setString(4, herb.displayName);
            statement.setLong(5, System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    public ForagingDirection loadDirection(String worldId) throws SQLException {
        try(Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "SELECT direction FROM foraging_profiles WHERE world_id = ?")) {
            statement.setString(1, clean(worldId));
            try(ResultSet rows = statement.executeQuery()) {
                return(rows.next() ? ForagingDirection.parse(rows.getString(1)) : ForagingDirection.NORTH);
            }
        }
    }

    public void saveDirection(String worldId, ForagingDirection direction) throws SQLException {
        try(Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO foraging_profiles(world_id, direction) VALUES(?,?) " +
                        "ON CONFLICT(world_id) DO UPDATE SET direction=excluded.direction")) {
            statement.setString(1, clean(worldId));
            statement.setString(2, direction.name());
            statement.executeUpdate();
        }
    }

    private Connection connect() throws SQLException {
        return(DriverManager.getConnection(databaseUrl));
    }

    private static String clean(String value) {
        return(value == null ? "" : value);
    }

    @Override
    public void close() {
    }
}
