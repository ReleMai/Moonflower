package haven.foraging;

import haven.ClientData;
import haven.Coord2d;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
            statement.execute("CREATE TABLE IF NOT EXISTS foraging_profile_routes (" +
                    "world_id TEXT NOT NULL, point_index INTEGER NOT NULL, x REAL NOT NULL, y REAL NOT NULL, " +
                    "PRIMARY KEY(world_id, point_index))");
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

    public List<Coord2d> loadRoute(String worldId) throws SQLException {
        List<Coord2d> result = new ArrayList<>();
        try(Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(
                "SELECT x, y FROM foraging_profile_routes WHERE world_id = ? ORDER BY point_index")) {
            statement.setString(1, clean(worldId));
            try(ResultSet rows = statement.executeQuery()) {
                while(rows.next())
                    result.add(new Coord2d(rows.getDouble(1), rows.getDouble(2)));
            }
        }
        return(result);
    }

    public void saveRoute(String worldId, List<Coord2d> route) throws SQLException {
        try(Connection connection = connect()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try(PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM foraging_profile_routes WHERE world_id = ?")) {
                    delete.setString(1, clean(worldId));
                    delete.executeUpdate();
                }
                try(PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO foraging_profile_routes(world_id, point_index, x, y) VALUES(?,?,?,?)")) {
                    for(int index = 0; index < route.size(); index++) {
                        Coord2d point = route.get(index);
                        insert.setString(1, clean(worldId));
                        insert.setInt(2, index);
                        insert.setDouble(3, point.x);
                        insert.setDouble(4, point.y);
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
            } catch(SQLException failure) {
                try {
                    connection.rollback();
                } catch(SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw(failure);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
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
