package org.alifmis.trailingeffectplugin.manager;

import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

// Stores player trails in a SQLite database (trails.db) with an in-memory cache.

public class TrailManager {

    private static final String TABLE = "player_trails";

    private final JavaPlugin plugin;
    private final ConcurrentHashMap<UUID, Set<Particle>> playerTrails = new ConcurrentHashMap<>();

    private volatile Connection connection;

    public TrailManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        try {
            // Driver is loaded by name (no compile-time dependency — it ships inside the server)
            Class.forName("org.sqlite.JDBC");
            File dbFile = new File(plugin.getDataFolder(), "trails.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                        + "uuid TEXT NOT NULL, particle TEXT NOT NULL,"
                        + " PRIMARY KEY (uuid, particle))");
            }
            loadAll();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to initialize trails database — trails will NOT persist", e);
            connection = null;
        }
    }

    public void addTrail(UUID playerUuid, Particle particle) {
        Set<Particle> trails = playerTrails.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet());
        if (!trails.add(particle)) return; // already set — nothing to persist
        persistAsync("INSERT OR IGNORE INTO " + TABLE + " (uuid, particle) VALUES (?, ?)", playerUuid, particle);
    }

    public boolean removeTrail(UUID playerUuid, Particle particle) {
        Set<Particle> trails = playerTrails.get(playerUuid);
        if (trails == null || !trails.remove(particle)) return false;
        if (trails.isEmpty()) playerTrails.remove(playerUuid);
        persistAsync("DELETE FROM " + TABLE + " WHERE uuid = ? AND particle = ?", playerUuid, particle);
        return true;
    }

    public Set<Particle> getTrails(UUID playerUuid) {
        return playerTrails.getOrDefault(playerUuid, Collections.emptySet());
    }

    public boolean hasTrails(UUID playerUuid) {
        Set<Particle> trails = playerTrails.get(playerUuid);
        return trails != null && !trails.isEmpty();
    }

    public Map<UUID, Set<Particle>> getAllTrails() {
        return Collections.unmodifiableMap(playerTrails);
    }

    // Closes the database. Call during onDisable.
    public void close() {
        Connection conn = connection;
        connection = null;
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to close trails database", e);
            }
        }
    }

    // Writes run on the async scheduler — never on a region thread.
    private void persistAsync(String sql, UUID uuid, Particle particle) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            if (connection == null) return;
            synchronized (this) {
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, particle.name());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to write trail to database", e);
                }
            }
        });
    }

    private void loadAll() throws SQLException {
        int count = 0;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT uuid, particle FROM " + TABLE)) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString(1));
                    Particle particle = safeValueOf(rs.getString(2));
                    if (particle != null) {
                        playerTrails.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(particle);
                        count++;
                    }
                } catch (IllegalArgumentException ignored) {
                    // skip corrupt rows
                }
            }
        }
        plugin.getLogger().info("Loaded " + count + " trails from database");
        if (count == 0) importLegacyTrails();
    }

    private void importLegacyTrails() {
        File file = new File(plugin.getDataFolder(), "trails.yml");
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("player-trails");
        if (section == null) return;
        int imported = 0;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                for (String name : section.getStringList(key)) {
                    Particle particle = safeValueOf(name);
                    if (particle == null) continue;
                    playerTrails.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(particle);
                    if (!insertSync(uuid, particle)) continue;
                    imported++;
                }
            } catch (IllegalArgumentException ignored) {
                // skip invalid UUID keys
            }
        }
        plugin.getLogger().info("Imported " + imported + " trails from legacy trails.yml");
    }

    private boolean insertSync(UUID uuid, Particle particle) {
        if (connection == null) return false;
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT OR IGNORE INTO " + TABLE + " (uuid, particle) VALUES (?, ?)")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, particle.name());
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to import trail for " + uuid, e);
            return false;
        }
    }

    private static Particle safeValueOf(String name) {
        try {
            return Particle.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
