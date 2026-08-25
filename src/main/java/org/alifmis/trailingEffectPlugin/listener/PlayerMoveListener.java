package org.alifmis.trailingeffectplugin.listener;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.alifmis.trailingeffectplugin.manager.TrailManager;

import java.util.Set;

public class PlayerMoveListener implements Listener {

    private final TrailManager trailManager;
    private final JavaPlugin plugin;

    public PlayerMoveListener(TrailManager trailManager, JavaPlugin plugin) {
        this.trailManager = trailManager;
        this.plugin = plugin;
    }

    private static final double RESERVED_HEIGHT = 0.2;

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Disable for spectator mode
        if (player.getGameMode() == GameMode.SPECTATOR) return;

        Location from = event.getFrom();
        Location to = event.getTo();

        // Ignore pure head-rotation — only spawn on block movement
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Set<Particle> trails = trailManager.getTrails(player.getUniqueId());
        if (trails.isEmpty()) return;

        int interval = Math.max(1, plugin.getConfig().getInt("particle-spawn-interval-ticks", 1));
        if (interval > 1 && player.getTicksLived() % interval != 0) return;

        Location loc = player.getLocation();

        boolean dense = plugin.getConfig().getBoolean("dense-particle-spawn", false);

        for (Particle particle : trails) {
            if (dense) {
                double height = player.getHeight();
                double step = 0.3;
                for (double yOffset = 0; yOffset < height; yOffset += step) {
                    spawnParticle(particle, loc.clone().add(0, yOffset, 0));
                }
            } else {
                spawnParticle(particle, loc.clone().add(0, RESERVED_HEIGHT, 0));
            }
        }
    }

    // Random initial momentum: each particle gets its own random direction and speed
    private void spawnParticle(Particle particle, Location pLoc) {
        double dx = (Math.random() - 0.5) * 0.5;
        double dy = Math.abs(Math.random() - 0.5);
        double dz = (Math.random() - 0.5) * 0.5;
        double speed = 0.05 + Math.random() * 0.2;
        pLoc.getWorld().spawnParticle(particle, pLoc, 1, dx, dy, dz, speed);
    }
}
