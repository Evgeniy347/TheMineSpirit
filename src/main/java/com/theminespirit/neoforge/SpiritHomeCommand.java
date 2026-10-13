package com.theminespirit.neoforge;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

final class SpiritHomeCommand {
    private SpiritHomeCommand() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> spirit) {
        spirit.then(literal("home")
                .then(argument("spirits", EntityArgument.entities())
                        .then(literal("clear").executes(SpiritHomeCommand::clear))
                        .then(literal("set")
                                .executes(context -> set(context, null))
                                .then(argument("pos", BlockPosArgument.blockPos())
                                        .executes(context -> set(context,
                                                BlockPosArgument.getLoadedBlockPos(context, "pos")))))));
    }

    private static int set(CommandContext<CommandSourceStack> context, BlockPos explicit) throws CommandSyntaxException {
        List<SpiritEntity> spirits = SelectedSpirits.selected(context);
        if (spirits.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Дом некому поставить: среди выбранных сущностей нет духа"));
            return 0;
        }
        for (SpiritEntity spirit : spirits) {
            BlockPos at = explicit == null ? spirit.blockPosition() : explicit;
            spirit.setHome(new BlockCoord(at.getX(), at.getY(), at.getZ()));
        }
        context.getSource().sendSuccess(() -> Component.literal("Дом поставлен духам: " + spirits.size()), true);
        return spirits.size();
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        List<SpiritEntity> spirits = SelectedSpirits.selected(context);
        if (spirits.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Дом некому снять: среди выбранных сущностей нет духа"));
            return 0;
        }
        spirits.forEach(SpiritEntity::clearHome);
        context.getSource().sendSuccess(() -> Component.literal("Дом снят у духов: " + spirits.size()), true);
        return spirits.size();
    }
}
