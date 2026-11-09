package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.RepairPlan;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.TransferCarriers;
import com.theminespirit.core.board.TransferTicket;
import com.theminespirit.core.board.VitalGate;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import com.theminespirit.neoforge.inventory.SpiritSupplies;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class BoardDispatchDesk {
    private static final long EARLY_DISPATCH_TICKS = 1;
    private static final Logger LOGGER = LogUtils.getLogger();

    private final BoardService host;
    private final Board board;
    private final Map<String, TicketTarget> targets;
    private final BoardActivityDesk activities;
    private final BoardSettlementDesk settlement;
    private final CrafterMemory crafters = new CrafterMemory();
    private final TransferWait transferWait = new TransferWait();
    private boolean freshTickets;
    private long lastDispatch;
    private int claims;

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
        if (now - lastDispatch >= EARLY_DISPATCH_TICKS && (freshTickets || holderFreed(level))) {
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

    void activityIssued(String ticketId, long generation) {
        settlement.issued(ticketId, generation);
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
        StockView stock = host.stock();
        List<BoardTicket> claimable = DispatchRound.order(board.claimable(stock, now));
        transferWait.retain(board);
        crafters.retain(id -> board.statusOf(id).filter(TransferWatch::open).isPresent());
        withdrawForVitals(spirits, claimable);
        DispatchRound round = new DispatchRound(board, now);
        DispatchPick pick = new DispatchPick(round, spirits, stock);
        for (BoardTicket ticket : claimable) {
            SpiritEntity spirit = pick.choose(ticket, targets.get(ticket.id()), crafters);
            if (spirit == null) {
                String why = pick.why(ticket, targets.get(ticket.id()));
                board.noteWait(ticket.id(), why);
                LOGGER.debug("Задание {} ждёт свободного духа: {}", ticket.id(), why);
                continue;
            }
            for (BoardTicket displaced : round.drainDisplaced(spirit.getUUID())) {
                settlement.displace(spirit, displaced, ticket);
            }
            board.noteWait(ticket.id(), null);
            TicketTarget target = gatherOnTop(
                    ticket,
                    TransferTicket.restore(
                            ticket, targets.getOrDefault(ticket.id(), TicketTarget.at(SettlementHome.of(spirits)))),
                    spirit);
            Optional<TicketTarget> fitted = fitTransfer(ticket, target, spirit);
            if (fitted.isEmpty()) {
                dropEmptyTransfer(ticket, target, spirit);
                continue;
            }
            target = fitted.orElseThrow();
            if (TransferWatch.ensureTransfers(level, host, spirits, stock, ticket, spirit, now)) {
                transferWait.await(board, crafters, ticket, spirit, spirits, round);
                continue;
            }
            Optional<TaskNode> planned = planTicket(ticket, target, round);
            if (planned.isEmpty()) {
                continue;
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
                JobLaunch.start(spirit, ticket.id(), plan);
            } catch (RuntimeException failure) {
                LOGGER.error(
                        "Задание {}: дух {} не принял задание: {}",
                        ticket.id(),
                        spirit.displayName(),
                        failure.getMessage());
                continue;
            }
            BoardOutcome outcome = board.claim(ticket.id(), spirit.getUUID(), now);
            if (outcome == BoardOutcome.CLAIMED) {
                crafters.forget(ticket.id());
                settlement.issued(ticket.id(), spirit.taskGeneration());
                settlement.progressIssued(level, ticket, target);
                claims++;
                round.take(spirit.getUUID());
                WorkReserveHub.reserve(spirit, new WorkTarget(WorkKind.CELL, target.target()));
                host.notices.assigned(board, ticket, target, pick.whyChosen(ticket, spirit, crafters), spirit);
            } else {
                JobLaunch.dropUnclaimed(spirit, ticket.id(), outcome);
            }
        }
    }

    private void withdrawForVitals(List<SpiritEntity> spirits, List<BoardTicket> claimable) {
        for (SpiritEntity spirit : spirits) {
            if (spirit.isRemoved()) {
                continue;
            }
            Map<String, String> needs = SpiritVitals.needs(spirit);
            if (needs.isEmpty()) {
                continue;
            }
            boolean chain = claimable.stream()
                    .anyMatch(ticket -> VitalGate.satisfies(ticket.id(), needs)
                            && ticket.assignee().map(spirit.getUUID()::equals).orElse(true));
            if (chain) {
                continue;
            }
            List<BoardTicket> held = List.copyOf(board.assignedTo(spirit.getUUID()));
            for (BoardTicket ticket : held) {
                if (VitalGate.blocksWork(ticket, needs)) {
                    settlement.withdraw(
                            spirit, ticket, needs.values().iterator().next());
                }
            }
        }
    }

    private static TicketTarget gatherOnTop(BoardTicket ticket, TicketTarget target, SpiritEntity spirit) {
        String item = target.supplyItem();
        if (ticket.boardCase() != BoardCase.GATHER_MATERIALS
                || item == null
                || "coal".equals(item)
                || "cobblestone".equals(item)) {
            return target;
        }
        int held = new SpiritSupplies(spirit, CatalogLimits.CONTAINER_RADIUS_DEFAULT).inInventory(item);
        return held <= 0
                ? target
                : target.withSupply(item, Math.min(CatalogLimits.COUNT_MAX, held + target.supplyCount()));
    }

    private Optional<TaskNode> planTicket(BoardTicket ticket, TicketTarget target, DispatchRound round) {
        try {
            return Optional.of(JobLaunch.plan(ticket, target));
        } catch (RuntimeException failure) {
            if (RepairPlan.goalless(ticket, targets.get(ticket.id()))) {
                dropGoallessRepair(ticket);
                return Optional.empty();
            }
            LOGGER.error(
                    "Задание {} (кейс {}): план не собран: {}", ticket.id(), ticket.boardCase(), failure.getMessage());
            dropUnplannedTransfer(ticket, round);
            return Optional.empty();
        }
    }

    private void dropGoallessRepair(BoardTicket ticket) {
        board.cancel(ticket.id());
        targets.remove(ticket.id());
        host.persistBoard();
        LOGGER.info(
                "[board] ремонт {} снят: цели нет (план сменился или сервер перезапущен), аудит этапа поставит заново",
                ticket.id());
    }

    private void dropUnplannedTransfer(BoardTicket ticket, DispatchRound round) {
        if (ticket.boardCase() != BoardCase.TRANSFER_ITEM) {
            return;
        }
        TransferCarriers.dropUnplanned(board, round, ticket);
        targets.remove(ticket.id());
        LOGGER.warn(
                "[incident] передача {} снята: план не собран, держатель {} свободен, доска поставит её заново",
                ticket.id(),
                ticket.assignee().map(UUID::toString).orElse("-"));
        host.persistBoard();
    }

    private void dropEmptyTransfer(BoardTicket ticket, TicketTarget target, SpiritEntity spirit) {
        board.cancel(ticket.id());
        targets.remove(ticket.id());
        host.persistBoard();
        LOGGER.info(
                "Передача {} снята: у духа {} не осталось {}", ticket.id(), spirit.displayName(), target.supplyItem());
    }

    private static Optional<TicketTarget> fitTransfer(BoardTicket ticket, TicketTarget target, SpiritEntity spirit) {
        if (ticket.boardCase() != BoardCase.TRANSFER_ITEM
                || target.transferFrom() == null
                || !target.transferFrom().equals(spirit.getUUID())
                || target.supplyItem() == null) {
            return Optional.of(target);
        }
        int held = new SpiritSupplies(spirit, CatalogLimits.CONTAINER_RADIUS_DEFAULT).inInventory(target.supplyItem());
        if (held <= 0) {
            return Optional.empty();
        }
        return Optional.of(
                held >= target.supplyCount()
                        ? target
                        : target.withTransfer(target.transferFrom(), target.transferTo(), target.supplyItem(), held));
    }
}
