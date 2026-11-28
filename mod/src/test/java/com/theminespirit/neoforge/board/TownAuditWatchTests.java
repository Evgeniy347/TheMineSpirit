package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BuildGap;
import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.board.audit.AuditBudgets;
import com.theminespirit.core.board.audit.AuditRegistry;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.TownFixtures;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TownAuditWatchTests {

    @Test
    void townAuditWatch_RegionChunks_QueueEveryChunkOnce() {
        ColumnMask region = TownFixtures.flatPlan().region();
        Set<ChunkCell> expected = new HashSet<>();
        for (ColumnMask.Cell cell : region.cells()) {
            expected.add(new ChunkCell(cell.x() >> 4, cell.z() >> 4));
        }

        Set<ChunkCell> chunks = TownAuditWatch.regionChunks(region);
        assertEquals(
                expected,
                chunks,
                "Контур превращается в один набор чанков: иначе граница поселения"
                        + " потеряет крайние чанки, и аудит не увидит яму у стены. При смене формулы перепроверь"
                        + " regionChunks и ColumnMask.cells.");

        AuditRegistry registry = new AuditRegistry(AuditBudgets.DEFAULT, 16, 31);
        registry.plan(1, 64);
        registry.retain(chunks);
        chunks.forEach(registry::ensure);
        assertEquals(
                chunks.size(),
                registry.caches(),
                "Каждый чанк региона получает окно кэша: иначе"
                        + " улица на краю контура останется неизвестной, и дух застрянет перед ней. При смене"
                        + " очереди перепроверь ensure и TownAuditWatch.tick.");
    }

    @Test
    void worldProbe_CachedCell_ReturnsBlockAndMarksOutsideUnknown() {
        AuditRegistry registry = new AuditRegistry(AuditBudgets.DEFAULT, 16, 31);
        BlockCoord known = new BlockCoord(0, 48, 0);
        BlockCoord unknown = new BlockCoord(16, 48, 0);
        registry.plan(1, 64);
        registry.retain(Set.of(new ChunkCell(0, 0)));
        registry.ensure(new ChunkCell(0, 0));
        registry.fill(
                4096, cell -> cell.equals(known) ? "minecraft:oak_stairs[half=top,facing=east]" : "minecraft:air", 1L);
        BuildGap.Probe probe = WorldProbe.of(registry, null);

        assertTrue(
                probe.loaded(known),
                "Заполненная клетка обязана читаться из кэша: иначе BuildGap снова начнёт обходить мир и"
                        + " холодный граф потеряет единый источник факта. При смене пробы перепроверь"
                        + " WorldProbe.loaded и AuditRegistry.blockAt.");
        assertEquals(
                "minecraft:oak_stairs",
                probe.block(known),
                "Строка состояния не должна просачиваться в block: иначе BlockMatch начнёт сравнивать"
                        + " свойства вместо id и постройка будет вечно неготовой. При смене пробы перепроверь"
                        + " WorldProbe.block и BuildGap.expect.");
        assertFalse(
                probe.loaded(unknown),
                "Клетка вне кэша обязана быть unknown, а не air: иначе BuildGap объявит отсутствующий чанк"
                        + " пустым и выдаст работу по выдуманному факту. При смене пробы перепроверь"
                        + " WorldProbe.loaded и неизвестные клетки BuildGap.");
    }
}
