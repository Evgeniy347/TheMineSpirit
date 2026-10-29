package com.theminespirit.core.rest;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

public record BotInfo(
        String id,
        String name,
        BotState state,
        int x,
        int y,
        int z,
        String dimension,
        String currentTask,
        double food,
        @Nullable BotBoardTask boardTask,
        long tick,
        long positionTick) {
    public BotInfo(
            String id,
            String name,
            BotState state,
            int x,
            int y,
            int z,
            String dimension,
            String currentTask,
            double food) {
        this(id, name, state, x, y, z, dimension, currentTask, food, null, 0, 0);
    }

    public BotInfo {
        Objects.requireNonNull(id, "id не может быть null: по id клиент обращается к духу");
        Objects.requireNonNull(name, "name не может быть null: имя показывается в списке духов");
        Objects.requireNonNull(state, "state не может быть null: клиент выбирает действия по состоянию духа");
        Objects.requireNonNull(dimension, "dimension не может быть null: позиция без измерения неоднозначна");
        if (tick < 0 || positionTick < 0) {
            throw new IllegalArgumentException(
                    "Тик " + tick + " и тик позиции " + positionTick + " отрицательны: отсчёт идёт вперёд");
        }
        if (positionTick > tick) {
            throw new IllegalArgumentException(
                    "Тик позиции " + positionTick + " больше текущего " + tick + ": позиция не может быть из будущего");
        }
        if (!Double.isFinite(food) || food < 0 || food > 1) {
            throw new IllegalArgumentException(
                    "Сытость " + food + " вне [0; 1]: клиент ждёт долю шкалы голода, а не число сытости");
        }
    }
}
