package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Objects;
import org.slf4j.Logger;

final class JobLaunch {
    private static final Logger LOGGER = LogUtils.getLogger();

    private JobLaunch() {}

    static void start(Board board, ActivityCards cards, SpiritEntity spirit, String ticketId, TaskNode plan) {
        Objects.requireNonNull(board, "Доска не может быть null: занятие снимается с неё");
        Objects.requireNonNull(cards, "Карточки занятий не могут быть null: иначе карточка занятия останется");
        Objects.requireNonNull(spirit, "Дух не может быть null: задание стартует у конкретного духа");
        Objects.requireNonNull(plan, "План не может быть null: духу нечего исполнять");
        String activity = board.occupation(spirit.getUUID()).map(Enum::name).orElse(null);
        String prior = activity != null ? activity : spirit.currentActivity().orElse(null);
        boolean background = spirit.runsBackgroundTask();
        if (activity != null) {
            board.vacate(spirit.getUUID());
            cards.forget(spirit.getUUID());
        }
        spirit.startTask(plan);
        if (activity != null || background) {
            LOGGER.info(
                    "[board] задание {} стартует скрипт {}, activity {} отменена",
                    ticketId,
                    spirit.currentTaskScript().orElse("?"),
                    prior == null ? BoardActivities.idOf(spirit.getUUID()) : prior);
        }
    }
}
