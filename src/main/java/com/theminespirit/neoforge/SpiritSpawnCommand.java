package com.theminespirit.neoforge;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

final class SpiritSpawnCommand {
    private SpiritSpawnCommand() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> spirit) {
        spirit.then(literal("spawn")
                .executes(context -> spawnNear(context.getSource()))
                .then(argument("pos", Vec3Argument.vec3())
                        .executes(context -> spawnAt(context.getSource(),
                                Vec3Argument.getVec3(context, "pos")))));
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
}
