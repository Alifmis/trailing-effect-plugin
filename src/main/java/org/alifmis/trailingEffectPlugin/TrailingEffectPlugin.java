package org.alifmis.trailingeffectplugin;

import org.bukkit.plugin.java.JavaPlugin;
import org.alifmis.trailingeffectplugin.command.TrailingCommand;
import org.alifmis.trailingeffectplugin.listener.PlayerMoveListener;
import org.alifmis.trailingeffectplugin.manager.TrailManager;

public final class TrailingEffectPlugin extends JavaPlugin {

    private TrailManager trailManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.trailManager = new TrailManager(this);
        this.trailManager.init();

        getServer().getPluginManager().registerEvents(new PlayerMoveListener(trailManager, this), this);

        getLifecycleManager().registerEventHandler(
                io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS,
                commands -> {
                    var registrar = commands.registrar();
                    new TrailingCommand(trailManager, this).register(registrar);
                }
        );

        getLogger().info("TrailingEffectPlugin enabled — " + trailManager.getAllTrails().size() + " tracked players");
    }

    @Override
    public void onDisable() {
        if (trailManager != null) {
            trailManager.close();
            getLogger().info("Trails database closed");
        }
    }

    public TrailManager getTrailManager() {
        return trailManager;
    }
}
