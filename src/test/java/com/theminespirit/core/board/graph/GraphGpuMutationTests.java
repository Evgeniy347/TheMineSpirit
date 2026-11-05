package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("mutation")
class GraphGpuMutationTests {
    private static final int SECONDS = 900;

    @Test
    void gpu_PythonMatchesJavaSample_AndMutationBatchFinishes() throws IOException, InterruptedException {
        SimulationSample.write();
        assertTrue(Files.size(SimulationSample.OUT) > 0, "Выборка симуляций для сверки записана");
        List<String> command = new ArrayList<>();
        String configured = System.getProperty("theminespirit.python");
        if (configured != null && !configured.isBlank()) {
            command.addAll(List.of(configured.trim().split("\\s+")));
        } else if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            command.addAll(List.of("py", "-3.9"));
        } else {
            command.add("python3");
        }
        command.addAll(List.of(
                "tools/graph-gpu/mutation_gpu.py",
                "--sample",
                "3",
                "--limit",
                "1200",
                "--device",
                "cuda",
                "--seed",
                Long.toString(MutationSeeds.seed())));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        boolean finished = process.waitFor(SECONDS, TimeUnit.SECONDS);
        String output = finished ? new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8) : "";
        if (!finished) {
            process.destroyForcibly();
        }
        System.out.println(output);
        assertTrue(finished, "Перебор на видеокарте не уложился в " + SECONDS + " с: " + output);
        assertEquals(
                0, process.exitValue(), "Перебор на видеокарте нашёл расхождение с Java или затор: " + tail(output));
    }

    private static String tail(String output) {
        List<String> lines = List.of(output.split("\\R"));
        int from = Math.max(0, lines.size() - 20);
        return String.join(System.lineSeparator(), lines.subList(from, lines.size()));
    }
}
