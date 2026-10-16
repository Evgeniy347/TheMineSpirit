package com.theminespirit.neoforge;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theminespirit.neoforge.coordinator.CoordinatorRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

final class SpiritInviteCommand {
    private SpiritInviteCommand() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> spirit) {
        spirit.then(literal("invite")
                .then(argument("expedition", StringArgumentType.string())
                        .then(argument("answer", StringArgumentType.word())
                                .executes(SpiritInviteCommand::answer))));
    }

    private static int answer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = context.getSource().getLevel();
        String expedition = StringArgumentType.getString(context, "expedition");
        String answer = StringArgumentType.getString(context, "answer");
        if (!answer.equals("wait") && !answer.equals("go") && !answer.equals("join")) {
            context.getSource().sendFailure(Component.literal("Ответ '" + answer
                    + "' непонятен: годится wait (подождите +60с), go (идите без меня) или join (иду с вами)"));
            return 0;
        }
        boolean taken = CoordinatorRegistry.answerInvite(level, expedition, answer, player,
                level.getGameTime());
        if (!taken) {
            context.getSource().sendFailure(Component.literal("Поход '" + expedition
                    + "' не ждёт ответа: приглашение истекло или команда уже ушла"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Ответ принят: духи учтут решение игрока"), true);
        return 1;
    }
}
