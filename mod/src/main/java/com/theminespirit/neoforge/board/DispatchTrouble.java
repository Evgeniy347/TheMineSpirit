package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.RepairPlan;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.TransferCarriers;
import com.theminespirit.core.board.VitalGate;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;

final class DispatchTrouble {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final BoardDispatchDesk desk;

    DispatchTrouble(BoardDispatchDesk desk) {
        this.desk = desk;
    }

    void withdrawForVitals(List<SpiritEntity> spirits, List<BoardTicket> claimable) {
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
            if (!chain) {
                continue;
            }
            List<BoardTicket> held = List.copyOf(desk.board().assignedTo(spirit.getUUID()));
            for (BoardTicket ticket : held) {
                if (VitalGate.blocksWork(ticket, needs)) {
                    desk.settlementDesk()
                            .withdraw(spirit, ticket, needs.values().iterator().next());
                }
            }
        }
    }

    Optional<TaskNode> planTicket(BoardTicket ticket, TicketTarget target, DispatchRound round) {
        try {
            return Optional.of(JobLaunch.plan(ticket, target));
        } catch (RuntimeException failure) {
            if (RepairPlan.goalless(ticket, desk.targets().get(ticket.id()))) {
                dropGoallessRepair(ticket);
                return Optional.empty();
            }
            LOGGER.error(
                    "Задание {} (кейс {}): план не собран: {}", ticket.id(), ticket.boardCase(), failure.getMessage());
            dropUnplannedTransfer(ticket, round);
            return Optional.empty();
        }
    }

    void dropEmptyTransfer(BoardTicket ticket, TicketTarget target, SpiritEntity spirit) {
        desk.board().cancel(ticket.id());
        desk.targets().remove(ticket.id());
        desk.host().persistBoard();
        LOGGER.info(
                "Передача {} снята: у духа {} не осталось {}", ticket.id(), spirit.displayName(), target.supplyItem());
    }

    private void dropGoallessRepair(BoardTicket ticket) {
        desk.board().cancel(ticket.id());
        desk.targets().remove(ticket.id());
        desk.host().persistBoard();
        LOGGER.info(
                "[board] ремонт {} снят: цели нет (план сменился или сервер перезапущен), аудит этапа поставит заново",
                ticket.id());
    }

    private void dropUnplannedTransfer(BoardTicket ticket, DispatchRound round) {
        if (ticket.boardCase() != BoardCase.TRANSFER_ITEM) {
            return;
        }
        TransferCarriers.dropUnplanned(desk.board(), round, ticket);
        desk.targets().remove(ticket.id());
        LOGGER.warn(
                "[incident] передача {} снята: план не собран, держатель {} свободен, доска поставит её заново",
                ticket.id(),
                ticket.assignee().map(UUID::toString).orElse("-"));
        desk.host().persistBoard();
    }
}
