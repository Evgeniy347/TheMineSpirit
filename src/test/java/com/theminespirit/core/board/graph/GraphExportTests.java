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
    private static final int CASES = 200;

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
