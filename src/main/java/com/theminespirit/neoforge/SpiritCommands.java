package com.theminespirit.neoforge;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theminespirit.core.observation.ScanRadius;
import com.theminespirit.core.observation.WorldObservation;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class SpiritCommands {
    private SpiritCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(literal("spirit")
                .requires(source -> source.hasPermission(2))
                .then(literal("spawn")
                        .executes(context -> spawnNear(context.getSource()))
                        .then(argument("pos", Vec3Argument.vec3())
                                .executes(context -> spawnAt(context.getSource(),
                                        Vec3Argument.getVec3(context, "pos")))))
                .then(literal("observe")
                        .executes(context -> observe(context.getSource(), ScanRadius.DEFAULT.value(), false))
                        .then(argument("radius", IntegerArgumentType.integer(ScanRadius.MIN, ScanRadius.MAX))
                                .executes(context -> observe(context.getSource(),
                                        IntegerArgumentType.getInteger(context, "radius"), false))
                                .then(literal("json")
                                        .executes(context -> observe(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "radius"), true))))));
    }

    private static int spawnNear(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return spawnAt(source, player.position());
    }

    private static int spawnAt(CommandSourceStack source, Vec3 pos) {
        ServerLevel level = source.getLevel();
        SpiritEntity spirit = ModEntities.SPIRIT.get().create(level);
        if (spirit == null) {
            source.sendFailure(Component.literal("Дух не создан: тип сущности "
                    + ModEntities.SPIRIT.getId() + " не зарегистрирован"));
            return 0;
        }
        spirit.moveTo(pos);
        level.addFreshEntity(spirit);
        source.sendSuccess(() -> Component.literal(
                "Дух призван в (" + pos.x + ", " + pos.y + ", " + pos.z + ")"), true);
        return 1;
    }

    private static int observe(CommandSourceStack source, int radius, boolean asJson) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        WorldObservation observation = WorldScanner.observe(
                source.getLevel(),
                "player_" + player.getScoreboardName(),
                new com.theminespirit.core.observation.BlockCoord(
                        player.blockPosition().getX(), player.blockPosition().getY(), player.blockPosition().getZ()),
                ScanRadius.of(radius),
                player.getInventory());
        if (asJson) {
            source.sendSuccess(() -> Component.literal(observation.toJson()), false);
        } else {
            source.sendSuccess(() -> Component.literal("hash " + observation.hash()
                    + ", блоков " + observation.blocks().size()
                    + ", сущностей " + observation.entities().size()
                    + ", контейнеров " + observation.containers().size()), false);
        }
        return 1;
    }
}