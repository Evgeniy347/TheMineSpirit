package com.theminespirit.neoforge;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.WorldObservation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

final class SpiritObserveCommand {
    private SpiritObserveCommand() {}

    static void register(LiteralArgumentBuilder<CommandSourceStack> spirit) {
        spirit.then(literal("observe")
                .executes(context -> observe(context.getSource(), ScanRadius.DEFAULT.value(), false))
                .then(argument("radius", IntegerArgumentType.integer(ScanRadius.MIN, ScanRadius.MAX))
                        .executes(context ->
                                observe(context.getSource(), IntegerArgumentType.getInteger(context, "radius"), false))
                        .then(literal("json")
                                .executes(context -> observe(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "radius"),
                                        true)))));
    }

    private static int observe(CommandSourceStack source, int radius, boolean asJson) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        BlockCoord center = new BlockCoord(
                player.blockPosition().getX(),
                player.blockPosition().getY(),
                player.blockPosition().getZ());
        WorldObservation observation = WorldScanner.observe(
                        source.getLevel(),
                        "player_" + player.getScoreboardName(),
                        center,
                        ScanRadius.of(radius),
                        player.getInventory())
                .observation();
        if (asJson) {
            source.sendSuccess(() -> Component.literal(observation.toJson()), false);
        } else {
            source.sendSuccess(
                    () -> Component.literal("hash " + observation.hash()
                            + ", блоков " + observation.blocks().size()
                            + ", сущностей " + observation.entities().size()
                            + ", контейнеров " + observation.containers().size()),
                    false);
        }
        return 1;
    }
}
