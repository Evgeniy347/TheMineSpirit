package com.theminespirit.core.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public record PlanTuning(
        int mineFloorTake,
        int leftOreRadius,
        int mineTunnelSize,
        int mineStepsSize,
        int supplyContainerRadius,
        int dismantleRadius,
        int relocateClearRadius,
        int charcoalLogs,
        int charcoalBatch,
        int torchesPerCraft,
        int torchBatch,
        int fenceParts) {
    private static final Set<String> FIELDS = Set.of(
            "mineFloorTake",
            "leftOreRadius",
            "mineTunnelSize",
            "mineStepsSize",
            "supplyContainerRadius",
            "dismantleRadius",
            "relocateClearRadius",
            "charcoalLogs",
            "charcoalBatch",
            "torchesPerCraft",
            "torchBatch",
            "fenceParts");
    private static final PlanTuning DEFAULTS = new PlanTuning(32, 4, 5, 3, 8, 4, 4, 4, 2, 4, 16, 4);

    public PlanTuning {
        requirePositive(mineFloorTake, "mineFloorTake");
        requirePositive(leftOreRadius, "leftOreRadius");
        requirePositive(mineTunnelSize, "mineTunnelSize");
        requirePositive(mineStepsSize, "mineStepsSize");
        requirePositive(supplyContainerRadius, "supplyContainerRadius");
        requirePositive(dismantleRadius, "dismantleRadius");
        requirePositive(relocateClearRadius, "relocateClearRadius");
        requirePositive(charcoalLogs, "charcoalLogs");
        requirePositive(charcoalBatch, "charcoalBatch");
        requirePositive(torchesPerCraft, "torchesPerCraft");
        requirePositive(torchBatch, "torchBatch");
        requirePositive(fenceParts, "fenceParts");
    }

    public static PlanTuning defaults() {
        return DEFAULTS;
    }

    public static PlanTuning fromJson(@Nullable JsonNode node, String origin) {
        Objects.requireNonNull(origin, "Источник таблицы не может быть null: сообщение об ошибке называет файл");
        if (node == null || node.isNull()) {
            return DEFAULTS;
        }
        if (!node.isObject()) {
            throw new IllegalStateException(
                    "Файл '" + origin + "': поле 'plans' — не объект, ожидается набор размеров планов");
        }
        JsonResource.checkFields(node, FIELDS, origin, "поля 'plans'");
        return new PlanTuning(
                integer(node, "mineFloorTake", origin),
                integer(node, "leftOreRadius", origin),
                integer(node, "mineTunnelSize", origin),
                integer(node, "mineStepsSize", origin),
                integer(node, "supplyContainerRadius", origin),
                integer(node, "dismantleRadius", origin),
                integer(node, "relocateClearRadius", origin),
                integer(node, "charcoalLogs", origin),
                integer(node, "charcoalBatch", origin),
                integer(node, "torchesPerCraft", origin),
                integer(node, "torchBatch", origin),
                integer(node, "fenceParts", origin));
    }

    private static int integer(JsonNode node, String field, String origin) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber()) {
            throw new IllegalStateException("Файл '" + origin + "': поле 'plans." + field
                    + "' обязано быть целым числом — размер плана читается из данных");
        }
        return value.intValue();
    }

    private static void requirePositive(int value, String field) {
        if (value < 1) {
            throw new IllegalArgumentException("Поле plans." + field + " = " + value
                    + ": размер плана обязан быть положительным, иначе задание пустое и дух встанет");
        }
    }
}
