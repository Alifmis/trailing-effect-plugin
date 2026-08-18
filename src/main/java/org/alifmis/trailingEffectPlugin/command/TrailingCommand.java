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
import org.alifmis.trailingeffectplugin.manager.TrailManager;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class TrailingCommand {

    private final TrailManager trailManager;

    public TrailingCommand(TrailManager trailManager) {
        this.trailManager = trailManager;
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

        Particle particle;
        try {
            particle = Particle.valueOf(particleName);
        } catch (IllegalArgumentException e) {
            ctx.getSource().getSender().sendMessage(
                    Component.text("Unknown particle: " + particleName.toLowerCase(), NamedTextColor.RED));
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            String name = player.getName();
            if (name.toLowerCase().startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestAllParticles(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        for (Particle particle : Particle.values()) {
            String name = particle.name().toLowerCase();
            if (name.startsWith(remaining)) {
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
