package com.theminespirit.core.board.graph;

import com.theminespirit.core.board.GraphBlock;
import com.theminespirit.core.board.graph.GraphFacts.Position;
import com.theminespirit.core.board.graph.GraphFacts.Spirit;
import com.theminespirit.core.board.graph.GraphSiteFacts.StageReqs;
import com.theminespirit.core.board.graph.GraphSiteFacts.Stray;
import com.theminespirit.core.board.graph.GraphSiteFacts.Terrain;
import com.theminespirit.core.board.graph.GraphState.Built;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.UnaryOperator;

final class GraphMutations {
    record Mutation(String name, UnaryOperator<GraphWorld> apply) {}

    private static final List<Mutation> SINGLE = buildSingle();

    private GraphMutations() {}

    static List<Mutation> single() {
        return SINGLE;
    }

    static List<GraphWorld> simulationBases() {
        return baselines();
    }

    static List<Mutation> curated() {
        List<Mutation> all = new ArrayList<>();
        int index = 0;
        for (SupplyGraph.Item item : GraphWorld.GRAPH.items().values()) {
            all.add(new Mutation(item.id() + "=0", world -> world.copy().with(item.id(), 0)));
            if (index++ % 5 == 0) {
                int count = item.xmin() * 4;
                all.add(new Mutation(
                        item.id() + "=" + count, world -> world.copy().with(item.id(), count)));
            }
        }
        for (String building : GraphWorld.GRAPH.buildings().keySet()) {
            for (Built built : Built.values()) {
                all.add(new Mutation(
                        building + "=" + built, world -> world.copy().built(building, built)));
            }
        }
        for (String event : GraphWorld.GRAPH.events().keySet()) {
            all.add(new Mutation("event=" + event, world -> world.copy().event(event)));
        }
        GraphWorld.GRAPH.ways().stream()
                .map(SupplyGraph.Way::world)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(resource -> all.add(new Mutation("нет " + resource, world -> {
                    GraphWorld copy = world.copy();
                    copy.world.remove(resource);
                    return copy;
                })));
        for (int tier = -1; tier <= 2; tier++) {
            int chosen = tier;
            all.add(new Mutation("tools=" + tier, world -> world.copy().tools("spirit-1", chosen)));
        }
        for (String sapling : GraphWorld.GRAPH.members("#saplings")) {
            all.add(new Mutation(sapling + "=2", world -> world.copy().with(sapling, 2)));
        }
        for (int free : List.of(0, 19, 20, 100)) {
            all.add(new Mutation("free=" + free, world -> {
                GraphWorld copy = world.copy();
                copy.storageFree = free;
                return copy;
            }));
        }
        return all;
    }

    static List<Mutation> sample(long seed, int count) {
        List<Mutation> pool = new ArrayList<>(SINGLE);
        Collections.shuffle(pool, new Random(seed));
        return pool.subList(0, Math.min(count, pool.size()));
    }

    static List<Mutation> simulationSample(long seed, int count) {
        List<Mutation> pool = new ArrayList<>();
        for (Mutation mutation : SINGLE) {
            if (!mutation.name().startsWith("нет ")) {
                pool.add(mutation);
            }
        }
        Collections.shuffle(pool, new Random(seed));
        return pool.subList(0, Math.min(count, pool.size()));
    }

    static List<GraphWorld> baselines() {
        GraphWorld empty = GraphWorld.empty();
        GraphWorld settled = GraphWorld.settled();
        GraphWorld crowded = GraphWorld.settled().tools("spirit-2", -1).tools("spirit-3", 2);
        GraphWorld second = GraphWorld.settled();
        second.stage = 2;
        GraphWorld.GRAPH.buildings().keySet().forEach(id -> second.built(id, Built.PLANNED));
        GraphWorld ten = GraphWorld.settled();
        for (int index = 2; index <= 10; index++) {
            ten.tools("spirit-" + index, -1);
        }
        return List.of(empty, settled, crowded, second, ten);
    }

