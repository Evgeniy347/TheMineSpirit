package com.theminespirit.neoforge;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.rest.BoardTask;
import com.theminespirit.core.rest.BoardTasks;
import com.theminespirit.core.rest.BoardText;
import com.theminespirit.neoforge.board.BoardRegistry;
import com.theminespirit.neoforge.board.BoardService;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public final class SpiritBoardCommand {
    private SpiritBoardCommand() {}

    public static void register(LiteralArgumentBuilder<CommandSourceStack> spirit) {
        spirit.then(literal("board")
                .executes(SpiritBoardCommand::list)
                .then(literal("cancel")
                        .then(argument("id", MessageArgument.message()).executes(SpiritBoardCommand::cancel))));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BoardService service = BoardRegistry.get(level);
        List<BoardTicket> tickets = service.board().all();
        if (tickets.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Доска пуста: заданий поселения нет"), false);
            return 0;
        }
        Board board = service.board();
        source.sendSuccess(() -> Component.literal("Доска заданий (" + tickets.size() + "):"), false);
        for (BoardTicket ticket : tickets) {
            BoardTask task = BoardTasks.of(board, ticket, service.stock());
            source.sendSuccess(() -> Component.literal(BoardText.line(task)), false);
        }
        return tickets.size();
    }

    private static int cancel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        String id = MessageArgument.getMessage(context, "id").getString();
        BoardService service = BoardRegistry.get(level);
        if (service.board().find(id).isEmpty()) {
            source.sendFailure(
                    Component.literal("Задание доски '" + id + "' не найдено: список заданий — /spirit board"));
            return 0;
        }
        service.cancel(level, id);
        source.sendSuccess(() -> Component.literal("Задание доски " + id + " отменено"), true);
        return 1;
    }
}
