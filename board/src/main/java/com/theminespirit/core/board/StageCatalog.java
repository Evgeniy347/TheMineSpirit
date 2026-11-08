package com.theminespirit.core.board;

import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.building.DemoBuildings;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.TownPlan;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class StageCatalog {
    private static final Map<String, String> GRAPH_ALIASES = Map.of("storage", "warehouse", "pen", "pen_cow");
    private static final String GRAPH_BUILDING = "graph:building:";

    public record Entry(String id, BoardCase boardCase, String scheme) {
        public Entry {
            Objects.requireNonNull(id, "id постройки плана не может быть null");
            Objects.requireNonNull(boardCase, "Кейс доски не может быть null: без него не выдать задание");
            Objects.requireNonNull(scheme, "Схема постройки не может быть null: без неё нечего строить");
        }
    }

    private StageCatalog() {}

    public static Optional<Entry> entry(String planId) {
        Objects.requireNonNull(planId, "id постройки плана не может быть null: по нему берут кейс и схему");
        Optional<SupplyGraph.Building> building = graphBuilding(planId);
        Optional<DemoBuildings.Building> catalog = catalogBuilding(planId);
        if (building.isEmpty() || catalog.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Entry(
                planId,
                building.orElseThrow().boardCase(),
                catalog.orElseThrow().level(1).scheme()));
    }

    public static Optional<BlockCoord> origin(String planId, TownPlan plan, int levelY) {
        Objects.requireNonNull(planId, "id постройки плана не может быть null: по нему ищут участок");
        Objects.requireNonNull(plan, "План поселения не может быть null: постройка встаёт в свой слот");
        int y = originY(planId, levelY);
        return plan.placement(planId).map(place -> new BlockCoord(place.x(), y, place.z()));
    }

    public static int originY(String planId, int levelY) {
        Objects.requireNonNull(planId, "id постройки плана не может быть null: по нему ищут участок");
        return levelY;
    }

    public static boolean placed(String planId) {
        return entry(planId)
                .map(entry -> entry.boardCase() == BoardCase.BUILD_PLACED || latePlan(planId))
                .orElse(false);
    }

    private static boolean latePlan(String planId) {
        for (StageRules.Stage stage : StageRules.current().stages()) {
            if (stage.stage() >= 3 && stage.plan().contains(planId)) {
                return true;
            }
        }
        return false;
    }

    public static boolean watcherOwned(BoardCase boardCase) {
        Objects.requireNonNull(boardCase, "кейс постройки не может быть null: по нему видно, чей это сторож");
        return false;
    }

    public static Optional<String> graphId(String planId) {
        Objects.requireNonNull(planId, "id постройки плана не может быть null: по нему ищут постройку графа");
        return graphBuilding(planId).map(SupplyGraph.Building::id);
    }

    public static Optional<DemoBuildings.Building> catalog(String planId) {
        Objects.requireNonNull(planId, "id постройки плана не может быть null: по нему ищут семью каталога");
        return catalogBuilding(planId);
    }

    public static Optional<String> scheme(String planId, int level) {
        Objects.requireNonNull(planId, "id постройки плана не может быть null: по нему ищут схему уровня");
        if (level < 1) {
            throw new IllegalArgumentException(
                    "Уровень " + level + " у постройки '" + planId + "': схемы уровней начинаются с первого");
        }
        return catalogBuilding(planId)
                .flatMap(building -> building.levels().stream()
                        .filter(value -> value.level() == level)
                        .map(DemoBuildings.Level::scheme)
                        .findFirst());
    }

    public static int maxLevel(String planId) {
        Objects.requireNonNull(planId, "id постройки плана не может быть null: по нему ищут высший уровень");
        return catalogBuilding(planId)
                .map(building -> building.levels().stream()
                        .mapToInt(DemoBuildings.Level::level)
                        .max()
                        .orElse(0))
                .orElse(0);
    }

    public static Optional<String> planId(String ticketId) {
        Objects.requireNonNull(ticketId, "id задания не может быть null: по нему ищут постройку плана");
        if (!ticketId.startsWith(GRAPH_BUILDING) || ticketId.length() == GRAPH_BUILDING.length()) {
            return Optional.empty();
        }
        return Optional.of(ticketId.substring(GRAPH_BUILDING.length()));
    }

    private static Optional<SupplyGraph.Building> graphBuilding(String planId) {
        Map<String, SupplyGraph.Building> buildings = SupplyGraph.current().buildings();
        SupplyGraph.Building exact = buildings.get(planId);
        if (exact != null) {
            return Optional.of(exact);
        }
        String alias = GRAPH_ALIASES.get(planId);
        if (alias != null) {
            return Optional.ofNullable(buildings.get(alias));
        }
        return stripped(planId).flatMap(base -> Optional.ofNullable(buildings.get(base)));
    }

    private static Optional<DemoBuildings.Building> catalogBuilding(String planId) {
        Optional<DemoBuildings.Building> exact = DemoBuildings.byId(planId);
        if (exact.isPresent()) {
            return exact;
        }
        return stripped(planId).flatMap(DemoBuildings::byId);
    }

    private static Optional<String> stripped(String planId) {
        String candidate = planId;
        int cut = candidate.lastIndexOf('_');
        while (cut > 0) {
            candidate = candidate.substring(0, cut);
            if (DemoBuildings.byId(candidate).isPresent()
                    || SupplyGraph.current().buildings().containsKey(candidate)) {
                return Optional.of(candidate);
            }
            cut = candidate.lastIndexOf('_');
        }
        return Optional.empty();
    }
}
