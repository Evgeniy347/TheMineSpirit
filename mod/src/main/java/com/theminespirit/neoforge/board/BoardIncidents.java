package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.FailureRepeat;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.neoforge.SpiritEntity;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class BoardIncidents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final FailureRepeat failures = new FailureRepeat(BoardTuning.logSummaryTicks());

    void rescued(SpiritEntity spirit, BoardTicket ticket) {
        LOGGER.info("[rescue] дух {} выполнил спасение {}", spirit.displayName(), ticket.id());
    }

    void stalled(SpiritEntity spirit, BoardTicket ticket, @Nullable TicketTarget target, String reason, long now) {
        String key = "stall:" + ticket.id() + ":" + spirit.getUUID();
        failures.note(key, reason, now).ifPresent(note -> {
            if (note.changed()) {
                LOGGER.warn(
                        "[incident] дух {}: {} {}; задание провалено; {}",
                        spirit.displayName(),
                        ticket.id(),
                        reason,
                        IncidentText.of(spirit, ticket, target, reason));
                return;
            }
            repeat("дух " + spirit.displayName() + " на задании " + ticket.id(), note);
        });
    }

    void failed(SpiritEntity spirit, BoardTicket ticket, @Nullable TicketTarget target, String why, long now) {
        String key = "settle:" + ticket.id() + ":" + spirit.getUUID();
        failures.note(key, why, now).ifPresent(note -> {
            if (note.changed()) {
                LOGGER.warn(
                        "[incident] дух {} ({}) провалил задание доски {} (кейс {}): {}; доска выдаёт "
                                + "следующее задание; {}",
                        spirit.displayName(),
                        spirit.getUUID(),
                        ticket.id(),
                        ticket.boardCase(),
                        why,
                        IncidentText.of(spirit, ticket, target, why));
                return;
            }
            repeat("дух " + spirit.displayName() + " на задании " + ticket.id(), note);
        });
    }

    private static void repeat(String what, FailureRepeat.Note note) {
        LOGGER.warn(
                "[incident] сводка: {} повторялся {} раз за окно с той же причиной: {}",
                what,
                note.count(),
                note.reason());
    }
}
