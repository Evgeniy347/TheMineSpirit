package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.TransferCarriers;
import com.theminespirit.core.board.TransferWaitNotes;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;

final class TransferWait {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final TransferWaitNotes notes = new TransferWaitNotes();

    void await(
            Board board,
            CrafterMemory crafters,
            BoardTicket ticket,
            SpiritEntity crafter,
            List<SpiritEntity> spirits,
            DispatchRound round) {
        crafters.remember(ticket.id(), crafter.getUUID());
        Set<UUID> running = new HashSet<>();
        for (SpiritEntity each : spirits) {
            if (each.taskStatus().orElse(null) == RunStatus.RUNNING) {
                running.add(each.getUUID());
            }
        }
        Set<UUID> carriers = TransferCarriers.carrying(board, ticket.id(), running);
        Set<UUID> planned = TransferCarriers.planned(board, ticket.id());
        List<String> names = new ArrayList<>();
        List<String> waiting = new ArrayList<>();
        for (SpiritEntity each : spirits) {
            if (carriers.contains(each.getUUID())) {
                names.add(each.displayName());
                round.await(each.getUUID(), "несёт ингредиенты для " + ticket.id());
            } else if (planned.contains(each.getUUID())) {
                waiting.add(each.displayName());
            }
        }
        String state;
        if (!names.isEmpty()) {
            state = "носильщики: " + String.join(", ", names) + "; крафтер " + crafter.displayName()
                    + " ждёт ингредиенты";
        } else if (!waiting.isEmpty()) {
            state = "передачи ждут выдачи: " + String.join(", ", waiting);
        } else {
            state = "носильщиков нет: ингредиентов не хватает у духов и на складе, крафт не начат";
        }
        board.noteWait(ticket.id(), "ждёт передачи ингредиентов: " + state);
        notes.observe(ticket.id(), state).ifPresent(LOGGER::info);
    }

    void retain(Board board) {
        Set<String> open = new HashSet<>();
        for (BoardTicket ticket : board.tickets()) {
            open.add(ticket.id());
        }
        notes.retain(open);
    }
}
