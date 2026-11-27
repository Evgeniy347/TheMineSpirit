package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.board.audit.AuditBudgets;
import com.theminespirit.core.board.audit.AuditRegistry;
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
}
