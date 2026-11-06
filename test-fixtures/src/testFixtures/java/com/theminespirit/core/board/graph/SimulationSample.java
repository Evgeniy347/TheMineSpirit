package com.theminespirit.core.board.graph;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SimulationSample {
    static final int LIMIT = 2500;
    static final Path OUT = Path.of("TestResults/mutation/simulation.json");
    private static final int MUTATIONS = 3;

    record Case(String name, GraphWorld world, GraphSimulation.Outcome outcome, int limit) {}

    private SimulationSample() {}

    static List<Case> cases() {
        List<Case> cases = new ArrayList<>();
        List<GraphWorld> bases = GraphMutations.simulationBases().subList(0, 3);
        for (GraphWorld base : bases) {
            cases.add(new Case(
                    "старт этап " + base.stage + ", духов " + base.kits.size(),
                    base,
                    GraphSimulation.run(base, LIMIT),
                    LIMIT));
        }
        List<GraphMutations.Mutation> mutations = GraphMutations.simulationSample(58, MUTATIONS);
        for (GraphMutations.Mutation mutation : mutations) {
            for (GraphWorld base : bases.subList(0, 2)) {
                GraphWorld world = mutation.apply().apply(base);
                cases.add(new Case(
                        mutation.name() + ", старт этап " + base.stage,
                        world,
                        GraphSimulation.run(world, LIMIT),
                        LIMIT));
            }
        }
        return cases;
    }

    static void write() throws IOException {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Case sample : cases()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", sample.name());
            entry.put("state", GraphStateJson.state(sample.world().state()));
            entry.put("limit", sample.limit());
            entry.put("steps", sample.outcome().steps());
            entry.put("finished", sample.outcome().finished());
            entry.put("stage", sample.outcome().reachedStage());
            rows.add(entry);
        }
        Files.createDirectories(OUT.getParent());
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(OUT.toFile(), rows);
    }
}
