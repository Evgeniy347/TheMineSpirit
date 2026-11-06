package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class AuditDiffTests {
    private static final String LIVE = "/live/observe22/street-loss.txt";

    @Test
    void between_StreetBlockDugOut_NamesCellAndStandingBlock() throws IOException {
        List<BlockCoord> cells = liveLosses();
        BuildGap.Gap before = gap(cells, new Probe(cells, "minecraft:grass_block", Set.of()));
        BuildGap.Gap after = gap(cells, new Probe(cells, "minecraft:air", Set.of()));

        AuditDiff diff = AuditDiff.between(before, after, cell -> "minecraft:air");

        assertEquals(
                cells.size(),
                diff.lost().size(),
                "observe22: клетка улицы была дёрном (земля по плану покрыта травой), стала воздухом — аудит "
                        + "обязан назвать её потерей, иначе падение streets 944→899 не разобрать");
        assertTrue(
                diff.lost().get(0).text().contains("стоит minecraft:air"),
                "Журнал обязан назвать, что стоит на месте клетки: без этого непонятно, дух выкопал или блок подменили");
    }

    @Test
    void between_BlockReplacedByPath_CountedAsLoss() {
        BlockCoord cell = new BlockCoord(-190, 68, -516);
        BuildGap.Gap before = gap(List.of(cell), new Probe(List.of(cell), "minecraft:grass_block", Set.of()));
        BuildGap.Gap after = gap(List.of(cell), new Probe(List.of(cell), "minecraft:dirt_path", Set.of()));

        AuditDiff diff = AuditDiff.between(before, after, value -> "minecraft:dirt_path");

        assertEquals(
                1,
                diff.lost().size(),
                "Строгий аудит улиц не считает dirt_path землёй: подмена дёрна тропой обязана попасть в журнал");
        assertTrue(
                diff.lost().get(0).text().contains("ожидался minecraft:dirt, стоит minecraft:air")
                        || diff.lost().get(0).text().contains("стоит minecraft:dirt_path"),
                "В причине потери обязан быть реально стоящий блок, а не голая цифра");
    }

    @Test
    void between_ChunkBecameUnloaded_ReasonIsChunkNotBlockGone() {
        BlockCoord cell = new BlockCoord(-193, 68, -524);
        BuildGap.Gap before = gap(List.of(cell), new Probe(List.of(cell), "minecraft:grass_block", Set.of()));
        BuildGap.Gap after = gap(List.of(cell), new Probe(List.of(cell), "minecraft:grass_block", Set.of(cell)));

        AuditDiff diff = AuditDiff.between(before, after, value -> "minecraft:grass_block");

        assertEquals(1, diff.lost().size(), "Клетка пропала из готовности — потеря одна");
        assertEquals(
                "чанк не загружен",
                diff.lost().get(0).standing(),
                "Причина потери в незагруженном чанке — не «блока нет»: иначе ремонт снесёт готовую улицу");
        assertEquals(
                List.of(cell),
                diff.unknownCells(),
                "Журнал называет клетки, которые перестали быть известны: по ним видно, почему готовность «неизвестно»");
    }

    @Test
    void between_CellGoneFromPlan_ReasonIsPlanMoved() {
        BlockCoord cell = new BlockCoord(-188, 68, -519);
        BlockCoord other = new BlockCoord(-187, 68, -519);
        BuildGap.Gap before = gap(List.of(cell), new Probe(List.of(cell), "minecraft:grass_block", Set.of()));
        BuildGap.Gap after = gap(List.of(other), new Probe(List.of(other), "minecraft:grass_block", Set.of()));

        AuditDiff diff = AuditDiff.between(before, after, value -> "minecraft:grass_block");

        assertEquals(1, diff.lost().size(), "Клетка выпала из плана — прежняя готовность перестала считаться");
        assertEquals(
                "клетки больше нет в плане",
                diff.lost().get(0).standing(),
                "Если клетки нет в новом плане, нельзя писать «стоит блок»: аудит сверяет другой план");
    }

    @Test
    void between_CellBecomesDirt_GainedOne() {
        BlockCoord cell = new BlockCoord(-190, 68, -514);
        BuildGap.Gap before = gap(List.of(cell), new Probe(List.of(cell), "minecraft:air", Set.of()));
        BuildGap.Gap after = gap(List.of(cell), new Probe(List.of(cell), "minecraft:grass_block", Set.of()));

        AuditDiff diff = AuditDiff.between(before, after, value -> "minecraft:grass_block");

        assertTrue(diff.lost().isEmpty(), "Клетка стала дёрном — терять нечего");
        assertEquals(1, diff.gained(), "Дёрн на месте земли — клетка вернулась в готовность");
    }

    @Test
    void between_NothingChanged_Empty() {
        BlockCoord cell = new BlockCoord(-190, 68, -513);
        BuildGap.Gap same = gap(List.of(cell), new Probe(List.of(cell), "minecraft:grass_block", Set.of()));

        assertTrue(
                AuditDiff.between(same, same, value -> "minecraft:grass_block").empty(),
                "Без изменений журнал аудита молчит: иначе лог забивается повторами");
    }

    private static BuildGap.Gap gap(List<BlockCoord> cells, Probe probe) {
        List<TaskNode> steps = new ArrayList<>();
        for (BlockCoord cell : cells) {
            steps.add(new ScriptCall(
                    "place_block", Map.of("block", "minecraft:dirt", "x", cell.x(), "y", cell.y(), "z", cell.z())));
        }
        return BuildGap.trace(new SequenceNode(List.copyOf(steps)), "oak", probe, NoSchemes.INSTANCE);
    }

    private static List<BlockCoord> liveLosses() throws IOException {
        List<BlockCoord> cells = new ArrayList<>();
        try (InputStream stream = AuditDiffTests.class.getResourceAsStream(LIVE)) {
            if (stream == null) {
                throw new IOException("Нет живой фикстуры " + LIVE + ": клетки улиц из снимков observe22");
            }
            for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .lines()
                    .toList()) {
                String text = line.trim();
                if (text.isEmpty() || text.startsWith("#")) {
                    continue;
                }
                String[] parts = text.split(",", -1);
                cells.add(new BlockCoord(
                        Integer.parseInt(parts[0].trim()),
                        Integer.parseInt(parts[1].trim()),
                        Integer.parseInt(parts[2].trim())));
            }
        }
        return cells;
    }

    private record Probe(Map<BlockCoord, String> blocks, Set<BlockCoord> unloaded) implements BuildGap.Probe {
        private Probe(List<BlockCoord> cells, String block, Set<BlockCoord> unloaded) {
            this(map(cells, block), unloaded);
        }

        private static Map<BlockCoord, String> map(List<BlockCoord> cells, String block) {
            Map<BlockCoord, String> blocks = new LinkedHashMap<>();
            cells.forEach(cell -> blocks.put(cell, block));
            return blocks;
        }

        @Override
        public String block(BlockCoord at) {
            return blocks.getOrDefault(at, "minecraft:air");
        }

        @Override
        public boolean passable(BlockCoord at) {
            return true;
        }

        @Override
        public boolean loaded(BlockCoord at) {
            return !unloaded.contains(at);
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
