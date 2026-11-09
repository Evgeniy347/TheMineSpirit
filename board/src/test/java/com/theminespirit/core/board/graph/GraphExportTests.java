package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("export")
class GraphExportTests {
    private static final Path OUT = Path.of("TestResults/graph-export/conformance.json");
    private static final Path LIVE = Path.of("TestResults/graph-export/live1.json");
    private static final Path BASES = Path.of("TestResults/graph-export/bases.json");
    private static final int CASES = 200;
    private static final int BASE_LIMIT = 4000;

    @Test
    void export_SampledStates_WriteConformanceCases() throws IOException {
        List<Map<String, Object>> cases = new ArrayList<>();
        Random random = new Random(58);
        List<GraphWorld> bases = GraphMutations.baselines();
        for (int index = 0; index < CASES; index++) {
            List<GraphMutations.Mutation> combo =
                    GraphChaos.combo(random, 1 + random.nextInt(4), GraphMutations.exported());
            GraphWorld world = GraphChaos.apply(bases.get(index % bases.size()), combo);
            GraphPlan plan = world.plan();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("mutations", GraphChaos.names(combo));
            entry.put("state", GraphStateJson.state(world.state()));
            List<Map<String, Object>> tasks = new ArrayList<>();
            for (GraphPlan.Task task : plan.tasks()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", task.id());
                row.put("priority", task.priority());
                row.put("feasible", task.feasible());
                row.put("chance", task.chance());
                tasks.add(row);
            }
            entry.put("tasks", tasks);
            entry.put(
                    "notices",
                    plan.notices().stream().map(GraphPlan.Notice::building).toList());
            cases.add(entry);
        }
        GraphWorld weak = GraphWorld.empty().event("life_threat_player");
        weak.facts = weak.facts.withPlayers(
                List.of(new GraphFacts.Player("Steve", new GraphFacts.Position(0, 64, 0), 4.0F, 20.0F, 4)));
        cases.add(exportCase(weak, "weak_player_fact"));
        for (Map<String, Object> playerCase : playerFactVector()) {
            cases.add(playerCase);
        }
        cases.add(exportCase(foodStageTwo(), "food_stage_two"));
        Files.createDirectories(OUT.getParent());
        Files.copy(
                Path.of("mod/src/main/resources/" + SupplyGraph.RESOURCE),
                OUT.resolveSibling("graph.json"),
                StandardCopyOption.REPLACE_EXISTING);
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(OUT.toFile(), cases);
        assertTrue(Files.size(OUT) > 0, "Контрольные случаи графа для Python-модели записаны в " + OUT);
    }

    private static List<Map<String, Object>> playerFactVector() {
        return List.of(
                playerCase(4.0F, 20, "weak_player_health"),
                playerCase(20.0F, 4, "weak_player_food"),
                playerCase(20.0F, 20, "healthy_player_fact"));
    }

    private static Map<String, Object> playerCase(float health, int food, String mutation) {
        GraphWorld world = GraphWorld.empty();
        world.facts = world.facts.withPlayers(
                List.of(new GraphFacts.Player("Steve", new GraphFacts.Position(0, 64, 0), health, 20.0F, food)));
        return exportCase(world, mutation);
    }

    private static GraphWorld foodStageTwo() {
        GraphWorld world = GraphWorld.settled();
        world.stage = 2;
        for (String building : List.of("warehouse", "smithing_table", "anvil", "plaza", "family_house_a", "field")) {
            world.built(building, GraphState.Built.PLANNED);
        }
        world.built("wheat_field", GraphState.Built.BUILT);
        world.tools("spirit-2", 1);
        world.with("coal", 0).with("food", 0).event("hungry");
        return world;
    }

    private static Map<String, Object> exportCase(GraphWorld world, String mutation) {
        GraphPlan plan = world.plan();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("mutations", List.of(mutation));
        entry.put("state", GraphStateJson.state(world.state()));
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (GraphPlan.Task task : plan.tasks()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", task.id());
            row.put("priority", task.priority());
            row.put("feasible", task.feasible());
            row.put("chance", task.chance());
            tasks.add(row);
        }
        entry.put("tasks", tasks);
        entry.put(
                "notices",
                plan.notices().stream().map(GraphPlan.Notice::building).toList());
        return entry;
    }

    @Test
    void export_LiveStageOne_WriteStart() throws IOException {
        Files.createDirectories(LIVE.getParent());
        new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(
                        LIVE.toFile(),
                        GraphStateJson.state(GraphMutations.liveStageOne().state()));
        assertTrue(Files.size(LIVE) > 0, "Живой этап 1 для Python-модели записан в " + LIVE);
    }

    @Test
    void export_BaseWorlds_WriteStarts() throws IOException {
        Map<String, Object> bases = new LinkedHashMap<>();
        for (GraphBaseWorlds.BaseWorld base : GraphBaseWorlds.baseWorlds()) {
            GraphSimulation.Outcome outcome = GraphSimulation.runUntil(base.world(), BASE_LIMIT, base.stopStage());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("state", GraphStateJson.state(base.world().state()));
            entry.put("limit", BASE_LIMIT);
            entry.put("stopStage", base.stopStage());
            entry.put("steps", outcome.steps());
            entry.put("finished", outcome.finished());
            entry.put("stage", outcome.reachedStage());
            entry.put(
                    "trail",
                    outcome.trail().stream()
                            .map(step -> Map.of("id", step.task(), "priority", step.priority()))
                            .toList());
            bases.put(base.name(), entry);
        }
        Files.createDirectories(BASES.getParent());
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(BASES.toFile(), bases);
        assertTrue(Files.size(BASES) > 0, "Базовые миры для Python-модели записаны в " + BASES);
    }
}
