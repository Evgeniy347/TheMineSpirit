package com.theminespirit.core.board;

import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.building.DemoBuildings;
import com.theminespirit.core.building.SchematicGround;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
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
        boolean ground = entry(planId)
                .flatMap(found -> DataTables.INSTANCE.presets().current().byId(found.scheme()))
                .map(preset -> SchematicGround.groundLayer(preset.schematic()))
                .orElse(false);
        return ground ? levelY - 1 : levelY;
    }

    public static boolean placed(String planId) {
        return entry(planId)
                .map(entry -> entry.boardCase() == BoardCase.BUILD_PLACED)
                .orElse(false);
    }

    public static boolean watcherOwned(BoardCase boardCase) {
        Objects.requireNonNull(boardCase, "кейс постройки не может быть null: по нему видно, чей это сторож");
        return false;
    }

    public static Optional<String> graphId(String planId) {
        Objects.requireNonNull(planId, "id постройки плана не может быть null: по нему ищут постройку графа");
        return graphBuilding(planId).map(SupplyGraph.Building::id);
    }

    public static Optional<String> planId(String ticketId) {
        Objects.requireNonNull(ticketId, "id задания не может быть null: по нему ищут постройку плана");
        if (!ticketId.startsWith(GRAPH_BUILDING) || ticketId.length() == GRAPH_BUILDING.length()) {
            return Optional.empty();
        }
        return Optional.of(ticketId.substring(GRAPH_BUILDING.length()));
    }

    private static Optional<SupplyGraph.Building> graphBuilding(String planId) {
        Map<String, SupplyGraph.Building> buildings =
                DataTables.INSTANCE.graph().current().buildings();
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
                    || DataTables.INSTANCE.graph().current().buildings().containsKey(candidate)) {
                return Optional.of(candidate);
            }
            cut = candidate.lastIndexOf('_');
        }
        return Optional.empty();
    }
}
