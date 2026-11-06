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
            entry.put("state", state(world.state()));
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
        Files.createDirectories(OUT.getParent());
        Files.copy(
                Path.of("src/main/resources/" + SupplyGraph.RESOURCE),
                OUT.resolveSibling("graph.json"),
                StandardCopyOption.REPLACE_EXISTING);
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(OUT.toFile(), cases);
        assertTrue(Files.size(OUT) > 0, "Контрольные случаи графа для Python-модели записаны в " + OUT);
    }

    @Test
    void export_LiveStageOne_WriteStart() throws IOException {
        Files.createDirectories(LIVE.getParent());
        new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(LIVE.toFile(), state(GraphMutations.liveStageOne().state()));
        assertTrue(Files.size(LIVE) > 0, "Живой этап 1 для Python-модели записан в " + LIVE);
    }

    @Test
    void export_BaseWorlds_WriteStarts() throws IOException {
        Map<String, Object> bases = new LinkedHashMap<>();
        for (GraphBaseWorlds.BaseWorld base : GraphBaseWorlds.baseWorlds()) {
            GraphSimulation.Outcome outcome = GraphSimulation.runUntil(base.world(), BASE_LIMIT, base.stopStage());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("state", state(base.world().state()));
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

    static Map<String, Object> state(GraphState state) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stock", state.stock());
        out.put("stage", state.stage());
        out.put("kits", state.kits());
        out.put("buildings", state.buildings());
        out.put("materials", state.materials());
        out.put("storageFreePercent", state.storageFreePercent());
        out.put("events", state.events().stream().sorted().toList());
        out.put("world", state.world().stream().sorted().toList());
        out.put("distances", state.distances());
        out.put("stations", state.stations());
        out.put("orders", state.orders());
        out.put("facts", state.facts());
        return out;
    }
}
