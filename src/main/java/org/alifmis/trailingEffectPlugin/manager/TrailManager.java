package org.alifmis.trailingeffectplugin.manager;

import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TrailManager {

    private final JavaPlugin plugin;
    private final ConcurrentHashMap<UUID, Set<Particle>> playerTrails = new ConcurrentHashMap<>();

    public TrailManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void addTrail(UUID playerUuid, Particle particle) {
        playerTrails.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet()).add(particle);
    }

    public boolean removeTrail(UUID playerUuid, Particle particle) {
        Set<Particle> trails = playerTrails.get(playerUuid);
        if (trails == null) return false;
        boolean removed = trails.remove(particle);
        if (trails.isEmpty()) {
            playerTrails.remove(playerUuid);
        }
        return removed;
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

    public void saveTrails() {
        File file = new File(plugin.getDataFolder(), "trails.yml");
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Set<Particle>> entry : playerTrails.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            List<String> particleNames = entry.getValue().stream()
                    .map(p -> p.name().toLowerCase())
                    .sorted()
                    .toList();
            config.set("player-trails." + entry.getKey().toString(), particleNames);
        }
        try {
            config.save(file);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save trails.yml: " + e.getMessage());
        }
    }

    public void loadTrails() {
        File file = new File(plugin.getDataFolder(), "trails.yml");
        if (!file.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("player-trails");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                List<String> particleNames = section.getStringList(key);
                for (String name : particleNames) {
                    try {
                        Particle particle = Particle.valueOf(name.toUpperCase());
                        playerTrails.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(particle);
                    } catch (IllegalArgumentException ignored) {
                        // skip unknown/removed particles
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // skip invalid UUID key
            }
        }
    }
}
