package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.HolderRelease;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class BoardCrewDesk {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final BoardService host;
    private final Board board;
    private final Map<String, TicketTarget> targets;

    BoardCrewDesk(BoardService host, Board board, Map<String, TicketTarget> targets) {
        this.host = Objects.requireNonNull(host, "host не может быть null: команда проваливается на доске");
        this.board = Objects.requireNonNull(board, "board не может быть null: команда живёт на доске");
        this.targets = Objects.requireNonNull(targets, "targets не может быть null: цель нужна для сверки ремонта");
    }

    void watch(List<SpiritEntity> spirits) {
        Set<UUID> live = new HashSet<>();
        for (SpiritEntity spirit : spirits) {
            if (!spirit.isRemoved()) {
                live.add(spirit.getUUID());
            }
        }
        for (BoardTicket ticket : board.all()) {
            if (ticket.executors().isEmpty() || board.statusOf(ticket.id()).orElse(null) != BoardStatus.ASSIGNED) {
                continue;
            }
            Optional<UUID> lost = ticket.executors().stream()
                    .filter(executor -> !live.contains(executor))
                    .findFirst();
            if (lost.isEmpty()) {
                continue;
            }
            String reason = "участник похода " + lost.orElseThrow() + " пропал: команда возвращается";
            LOGGER.warn("[board] занятие {} провалено: {}", ticket.id(), reason);
            board.fail(ticket.id(), host.stock(), reason);
            cancel(ticket, null, spirits);
        }
    }

    void releaseDeadHolders(ServerLevel level, List<SpiritEntity> spirits, long now) {
        Set<UUID> live = new HashSet<>();
        for (SpiritEntity spirit : spirits) {
            live.add(spirit.getUUID());
        }
        for (BoardTicket ticket : board.all()) {
            if (board.statusOf(ticket.id()).orElse(null) != BoardStatus.ASSIGNED) {
                continue;
            }
            UUID holder = board.holderOf(ticket.id()).orElse(null);
            if (holder == null || live.contains(holder)) {
                continue;
            }
            if (BoardActivities.isActivity(ticket.boardCase())) {
                String reason = "лидер занятия " + holder + " пропал: занятие провалено";
                board.fail(ticket.id(), host.stock(), reason);
                cancel(ticket, holder, spirits);
                continue;
            }
            if (!RepairGoalDone.of(level, ticket, targets.get(ticket.id()))) {
                continue;
            }
            board.complete(ticket.id());
        }
        for (HolderRelease freed : board.releaseDeadHolders(live)) {
            LOGGER.info(
                    "[board] задание {} освобождено: держатель {} погиб или удалён", freed.ticketId(), freed.holder());
            host.journal()
                    .recordEntry(BoardLog.note(
                            freed.ticketId(),
                            freed.boardCase(),
                            now,
                            List.of("держатель " + freed.holder() + " погиб/удалён"),
                            freed.holder().toString(),
                            ConfigStamp.current()));
        }
    }

    void cancel(BoardTicket ticket, @Nullable UUID leader, List<SpiritEntity> spirits) {
        if (ticket.executors().isEmpty()) {
            return;
        }
        for (SpiritEntity spirit : spirits) {
            if (spirit.isRemoved() || spirit.getUUID().equals(leader)) {
                continue;
            }
            if (ticket.executors().contains(spirit.getUUID())) {
                JobLaunch.start(spirit, ticket.id(), new ScriptCall("return_home", Map.of()));
                WorkReserveHub.releaseAll(spirit);
            }
        }
    }
}
