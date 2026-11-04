package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.MoreActivityPlans;
import com.theminespirit.core.board.TicketPlanPick;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;

final class JobLaunch {
    private static final Logger LOGGER = LogUtils.getLogger();

    private JobLaunch() {}

    static TaskNode plan(BoardTicket ticket, TicketTarget target) {
        Objects.requireNonNull(ticket, "Задание не может быть null: план строится по его кейсу");
        Objects.requireNonNull(target, "Цель не может быть null: без неё план не собрать");
        if (target.plan() != null) {
            return target.plan();
        }
        if (TicketPlans.EXTERNAL_CASES.contains(ticket.boardCase())) {
            return new ScriptCall("eat", Map.of());
        }
        return MoreActivityPlans.handles(ticket.boardCase())
                ? MoreActivityPlans.plan(ticket.boardCase(), target)
                : TicketPlanPick.plan(ticket.boardCase(), ticket.level(), target);
    }

    static void start(ActivityCards cards, SpiritEntity spirit, String ticketId, TaskNode plan) {
        Objects.requireNonNull(cards, "Карточки занятий не могут быть null: иначе карточка занятия останется");
        Objects.requireNonNull(spirit, "Дух не может быть null: задание стартует у конкретного духа");
        Objects.requireNonNull(plan, "План не может быть null: духу нечего исполнять");
        String prior = spirit.currentActivity().orElse(null);
        boolean background = spirit.runsBackgroundTask();
        cards.forget(spirit.getUUID());
        spirit.startTask(plan);
        if (background) {
            String script =
                    BoardActivities.cardScript(spirit.currentTaskScript().orElse(null), plan);
            LOGGER.info(
                    "[board] задание {} стартует скрипт {}, вытесняет фоновое занятие {}",
                    ticketId,
                    script == null ? "?" : script,
                    prior == null ? "?" : prior);
        }
    }

    static void dropUnclaimed(ActivityCards cards, SpiritEntity spirit, String ticketId, BoardOutcome outcome) {
        spirit.cancelTask();
        cards.forget(spirit.getUUID());
        WorkReserveHub.releaseAll(spirit);
        LOGGER.error(
                "Задание {} не выдано духу {}: {}; задача отменена у духа", ticketId, spirit.displayName(), outcome);
    }
}
