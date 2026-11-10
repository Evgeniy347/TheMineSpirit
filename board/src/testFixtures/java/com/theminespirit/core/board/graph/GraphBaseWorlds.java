package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.GraphBlock;
import com.theminespirit.core.board.graph.GraphFacts.Position;
import com.theminespirit.core.board.graph.GraphSiteFacts.StageReqs;
import com.theminespirit.core.board.graph.GraphSiteFacts.Stray;
import com.theminespirit.core.board.graph.GraphSiteFacts.Terrain;
import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GraphBaseWorlds {
    record BaseWorld(String name, GraphWorld world, int stopStage) {}

    private GraphBaseWorlds() {}

    static List<BaseWorld> baseWorlds() {
        return List.of(
                new BaseWorld("live1", GraphMutations.liveStageOne(), 2),
                new BaseWorld("dirty", dirty(), 4),
                new BaseWorld("failures", failures(), 4),
                new BaseWorld("night", nightThreat(), 2),
                new BaseWorld("noaxe", emptyNoAxe(), 4));
    }

    private static GraphWorld ready(int stage, int spirits) {
        GraphWorld world = GraphWorld.empty();
        world.world.clear();
        world.stage = stage;
        for (SupplyGraph.Building building : GraphWorld.GRAPH.buildings().values()) {
            boolean optional = building.optional();
            boolean triggered = GraphWorld.GRAPH.triggers().stream()
                    .anyMatch(trigger -> trigger.build().equals(building.id()));
            Built state = optional
                    ? (triggered ? Built.BUILT : Built.ABSENT)
                    : (building.stage() <= stage ? Built.BUILT : Built.ABSENT);
            world.buildings.put(building.id(), state);
        }
        String last = "kennel";
        if (stage != 3 || !GraphWorld.GRAPH.buildings().containsKey(last)) {
            last = GraphWorld.GRAPH.buildings().values().stream()
                    .filter(building -> !building.optional() && building.stage() == stage)
                    .map(SupplyGraph.Building::id)
                    .reduce((first, second) -> second)
                    .orElse(null);
        }
        if (last != null) {
            world.buildings.put(last, Built.PLANNED);
        }
        for (int index = 1; index <= spirits; index++) {
            world.tools("spirit-" + index, 3);
        }
        for (SupplyGraph.Item item : GraphWorld.GRAPH.items().values()) {
            world.stock.put(item.id(), item.id().startsWith("herd_") ? 0 : 100_000);
        }
        return world;
    }

    static GraphWorld dirty() {
        GraphWorld world = ready(3, 20);
        world.world("more:player_near");
        world.world("more:player_building");
        world.world("more:dusk_soon");
        world.world("more:bad_trip_conditions");
        List<Stray> strays = new ArrayList<>();
        for (int index = 0; index < 236; index++) {
            strays.add(new Stray("stray-" + index, "wall", new Position(index, 64, 0), null, Map.of("wall", 1)));
        }
        Map<String, GraphFacts.Building> damaged = new LinkedHashMap<>();
        for (String id : List.of("crafting_table", "furnace", "clearing", "chests")) {
            damaged.put(
                    id,
                    new GraphFacts.Building(id, 0, 60, 5, Map.of("minecraft:dirt", 3), Map.of(), 0, null, false, true));
        }
        world.facts = new GraphFacts(
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                damaged,
                strays,
                new Terrain(
                        760,
                        120,
                        50,
                        5,
                        30,
                        Map.of("minecraft:dirt", 100),
                        Map.of("minecraft:dirt", 200),
                        Map.of("minecraft:oak_log", 50),
                        false),
                new StageReqs(Map.of(), Map.of()),
                new GraphFeedback(
                        new GraphFeedback.Clock(false, true, false, 12_000),
                        new GraphFeedback.Player(null, true, true, false, false),
                        List.of()),
                List.of(),
                List.of(),
                List.of());
        return world;
    }

    static GraphWorld failures() {
        GraphWorld world = GraphWorld.settled();
        world.world("more:work_duplicate");
        world.world("more:failures_repeat");
        world.distances.put("tree:oak", 240);
        world.distances.put("tree:jungle", 240);
        world.distances.put("animals", 180);
        world.facts = GraphFacts.empty()
                .withFeedback(new GraphFeedback(
                        GraphFeedback.Clock.unknown(),
                        GraphFeedback.Player.none(),
                        List.of(
                                new GraphFeedback.Failure(
                                        "mine_cobble", "1,64,0", 2, new GraphBlock.Path("1,64,0"), 10),
                                new GraphFeedback.Failure(
                                        "mine_cobble", "9,64,0", 3, new GraphBlock.Path("9,64,0"), 11),
                                new GraphFeedback.Failure(
                                        "chop_oak_log", "1,64,0", 2, new GraphBlock.Path("1,64,0"), 20),
                                new GraphFeedback.Failure(
                                        "chop_oak_log", "9,64,0", 2, new GraphBlock.Path("9,64,0"), 21),
                                new GraphFeedback.Failure("mine_iron", "1,64,0", 3, new GraphBlock.Path("1,64,0"), 30),
                                new GraphFeedback.Failure("mine_iron", "1,64,0", 1, null, 31))));
        return world;
    }

    static GraphWorld nightThreat() {
        GraphWorld world = GraphMutations.liveStageOne();
        world.event("night");
        world.event("expedition_ready");
        for (String tag : List.of("tree:oak", "tree:jungle", "animals")) {
            world.distances.put(tag, 80);
        }
        List<GraphFacts.Threat> threats = List.of(
                new GraphFacts.Threat(
                        "threat-0",
                        "minecraft:zombie",
                        10.0f,
                        20.0f,
                        new Position(0, 64, 0),
                        "spirit-1",
                        GraphFacts.TargetKind.SPIRIT,
                        true,
                        true,
                        null,
                        3,
                        null,
                        0.2),
                new GraphFacts.Threat(
                        "threat-1",
                        "minecraft:skeleton",
                        8.0f,
                        20.0f,
                        new Position(4, 64, 0),
                        "player",
                        GraphFacts.TargetKind.PLAYER,
                        false,
                        true,
                        null,
                        6,
                        null,
                        0.22));
        world.facts = world.facts.withThreats(threats);
        return world;
    }

    static GraphWorld emptyNoAxe() {
        GraphWorld world = GraphWorld.empty();
        world.distances.put("tree:oak", 8);
        world.distances.put("tree:jungle", 8);
        return world;
    }
}
