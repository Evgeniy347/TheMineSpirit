package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BoardTuning;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.TransferTicket;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class BoardDispatchDesk {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final BoardService host;
    private final Board board;
    private final Map<String, TicketTarget> targets;
    private final BoardActivityDesk activities;
    private final BoardSettlementDesk settlement;
    private final CrafterMemory crafters = new CrafterMemory();
    private final TransferWait transferWait = new TransferWait();
    private final OrphanTaskWatch orphans = new OrphanTaskWatch();
    private boolean freshTickets;
    private long lastDispatch;
    private int claims;
    private final IssueSteps timer = new IssueSteps();
    private final DispatchTrouble trouble = new DispatchTrouble(this);

    BoardDispatchDesk(BoardService host, Board board, Map<String, TicketTarget> targets) {
        this.host = Objects.requireNonNull(host, "host не может быть null: занятие запускает доска");
        this.board = Objects.requireNonNull(board, "board не может быть null: задания выдаёт доска");
        this.targets = Objects.requireNonNull(targets, "targets не может быть null: цели задания общие с доской");
        this.settlement = new BoardSettlementDesk(host, board, targets);
        this.activities = new BoardActivityDesk(host, board, targets);
    }

    int claims() {
        return claims;
    }

    void submitted() {
        freshTickets = true;
    }

    void retainTickets(Set<String> alive) {
        settlement.retain(alive);
    }

    void tick(ServerLevel level, long now) {
        if (now - lastDispatch >= BoardTuning.dispatchRetryTicks() && (freshTickets || holderFreed(level))) {
            lastDispatch = now;
            freshTickets = false;
            issue(level, SpiritCensus.all(level), now);
        }
    }

    void reconcile(List<SpiritEntity> spirits, boolean census) {
        BoardPositionWatch.observe(board, spirits, board.now());
        WaitRefresh.tick(board, spirits, targets, host.stock(), crafters, board.now());
        if (!census) return;
    }

    void settleOwners(List<SpiritEntity> spirits) {
        settlement.settleOwners(spirits);
        orphans.watch(board, spirits);
    }

    void releaseDeadHolders(ServerLevel level, List<SpiritEntity> spirits, long now) {
        settlement.releaseDeadHolders(level, spirits, now);
    }

    void dropAbsentOwners(List<SpiritEntity> spirits, long now) {
        settlement.dropAbsentOwners(spirits, now);
    }

    void release(SpiritEntity spirit) {
        settlement.release(spirit);
    }

    BoardActivityDesk activityDesk() {
        return activities;
    }

    Board board() {
        return board;
    }

    Map<String, TicketTarget> targets() {
        return targets;
    }

    BoardSettlementDesk settlementDesk() {
        return settlement;
    }

    BoardService host() {
        return host;
    }

    void activityIssued(String ticketId, long generation) {
        settlement.issued(ticketId, generation);
    }

    long issuedGeneration(String ticketId) {
        return settlement.issuedGeneration(ticketId);
    }

    private boolean holderFreed(ServerLevel level) {
        for (UUID holder : board.assignedCounts().keySet()) {
            if (level.getEntity(holder) instanceof SpiritEntity spirit
                    && spirit.taskStatus().orElse(null) != RunStatus.RUNNING) {
                return true;
            }
        }
        return false;
    }

    void issue(ServerLevel level, List<SpiritEntity> spirits, long now) {
        timer.begin();
        issueAll(level, spirits, now);
        timer.end(now, LOGGER);
    }

    private void issueAll(ServerLevel level, List<SpiritEntity> spirits, long now) {
        long step = System.nanoTime();
        StockView stock = host.stock();
        List<BoardTicket> claimable = DispatchRound.order(board.claimable(stock, now));
        step = timer.lap("claimable", step);
        transferWait.retain(board);
        crafters.retain(id -> board.statusOf(id).filter(TransferWatch::open).isPresent());
        trouble.withdrawForVitals(spirits, claimable);
        step = timer.lap("vitals", step);
        DispatchRound round = new DispatchRound(board, now);
        DispatchPick pick = new DispatchPick(round, spirits, stock);
        step = timer.lap("candidates", step);
        Optional<BlockCoord> settlementHome = HomePoint.of(level, spirits);
        timer.lap("home", step);
        Batch batch = new Batch(level, spirits, stock, round, pick, settlementHome, now);
        int explained = 0;
        for (BoardTicket ticket : timer.window(claimable)) {
            if (issueTicket(batch, ticket, explained < IssueSteps.WHY_BATCH)) {
                explained++;
            }
        }
    }

    private record Batch(
            ServerLevel level,
            List<SpiritEntity> spirits,
            StockView stock,
            DispatchRound round,
            DispatchPick pick,
            Optional<BlockCoord> settlementHome,
            long now) {}

    private boolean issueTicket(Batch batch, BoardTicket ticket, boolean explain) {
        long chooseStarted = System.nanoTime();
        SpiritEntity spirit = batch.pick().choose(ticket, targets.get(ticket.id()), crafters);
        timer.add("choose", chooseStarted);
        if (spirit == null) {
            return explainWait(batch, ticket, explain);
        }
        for (BoardTicket displaced : batch.round().drainDisplaced(spirit.getUUID())) {
            settlement.displace(spirit, displaced, ticket);
        }
        if (targets.get(ticket.id()) == null && batch.settlementHome().isEmpty()) {
            board.noteWait(ticket.id(), "поселение без дома: заданию некуда идти");
            return false;
        }
        TicketTarget base = targets.get(ticket.id());
        if (base == null) {
            base = TicketTarget.at(batch.settlementHome().orElseThrow());
        }
        board.noteWait(ticket.id(), null);
        TicketTarget target = IssueSteps.gatherOnTop(ticket, TransferTicket.restore(ticket, base), spirit);
        Optional<TicketTarget> fitted = IssueSteps.fitTransfer(ticket, target, spirit);
        if (fitted.isEmpty()) {
            trouble.dropEmptyTransfer(ticket, target, spirit);
            return false;
        }
        target = fitted.orElseThrow();
        long transferStarted = System.nanoTime();
        boolean awaits = TransferWatch.ensureTransfers(
                batch.level(), host, batch.spirits(), batch.stock(), ticket, spirit, batch.now());
        timer.add("transfers", transferStarted);
        if (awaits) {
            transferWait.await(board, crafters, ticket, spirit, batch.spirits(), batch.round());
            return false;
        }
        return startJob(batch, ticket, target, spirit);
    }

    private boolean explainWait(Batch batch, BoardTicket ticket, boolean explain) {
        if (!explain) {
            return false;
        }
        long whyStarted = System.nanoTime();
        board.noteWait(ticket.id(), batch.pick().why(ticket, targets.get(ticket.id())));
        timer.add("why", whyStarted);
        return true;
    }

    private boolean startJob(Batch batch, BoardTicket ticket, TicketTarget target, SpiritEntity spirit) {
        long planStarted = System.nanoTime();
        Optional<TaskNode> planned = trouble.planTicket(ticket, target, batch.round());
        timer.add("plan", planStarted);
        if (planned.isEmpty()) {
            return false;
        }
        TaskNode plan = planned.orElseThrow();
        if (spirit.runsBackgroundTask()) {
            LOGGER.info(
                    "Задание {} вытесняет фоновое занятие {} духа {}",
                    ticket.id(),
                    spirit.currentTaskScript().orElse("?"),
                    spirit.displayName());
        }
        try {
            long startStarted = System.nanoTime();
            JobLaunch.start(spirit, ticket.id(), plan);
            timer.add("start", startStarted);
        } catch (RuntimeException failure) {
            LOGGER.error(
                    "Задание {}: дух {} не принял задание: {}",
                    ticket.id(),
                    spirit.displayName(),
                    failure.getMessage());
            return false;
        }
        BoardOutcome outcome = board.claim(ticket.id(), spirit.getUUID(), batch.now());
        if (outcome == BoardOutcome.CLAIMED) {
            crafters.forget(ticket.id());
            settlement.issued(ticket.id(), spirit.taskGeneration());
            settlement.progressIssued(batch.level(), ticket, target);
            claims++;
            batch.round().take(spirit.getUUID());
            WorkReserveHub.reserve(spirit, new WorkTarget(WorkKind.CELL, target.target()));
            host.notices.assigned(board, ticket, target, batch.pick().whyChosen(ticket, spirit, crafters), spirit);
        } else {
            JobLaunch.dropUnclaimed(spirit, ticket.id(), outcome);
        }
        batch.pick().refresh(spirit);
        return false;
    }
}
