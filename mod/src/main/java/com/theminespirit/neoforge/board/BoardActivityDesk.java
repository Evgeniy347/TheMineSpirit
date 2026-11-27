package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.ActivityTickets;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.MoreActivityPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class BoardActivityDesk {
    static final int FALLBACK_PRIORITY = 1;
    private static final Logger LOGGER = LogUtils.getLogger();

    private final BoardService host;
    private final Board board;
    private final Map<String, TicketTarget> targets;

    BoardActivityDesk(BoardService host, Board board, Map<String, TicketTarget> targets) {
        this.host = Objects.requireNonNull(host, "host не может быть null: занятие ставится доской");
        this.board = Objects.requireNonNull(board, "board не может быть null: занятие живёт на доске");
        this.targets = Objects.requireNonNull(targets, "targets не может быть null: цель занятия общая с доской");
    }

    boolean post(ActivityTickets.Request request, BlockCoord at, @Nullable String hold) {
        Objects.requireNonNull(request, "Запрос занятия не может быть null: без него нечего ставить");
        String id = request.identity().id();
        if (!BoardActivities.isActivity(request.identity().activity())) {
            throw new IllegalArgumentException(
                    "Кейс " + request.identity().activity() + " — не занятие: через эту дверь идут только ACTIVITY_*");
        }
        BoardTicket ticket = ActivityTickets.ticket(request);
        TicketTarget goal = TicketTarget.at(at).withPlan(request.plan().task());
        BoardOutcome outcome = host.submit(ticket, goal);
        if (outcome == BoardOutcome.DUPLICATE) {
            board.updatePending(ticket);
            targets.put(id, goal);
        }
        if (hold == null || hold.isBlank()) {
            board.holds().release(id);
        } else {
            board.holds().hold(id, hold);
        }
        if (outcome == BoardOutcome.BACKOFF || outcome == BoardOutcome.STAGE_LOCKED) {
            LOGGER.warn("[board] занятие {} не принято: {}", id, outcome);
            return false;
        }
        return true;
    }

    void fillIdle(List<SpiritEntity> spirits, long now) {
        for (SpiritEntity spirit : spirits) {
            if (spirit.isRemoved()
                    || spirit.tactics().wantsControl()
                    || spirit.currentTaskScript().isPresent()
                    || spirit.taskStatus().orElse(null) == RunStatus.RUNNING
                    || !board.assignedTo(spirit.getUUID()).isEmpty()
                    || !SpiritVitals.needs(spirit).isEmpty()) {
                continue;
            }
            String id = "activity:idle_sense:" + spirit.getUUID();
            BoardStatus status = board.statusOf(id).orElse(null);
            if (status != null && status.open()) {
                continue;
            }
            if (status == BoardStatus.DONE) {
                board.reopen(id);
            } else if (status != null) {
                board.cancel(id);
                targets.remove(id);
            }
            BlockCoord at = new BlockCoord(
                    spirit.blockPosition().getX(),
                    spirit.blockPosition().getY(),
                    spirit.blockPosition().getZ());
            post(idleSense(spirit.getUUID(), at, now), at, null);
        }
    }

    static ActivityTickets.Request idleSense(UUID spirit, BlockCoord at, long now) {
        TicketTarget target = TicketTarget.at(at);
        TaskNode plan = MoreActivityPlans.plan(BoardCase.ACTIVITY_SENSE, target);
        return new ActivityTickets.Request(
                new ActivityTickets.Identity(
                        "activity:idle_sense:" + spirit, BoardCase.ACTIVITY_SENSE, BoardTicket.ALWAYS_CAUSE),
                new ActivityTickets.Crew(Optional.of(spirit), List.of()),
                FALLBACK_PRIORITY,
                now,
                new ActivityTickets.Plan("scan_blocks", "", 0, plan),
                Map.of());
    }

    boolean start(String id, SpiritEntity spirit, TaskNode plan, long now) {
        Objects.requireNonNull(spirit, "Дух занятия не может быть null: занятие адресовано конкретному духу");
        BoardTicket ticket = board.find(id).orElse(null);
        if (ticket == null) {
            return false;
        }
        BoardOutcome outcome = board.claim(id, spirit.getUUID(), now);
        if (outcome != BoardOutcome.CLAIMED && outcome != BoardOutcome.TAKEN_BY_OTHER) {
            return false;
        }
        if (outcome == BoardOutcome.TAKEN_BY_OTHER) {
            return false;
        }
        JobLaunch.start(spirit, id, plan);
        host.activityIssued(id, spirit.taskGeneration());
        return true;
    }

    boolean startCrew(String id, SpiritEntity spirit, TaskNode plan) {
        Objects.requireNonNull(spirit, "Участник команды не может быть null: задание уходит конкретному духу");
        JobLaunch.start(spirit, id, plan);
        return true;
    }

    void hold(String id, @Nullable String reason) {
        if (reason == null || reason.isBlank()) {
            board.holds().release(id);
        } else {
            board.holds().hold(id, reason);
        }
    }

    Optional<BoardTicket> ticket(String id) {
        return board.find(id);
    }
}
