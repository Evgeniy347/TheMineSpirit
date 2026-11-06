package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("mutation")
class GraphChaosTests {
    private static final int LIMIT = 12_000;
    private static final int COMBOS = 3000;
    private static final int SIMULATED_COMBOS = 10;
    private static final int CHAOS_SEEDS = 6;
    private static final int WEIGHT_SEEDS = 4;
    private static final double SPREAD = 0.3;
    private static final Path METRICS = Path.of("TestResults/mutation/graph-metrics.md");

    private static List<String> head(List<String> found) {
        return found.subList(0, Math.min(5, found.size()));
    }

    @Test
    void triples_Sampled_NoStallNoGap() {
        List<String> found = new ArrayList<>();
        Random random = new Random(MutationSeeds.seed() ^ 0x5DEECE66DL);
        List<GraphWorld> bases = GraphMutations.baselines();
        for (int index = 0; index < COMBOS; index++) {
            List<GraphMutations.Mutation> combo = GraphChaos.combo(random, 3);
            GraphWorld world = GraphChaos.apply(bases.get(index % bases.size()), combo);
            GraphPlan plan = world.plan();
            if (!plan.tasks().isEmpty() && GraphWorld.issueOrder(plan).isEmpty()) {
                found.add(GraphChaos.names(combo) + ": ни одного выполнимого");
            }
        }
        assertEquals(List.of(), head(found), "Тройки мутаций: мир встал (" + found.size() + " из " + COMBOS + ")");
    }

    @Test
    void combos_ThreeToSix_SimulationFinishes() {
        List<String> stuck = new ArrayList<>();
        List<GraphWorld> bases = GraphMutations.simulationBases();
        long baseSeed = MutationSeeds.seed();
        for (int seed = 0; seed < SIMULATED_COMBOS; seed++) {
            Random random = new Random(baseSeed + seed);
            List<GraphMutations.Mutation> combo = GraphChaos.combo(random, 3 + random.nextInt(4));
            GraphWorld world = GraphChaos.apply(bases.get(seed % bases.size()), combo);
            GraphSimulation.Outcome outcome = GraphSimulation.run(world, LIMIT);
            if (!outcome.finished()) {
                stuck.add("сид " + (baseSeed + seed) + " " + GraphChaos.names(combo) + ": " + outcome.stuck());
            }
        }
        assertEquals(List.of(), head(stuck), "Наборы из 3–6 мутаций: поселение застряло (" + stuck.size() + ")");
    }

    @Test
    void chaos_EventsWearGrowth_SimulationFinishes() {
        List<String> stuck = new ArrayList<>();
        List<GraphWorld> bases = GraphMutations.simulationBases();
        long baseSeed = MutationSeeds.seed() ^ 0xC0FFEEL;
        for (int seed = 0; seed < CHAOS_SEEDS; seed++) {
            Random random = new Random(baseSeed + seed);
            GraphSimulation.Outcome outcome = GraphSimulation.run(
                    bases.get(seed % bases.size()), LIMIT, GraphChaos.chaos(random, 0.05, 0.1, 0.02));
            if (!outcome.finished()) {
                stuck.add("сид " + (baseSeed + seed) + ": " + outcome.stuck());
            }
        }
        assertEquals(
                List.of(), head(stuck), "Хаос (события, износ, рост духов): поселение застряло (" + stuck.size() + ")");
    }

    @Test
    void weights_Perturbed30_SimulationFinishes() {
        List<String> stuck = new ArrayList<>();
        List<GraphWorld> bases = GraphMutations.simulationBases();
        long baseSeed = MutationSeeds.seed() ^ 0xBEEFL;
        for (int seed = 0; seed < WEIGHT_SEEDS; seed++) {
            SupplyGraph graph = GraphChaos.perturbed(GraphWorld.GRAPH, new Random(baseSeed + seed), SPREAD);
            for (int sample = 0; sample < 2; sample++) {
                GraphWorld base = bases.get((seed * 2 + sample) % bases.size());
                GraphWorld world = base.copy();
                world.graph = graph;
                GraphSimulation.Outcome outcome = GraphSimulation.run(world, LIMIT);
                if (!outcome.finished()) {
                    stuck.add("сид весов " + (baseSeed + seed) + ", старт этап " + base.stage + ": " + outcome.stuck());
                }
            }
        }
        assertEquals(List.of(), head(stuck), "Веса ±30 %: поселение застряло (" + stuck.size() + ")");
    }

    @Test
    void metrics_Baselines_WriteReport() throws IOException {
        StringBuilder report = new StringBuilder("# Метрики пути поселения\n\n");
        report.append("| старт | шагов | этап 1 | этап 2 | этап 3 |\n|---|---|---|---|---|\n");
        List<GraphWorld> bases = GraphMutations.simulationBases();
        long baseSeed = MutationSeeds.seed();
        for (int index = 0; index < bases.size(); index++) {
            row(report, "база " + index, GraphSimulation.run(bases.get(index), LIMIT));
        }
        for (int seed = 0; seed < 2; seed++) {
            row(
                    report,
                    "хаос, сид " + (baseSeed + seed),
                    GraphSimulation.run(
                            bases.get(seed), LIMIT, GraphChaos.chaos(new Random(baseSeed + seed), 0.05, 0.1, 0.02)));
            GraphWorld world = bases.get(seed).copy();
            world.graph = GraphChaos.perturbed(GraphWorld.GRAPH, new Random(baseSeed + seed), SPREAD);
            row(report, "веса ±30 %, сид " + (baseSeed + seed), GraphSimulation.run(world, LIMIT));
        }
        Files.createDirectories(METRICS.getParent());
        Files.writeString(METRICS, report, StandardCharsets.UTF_8);
        assertTrue(Files.exists(METRICS), "Метрики пути записаны в " + METRICS);
    }

    private static void row(StringBuilder report, String name, GraphSimulation.Outcome outcome) {
        Map<Integer, Integer> stages = outcome.stageSteps();
        report.append("| ")
                .append(name)
                .append(" | ")
                .append(outcome.finished() ? outcome.steps() : "застрял")
                .append(" | ")
                .append(stages.getOrDefault(1, 0))
                .append(" | ")
                .append(stages.getOrDefault(2, 0))
                .append(" | ")
                .append(stages.getOrDefault(3, 0))
                .append(" |\n");
    }
}
