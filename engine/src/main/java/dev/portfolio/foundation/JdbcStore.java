package dev.portfolio.foundation;

import java.sql.*;
import java.util.*;
import java.util.function.Function;

/** PostgreSQL aggregate store with prepared statements and transaction-wide serialization.
 * Data is stored per aggregate, not as one giant database blob. The global write mutex is
 * intentional for a single-workspace application; remove it only with stronger per-domain invariants.
 */
public final class JdbcStore implements Store {
    private final String url;
    private final String user;
    private final String password;
    public JdbcStore(String url, String user, String password) {
        if (!(url.startsWith("jdbc:postgresql:") || url.startsWith("jdbc:h2:"))) throw new IllegalArgumentException("Only PostgreSQL and test H2 JDBC URLs are supported");
        this.url = url;
        this.user = user;
        this.password = password;
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS workspace_mutex (id INTEGER PRIMARY KEY)");
            try {
                s.executeUpdate("INSERT INTO workspace_mutex(id) VALUES(1)");
            } catch (SQLException e) {
                if (!"23505".equals(e.getSQLState())) throw e;
            }
            s.execute("CREATE TABLE IF NOT EXISTS workspace_records (bucket VARCHAR(96) NOT NULL, record_id VARCHAR(96) NOT NULL, payload TEXT NOT NULL, revision BIGINT NOT NULL DEFAULT 1, updated_at VARCHAR(40) NOT NULL, PRIMARY KEY(bucket, record_id))");
            s.execute("CREATE INDEX IF NOT EXISTS workspace_updated ON workspace_records(bucket, updated_at)");
        } catch (SQLException e) {
            throw new IllegalStateException("Database initialization failed", e);
        }
    }
    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
    @Override public String mode() {
        return "jdbc";
    }
    @Override public <T> T read(Function<Tx, T> work) {
        return transact(work, false);
    }
    @Override public <T> T write(Function<Tx, T> work) {
        return transact(work, true);
    }
    private <T> T transact(Function<Tx, T> work, boolean writable) {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            c.setTransactionIsolation(writable ? Connection.TRANSACTION_READ_COMMITTED : Connection.TRANSACTION_REPEATABLE_READ);
            if (!writable) c.setReadOnly(true);
            try {
                if (writable) try (PreparedStatement lock = c.prepareStatement("SELECT id FROM workspace_mutex WHERE id=1 FOR UPDATE")) {
                    lock.executeQuery().close();
                }
                T result = work.apply(new View(c, writable));
                c.commit();
                return result;
            } catch (RuntimeException | SQLException e) {
                c.rollback();
                if (e instanceof RuntimeException runtime) throw runtime;
                throw new IllegalStateException("Database transaction failed", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Database unavailable", e);
        }
    }
    private record View(Connection connection, boolean writable) implements Tx {
        public Optional<Map<String, Object>> get(String bucket, String id) {
            Checks.id(bucket);
            Checks.id(id);
            try (PreparedStatement s = connection.prepareStatement("SELECT payload FROM workspace_records WHERE bucket=? AND record_id=?")) {
                s.setString(1, bucket);
                s.setString(2, id);
                try (ResultSet rows = s.executeQuery()) {
                    return rows.next() ? Optional.of(Json.object(Json.parse(rows.getString(1)))) : Optional.empty();
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Read failed", e);
            }
        }
        public List<Map<String, Object>> scan(String bucket) {
            Checks.id(bucket);
            try (PreparedStatement s = connection.prepareStatement("SELECT payload FROM workspace_records WHERE bucket=? ORDER BY updated_at,record_id")) {
                s.setString(1, bucket);
                List<Map<String, Object>> result = new ArrayList<>();
                try (ResultSet rows = s.executeQuery()) {
                    while (rows.next()) result.add(Json.object(Json.parse(rows.getString(1))));
                }
                return result;
            } catch (SQLException e) {
                throw new IllegalStateException("Scan failed", e);
            }
        }
        public void put(String bucket, String id, Map<String, Object> record) {
            if (!writable) throw new IllegalStateException("Read-only transaction");
            Checks.id(bucket);
            Checks.id(id);
            try (PreparedStatement update = connection.prepareStatement("UPDATE workspace_records SET payload=?,revision=revision+1,updated_at=? WHERE bucket=? AND record_id=?")) {
                update.setString(1, Json.write(record));
                update.setString(2, Checks.now());
                update.setString(3, bucket);
                update.setString(4, id);
                if (update.executeUpdate() == 0) try (PreparedStatement insert = connection.prepareStatement("INSERT INTO workspace_records(bucket,record_id,payload,revision,updated_at) VALUES(?,?,?,1,?)")) {
                    insert.setString(1, bucket);
                    insert.setString(2, id);
                    insert.setString(3, Json.write(record));
                    insert.setString(4, Checks.now());
                    insert.executeUpdate();
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Write failed", e);
            }
        }
        public void delete(String bucket, String id) {
            if (!writable) throw new IllegalStateException("Read-only transaction");
            try (PreparedStatement s = connection.prepareStatement("DELETE FROM workspace_records WHERE bucket=? AND record_id=?")) {
                s.setString(1, bucket);
                s.setString(2, id);
                s.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("Delete failed", e);
            }
        }
    }
}
