package com.theminespirit.core.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.api.JsonResource;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record FactTuning(
        int animalRadius,
        int penRadius,
        int wildRadius,
        int villagerRadius,
        int bedRadius,
        int hazardRadius,
        int flowRadius) {
    private static final Set<String> FIELDS = Set.of(
            "animalRadius", "penRadius", "wildRadius", "villagerRadius", "bedRadius", "hazardRadius", "flowRadius");
    private static final FactTuning DEFAULTS = new FactTuning(48, 12, 16, 48, 20, 3, 8);

    public FactTuning {
        requirePositive(animalRadius, "animalRadius");
        requirePositive(penRadius, "penRadius");
        requirePositive(wildRadius, "wildRadius");
        requirePositive(villagerRadius, "villagerRadius");
        requirePositive(bedRadius, "bedRadius");
        requirePositive(hazardRadius, "hazardRadius");
        requirePositive(flowRadius, "flowRadius");
    }

    static FactTuning defaults() {
        return DEFAULTS;
    }

    static FactTuning fromJson(@Nullable JsonNode node, String origin) {
        Objects.requireNonNull(origin, "Источник таблицы не может быть null: сообщение об ошибке называет файл");
        if (node == null || node.isNull()) {
            return DEFAULTS;
        }
        if (!node.isObject()) {
            throw new IllegalStateException(
                    "Файл '" + origin + "': поле 'facts' — не объект, ожидается набор радиусов сканирования");
        }
        JsonResource.checkFields(node, FIELDS, origin, "поля 'facts'");
        return new FactTuning(
                integer(node, "animalRadius", origin),
                integer(node, "penRadius", origin),
                integer(node, "wildRadius", origin),
                integer(node, "villagerRadius", origin),
                integer(node, "bedRadius", origin),
                integer(node, "hazardRadius", origin),
                integer(node, "flowRadius", origin));
    }

    private static int integer(JsonNode node, String field, String origin) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber()) {
            throw new IllegalStateException("Файл '" + origin + "': поле 'facts." + field
                    + "' обязано быть целым числом — радиус читается из данных");
        }
        return value.intValue();
    }

    private static void requirePositive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "Поле facts." + field + " = " + value + ": радиус сканирования обязан быть положительным");
        }
    }
}
