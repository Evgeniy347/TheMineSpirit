package com.theminespirit.core.board;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.api.JsonResource;
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
        int fenceParts,
        int oreCount,
        int oreRadius,
        int logsPerTree,
        int clearGrid,
        int clearHeight,
        int freeRadius,
        int freeDirtReach,
        int farmRadius,
        int farmCount,
        int containerRadius,
        int craterFillRadius,
        int demolitionRadius,
        int demolitionHeight,
        int demolitionBatch) {
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
            "fenceParts",
            "oreCount",
            "oreRadius",
            "logsPerTree",
            "clearGrid",
            "clearHeight",
            "freeRadius",
            "freeDirtReach",
            "farmRadius",
            "farmCount",
            "containerRadius",
            "craterFillRadius",
            "demolitionRadius",
            "demolitionHeight",
            "demolitionBatch");
    private static final PlanTuning DEFAULTS =
            new PlanTuning(32, 4, 5, 3, 8, 4, 4, 4, 2, 4, 16, 4, 8, 16, 4, 3, 3, 16, 13, 8, 4, 8, 1, 4, 2, 48);

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
        requirePositive(oreCount, "oreCount");
        requirePositive(oreRadius, "oreRadius");
        requirePositive(logsPerTree, "logsPerTree");
        requirePositive(clearGrid, "clearGrid");
        requirePositive(clearHeight, "clearHeight");
        requirePositive(freeRadius, "freeRadius");
        requirePositive(freeDirtReach, "freeDirtReach");
        requirePositive(farmRadius, "farmRadius");
        requirePositive(farmCount, "farmCount");
        requirePositive(containerRadius, "containerRadius");
        requirePositive(craterFillRadius, "craterFillRadius");
        requirePositive(demolitionRadius, "demolitionRadius");
        requirePositive(demolitionHeight, "demolitionHeight");
        requirePositive(demolitionBatch, "demolitionBatch");
    }

    static PlanTuning defaults() {
        return DEFAULTS;
    }

    static PlanTuning fromJson(@Nullable JsonNode node, String origin) {
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
                integer(node, "fenceParts", origin),
                integer(node, "oreCount", origin),
                integer(node, "oreRadius", origin),
                integer(node, "logsPerTree", origin),
                integer(node, "clearGrid", origin),
                integer(node, "clearHeight", origin),
                integer(node, "freeRadius", origin),
                integer(node, "freeDirtReach", origin),
                integer(node, "farmRadius", origin),
                integer(node, "farmCount", origin),
                integer(node, "containerRadius", origin),
                integer(node, "craterFillRadius", origin),
                integer(node, "demolitionRadius", origin),
                integer(node, "demolitionHeight", origin),
                integer(node, "demolitionBatch", origin));
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
