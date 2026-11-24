package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.ConstructionBatches;
import com.theminespirit.core.board.ConstructionMarks;
import com.theminespirit.core.board.RepairAudit;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.board.graph.GraphFacts;
import com.theminespirit.core.building.api.WoodSpecies;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.OrchardSites;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.core.town.SettlementScan;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownPlan;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

public final class ConstructionAuditService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int LIMIT_PER_BATCH = 2000;

    public record Batch(List<BlockPos> demolish, List<BlockPos> place, int index, boolean hasMore) {
        public Batch {
            demolish = List.copyOf(demolish);
            place = List.copyOf(place);
        }
    }

    private ConstructionAuditService() {}

    public static Batch batch(ServerLevel level, BlockPos center, int index) {
        Optional<TownPlan> town = SettlementPlotStore.get(level).town();
        Optional<StageSite> site = SettlementPlotStore.liveSite(level);
        Optional<SettlementPlot> plot = SettlementPlotStore.get(level).plot();
        if (town.isEmpty() || site.isEmpty() || plot.isEmpty()) {
            LOGGER.debug("Аудит подсветки: поселение не размечено, пачка {}", index);
            return new Batch(List.of(), List.of(), index, false);
        }
        List<ConstructionBatches.ChunkCell> order = ConstructionBatches.order(
                settlementChunks(town.orElseThrow(), site.orElseThrow()), center.getX() >> 4, center.getZ() >> 4);
        LOGGER.debug(
                "Аудит подсветки: чанков поселения {}, размещений {}, пачка {}",
                order.size(),
                town.orElseThrow().placements().size(),
                index);
        List<ConstructionBatches.ChunkCell> chunks = ConstructionBatches.batch(order, index);
        if (chunks.isEmpty()) {
            return new Batch(List.of(), List.of(), index, false);
        }
        boolean hasMore = ConstructionBatches.hasMore(order, index);
        Set<ConstructionBatches.ChunkCell> selected = Set.copyOf(chunks);
        Set<BlockPos> demolish = new LinkedHashSet<>();
        Set<BlockPos> place = new LinkedHashSet<>();
        addSchemes(
                level,
                town.orElseThrow(),
                site.orElseThrow(),
                plot.orElseThrow().wood(),
                selected,
                demolish,
                place);
        SettlementScan.Strays strays = WorldAreaScan.cachedOrScan(level, site.orElseThrow());
        addCells(level, selected, demolish, WorldAreaScan.raised(strays));
        addCells(level, selected, place, WorldAreaScan.holes(strays));
        addSupport(level, town.orElseThrow(), site.orElseThrow(), selected, place);
        return new Batch(List.copyOf(demolish), List.copyOf(place), index, hasMore);
    }

    private static void addSupport(
            ServerLevel level,
            TownPlan town,
            StageSite site,
            Set<ConstructionBatches.ChunkCell> selected,
            Set<BlockPos> place) {
        int groundY = site.anchor().y() - 1;
        List<Placement> lots = new ArrayList<>(town.placements());
        lots.add(site.hut());
        lots.add(site.yard());
        lots.add(site.mine());
        lots.add(site.orchard());
        lots.addAll(site.placements());
        lots.addAll(site.fields());
        for (Placement at : lots) {
            if (!intersects(at, selected)) {
                continue;
            }
            for (int x = at.x(); x < at.x() + at.width(); x++) {
                for (int z = at.z(); z < at.z() + at.depth(); z++) {
                    if (place.size() >= LIMIT_PER_BATCH) {
                        return;
                    }
                    if (!selected.contains(ConstructionBatches.at(x, z))) {
                        continue;
                    }
                    BlockPos ground = new BlockPos(x, groundY, z);
                    if (!level.isLoaded(ground) || !level.getBlockState(ground).isAir()) {
                        continue;
                    }
                    if (level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) > groundY) {
                        place.add(ground);
                    }
                }
            }
        }
    }

    private static void addSchemes(
            ServerLevel level,
            TownPlan town,
            StageSite site,
            String settlementWood,
            Set<ConstructionBatches.ChunkCell> selected,
            Set<BlockPos> demolish,
            Set<BlockPos> place) {
        BlockCoord anchor = site.anchor();
        for (Placement at : town.placements()) {
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
    }

    private static Set<ConstructionBatches.ChunkCell> settlementChunks(TownPlan town, StageSite site) {
        Set<ConstructionBatches.ChunkCell> chunks = new LinkedHashSet<>();
        for (ColumnMask.Cell cell : town.region().cells()) {
            chunks.add(ConstructionBatches.at(cell.x(), cell.z()));
        }
        for (ColumnMask.Cell cell : site.zone().cells()) {
            chunks.add(ConstructionBatches.at(cell.x(), cell.z()));
        }
        return chunks;
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

    private static void addCells(
            ServerLevel level,
            Set<ConstructionBatches.ChunkCell> chunks,
            Set<BlockPos> target,
            List<BlockCoord> cells) {
        for (BlockCoord cell : cells) {
            if (target.size() >= LIMIT_PER_BATCH) {
                return;
            }
            if (!chunks.contains(ConstructionBatches.at(cell.x(), cell.z()))) {
                continue;
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
