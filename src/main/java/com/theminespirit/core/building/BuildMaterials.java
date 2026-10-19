package com.theminespirit.core.building;

import com.theminespirit.core.plan.ScriptCall;
import java.util.List;
import java.util.Objects;

public final class BuildMaterials {
    private BuildMaterials() {}

    public record Plan(List<ScriptCall> steps, int have, int missing) {
        public Plan {
            Objects.requireNonNull(steps, "Шаги снабжения не могут быть null: пустой план задают пустым списком");
            steps = List.copyOf(steps);
            if (have < 0 || missing < 0) {
                throw new IllegalArgumentException(
                        "Счётчики материалов не могут быть отрицательными: have=" + have + ", missing=" + missing);
            }
        }
    }

    public static Plan plan(String blockId, int needed, BuildSupplies supplies) {
        Objects.requireNonNull(blockId, "Id блока не может быть null: без него неизвестно, что считать");
        Objects.requireNonNull(supplies, "Снабжение не может быть null: без него неизвестно, сколько материала есть");
        if (blockId.isBlank()) {
            throw new IllegalArgumentException("Id блока пуст: по пустому id материал не найти");
        }
        if (needed < 1) {
            throw new IllegalArgumentException(
                    "Нужно блоков " + needed + ", а надо хотя бы 1: нулевая стройка не планирует снабжение");
        }
        int have = supplies.inInventory(blockId);
        if (have < 0) {
            throw new IllegalArgumentException(
                    "В инвентаре насчитано " + have + " блоков '" + blockId + "': счётчик не бывает отрицательным");
        }
        int missing = Math.max(0, needed - have);
        List<ScriptCall> steps = missing == 0 ? List.of() : List.copyOf(supplies.withdraw(blockId, missing));
        return new Plan(steps, Math.min(have, needed), missing);
    }
}
