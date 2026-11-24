package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.ConstructionBatches;
import com.theminespirit.core.board.ConstructionMarks;
import com.theminespirit.core.board.RepairAudit;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.building.api.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.OrchardSites;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownPlan;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class ConstructionAuditService {
    public static final int RADIUS_CHUNKS = 4;
    private static final int LIMIT_PER_BATCH = 2000;

    public record Batch(List<BlockPos> demolish, List<BlockPos> place, int index, boolean hasMore) {
        public Batch {
            demolish = List.copyOf(demolish);
            place = List.copyOf(place);
        }
    }

    private ConstructionAuditService() {}

    public static Batch batch(ServerLevel level, BlockPos center, int index) {
        List<ConstructionBatches.ChunkCell> order =
                ConstructionBatches.spiral(center.getX() >> 4, center.getZ() >> 4, RADIUS_CHUNKS);
        List<ConstructionBatches.ChunkCell> chunks = ConstructionBatches.batch(order, index);
        if (chunks.isEmpty()) {
            return new Batch(List.of(), List.of(), index, false);
        }
        boolean hasMore = ConstructionBatches.hasMore(order, index);
        Set<ConstructionBatches.ChunkCell> selected = Set.copyOf(chunks);
        Optional<TownPlan> town = SettlementPlotStore.get(level).town();
        Optional<StageSite> site = SettlementPlotStore.liveSite(level);
        Optional<SettlementPlot> plot = SettlementPlotStore.get(level).plot();
        if (town.isEmpty() || site.isEmpty() || plot.isEmpty()) {
            return new Batch(List.of(), List.of(), index, hasMore);
        }
        BlockCoord anchor = site.orElseThrow().anchor();
        String settlementWood = plot.orElseThrow().wood();
        Set<BlockPos> demolish = new LinkedHashSet<>();
        Set<BlockPos> place = new LinkedHashSet<>();
        for (Placement at : town.orElseThrow().placements()) {
            if (!intersects(at, selected)) {
                continue;
            }
            Optional<StageCatalog.Entry> entry = StageCatalog.entry(at.id());
            if (entry.isEmpty()) {
                continue;
            }
            GraphFacts.Position origin = new GraphFacts.Position(at.x(), anchor.y(), at.z());
            RepairAudit.Report report = BuildingAudit.schemeReport(
                    level, entry.orElseThrow().scheme(), origin, wood(at, settlementWood), level.getGameTime());
            ConstructionMarks.Marks marks = ConstructionMarks.in(report, selected);
            add(level, demolish, marks.demolish());
            add(level, place, marks.place());
        }
        return new Batch(List.copyOf(demolish), List.copyOf(place), index, hasMore);
    }

    private static boolean intersects(Placement place, Set<ConstructionBatches.ChunkCell> chunks) {
        int minX = place.x() >> 4;
        int maxX = (place.x() + place.width() - 1) >> 4;
        int minZ = place.z() >> 4;
        int maxZ = (place.z() + place.depth() - 1) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (chunks.contains(new ConstructionBatches.ChunkCell(x, z))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void add(ServerLevel level, Set<BlockPos> target, Set<BlockCoord> cells) {
        for (BlockCoord cell : cells) {
            if (target.size() >= LIMIT_PER_BATCH) {
                return;
            }
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (level.isLoaded(pos)) {
                target.add(pos);
            }
        }
    }

    private static String wood(Placement place, String settlementWood) {
        if (OrchardSites.MAIN_PLACEMENT.equals(place.id())) {
            return WoodSpecies.OAK;
        }
        return OrchardSites.speciesOfBuilding(place.id()).orElse(settlementWood);
    }
}
