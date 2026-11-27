package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.ConstructionBatches.ChunkCell;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.audit.AuditRegistry;
import com.theminespirit.core.board.audit.ChunkCache;
import com.theminespirit.core.board.audit.ExpectIndex;
import com.theminespirit.core.board.audit.Expectation;
import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.SchematicResources;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class AuditExpectations {
    private static final Set<String> MINE_IDS = Set.of("mine_entrance", "mine_descent", "mine_tunnel");

    private AuditExpectations() {}

    static ExpectIndex.Index index(TownPlan town, int stage, int levelY) {
        return ExpectIndex.of(town, stage, levelY, MINE_IDS, AuditExpectations::scheme);
    }

    static void deepBands(AuditRegistry registry, ExpectIndex.Index index) {
        registry.pruneDeepBands();
        Map<ChunkCell, int[]> ranges = new LinkedHashMap<>();
        int surfaceMax = registry.minY() + registry.layers() - 1;
        for (Expectation expectation : index.cells().values()) {
            BlockCoord cell = expectation.cell();
            if (cell.y() >= registry.minY() && cell.y() <= surfaceMax) {
                continue;
            }
            ChunkCell chunk = new ChunkCell(cell.x() >> 4, cell.z() >> 4);
            int[] range = ranges.computeIfAbsent(chunk, ignored -> new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE});
            range[0] = Math.min(range[0], cell.y());
            range[1] = Math.max(range[1], cell.y());
        }
        for (Map.Entry<ChunkCell, int[]> entry : ranges.entrySet()) {
            int y = entry.getValue()[0];
            int max = entry.getValue()[1];
            while (y <= max) {
                int end = Math.min(max, y + ChunkCache.MAX_LAYERS - 1);
                registry.ensureBand(entry.getKey(), y, end - y + 1);
                y = end + 1;
            }
        }
    }

    private static Optional<ExpectIndex.Scheme> scheme(String planId, int level) {
        return StageCatalog.scheme(planId, level).map(file -> {
            Schematic schematic = SchematicResources.load(file);
            BlockCoord entry = PresetLibrary.current()
                    .byId(file)
                    .map(preset -> preset.entry())
                    .orElseGet(() -> new BlockCoord(0, 0, 0));
            return new ExpectIndex.Scheme(schematic, entry);
        });
    }
}
