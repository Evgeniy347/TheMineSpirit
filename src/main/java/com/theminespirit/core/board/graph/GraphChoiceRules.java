package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.BoardCase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

final class GraphChoiceRules {
    private static final String BUILDING = "building:";
    private static final String EVENT = "event:";
    private static final char AT = '@';

    private final SupplyGraph graph;
    private final GraphState state;
    private final Map<String, SupplyGraph.Way> ways = new LinkedHashMap<>();

    GraphChoiceRules(SupplyGraph graph, GraphState state) {
        this.graph = graph;
        this.state = state;
        graph.ways().forEach(way -> ways.putIfAbsent(way.id(), way));
    }

    static List<Map.Entry<String, Double>> ordered(Map<String, Double> scores) {
        return scores.entrySet().stream()
                .sorted((one, two) -> {
                    int byValue = Double.compare(two.getValue(), one.getValue());
                    return byValue != 0 ? byValue : one.getKey().compareTo(two.getKey());
                })
                .toList();
    }

    String makes(String key) {
        if (key.startsWith(BUILDING) || key.startsWith(EVENT) || key.startsWith("explore:")) {
            return key;
        }
        int at = key.indexOf(AT);
        SupplyGraph.Way way = ways.get(at < 0 ? key : key.substring(0, at));
        return way == null ? key : way.makes();
    }

    double value(String key, double value, boolean repeat) {
        GraphChoice tuning = graph.costs().choice();
        SupplyGraph.Way way = way(key);
        double factor = 1.0;
        double bonus = 0.0;
        if (state.world().contains(MoreRules.STRATEGIC_GOAL)) {
            bonus += tuning.strategicBoost();
        }
        if (key.startsWith(BUILDING)) {
            String id = key.substring(BUILDING.length());
            SupplyGraph.Building building = graph.buildings().get(id);
            if (state.events().contains(More4Rules.SPACE_FOUND)) {
                bonus += tuning.spaceFoundBoost();
            }
            if (state.events().contains(More4Rules.SPACE_TIGHT)) {
                factor *= tuning.spaceTight();
            }
            if (state.events().contains(More3Rules.STAGE_SLOW)
                    && building != null
                    && building.stage() == state.stage()) {
                bonus += tuning.bottleneckBoost();
            }
        } else if (way != null) {
            if (state.events().contains(More3Rules.STAGE_SLOW) && stageOf(way) == state.stage()) {
                bonus += tuning.bottleneckBoost();
            }
            if (state.events().contains(More3Rules.STATION_IDLE) && idleStation(way)) {
                bonus += tuning.idleStationBoost();
            }
            if (state.events().contains(More3Rules.PLAYER_SLEEP) && travel(key) > 0) {
                factor *= tuning.nightHome();
            }
            if (state.events().contains(More4Rules.TAMED_PET) && way.boardCase() == BoardCase.ACTIVITY_GUARD) {
                factor *= tuning.petAlone();
            }
            if (state.events().contains(More7Rules.PEN_FULL) && addsHerd(way)) {
                factor *= tuning.herdFull();
            }
            if (state.events().contains(More7Rules.FIELD_FULL) && sows(way)) {
                factor *= tuning.fieldFull();
            }
        }
        if (repeat) {
            if (state.world().contains(MoreRules.WORK_DUPLICATE)) {
                factor *= tuning.duplicate();
            }
            if (state.events().contains(More3Rules.WORK_CONFLICT)) {
                factor *= tuning.conflict();
            }
            if (state.events().contains(More7Rules.WORK_PILEUP)) {
                factor *= tuning.pileup();
            }
        }
        int failures = failures(key);
        if (failures > 0) {
            factor *= Math.pow(tuning.failureBackoff(), Math.min(failures, 3));
        }
        return value * factor + bonus;
    }

    int travelTicks(String key) {
        int raw = travel(key);
        if (!state.world().contains(MoreRules.BAD_TRIP_CONDITIONS) && failures(key) == 0) {
            return raw;
        }
        return (int) Math.round(raw * graph.costs().choice().farTrip());
    }

    private int failures(String key) {
        String base = baseKey(key);
        if (life(base)) {
            return 0;
        }
        int total = 0;
        for (GraphFeedback.Failure failure : state.facts().feedback().failures()) {
            if (failure.node().equals(key) || failure.node().equals(base)) {
                total += failure.count();
            }
        }
        return total;
    }

    private static boolean life(String base) {
        return "rescue_spirit".equals(base) || base.startsWith(EVENT + "life_");
    }

    private static String baseKey(String key) {
        int at = key.indexOf(AT);
        return at < 0 ? key : key.substring(0, at);
    }

    int wayTicks(String key, SupplyGraph.Way way) {
        int ticks = graph.costs().ticks(way.id(), way.boardCase()) + travelTicks(key);
        if (state.events().contains(More3Rules.WORK_YIELD_LOW)) {
            ticks = (int) Math.round(ticks * graph.costs().choice().slowWork());
        }
        return ticks;
    }

    private SupplyGraph.@Nullable Way way(String key) {
        if (key.startsWith(EVENT)) {
            String[] parts = key.split(":", 3);
            return parts.length == 3 ? ways.get(parts[2]) : null;
        }
        int at = key.indexOf(AT);
        return ways.get(at < 0 ? key : key.substring(0, at));
    }

    private int travel(String key) {
        int at = key.indexOf(AT);
        SupplyGraph.Way way = ways.get(at < 0 ? key : key.substring(0, at));
        String resource = way == null ? null : way.world();
        return resource == null ? 0 : graph.costs().travel(state.distances().getOrDefault(resource, 0));
    }

    private int stageOf(SupplyGraph.Way way) {
        SupplyGraph.Item item = graph.items().get(way.makes());
        return item == null ? -1 : item.stage();
    }

    private boolean idleStation(SupplyGraph.Way way) {
        String building = way.building();
        if (building == null || !graph.costs().stationCapacity().containsKey(building)) {
            return false;
        }
        return state.facts().stations().values().stream()
                .anyMatch(station -> !station.busy()
                        && (station.id().equals(building) || station.type().equals(building)));
    }

    private boolean addsHerd(SupplyGraph.Way way) {
        return way.boardCase() == BoardCase.BREED_ANIMALS || way.boardCase() == BoardCase.CAPTURE_ANIMAL;
    }

    private boolean sows(SupplyGraph.Way way) {
        return way.consumes().keySet().stream().anyMatch(item -> "#seeds".equals(item) || item.endsWith("_seeds"));
    }
}
