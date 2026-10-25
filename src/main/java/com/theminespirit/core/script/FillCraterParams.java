package com.theminespirit.core.script;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public sealed interface FillCraterParams {
    record ById(UUID id) implements FillCraterParams {
        public ById {
            Objects.requireNonNull(id, "Id воронки не может быть null: без него воронку не найти");
        }
    }

    record ByPosition(int x, int y, int z) implements FillCraterParams {
        public ByPosition {
            BuildingCoords.checkX(x);
            BuildingCoords.checkY(y);
            BuildingCoords.checkZ(z);
        }
    }

    static FillCraterParams from(Map<String, Object> params) {
        Objects.requireNonNull(params, "params не может быть null: параметры скрипта приходят картой");
        String value = ScriptArgs.requireString(params, "crater");
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "Параметр 'crater' пуст: укажите UUID воронки из /spirit craters " + "или позицию «x,y,z»");
        }
        try {
            return new ById(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException notUuid) {
            return byPosition(value);
        }
    }

    private static FillCraterParams byPosition(String value) {
        String[] parts = value.split(",", -1);
        if (parts.length != 3) {
            throw new IllegalArgumentException("Параметр 'crater' = '" + value + "' не UUID и не позиция "
                    + "«x,y,z»: укажите id воронки или три целых координаты через запятую");
        }
        try {
            return new ByPosition(
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Позиция воронки '" + value + "' содержит не целое число: "
                    + "координаты задают целыми, например «10,64,-5»");
        }
    }
}
