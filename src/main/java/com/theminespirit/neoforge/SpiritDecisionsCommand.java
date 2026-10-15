package com.theminespirit.neoforge;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theminespirit.core.tactics.adapter.DecisionRecord;
import com.theminespirit.core.tactics.adapter.DecisionText;
import com.theminespirit.neoforge.tactics.TacticsService;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

final class SpiritDecisionsCommand {
    private SpiritDecisionsCommand() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> spirit) {
        spirit.then(literal("decisions")
                .then(argument("spirits", EntityArgument.entities())
                        .executes(SpiritDecisionsCommand::decisions)));
    }

    private static int decisions(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        List<SpiritEntity> spirits = SelectedSpirits.selected(context);
        if (spirits.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Журнал решений некому показать: среди выбранных сущностей нет духа"));
            return 0;
        }
        for (SpiritEntity spirit : spirits) {
            List<DecisionRecord> records = spirit.tactics().decisions(TacticsService.DECISIONS_IN_CHAT);
            if (records.isEmpty()) {
                context.getSource().sendSuccess(() -> Component.literal("Дух " + spirit.displayName()
                        + " ещё не менял решение: записей в журнале нет"), false);
                continue;
            }
            for (DecisionRecord record : records) {
                context.getSource().sendSuccess(() -> Component.literal(DecisionText.format(record)), false);
            }
        }
        return spirits.size();
    }
}
