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
        int flowRadius,
        int dropRadius,
        int decayRadius,
        int playerRadius,
        int portalRadius,
        int hazardScanRadius,
        int petRadius,
        int spaceRadius,
        int growRadius,
        int mineRadius,
        int homeRadius,
        int looseRadius,
        int stationRadius,
        int fieldRadius,
        int conflictRadius,
        int threatRadius,
        int farmScanRadius,
        int herdSurveyRadius,
        int lifeScanRadius,
        int nearbyPlayerRadius,
        int dropScanRadius,
        int areaScanRadius,
        int stationSearchRadius,
        int standSnapRadius,
        int homeSearchRadius,
        int hostileScanRadius,
        int chestSearchRadius,
        int settlementScanRadius,
        int enemyScanRadius,
        int workPointRadius,
        int furnaceSearchRadius) {
    private static final Set<String> FIELDS = Set.of(
            "animalRadius",
            "penRadius",
            "wildRadius",
            "villagerRadius",
            "bedRadius",
            "hazardRadius",
            "flowRadius",
            "dropRadius",
            "decayRadius",
            "playerRadius",
            "portalRadius",
            "hazardScanRadius",
            "petRadius",
            "spaceRadius",
            "growRadius",
            "mineRadius",
            "homeRadius",
            "looseRadius",
            "stationRadius",
            "fieldRadius",
            "conflictRadius",
            "threatRadius",
            "farmScanRadius",
            "herdSurveyRadius",
            "lifeScanRadius",
            "nearbyPlayerRadius",
            "dropScanRadius",
            "areaScanRadius",
            "stationSearchRadius",
            "standSnapRadius",
            "homeSearchRadius",
            "hostileScanRadius",
            "chestSearchRadius",
            "settlementScanRadius",
            "enemyScanRadius",
            "workPointRadius",
            "furnaceSearchRadius");
    private static final FactTuning DEFAULTS = new FactTuning(
            48, 12, 16, 48, 20, 3, 8, 8, 16, 24, 24, 4, 32, 48, 16, 8, 32, 24, 8, 24, 4, 16, 16, 32, 16, 16, 24, 16, 12,
            16, 16, 16, 16, 64, 16, 32, 4);

    public FactTuning {
        requirePositive(animalRadius, "animalRadius");
        requirePositive(penRadius, "penRadius");
        requirePositive(wildRadius, "wildRadius");
        requirePositive(villagerRadius, "villagerRadius");
        requirePositive(bedRadius, "bedRadius");
        requirePositive(hazardRadius, "hazardRadius");
        requirePositive(flowRadius, "flowRadius");
        requirePositive(dropRadius, "dropRadius");
        requirePositive(decayRadius, "decayRadius");
        requirePositive(playerRadius, "playerRadius");
        requirePositive(portalRadius, "portalRadius");
        requirePositive(hazardScanRadius, "hazardScanRadius");
        requirePositive(petRadius, "petRadius");
        requirePositive(spaceRadius, "spaceRadius");
        requirePositive(growRadius, "growRadius");
        requirePositive(mineRadius, "mineRadius");
        requirePositive(homeRadius, "homeRadius");
        requirePositive(looseRadius, "looseRadius");
        requirePositive(stationRadius, "stationRadius");
        requirePositive(fieldRadius, "fieldRadius");
        requirePositive(conflictRadius, "conflictRadius");
        requirePositive(threatRadius, "threatRadius");
        requirePositive(farmScanRadius, "farmScanRadius");
        requirePositive(herdSurveyRadius, "herdSurveyRadius");
        requirePositive(lifeScanRadius, "lifeScanRadius");
        requirePositive(nearbyPlayerRadius, "nearbyPlayerRadius");
        requirePositive(dropScanRadius, "dropScanRadius");
        requirePositive(areaScanRadius, "areaScanRadius");
        requirePositive(stationSearchRadius, "stationSearchRadius");
        requirePositive(standSnapRadius, "standSnapRadius");
        requirePositive(homeSearchRadius, "homeSearchRadius");
        requirePositive(hostileScanRadius, "hostileScanRadius");
        requirePositive(chestSearchRadius, "chestSearchRadius");
        requirePositive(settlementScanRadius, "settlementScanRadius");
        requirePositive(enemyScanRadius, "enemyScanRadius");
        requirePositive(workPointRadius, "workPointRadius");
        requirePositive(furnaceSearchRadius, "furnaceSearchRadius");
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
                integer(node, "flowRadius", origin),
                integer(node, "dropRadius", origin),
                integer(node, "decayRadius", origin),
                integer(node, "playerRadius", origin),
                integer(node, "portalRadius", origin),
                integer(node, "hazardScanRadius", origin),
                integer(node, "petRadius", origin),
                integer(node, "spaceRadius", origin),
                integer(node, "growRadius", origin),
                integer(node, "mineRadius", origin),
                integer(node, "homeRadius", origin),
                integer(node, "looseRadius", origin),
                integer(node, "stationRadius", origin),
                integer(node, "fieldRadius", origin),
                integer(node, "conflictRadius", origin),
                integer(node, "threatRadius", origin),
                integer(node, "farmScanRadius", origin),
                integer(node, "herdSurveyRadius", origin),
                integer(node, "lifeScanRadius", origin),
                integer(node, "nearbyPlayerRadius", origin),
                integer(node, "dropScanRadius", origin),
                integer(node, "areaScanRadius", origin),
                integer(node, "stationSearchRadius", origin),
                integer(node, "standSnapRadius", origin),
                integer(node, "homeSearchRadius", origin),
                integer(node, "hostileScanRadius", origin),
                integer(node, "chestSearchRadius", origin),
                integer(node, "settlementScanRadius", origin),
                integer(node, "enemyScanRadius", origin),
                integer(node, "workPointRadius", origin),
                integer(node, "furnaceSearchRadius", origin));
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
