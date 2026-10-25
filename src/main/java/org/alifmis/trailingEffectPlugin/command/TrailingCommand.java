package org.alifmis.trailingeffectplugin.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.alifmis.trailingeffectplugin.manager.TrailManager;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class TrailingCommand {

    private final TrailManager trailManager;
    private final JavaPlugin plugin;

    public TrailingCommand(TrailManager trailManager, JavaPlugin plugin) {
        this.trailManager = trailManager;
        this.plugin = plugin;
    }

    public void register(Commands registrar) {
        registrar.register(
                Commands.literal("trailing")
                        .requires(source -> {
                            CommandSender sender = source.getSender();
                            return sender instanceof ConsoleCommandSender
                                    || sender.isOp()
                                    || sender.hasPermission("trailing");
                        })
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(this::suggestPlayers)
                                // "reload" is an argument, not a literal, so it is absent from the client command tree
                                .executes(ctx -> executeRootAction(ctx))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("particle", StringArgumentType.word())
                                                .suggests(this::suggestAllParticles)
                                                .executes(ctx -> executeAdd(ctx))
                                        )
                                )
                                .then(Commands.literal("delete")
                                        .then(Commands.argument("particle", StringArgumentType.word())
                                                .suggests(this::suggestPlayerParticles)
                                                .executes(ctx -> executeDelete(ctx))
                                        )
                                )
                        )
                        .build()
        );
    }

    private int executeAdd(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        String particleName = StringArgumentType.getString(ctx, "particle").toUpperCase();

        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text("Player not found: " + playerName, NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        // Non-admin senders (trailing permission only) may only modify their own trail
        if (!isAdmin(ctx.getSource()) && !isSelf(ctx.getSource(), target)) {
            ctx.getSource().getSender().sendMessage(
                    Component.text("You can only modify your own trail.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        // If player already has the max number of trails, do not allow adding another
        int maxTrails = Math.max(0, plugin.getConfig().getInt("max-trails-per-player", 2));
        if (trailManager.getTrails(target.getUniqueId()).size() >= maxTrails) {
            ctx.getSource().getSender().sendMessage(
                    Component.text("You can have at most " + maxTrails + " trail(s).", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        Particle particle;
        try {
            particle = Particle.valueOf(particleName);
        } catch (IllegalArgumentException e) {
            ctx.getSource().getSender().sendMessage(
                    Component.text("Unknown particle: " + particleName.toLowerCase(), NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        // Particles on the blocked list cannot be added by anyone
        if (isBlocked(particle)) {
            ctx.getSource().getSender().sendMessage(
                    Component.text("The particle " + particle.name().toLowerCase() + " is blocked by the server config.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        trailManager.addTrail(target.getUniqueId(), particle);
        ctx.getSource().getSender().sendMessage(
                Component.text("Added " + particle.name().toLowerCase() + " trail to " + target.getName(), NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private int executeDelete(CommandContext<CommandSourceStack> ctx) {
        String playerName = StringArgumentType.getString(ctx, "player");
        String particleName = StringArgumentType.getString(ctx, "particle").toUpperCase();

        Player target = Bukkit.getPlayerExact(playerName);
        if (target == null) {
            ctx.getSource().getSender().sendMessage(
                    Component.text("Player not found: " + playerName, NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        // Non-admin senders (trailing permission only) may only modify their own trail
        if (!isAdmin(ctx.getSource()) && !isSelf(ctx.getSource(), target)) {
            ctx.getSource().getSender().sendMessage(
                    Component.text("You can only modify your own trail.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        Particle particle;
        try {
            particle = Particle.valueOf(particleName);
        } catch (IllegalArgumentException e) {
            ctx.getSource().getSender().sendMessage(
                    Component.text("Unknown particle: " + particleName.toLowerCase(), NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }

        if (trailManager.removeTrail(target.getUniqueId(), particle)) {
            ctx.getSource().getSender().sendMessage(
                    Component.text("Removed " + particle.name().toLowerCase() + " trail from " + target.getName(), NamedTextColor.GREEN));
        } else {
            ctx.getSource().getSender().sendMessage(
                    Component.text(target.getName() + " does not have the " + particle.name().toLowerCase() + " trail", NamedTextColor.RED));
        }
        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggestPlayers(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        if (isAdmin(ctx.getSource())) {
            // "reload" is suggested here (server-side) so non-admins never see it
            if ("reload".startsWith(remaining)) {
                builder.suggest("reload");
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                String name = player.getName();
                if (name.toLowerCase().startsWith(remaining)) {
                    builder.suggest(name);
                }
            }
        } else if (ctx.getSource().getSender() instanceof Player player) {
            // Trailing-permission players only see (and can use) their own name
            String name = player.getName();
            if (name.toLowerCase().startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    private boolean isAdmin(CommandSourceStack source) {
        CommandSender sender = source.getSender();
        return sender instanceof ConsoleCommandSender || sender.isOp();
    }

    private boolean isSelf(CommandSourceStack source, Player target) {
        return source.getSender() instanceof Player player
                && player.getUniqueId().equals(target.getUniqueId());
    }

    private int executeRootAction(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String arg = StringArgumentType.getString(ctx, "player");
        if (!arg.equalsIgnoreCase("reload")) {
            sender.sendMessage(Component.text(
                    "Usage: /trailing reload | /trailing <player> add|delete <particle>", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        if (!isAdmin(ctx.getSource())) {
            sender.sendMessage(Component.text("You don't have permission to reload the config.", NamedTextColor.RED));
            return Command.SINGLE_SUCCESS;
        }
        plugin.reloadConfig();
        int maxTrails = Math.max(0, plugin.getConfig().getInt("max-trails-per-player", 2));
        int blockedCount = plugin.getConfig().getStringList("blocked-particles").size();
        sender.sendMessage(
                Component.text("Config reloaded. Max trails per player: " + maxTrails
                        + ", blocked particles: " + blockedCount, NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    // Reads the blocked-particles list fresh from config every call, so /trailing reload takes effect immediately
    private boolean isBlocked(Particle particle) {
        String name = particle.name().toLowerCase();
        for (String blocked : plugin.getConfig().getStringList("blocked-particles")) {
            if (blocked.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    private CompletableFuture<Suggestions> suggestAllParticles(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (Particle particle : Particle.values()) {
            String name = particle.name().toLowerCase();
            // Hide blocked particles from tab completion
            if (name.startsWith(remaining) && !isBlocked(particle)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestPlayerParticles(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String playerName = StringArgumentType.getString(ctx, "player");
        Player target = Bukkit.getPlayerExact(playerName);
        if (target != null) {
            String remaining = builder.getRemainingLowerCase();
            Set<Particle> trails = trailManager.getTrails(target.getUniqueId());
            for (Particle particle : trails) {
                String name = particle.name().toLowerCase();
                if (name.startsWith(remaining)) {
                    builder.suggest(name);
                }
            }
        }
        return builder.buildFuture();
    }
}
