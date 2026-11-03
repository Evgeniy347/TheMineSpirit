package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.board.graph.GraphNames;
import com.theminespirit.core.board.graph.GraphState;
import com.theminespirit.core.board.graph.GraphState.Built;
import com.theminespirit.core.board.graph.More6Rules;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;

final class More6Inputs {
    private static final String FIELD = "field";
    private static final String FARM = "farm";

    private More6Inputs() {}

    static GraphState apply(
            GraphState state, ServerLevel level, GraphSurroundings.Around around, FirstStepsRunner.StageScan scan) {
        More6Rules.Signals signals = signals(state, level, around, scan);
        return events(state, More6Rules.events(signals));
    }

    static GraphState events(GraphState state, Set<String> more) {
        Set<String> events = new LinkedHashSet<>(state.events());
        events.addAll(more);
        return new GraphState(
                state.stock(),
                state.stage(),
                state.kits(),
                state.buildings(),
                state.materials(),
                state.storageFreePercent(),
                Set.copyOf(events),
                state.world(),
                state.distances(),
                state.stations(),
                state.orders(),
                state.facts());
    }

    static More6Rules.Signals signals(
            GraphState state, ServerLevel level, GraphSurroundings.Around around, FirstStepsRunner.StageScan scan) {
        GraphFacts facts = state.facts();
        More6Scan.StationScan stations = More6Scan.stations(level, scan.stage(), builtStations(state, facts));
        List<String> idle = idleSpirits(facts);
        More6Store store = More6Store.get(level);
        long longest = store.noteIdle(idle, level.getGameTime());
        return new More6Rules.Signals(
                More6Rules.population(facts.spirits().size(), More6Scan.beds(level, scan.home())),
                More6Rules.risk(facts.spirits().values(), home(scan.home()), facts.threats()),
                More6Rules.unfinished(facts.buildings()),
                More6Rules.carried(carried(facts), needed(state)),
                More6Rules.stations(stations.unfuelled(), stations.misplaced()),
                More6Rules.loose(More6Scan.looseItems(level, scan.home())),
                More6Rules.fluids(
                        More6Scan.dryFields(level, fields(state, facts)),
                        around.resource("lava").isPresent() ? 1 : 0),
                More6Rules.demand(state.materials()),
                More6Rules.idle((int) Math.min(longest, Integer.MAX_VALUE), idle.size()));
    }

    static Map<String, Integer> carried(GraphFacts facts) {
        Map<String, Integer> carried = new LinkedHashMap<>();
        facts.spirits()
                .values()
                .forEach(spirit -> spirit.inventory()
                        .forEach((item, count) -> carried.merge(GraphNames.graphItem(item), count, Integer::sum)));
        return carried;
    }

    static Set<String> needed(GraphState state) {
        Set<String> needed = new LinkedHashSet<>();
        state.materials().values().forEach(materials -> needed.addAll(materials.keySet()));
        needed.addAll(state.orders().keySet());
        return needed;
    }

    static List<GraphFacts.Position> fields(GraphState state, GraphFacts facts) {
        List<GraphFacts.Position> fields = new ArrayList<>();
        facts.buildings().forEach((id, building) -> {
            if (state.built(id) == Built.BUILT
                    && (id.contains(FIELD) || FARM.equals(id))
                    && building.position() != null) {
                fields.add(building.position());
            }
        });
        return fields;
    }

    static Set<String> builtStations(GraphState state, GraphFacts facts) {
        Set<String> stations = new LinkedHashSet<>();
        for (String id : facts.stations().keySet()) {
            if (state.built(id) == Built.BUILT) {
                stations.add(id);
            }
        }
        return stations;
    }

    static List<String> idleSpirits(GraphFacts facts) {
        List<String> idle = new ArrayList<>();
        facts.spirits().values().stream()
                .filter(spirit -> spirit.activity() == null || spirit.activity().isBlank())
                .map(GraphFacts.Spirit::id)
                .sorted()
                .forEach(idle::add);
        return idle;
    }

    private static GraphFacts.Position home(BlockCoord home) {
        return new GraphFacts.Position(home.x(), home.y(), home.z());
    }
}
