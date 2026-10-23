package com.theminespirit.neoforge.board;

import com.theminespirit.core.intellect.ActivityId;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Предложение интеллекта поселения: дух, занятие, скрипт и план. Интеллект занятий сам не запускает — выдаёт
 * их доска ({@link BoardService#offerActivities}), чтобы всё, что делает дух, было видно на ней. Защита
 * ({@link ActivityId#DEFEND}) — без плана: это отметка для тактики, а не задача.
 */
public record ActivityOffer(
        SpiritEntity spirit,
        ActivityId activity,
        String script,
        @Nullable TaskNode task) {
    public ActivityOffer {
        Objects.requireNonNull(spirit, "Дух предложения не может быть null: занятие адресовано конкретному духу");
        Objects.requireNonNull(activity, "Занятие предложения не может быть null: доска показывает его кейсом");
        Objects.requireNonNull(script, "Скрипт предложения не может быть null: откат и учёт ведутся по скрипту");
        if (task == null && activity != ActivityId.DEFEND) {
            throw new IllegalArgumentException(
                    "Занятие " + activity + " без плана: духу нечего исполнять, план нужен всем, кроме защиты");
        }
    }

    @FunctionalInterface
    public interface Launcher {
        boolean start(SpiritEntity spirit, String script, TaskNode task, long tick);
    }
}
