package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.ChangeSummary;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.neoforge.SpiritEntity;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class BoardIncidents {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ChangeSummary log = new ChangeSummary(BoardTuning.logSummaryTicks());

    void rescued(SpiritEntity spirit, BoardTicket ticket) {
        LOGGER.info("[rescue] дух {} выполнил спасение {}", spirit.displayName(), ticket.id());
    }

    void stalled(
            SpiritEntity spirit,
            BoardTicket ticket,
            @Nullable TicketTarget target,
            String reason,
            BoardStatus status,
            long now) {
        String key = "stall:" + ticket.id() + ":" + spirit.getUUID();
        log.note(key, reason + "|" + status, 1, now).ifPresent(summary -> {
            if (summary.changed()) {
                LOGGER.warn(
                        "[incident] дух {}: {} {}; задание провалено; {}",
                        spirit.displayName(),
                        ticket.id(),
                        reason,
                        IncidentText.of(spirit, ticket, target, reason));
                return;
            }
            repeat("дух " + spirit.displayName() + " на задании " + ticket.id(), reason, summary);
        });
    }

    void failed(
            SpiritEntity spirit,
            BoardTicket ticket,
            @Nullable TicketTarget target,
            String why,
            BoardStatus status,
            long now) {
        String key = "settle:" + ticket.id() + ":" + spirit.getUUID();
        log.note(key, why + "|" + status, 1, now).ifPresent(summary -> {
            if (summary.changed()) {
                LOGGER.warn(
                        "[incident] дух {} ({}) провалил задание доски {} (кейс {}): {}; статус {}; доска выдаёт "
                                + "следующее задание; {}",
                        spirit.displayName(),
                        spirit.getUUID(),
                        ticket.id(),
                        ticket.boardCase(),
                        why,
                        status,
                        IncidentText.of(spirit, ticket, target, why));
                return;
            }
            repeat("дух " + spirit.displayName() + " на задании " + ticket.id(), why, summary);
        });
    }

    private static void repeat(String what, String reason, ChangeSummary.Summary summary) {
        LOGGER.warn(
                "[incident] сводка: {} повторялся {} раз за окно с той же причиной: {}", what, summary.count(), reason);
    }
}
