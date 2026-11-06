package com.theminespirit.core.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.theminespirit.core.inventory.JsonResource;
import java.util.Set;

public record RouteRules(
        double walkTicks,
        double waterTicks,
        double climbTicks,
        double jumpPenalty,
        double placeTicks,
        double breakPenalty,
        double fallTicks,
        int maxFall,
        int maxWaterFall,
        int maxWaterDepth,
        int maxNodes,
        double heuristicWeight,
        int minPartial) {
    public static final String RESOURCE = "data/theminespirit/movement/route.json";
    private static final Set<String> FIELDS = Set.of(
            "walkTicks",
            "waterTicks",
            "climbTicks",
            "jumpPenalty",
            "placeTicks",
            "breakPenalty",
            "fallTicks",
            "maxFall",
            "maxWaterFall",
            "maxWaterDepth",
            "maxNodes",
            "heuristicWeight",
            "minPartial");

    public RouteRules {
        if (!(walkTicks > 0) || !(waterTicks > 0) || !(climbTicks > 0)) {
            throw new IllegalArgumentException("Цены ходьбы, плавания и лазанья (" + walkTicks + ", " + waterTicks
                    + ", " + climbTicks + ") должны быть положительны: бесплатный ход ломает A*");
        }
        if (jumpPenalty < 0 || placeTicks < 0 || breakPenalty < 0 || fallTicks < 0) {
            throw new IllegalArgumentException(
                    "Надбавки за прыжок, установку, ломку и падение не бывают отрицательными: "
                            + "иначе маршрут выгоднее удлинять");
        }
        if (maxFall < 1 || maxWaterFall < maxFall) {
            throw new IllegalArgumentException("Падение: maxFall = " + maxFall + ", maxWaterFall = " + maxWaterFall
                    + ": спуск хотя бы на 1, в воду — не меньше, чем на землю");
        }
        if (maxWaterDepth < 0) {
            throw new IllegalArgumentException("Глубина воды " + maxWaterDepth
                    + " отрицательна: вода у поверхности (0) проходит, глубже — только с выходом к воздуху");
        }
        if (maxNodes < 1 || !(heuristicWeight >= 1) || minPartial < 1) {
            throw new IllegalArgumentException("Бюджет поиска: maxNodes = " + maxNodes + ", вес эвристики = "
                    + heuristicWeight + ", minPartial = " + minPartial
                    + ": нужен хотя бы один узел, вес не меньше 1 и выигрыш частичного пути хотя бы в блок");
        }
    }

    public static RouteRules parse(String json, String origin) {
        JsonNode root = JsonResource.parse(json, origin);
        JsonResource.checkFields(root, FIELDS, origin, "route.json");
        try {
            return new RouteRules(
                    JsonResource.decimal(root.get("walkTicks"), origin, "walkTicks"),
                    JsonResource.decimal(root.get("waterTicks"), origin, "waterTicks"),
                    JsonResource.decimal(root.get("climbTicks"), origin, "climbTicks"),
                    JsonResource.decimal(root.get("jumpPenalty"), origin, "jumpPenalty"),
                    JsonResource.decimal(root.get("placeTicks"), origin, "placeTicks"),
                    JsonResource.decimal(root.get("breakPenalty"), origin, "breakPenalty"),
                    JsonResource.decimal(root.get("fallTicks"), origin, "fallTicks"),
                    JsonResource.integer(root.get("maxFall"), origin, "maxFall"),
                    JsonResource.integer(root.get("maxWaterFall"), origin, "maxWaterFall"),
                    JsonResource.integer(root.get("maxWaterDepth"), origin, "maxWaterDepth"),
                    JsonResource.integer(root.get("maxNodes"), origin, "maxNodes"),
                    JsonResource.decimal(root.get("heuristicWeight"), origin, "heuristicWeight"),
                    JsonResource.integer(root.get("minPartial"), origin, "minPartial"));
        } catch (IllegalArgumentException error) {
            throw new IllegalStateException("Файл '" + origin + "': " + error.getMessage(), error);
        }
    }

    public static RouteRules load() {
        return parse(JsonResource.readClasspath(RESOURCE), RESOURCE);
    }
}
