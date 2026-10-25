package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.FenceGateRule;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicTextParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DefensePerimeterGuardTests {
    private static final Path ROOT = Path.of("src/main/resources/" + PresetLibrary.SCHEMATICS_ROOT);

    static List<String> lowBlocks(Schematic schematic) {
        List<String> problems = new ArrayList<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            boolean perimeter = entry.dx() == 0
                    || entry.dz() == 0
                    || entry.dx() == schematic.sizeX() - 1
                    || entry.dz() == schematic.sizeZ() - 1;
            if (entry.dy() == 0 && perimeter && !tall(entry.block())) {
                problems.add(entry.block() + "@" + entry.dx() + "," + entry.dz());
            }
        }
        return problems;
    }

    private static boolean tall(String block) {
        return block.endsWith("_fence") || block.endsWith("_fence_gate") || block.endsWith("_wall");
    }

    @Test
    void defenseSchemes_PerimeterLine_OnlyFenceHigh() throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.list(ROOT)) {
            for (Path file : files.filter(path -> path.toString().endsWith(SchematicTextParser.EXTENSION))
                    .sorted()
                    .toList()) {
                String text = Files.readString(file);
                if (!text.lines().anyMatch(line -> "type: defense".equals(line.strip()))) {
                    continue;
                }
                Schematic schematic =
                        SchematicTextParser.parse(text, file.toString()).schematic();
                lowBlocks(schematic).forEach(problem -> problems.add(file.getFileName() + ": " + problem));
            }
        }

        assertEquals(
                List.of(),
                problems,
                "Оборонная схема: в линии периметра на земле — только забор, калитка или стена (1,5 блока). Бревно "
                        + "высотой 1 перепрыгивается, и ограда проходима (INC-53). Как надо: столб — забор, факел "
                        + "держится на заборе");
    }

    @Test
    void lowBlocks_LogInLine_Reported() {
        Schematic schematic = SchematicTextParser.parse("""
                                type: defense
                                size: 3x1x1
                                legend:
                                  'F': oak_fence
                                  'L': oak_log[axis=y]
                                layers:
                                  - y0: |
                                      FLF
                                """, "test").schematic();

        assertEquals(
                List.of("minecraft:oak_log@1,0"),
                lowBlocks(schematic),
                "Шаблон сторожа: бревно в линии забора обязано попасть в отчёт, иначе сторож молчит о проходе");
    }

    @Test
    void defenseSchemes_FenceSpans_BetweenTenAndTwenty() throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.list(ROOT)) {
            for (Path file : files.filter(path -> path.toString().endsWith(SchematicTextParser.EXTENSION))
                    .sorted()
                    .toList()) {
                String text = Files.readString(file);
                if (!text.lines().anyMatch(line -> "type: defense".equals(line.strip()))) {
                    continue;
                }
                Schematic schematic =
                        SchematicTextParser.parse(text, file.toString()).schematic();
                spanProblems(schematic).forEach(problem -> problems.add(file.getFileName() + ": " + problem));
            }
        }

        assertEquals(
                List.of(),
                problems,
                "Оборонная ограда: отрезок забора между углом и калиткой — от " + FenceGateRule.MIN_SPAN + " до "
                        + FenceGateRule.MAX_SPAN + " блоков, иначе калитки либо редкие (дыра) либо частые (лишние "
                        + "проходы). Сторону короче 11 не делят. Как надо: калитки по FenceGateRule.gates. Нарушения");
    }

    @Test
    void fenceSpans_GateNearCorner_Reported() {
        Schematic schematic = SchematicTextParser.parse("""
                                type: defense
                                size: 23x1x1
                                legend:
                                  'F': oak_fence
                                  'G': oak_fence_gate[facing=north]
                                layers:
                                  - y0: |
                                      FFFFFGFFFFFFFFFFFFFFFFF
                                """, "test").schematic();

        List<String> problems = spanProblems(schematic);

        assertEquals(
                2,
                problems.size(),
                "Шаблон сторожа: калитка в 5 блоках от угла обязана попасть в отчёт на северной и южной линии, "
                        + "иначе сторож молчит о слишком коротком отрезке");
        assertTrue(
                problems.stream().allMatch(problem -> problem.contains("длиной 5")),
                "Шаблон сторожа обязан назвать отрезок длиной 5, а не любой другой: " + problems);
    }

    @Test
    void fenceScheme_GatesFaceOutward_WithTorchOnNeighborPost() throws IOException {
        Path file = ROOT.resolve("fence.txt");
        Schematic schematic = SchematicTextParser.parse(Files.readString(file), file.toString())
                .schematic();
        List<Schematic.Entry> gates = schematic.blocks().stream()
                .filter(entry -> entry.dy() == 0 && entry.block().endsWith("_fence_gate"))
                .toList();
        Set<Long> torches = new HashSet<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            if (entry.dy() == 1 && entry.block().endsWith("torch")) {
                torches.add(key(entry.dx(), entry.dz()));
            }
        }
        List<String> problems = new ArrayList<>();
        for (Schematic.Entry gate : gates) {
            String outward = outward(schematic, gate);
            if (outward.isEmpty()) {
                problems.add("калитка не на стороне периметра: " + gate.dx() + "," + gate.dz());
                continue;
            }
            if (!outward.equals(gate.state().get("facing"))) {
                problems.add("калитка " + gate.dx() + "," + gate.dz() + " смотрит не наружу " + outward);
            }
            boolean alongX = gate.dz() == 0 || gate.dz() == schematic.sizeZ() - 1;
            boolean torch = torches.contains(key(gate.dx() + (alongX ? 1 : 0), gate.dz() + (alongX ? 0 : 1)))
                    || torches.contains(key(gate.dx() - (alongX ? 1 : 0), gate.dz() - (alongX ? 0 : 1)));
            if (!torch) {
                problems.add("у калитки " + gate.dx() + "," + gate.dz() + " нет факела на соседнем столбе");
            }
        }

        assertEquals(4, gates.size(), "Ограда поселения 23×23 обязана иметь по калитке на каждой стороне");
        assertEquals(
                List.of(),
                problems,
                "Калитка ограды смотрит наружу своей стороны и несёт факел на соседнем столбе, как прежняя "
                        + "северная: иначе дух упирается в закрытую калитку или сторона остаётся без света. "
                        + "Нарушения");
    }

    private static List<String> spanProblems(Schematic schematic) {
        List<String> problems = new ArrayList<>();
        for (Side side : sides(schematic)) {
            int length = side.blocks().size();
            if (length <= FenceGateRule.MIN_SPAN) {
                continue;
            }
            boolean fence = false;
            List<Integer> points = new ArrayList<>();
            points.add(0);
            for (int index = 0; index < length; index++) {
                String block = side.blocks().get(index);
                if (block.endsWith("_fence_gate")) {
                    points.add(index);
                    fence = true;
                } else if (block.endsWith("_fence")) {
                    fence = true;
                }
            }
            if (!fence) {
                continue;
            }
            points.add(length - 1);
            for (int index = 1; index < points.size(); index++) {
                int span = points.get(index) - points.get(index - 1);
                if (span < FenceGateRule.MIN_SPAN || span > FenceGateRule.MAX_SPAN) {
                    problems.add(
                            side.name() + " " + points.get(index - 1) + ".." + points.get(index) + " длиной " + span);
                }
            }
        }
        return problems;
    }

    private static List<Side> sides(Schematic schematic) {
        Map<Long, String> grid = new HashMap<>();
        for (Schematic.Entry entry : schematic.blocks()) {
            if (entry.dy() == 0) {
                grid.put(key(entry.dx(), entry.dz()), entry.block());
            }
        }
        List<String> north = new ArrayList<>();
        List<String> south = new ArrayList<>();
        for (int x = 0; x < schematic.sizeX(); x++) {
            north.add(grid.getOrDefault(key(x, 0), ""));
            south.add(grid.getOrDefault(key(x, schematic.sizeZ() - 1), ""));
        }
        List<String> west = new ArrayList<>();
        List<String> east = new ArrayList<>();
        for (int z = 0; z < schematic.sizeZ(); z++) {
            west.add(grid.getOrDefault(key(0, z), ""));
            east.add(grid.getOrDefault(key(schematic.sizeX() - 1, z), ""));
        }
        return List.of(
                new Side("север", north), new Side("юг", south), new Side("запад", west), new Side("восток", east));
    }

    private static String outward(Schematic schematic, Schematic.Entry gate) {
        if (gate.dz() == 0) {
            return "north";
        }
        if (gate.dz() == schematic.sizeZ() - 1) {
            return "south";
        }
        if (gate.dx() == 0) {
            return "west";
        }
        if (gate.dx() == schematic.sizeX() - 1) {
            return "east";
        }
        return "";
    }

    private static long key(int dx, int dz) {
        return ((long) dx << 32) ^ (dz & 0xFFFFFFFFL);
    }

    private record Side(String name, List<String> blocks) {}
}
