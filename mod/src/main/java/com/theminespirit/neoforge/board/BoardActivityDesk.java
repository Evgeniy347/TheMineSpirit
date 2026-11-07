package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.ActivityQueue;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardActivities;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

final class BoardActivityDesk {
    private static final long WAIT_TICKS = 1200;
    private static final Logger LOGGER = LogUtils.getLogger();

    private final BoardService host;
    private final Board board;
    private final ActivityQueue activities = new ActivityQueue();

    BoardActivityDesk(BoardService host, Board board) {
        this.host = Objects.requireNonNull(host, "host не может быть null: карточки занятий живут на доске");
        this.board = Objects.requireNonNull(board, "board не может быть null: занятость духов видна доске");
    }

    boolean request(
            List<SpiritEntity> spirits,
            String key,
            int priority,
            BoardCase activity,
            String script,
            TaskNode task,
            long now) {
        SpiritEntity free = idle(spirits);
        if (free != null && launch(free, activity, script, task, starter(), now)) {
            activities.remove(key);
            return true;
        }
        if (activities.offer(key, activity, script, task, priority, now, WAIT_TICKS)) {
            LOGGER.info("[board] занятие {} ждёт свободного духа (вес {})", key, priority);
        }
        return true;
    }

    void startWaiting(List<SpiritEntity> spirits, long now) {
        for (ActivityQueue.Request request : activities.due(now)) {
            SpiritEntity spirit = idle(spirits);
            if (spirit == null) {
                return;
            }
            if (!launch(spirit, request.activity(), request.script(), request.task(), starter(), now)) {
                continue;
            }
            activities.remove(request.key());
            LOGGER.info("[board] отложенное занятие {} стартовало у {}", request.key(), spirit.displayName());
        }
    }

    boolean launch(
            SpiritEntity spirit,
            BoardCase activity,
            String script,
            TaskNode task,
            BoardService.Launcher launcher,
            long now) {
        Objects.requireNonNull(spirit, "Дух занятия не может быть null: занятие адресовано конкретному духу");
        if (!BoardActivities.isActivity(activity)) {
            throw new IllegalArgumentException(
                    "Кейс " + activity + " — не занятие: через launch идут только ACTIVITY_*");
        }
        if (!launcher.start(spirit, script, task, now)) return false;
        host.cards.remember(
                spirit.getUUID(), activity, spirit.taskGeneration(), script, BoardActivities.targetOf(task));
        return true;
    }

    private @Nullable SpiritEntity idle(List<SpiritEntity> spirits) {
        for (SpiritEntity spirit : spirits) {
            if (spirit.isRemoved() || !board.assignedTo(spirit.getUUID()).isEmpty()) {
                continue;
            }
            if (!spirit.currentTaskScript().isPresent() || spirit.runsBackgroundTask()) {
                return spirit;
            }
        }
        return null;
    }

    private BoardService.Launcher starter() {
        return (member, name, task, tick) -> {
            if (member.isRemoved()) return false;
            JobLaunch.start(host.cards, member, name, task);
            return true;
        };
    }
}
