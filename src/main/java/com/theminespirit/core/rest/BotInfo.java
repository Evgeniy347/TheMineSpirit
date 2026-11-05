package com.theminespirit.core.rest;

import java.util.Map;
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
        long positionTick,
        @Nullable String activity,
        @Nullable String script,
        long stateSinceTick,
        Map<String, Integer> inventory) {
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
        this(id, name, state, x, y, z, dimension, currentTask, food, null, 0, 0, null, null, 0, Map.of());
    }

    public BotInfo(
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
        this(
                id,
                name,
                state,
                x,
                y,
                z,
                dimension,
                currentTask,
                food,
                boardTask,
                tick,
                positionTick,
                null,
                null,
                tick,
                Map.of());
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
        if (stateSinceTick < 0 || stateSinceTick > tick) {
            throw new IllegalArgumentException("Тик состояния " + stateSinceTick + " вне [0; " + tick
                    + "]: состояние не может начаться в будущем");
        }
        if (!Double.isFinite(food) || food < 0 || food > 1) {
            throw new IllegalArgumentException(
                    "Сытость " + food + " вне [0; 1]: клиент ждёт долю шкалы голода, а не число сытости");
        }
        inventory =
                Map.copyOf(Objects.requireNonNull(inventory, "Инвентарь не может быть null: пустой — пустая карта"));
        for (Map.Entry<String, Integer> entry : inventory.entrySet()) {
            if (entry.getValue() == null || entry.getValue() < 0) {
                throw new IllegalArgumentException("Предмет '" + entry.getKey() + "' имеет количество "
                        + entry.getValue() + ": количество не может быть отрицательным или null");
            }
        }
    }
}
