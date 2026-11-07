package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.AbsentOwners;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.HolderRelease;
import com.theminespirit.core.board.RepairPlan;
import com.theminespirit.core.board.TaskStall;
import com.theminespirit.core.board.TicketRun;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolCraft;
import com.theminespirit.core.board.ToolLossRelease;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.task.SpiritReading;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class BoardSettlementDesk {
    private static final String DIG_OUT = "dig_out";
    private static final Logger LOGGER = LogUtils.getLogger();

    private final BoardService host;
    private final Board board;
    private final Map<String, TicketTarget> targets;
    private final Map<String, Long> issuedGenerations = new HashMap<>();
    private final TaskStall stall = new TaskStall();
    private final AbsentOwners absentOwners = new AbsentOwners();
    private final StageProgressWatch progress = new StageProgressWatch();
    private final BoardIncidents incidents = new BoardIncidents();

    BoardSettlementDesk(BoardService host, Board board, Map<String, TicketTarget> targets) {
        this.host = Objects.requireNonNull(host, "host не может быть null: расчёт задания живёт на доске");
        this.board = board;
        this.targets = targets;
    }

    void retain(Set<String> alive) {
        issuedGenerations.keySet().retainAll(alive);
    }

    void issued(String ticketId, long generation) {
        issuedGenerations.put(ticketId, generation);
    }

    void progressIssued(ServerLevel level, BoardTicket ticket, TicketTarget target) {
        progress.issued(level, ticket, target);
    }

    void forget(String ticketId) {
        issuedGenerations.remove(ticketId);
    }

    void release(SpiritEntity spirit) {
        for (BoardTicket ticket : board.assignedTo(spirit.getUUID())) {
            issuedGenerations.remove(ticket.id());
            board.release(ticket.id());
        }
        host.cards.forget(spirit.getUUID());
    }

    void settleOwners(List<SpiritEntity> spirits) {
        for (SpiritEntity spirit : spirits) {
            if (spirit.isRemoved()) {
                host.releaseSpirit(spirit);
                continue;
            }
            RunStatus status = spirit.taskStatus().orElse(null);
            dropUntooled(spirit);
            List<BoardTicket> held = board.assignedTo(spirit.getUUID());
            if (held.isEmpty()) {
                continue;
            }
            BoardTicket ticket = held.get(0);
            TicketRun run = TicketRun.decide(
                    issuedGenerations.getOrDefault(ticket.id(), SpiritReading.UNKNOWN_GENERATION),
                    spirit.taskGeneration(),
                    status,
                    spirit.tactics().wantsControl(),
                    targets.containsKey(ticket.id()));
            switch (run) {
                case SUCCEEDED, FAILED -> {
                    if (run == TicketRun.SUCCEEDED
                            && !ToolCraft.fulfilled(ticket.boardCase(), SpiritItems.ids(spirit))) {
                        ToolWait.release(board, issuedGenerations, spirit, ticket);
                        break;
                    }
                    String empty = run == TicketRun.SUCCEEDED && spirit.level() instanceof ServerLevel level
                            ? progress.emptyResult(level, ticket, targets.get(ticket.id()))
                            : null;
                    settle(spirit, ticket, run == TicketRun.SUCCEEDED && empty == null, empty);
                }
                case RESUME -> resume(spirit, ticket, spirits);
                case RUNNING -> checkStall(spirit, ticket);
                case DISPLACED -> DisplacedClaims.release(spirit, ticket, board, issuedGenerations);
            }
        }
    }

    void releaseDeadHolders(ServerLevel level, List<SpiritEntity> spirits, long now) {
        Set<UUID> live = new HashSet<>();
        for (SpiritEntity spirit : spirits) {
            live.add(spirit.getUUID());
        }
        for (BoardTicket ticket : board.all()) {
            if (board.statusOf(ticket.id()).orElse(null) != BoardStatus.ASSIGNED
                    || BoardActivities.isActivity(ticket.boardCase())) {
                continue;
            }
            UUID holder = board.holderOf(ticket.id()).orElse(null);
            if (holder == null || live.contains(holder) || !repairGoalDone(level, ticket)) {
                continue;
            }
            board.complete(ticket.id());
        }
        for (HolderRelease freed : board.releaseDeadHolders(live)) {
            if (freed.activity()) {
                host.cards.forget(freed.holder());
            }
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

    void dropAbsentOwners(List<SpiritEntity> spirits, long now) {
        Set<UUID> live = new HashSet<>();
        spirits.forEach(spirit -> live.add(spirit.getUUID()));
        for (String id : absentOwners.sweep(board, live, now)) {
            LOGGER.info(
                    "[board] задание {} снято: его дух удалён или не появлялся {} тиков", id, AbsentOwners.GRACE_TICKS);
        }
    }

    private boolean repairGoalDone(ServerLevel level, BoardTicket ticket) {
        if (ticket.boardCase() != BoardCase.REPAIR_BUILDING || !ticket.id().startsWith(RepairPlan.PREFIX)) {
            return false;
        }
        TicketTarget target = targets.get(ticket.id());
        StageWork work = target == null ? null : target.work();
        String wood = target == null ? null : target.wood();
        if (work == null || work.repairs().isEmpty() || wood == null) {
            return false;
        }
        FirstStepsRunner.Placed placed =
                FirstStepsRunner.audit(level, BoardCase.valueOf(work.repairs()), work.site(), wood);
        return placed.confident() && placed.total() > 0 && placed.done() >= placed.total();
    }

    private void dropUntooled(SpiritEntity spirit) {
        boolean walledIn = DIG_OUT.equals(spirit.currentTaskScript().orElse(null));
        for (ToolLossRelease.Released freed : ToolLossRelease.apply(
                board,
                spirit.getUUID(),
                SpiritItems.ids(spirit),
                walledIn,
                ticket -> host.sources.supplyOf(ticket, targets),
                DispatchPick.stockTools(host.stock()))) {
            Long issued = issuedGenerations.remove(freed.ticketId());
            if (issued != null
                    && issued == spirit.taskGeneration()
                    && spirit.taskStatus().orElse(null) == RunStatus.RUNNING) {
                spirit.cancelTask();
            }
            LOGGER.info("[tool] дух {}: {}", spirit.displayName(), freed.reason());
            host.journal()
                    .recordEntry(BoardLog.note(
                            freed.ticketId(),
                            freed.boardCase(),
                            board.now(),
                            List.of(freed.reason()),
                            spirit.getUUID().toString(),
                            ConfigStamp.current()));
        }
    }

    private void checkStall(SpiritEntity spirit, BoardTicket ticket) {
        BlockPos feet = spirit.blockPosition();
        String script = spirit.currentTaskScript().orElse(null);
        boolean paused =
                spirit.tactics().wantsControl() || spirit.taskSuspendReason().isPresent();
        BlockCoord at = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        boolean standing = stall.stalled(spirit.getUUID(), script, at, board.now(), paused);
        String key = spirit.taskProgress()
                .map(p -> p.step() + "/" + p.currentScript() + "/" + p.done())
                .orElse("-");
        boolean idle = stall.noProgress(spirit.getUUID(), key, at, board.now(), paused);
        if (!standing && !idle) {
            return;
        }
        String reason = StallText.reason(
                standing,
                script,
                key,
                feet.toShortString(),
                spirit.tactics().wantsControl(),
                spirit.taskSuspendReason().orElse(null));
        incidents.stalled(spirit, ticket, targets.get(ticket.id()), reason, board.now());
        stall.forget(spirit.getUUID());
        issuedGenerations.remove(ticket.id());
        spirit.cancelTask();
        host.sources.graphFailure(ticket, targets.get(ticket.id()), reason, board.now());
        board.fail(ticket.id(), host.stock(), reason);
    }

    private void resume(SpiritEntity spirit, BoardTicket ticket, List<SpiritEntity> spirits) {
        TicketTarget target = targets.getOrDefault(ticket.id(), TicketTarget.at(SettlementHome.of(spirits)));
        TaskNode plan;
        try {
            plan = JobLaunch.plan(ticket, target);
            JobLaunch.start(host.cards, spirit, ticket.id(), plan);
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "[incident] дух {} не продолжил задание {} после вытеснения: {}; задание возвращено доске",
                    spirit.displayName(),
                    ticket.id(),
                    failure.getMessage());
            issuedGenerations.remove(ticket.id());
            board.release(ticket.id());
            return;
        }
        issuedGenerations.put(ticket.id(), spirit.taskGeneration());
        LOGGER.info(
                "Дух {} продолжает задание {} (кейс {}): чужая задача его вытеснила, задание осталось за ним",
                spirit.displayName(),
                ticket.id(),
                ticket.boardCase());
    }

    private void settle(SpiritEntity spirit, BoardTicket ticket, boolean success, @Nullable String verdict) {
        issuedGenerations.remove(ticket.id());
        SettleNotes.note(
                spirit, ticket, success, host.supplyClosed(), spirit.level().getGameTime());
        String why = verdict != null ? verdict : spirit.taskFailureReason().orElse("причина не записана");
        if (success) {
            host.sources.graphSuccess(ticket, targets.get(ticket.id()), board.now());
            PlanCompletion.complete(board, ticket, targets.get(ticket.id()));
            if (ticket.boardCase() == BoardCase.TRANSFER_ITEM) {
                TransferNote.delivered(spirit, ticket, targets.get(ticket.id()));
            }
            switch (ticket.boardCase()) {
                case CAPTURE_ANIMAL -> host.completeCapture(ticket.id());
                case BUILD_PEN -> host.completePen(ticket.id());
                case LURE_ANIMAL -> host.completeLure(host.speciesFor(ticket));
                case RESCUE_SPIRIT -> incidents.rescued(spirit, ticket);
                default -> {}
            }
        } else {
            String failure =
                    verdict != null ? verdict : spirit.taskFailureReason().orElse(null);
            host.sources.graphFailure(ticket, targets.get(ticket.id()), failure, board.now());
            board.fail(ticket.id(), host.stock(), failure);
            incidents.failed(spirit, ticket, targets.get(ticket.id()), why, board.now());
            board.drainDetachments().forEach(host.journal()::recordEntry);
            host.flushBlockNotes();
            if (ticket.boardCase() == BoardCase.CAPTURE_ANIMAL) {
                host.captureFailed(ticket.id());
            }
            if (ticket.boardCase() == BoardCase.CAPTURE_ANIMAL
                    || ticket.boardCase() == BoardCase.BUILD_PEN
                    || ticket.boardCase() == BoardCase.LURE_ANIMAL) {
                host.notifyAnimalDied(host.speciesFor(ticket));
            }
        }
        WorkReserveHub.releaseAll(spirit);
    }
}
