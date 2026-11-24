package com.theminespirit.neoforge.board;

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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class ConstructionAuditService {
    public static final int RADIUS = 64;
    private static final int LIMIT = 6000;
    private static final int PLACEMENTS = 32;

    public record Marks(List<BlockPos> demolish, List<BlockPos> place) {
        public Marks {
            demolish = List.copyOf(demolish);
            place = List.copyOf(place);
        }
    }

    private ConstructionAuditService() {}

    public static Marks around(ServerLevel level, BlockPos center) {
        Optional<TownPlan> town = SettlementPlotStore.get(level).town();
        Optional<StageSite> site = SettlementPlotStore.liveSite(level);
        Optional<SettlementPlot> plot = SettlementPlotStore.get(level).plot();
        if (town.isEmpty() || site.isEmpty() || plot.isEmpty()) {
            return new Marks(List.of(), List.of());
        }
        BlockCoord anchor = site.orElseThrow().anchor();
        String settlementWood = plot.orElseThrow().wood();
        List<Placement> places = town.orElseThrow().placements().stream()
                .filter(place -> StageCatalog.entry(place.id()).isPresent())
                .filter(place -> Math.abs(place.centerX() - center.getX()) <= RADIUS + place.width()
                        && Math.abs(place.centerZ() - center.getZ()) <= RADIUS + place.depth())
                .sorted(Comparator.comparingLong(place -> distanceSq(place, center)))
                .limit(PLACEMENTS)
                .toList();
        Set<BlockPos> demolish = new LinkedHashSet<>();
        Set<BlockPos> place = new LinkedHashSet<>();
        BlockCoord middle = new BlockCoord(center.getX(), center.getY(), center.getZ());
        for (Placement at : places) {
            String scheme = StageCatalog.entry(at.id()).orElseThrow().scheme();
            GraphFacts.Position origin = new GraphFacts.Position(at.x(), anchor.y(), at.z());
            RepairAudit.Report report =
                    BuildingAudit.schemeReport(level, scheme, origin, wood(at, settlementWood), level.getGameTime());
            ConstructionMarks.Marks marks = ConstructionMarks.of(report, middle, RADIUS);
            add(level, demolish, marks.demolish());
            add(level, place, marks.place());
        }
        return new Marks(List.copyOf(demolish), List.copyOf(place));
    }

    private static void add(ServerLevel level, Set<BlockPos> target, Set<BlockCoord> cells) {
        for (BlockCoord cell : cells) {
            if (target.size() >= LIMIT) {
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

    private static long distanceSq(Placement place, BlockPos center) {
        long dx = (long) place.centerX() - center.getX();
        long dz = (long) place.centerZ() - center.getZ();
        return dx * dx + dz * dz;
    }
}
