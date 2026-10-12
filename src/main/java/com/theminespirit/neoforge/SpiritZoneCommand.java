package com.theminespirit.neoforge;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.WorkZone;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

final class SpiritZoneCommand {
    private static final int MAX_RADIUS = 1024;

    private SpiritZoneCommand() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> spirit) {
        spirit.then(literal("zone")
                .then(argument("spirits", EntityArgument.entities())
                        .then(literal("clear").executes(SpiritZoneCommand::clear))
                        .then(argument("center", BlockPosArgument.blockPos())
                                .then(argument("radius", IntegerArgumentType.integer(1, MAX_RADIUS))
                                        .executes(SpiritZoneCommand::set)))));
    }

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        List<SpiritEntity> spirits = selected(context);
        BlockPos center = BlockPosArgument.getLoadedBlockPos(context, "center");
        int radius = IntegerArgumentType.getInteger(context, "radius");
        if (spirits.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Рабочая зона некому поставить: среди выбранных сущностей нет духа"));
            return 0;
        }
        WorkZone zone = new WorkZone(new BlockCoord(center.getX(), center.getY(), center.getZ()), radius);
        spirits.forEach(spirit -> spirit.setWorkZone(zone));
        context.getSource().sendSuccess(() -> Component.literal("Рабочая зона радиуса " + radius + " вокруг " + center.toShortString()
                + " поставлена духам: " + spirits.size()), true);
        return spirits.size();
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        List<SpiritEntity> spirits = selected(context);
        if (spirits.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Рабочую зону некому снять: среди выбранных сущностей нет духа"));
            return 0;
        }
        spirits.forEach(SpiritEntity::clearWorkZone);
        context.getSource().sendSuccess(() -> Component.literal("Рабочая зона снята у духов: " + spirits.size()), true);
        return spirits.size();
    }

    private static List<SpiritEntity> selected(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        List<SpiritEntity> spirits = new ArrayList<>();
        for (Entity entity : EntityArgument.getEntities(context, "spirits")) {
            if (entity instanceof SpiritEntity spirit) {
                spirits.add(spirit);
            }
        }
        return spirits;
    }
}
