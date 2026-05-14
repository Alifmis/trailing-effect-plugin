package org.alifmis.trailingeffectplugin.listener;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.alifmis.trailingeffectplugin.manager.TrailManager;

import java.util.Set;

public class PlayerMoveListener implements Listener {

    private final TrailManager trailManager;

    public PlayerMoveListener(TrailManager trailManager) {
        this.trailManager = trailManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // 1. Disable for spectator mode
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

        Location loc = player.getLocation();

        double height = player.getHeight();
        double step = 0.3;

        for (Particle particle : trails) {
            for (double yOffset = 0; yOffset < height; yOffset += step) {
                Location pLoc = loc.clone().add(0, yOffset, 0);
                player.getWorld().spawnParticle(particle, pLoc, 1, 0.05, 0, 0.05, 0.01);
            }
        }
    }
}
