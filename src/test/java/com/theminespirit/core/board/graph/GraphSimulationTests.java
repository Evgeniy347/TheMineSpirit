package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("mutation")
class GraphSimulationTests {
    private static final int LIMIT = 4000;
    private static final Path REPORT = Path.of("TestResults/mutation/graph-trail.md");

    @Test
    void simulate_EveryStartClass_ReachesLastStage() {
        List<String> stuck = new ArrayList<>();
        for (GraphWorld base : GraphMutations.baselines()) {
            for (GraphMutations.Mutation mutation : GraphMutations.single()) {
                GraphSimulation.Outcome outcome =
                        GraphSimulation.run(mutation.apply().apply(base), LIMIT);
                if (!outcome.finished()) {
                    stuck.add("этап " + base.stage + ", " + mutation.name() + ": " + outcome.stuck());
                }
            }
        }
        assertEquals(
                List.of(),
                stuck.subList(0, Math.min(5, stuck.size())),
                "Поселение застряло (" + stuck.size() + " стартов)");
    }

    @Test
    void simulate_Baselines_WriteTrailReport() throws IOException {
        StringBuilder report = new StringBuilder("# Путь поселения по графу\n");
        for (GraphWorld base : GraphMutations.baselines()) {
            GraphSimulation.Outcome outcome = GraphSimulation.run(base, LIMIT);
            report.append("\n## Старт: этап ")
                    .append(base.stage)
                    .append(", духов ")
                    .append(base.kits.size())
                    .append(" — шагов ")
                    .append(outcome.steps())
                    .append(outcome.finished() ? "" : ", ЗАСТРЯЛ: " + outcome.stuck())
                    .append(outcome.strategist().isEmpty() ? "" : "; стратег внёс в план " + outcome.strategist())
                    .append("\n\n");
            String last = "";
            int repeat = 0;
            int stage = 0;
            for (GraphSimulation.Step step : outcome.trail()) {
                if (step.task().equals(last)) {
                    repeat++;
                    continue;
                }
                if (!last.isEmpty()) {
                    report.append(repeat > 1 ? " ×" + repeat : "").append('\n');
                }
                if (step.stage() != stage) {
                    stage = step.stage();
                    report.append("### этап ").append(stage).append('\n');
                }
                report.append("- ")
                        .append(step.task())
                        .append(" (")
                        .append(step.priority())
                        .append(')');
                last = step.task();
                repeat = 1;
            }
            report.append(repeat > 1 ? " ×" + repeat : "").append('\n');
        }
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report, StandardCharsets.UTF_8);
        assertEquals(true, Files.exists(REPORT), "Отчёт пути поселения записан в " + REPORT);
    }
}
