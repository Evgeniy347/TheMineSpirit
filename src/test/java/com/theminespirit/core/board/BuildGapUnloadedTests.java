package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BuildGapUnloadedTests {
    private static final String RESTART = "/live/observe20/unloaded.txt";

    @Test
    void trace_ChunksNotLoaded_UnknownInsteadOfMissing() throws IOException {
        Map<BlockCoord, Boolean> loaded = fixture(RESTART);
        TaskNode plan = dirt(loaded.keySet());
        BlockCoord last =
                loaded.keySet().stream().filter(loaded::get).findFirst().orElseThrow();

        BuildGap.Gap gap = BuildGap.trace(plan, "oak", new Probe(loaded, last), NoSchemes.INSTANCE);

        assertEquals(3, gap.unknown(), "Три клетки из фикстуры в незагруженных чанках — это unknown, а не дыры");
        assertFalse(gap.confident(), "Пока чанк не загружен, аудит не делает вывода о постройке");
        assertEquals(1, gap.total(), "В план входит только загруженная клетка: незагруженные не считаются");
        assertEquals(1, gap.shortage(), "Загруженная клетка пустая — её и называют недостающей");
        assertFalse(
                gap.missingCells().containsKey(new BlockCoord(-167, 69, -526)),
                "Клетка незагруженного чанка не попадает в список «нет блока»: иначе ремонт снесёт готовое");
    }

    @Test
    void trace_AllChunksLoaded_MissingCounted() throws IOException {
        Map<BlockCoord, Boolean> loaded = fixture(RESTART);
        Map<BlockCoord, Boolean> all = new LinkedHashMap<>();
        loaded.forEach((cell, ignored) -> all.put(cell, true));

        BuildGap.Gap gap = BuildGap.trace(dirt(all.keySet()), "oak", new Probe(all, null), NoSchemes.INSTANCE);

        assertTrue(gap.confident(), "Все чанки загружены — выводу о постройке ничего не мешает");
        assertEquals(all.size(), gap.unknown() + gap.total(), "Каждая клетка либо известна, либо неизвестна");
        assertEquals(all.size(), gap.shortage(), "Все четыре клетки пустые — четыре недостающих");
    }

    private static TaskNode dirt(Set<BlockCoord> cells) {
        List<TaskNode> steps = new ArrayList<>();
        for (BlockCoord cell : cells) {
            steps.add(new ScriptCall(
                    "place_block", Map.of("block", "minecraft:dirt", "x", cell.x(), "y", cell.y(), "z", cell.z())));
        }
        return new SequenceNode(List.copyOf(steps));
    }

    private static Map<BlockCoord, Boolean> fixture(String resource) throws IOException {
        Map<BlockCoord, Boolean> cells = new LinkedHashMap<>();
        try (InputStream stream = BuildGapUnloadedTests.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Нет фикстуры " + resource);
            }
            for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .toList()) {
                String text = line.trim();
                if (text.isEmpty() || text.startsWith("#")) {
                    continue;
                }
                int first = text.indexOf(',');
                int second = text.indexOf(',', first + 1);
                int third = text.indexOf(',', second + 1);
                cells.put(
                        new BlockCoord(
                                Integer.parseInt(text.substring(0, first)),
                                Integer.parseInt(text.substring(first + 1, second)),
                                Integer.parseInt(text.substring(second + 1, third))),
                        Boolean.parseBoolean(text.substring(third + 1)));
            }
        }
        return cells;
    }

    private record Probe(Map<BlockCoord, Boolean> loaded, BlockCoord wrong) implements BuildGap.Probe {
        @Override
        public String block(BlockCoord at) {
            return at.equals(wrong) ? "minecraft:oak_planks" : "minecraft:air";
        }

        @Override
        public boolean passable(BlockCoord at) {
            return true;
        }

        @Override
        public boolean loaded(BlockCoord at) {
            return loaded.getOrDefault(at, true);
        }
    }

    private enum NoSchemes implements BuildGap.Schemes {
        INSTANCE;

        @Override
        public Optional<MineLayout> mine(ScriptCall call) {
            return Optional.empty();
        }

        @Override
        public Optional<List<Schematic.PlacedEntry>> entries(ScriptCall call) {
            return Optional.empty();
        }
    }
}
