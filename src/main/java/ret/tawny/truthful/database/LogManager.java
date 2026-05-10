package ret.tawny.truthful.database;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.PrintWriter;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public final class LogManager {

    private static final int FLUSH_BATCH_SIZE = 500;
    private static final int MAX_LOG_ROWS = 250_000;
    private static final int RETENTION_DAYS = 14;
    private static final int MAX_PENDING_QUEUE = 20_000;

    private final Plugin plugin;
    private Connection writeConnection;
    private Connection readConnection;

    private final Queue<LogEntry> pending = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingSize = new AtomicInteger();
    private final AtomicInteger droppedLogs = new AtomicInteger();
    private final ReentrantLock writeLock = new ReentrantLock();

    public LogManager(final Plugin plugin) {
        this.plugin = plugin;
        setup();
        startFlushTask();
    }

    private void setup() {
        try {
            File folder = plugin.getDataFolder();
            if (!folder.exists()) {
                //noinspection ResultOfMethodCallIgnored
                folder.mkdirs();
            }

            File dbFile = new File(folder, "logs.db");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            this.writeConnection = DriverManager.getConnection(url);
            try (Statement pragmas = writeConnection.createStatement()) {
                pragmas.execute("PRAGMA journal_mode=WAL");
                pragmas.execute("PRAGMA synchronous=NORMAL");
                pragmas.execute("PRAGMA temp_store=MEMORY");
            }

            try (Statement st = writeConnection.createStatement()) {
                st.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS logs (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                                "uuid TEXT NOT NULL," +
                                "player TEXT NOT NULL," +
                                "check_name TEXT NOT NULL," +
                                "vl INTEGER NOT NULL," +
                                "ping INTEGER NOT NULL," +
                                "data TEXT," +
                                "ts BIGINT NOT NULL" +
                                ")"
                );
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_logs_uuid_ts ON logs(uuid, ts DESC)");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_logs_ts ON logs(ts DESC)");
            }

            this.readConnection = DriverManager.getConnection(url);
            try (Statement pragmas = readConnection.createStatement()) {
                pragmas.execute("PRAGMA journal_mode=WAL");
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize LogManager database.");
            e.printStackTrace();
        }
    }

    private void startFlushTask() {
        // Batch insert asynchronously to keep main thread clean.
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (pending.isEmpty()) {
                return;
            }
            flushNow();
        }, 20L, 20L);

        // Housekeeping: keep database growth bounded on busy servers.
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::pruneOldLogs, 20L * 300L, 20L * 300L);
    }

    private void flushNow() {
        if (this.writeConnection == null) {
            return;
        }

        writeLock.lock();
        try {
            List<LogEntry> batch = new ArrayList<>();
            while (!pending.isEmpty() && batch.size() < FLUSH_BATCH_SIZE) {
                LogEntry e = pending.poll();
                if (e != null) {
                    batch.add(e);
                    pendingSize.decrementAndGet();
                }
            }
            if (batch.isEmpty()) return;

            try (PreparedStatement ps = writeConnection.prepareStatement(
                    "INSERT INTO logs (uuid, player, check_name, vl, ping, data, ts) VALUES (?,?,?,?,?,?,?)"
            )) {
                for (LogEntry e : batch) {
                    ps.setString(1, e.uuid.toString());
                    ps.setString(2, e.player);
                    ps.setString(3, e.check);
                    ps.setInt(4, e.vl);
                    ps.setLong(5, e.ping);
                    ps.setString(6, e.data);
                    ps.setLong(7, e.timestamp);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (SQLException ex) {
            plugin.getLogger().severe("Failed to flush detection logs.");
            ex.printStackTrace();
        } finally {
            writeLock.unlock();
        }
    }

    private void pruneOldLogs() {
        if (this.writeConnection == null) {
            return;
        }

        writeLock.lock();
        try {
            long cutoff = System.currentTimeMillis() - (RETENTION_DAYS * 24L * 60L * 60L * 1000L);
            try (PreparedStatement deleteOld = writeConnection.prepareStatement("DELETE FROM logs WHERE ts < ?")) {
                deleteOld.setLong(1, cutoff);
                deleteOld.executeUpdate();
            }

            try (PreparedStatement trimRows = writeConnection.prepareStatement(
                    "DELETE FROM logs WHERE id NOT IN (SELECT id FROM logs ORDER BY ts DESC LIMIT ?)")) {
                trimRows.setInt(1, MAX_LOG_ROWS);
                trimRows.executeUpdate();
            }

            try (Statement optimize = writeConnection.createStatement()) {
                optimize.execute("PRAGMA optimize");
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to prune detection logs: " + ex.getMessage());
        } finally {
            writeLock.unlock();
        }
    }

    public void log(final UUID uuid, final String player, final String check, final int vl, final long ping, final String data) {
        final int size = pendingSize.incrementAndGet();
        if (size > MAX_PENDING_QUEUE) {
            pendingSize.decrementAndGet();
            droppedLogs.incrementAndGet();
            if (droppedLogs.get() % 500 == 1) {
                plugin.getLogger().warning("Log queue overflow: dropping detection logs to protect memory (dropped=" + droppedLogs.get() + ").");
            }
            return;
        }

        pending.add(new LogEntry(uuid, player, check, vl, ping, data, System.currentTimeMillis()));
    }

    public List<LogEntry> getLogs(final UUID uuid, final int limit) {
        List<LogEntry> out = new ArrayList<>();
        if (this.readConnection == null) return out;

        synchronized (readConnection) {
            try (PreparedStatement ps = readConnection.prepareStatement(
                    "SELECT uuid, player, check_name, vl, ping, data, ts FROM logs WHERE uuid=? ORDER BY ts DESC LIMIT ?"
            )) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, Math.max(1, limit));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString("uuid"));
                    String p = rs.getString("player");
                    String c = rs.getString("check_name");
                    int v = rs.getInt("vl");
                    long pg = rs.getLong("ping");
                    String d = rs.getString("data");
                    long ts = rs.getLong("ts");
                    out.add(new LogEntry(id, p, c, v, pg, d, ts));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        }

        return out;
    }

    public void exportToCsv(final File outFile) {
        if (this.readConnection == null) return;

        synchronized (readConnection) {
            try (PreparedStatement ps = readConnection.prepareStatement(
                    "SELECT uuid, player, check_name, vl, ping, data, ts FROM logs ORDER BY ts DESC"
            );
             ResultSet rs = ps.executeQuery();
             PrintWriter pw = new PrintWriter(outFile)) {

            pw.println("uuid,player,check,vl,ping,data,timestamp");

            while (rs.next()) {
                String uuid = rs.getString("uuid");
                String player = csv(rs.getString("player"));
                String check = csv(rs.getString("check_name"));
                int vl = rs.getInt("vl");
                long ping = rs.getLong("ping");
                String data = csv(rs.getString("data"));
                long ts = rs.getLong("ts");

                pw.println(uuid + "," + player + "," + check + "," + vl + "," + ping + "," + data + "," + ts);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        }
    }

    private static String csv(String in) {
        if (in == null) return "";
        String s = in.replace("\r", " ").replace("\n", " ").replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\"")) {
            return "\"" + s + "\"";
        }
        return s;
    }

    public void shutdown() {
        flushNow();

        writeLock.lock();
        try {
            if (writeConnection != null) {
                writeConnection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            writeLock.unlock();
        }
        synchronized (readConnection) {
            try {
                if (readConnection != null) {
                    readConnection.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static class LogEntry {
        public final UUID uuid;
        public final String player, check, data;
        public final int vl;
        public final long ping, timestamp;

        public LogEntry(UUID uuid, String player, String check, int vl, long ping, String data, long timestamp) {
            this.uuid = uuid;
            this.player = player;
            this.check = check;
            this.vl = vl;
            this.ping = ping;
            this.data = data;
            this.timestamp = timestamp;
        }

        public String toDisplayString() {
            final String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(this.timestamp));
            String extra = this.data == null ? "" : this.data.replace('\n', ' ').replace('\r', ' ');
            if (extra.length() > 120) {
                extra = extra.substring(0, 117) + "...";
            }

            return "§8[" + time + "] §f" + this.check + " §7VL:§c" + this.vl +
                    " §7Ping:§f" + this.ping + "ms" +
                    (extra.isEmpty() ? "" : " §8» §7" + extra);
        }
    }
}
