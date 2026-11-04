package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.JsonNode;

public record GraphChoice(
        double farTrip,
        double slowWork,
        double duplicate,
        double conflict,
        double pileup,
        double strategicBoost,
        double bottleneckBoost,
        double idleStationBoost,
        double nightHome,
        double spaceFoundBoost,
        double spaceTight,
        double petAlone,
        double herdFull,
        double fieldFull,
        double failureBackoff) {
    public static final GraphChoice NEUTRAL =
            new GraphChoice(1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 1.0, 1.0, 0.5);

    public GraphChoice {
        requireNonNegative("дальняя дорога", farTrip);
        requireNonNegative("медленная работа", slowWork);
        requireNonNegative("дубль работы", duplicate);
        requireNonNegative("конфликт работ", conflict);
        requireNonNegative("скопление работ", pileup);
        requireNonNegative("стратегическая цель", strategicBoost);
        requireNonNegative("узкое место этапа", bottleneckBoost);
        requireNonNegative("простой станции", idleStationBoost);
        requireNonNegative("работа ночью", nightHome);
        requireNonNegative("свободное место", spaceFoundBoost);
        requireNonNegative("тесное место", spaceTight);
        requireNonNegative("приручённый питомец", petAlone);
        requireNonNegative("полный загон", herdFull);
        requireNonNegative("полное поле", fieldFull);
        requireNonNegative("память неудач", failureBackoff);
    }

    static GraphChoice parse(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return NEUTRAL;
        }
        return new GraphChoice(
                root.path("farTrip").asDouble(NEUTRAL.farTrip()),
                root.path("slowWork").asDouble(NEUTRAL.slowWork()),
                root.path("duplicate").asDouble(NEUTRAL.duplicate()),
                root.path("conflict").asDouble(NEUTRAL.conflict()),
                root.path("pileup").asDouble(NEUTRAL.pileup()),
                root.path("strategicBoost").asDouble(NEUTRAL.strategicBoost()),
                root.path("bottleneckBoost").asDouble(NEUTRAL.bottleneckBoost()),
                root.path("idleStationBoost").asDouble(NEUTRAL.idleStationBoost()),
                root.path("nightHome").asDouble(NEUTRAL.nightHome()),
                root.path("spaceFoundBoost").asDouble(NEUTRAL.spaceFoundBoost()),
                root.path("spaceTight").asDouble(NEUTRAL.spaceTight()),
                root.path("petAlone").asDouble(NEUTRAL.petAlone()),
                root.path("herdFull").asDouble(NEUTRAL.herdFull()),
                root.path("fieldFull").asDouble(NEUTRAL.fieldFull()),
                root.path("failureBackoff").asDouble(NEUTRAL.failureBackoff()));
    }

    private static void requireNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException("Поправка выбора «" + name + "» = " + value
                    + ": она неотрицательна и конечна, " + "нейтральное значение — 1 у множителя и 0 у добавки");
        }
    }
}