    static GraphWorld liveStageOne() {
        GraphWorld world = GraphWorld.empty();
        GraphWorld.GRAPH.buildings().keySet().forEach(id -> world.built(id, Built.ABSENT));
        for (String id : List.of("crafting_table", "furnace", "chests", "clearing", "lighting", "fence", "hut")) {
            world.built(id, Built.BUILT);
        }
        for (String id : List.of("mine", "streets", "orchard_oak", "wheat_field")) {
            world.built(id, Built.PLANNED);
        }
        world.stage = 1;
        for (int index = 1; index <= 4; index++) {
            world.tools("spirit-" + index, -1);
        }
        world.stock.putAll(Map.of(
                "oak_log",
                26,
                "oak_planks",
                10,
                "stick",
                4,
                "cobblestone",
                20,
                "coal",
                6,
                "torch",
                0,
                "dirt",
                10,
                "oak_sapling",
                1,
                "wheat_seeds",
                8));
        world.materials.put("streets", Map.of("dirt", 400));
        world.materials.put("orchard_oak", Map.of("dirt", 18, "oak_sapling", 3, "torch", 3));
        List<Stray> strays = new ArrayList<>();
        for (int index = 0; index < 240; index++) {
            strays.add(new Stray("stray-" + index, "wall", new Position(index, 64, 0), null, Map.of("wall", 1)));
        }
        Map<String, Spirit> spirits = new LinkedHashMap<>();
        for (int index = 1; index <= 4; index++) {
            spirits.put(
                    "spirit-" + index,
                    new Spirit(
                            "spirit-" + index,
                            new Position(index - 1, 64, 0),
                            20.0f,
                            20.0f,
                            null,
                            20,
                            300,
                            false,
                            false,
                            index == 2 || index == 3,
                            null,
                            Map.of(),
                            List.of(),
                            List.of(),
                            null));
        }
        world.facts = new GraphFacts(
                spirits,
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                strays,
                new Terrain(0, 180, 0, 0, 0, Map.of(), Map.of(), Map.of(), false, 3263),
                new StageReqs(Map.of(), Map.of()),
                GraphFeedback.none(),
                List.of(),
                List.of(),
                List.of());
        world.event("demolish_stray").event("clear_floating");
        return world;
    }

    private static List<Mutation> buildSingle() {
        List<Mutation> all = new ArrayList<>();
        for (SupplyGraph.Item item : GraphWorld.GRAPH.items().values()) {
            for (int count : List.of(0, Math.max(0, item.xmin() - 1), item.xmin() * 4, 10_000)) {
                all.add(new Mutation(
                        item.id() + "=" + count, world -> world.copy().with(item.id(), count)));
            }
        }
        for (int tier = -1; tier <= 2; tier++) {
            int chosen = tier;
            all.add(new Mutation("tools=" + tier, world -> world.copy().tools("spirit-1", chosen)));
        }
        for (String building : GraphWorld.GRAPH.buildings().keySet()) {
            for (Built built : Built.values()) {
                all.add(new Mutation(
                        building + "=" + built, world -> world.copy().built(building, built)));
            }
        }
        for (String event : GraphWorld.GRAPH.events().keySet()) {
            all.add(new Mutation("event=" + event, world -> world.copy().event(event)));
        }
        GraphWorld.GRAPH.ways().stream()
                .map(SupplyGraph.Way::world)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(resource -> all.add(new Mutation("нет " + resource, world -> {
                    GraphWorld copy = world.copy();
                    copy.world.remove(resource);
                    return copy;
                })));
        for (String sapling : GraphWorld.GRAPH.members("#saplings")) {
            all.add(new Mutation(sapling + "=2", world -> world.copy().with(sapling, 2)));
        }
        for (int free : List.of(0, 19, 20, 100)) {
            all.add(new Mutation("free=" + free, world -> {
                GraphWorld copy = world.copy();
                copy.storageFree = free;
                return copy;
            }));
        }
        return all;
    }

    static List<Mutation> facts() {
        List<Mutation> all = new ArrayList<>();
        for (String building : GraphWorld.GRAPH.buildings().keySet()) {
            all.add(new Mutation(
                    building + ".lotReady=false", world -> world.copy().lotReady(building, false)));
            all.add(new Mutation(building + ".wrong=1", world -> world.copy().wrong(building, 1)));
        }
        addFailureMutations(all);
        return all;
    }

    private static void addFailureMutations(List<Mutation> all) {
        for (String node : List.of("chop_oak_log", "mine_cobble", "more_return_before_night")) {
            if (GraphWorld.GRAPH.way(node).isEmpty()) {
                continue;
            }
            all.add(new Mutation("неудача " + node + " NO_PATH x2", world -> failure(world, node, null, 2, 2)));
            all.add(new Mutation("неудача " + node + " CHUNK", world -> failure(world, node, null, 4, 2)));
            all.add(new Mutation(
                    "неудача " + node + " NO_MATERIAL",
                    world -> failure(world, node, new GraphBlock.Item("dirt", 1), 4, 2)));
            all.add(new Mutation("неудача " + node + " OTHER", world -> failure(world, node, null, 3, 2)));
        }
    }

    private static GraphWorld failure(GraphWorld world, String node, GraphBlock.Need cause, int count, int targets) {
        GraphWorld copy = world.copy();
        List<GraphFeedback.Failure> failures =
                new ArrayList<>(copy.facts.feedback().failures());
        for (int index = 0; index < targets; index++) {
            failures.add(new GraphFeedback.Failure(node, index + ",64,0", count, cause, 100));
        }
        copy.facts = copy.facts.withFeedback(
                new GraphFeedback(GraphFeedback.Clock.unknown(), GraphFeedback.Player.none(), failures));
        return copy;
    }

    static List<Mutation> exported() {
        List<Mutation> all = new ArrayList<>(single());
        all.addAll(facts());
        return all;
    }
}
