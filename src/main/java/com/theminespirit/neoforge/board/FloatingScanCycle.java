package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockBox;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.SectionSlice;
import com.theminespirit.core.observation.SectionSweep;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FloatingBlocks;
import com.theminespirit.core.town.FloatingSweep;
import com.theminespirit.core.town.FloatingWindow;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.BlockStates;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

final class FloatingScanCycle {
    static final int SLICES_PER_STEP = FloatingSweep.SLICES_PER_STEP;
    static final int STEP_EVERY_TICKS = FloatingSweep.STEP_EVERY_TICKS;
    private static final int WHOLE_CYCLE = 1_000_000;
    private static final Map<ResourceKey<Level>, FloatingScanCycle> CYCLES = new HashMap<>();

    private record Area(int minX, int minZ, int width, int depth, int bottom, int top) {}

    private Area area;
    private SectionSweep sweep;
    private List<FloatingBlocks.Finding> pending = new ArrayList<>();
    private List<FloatingBlocks.Finding> published = List.of();
    private Set<BlockCoord> seen = new LinkedHashSet<>();

    private FloatingScanCycle() {}

    static List<FloatingBlocks.Finding> find(ServerLevel level, StageSite site, ColumnMask region) {
        Optional<SettlementPlot> plot = SettlementPlotStore.get(level).plot();
        if (plot.isEmpty()) {
            return List.of();
        }
        SettlementPlot measured = plot.orElseThrow();
        return find(level, site, region, FloatingWindow.bottomOf(measured), FloatingWindow.topOf(measured));
    }

    static List<FloatingBlocks.Finding> find(
            ServerLevel level, StageSite site, ColumnMask region, int bottom, int top) {
        ColumnMask area = site.enclosure().intersect(region);
        if (area.isEmpty()) {
            return List.of();
        }
        return CYCLES.computeIfAbsent(level.dimension(), key -> new FloatingScanCycle())
                .step(level, site, area, bottom, top);
    }

    static void advance(ServerLevel level) {
        if (level.getGameTime() % STEP_EVERY_TICKS != 0) {
            return;
        }
        Optional<StageSite> site = SettlementPlotStore.get(level).site();
        Optional<TownPlan> town = SettlementPlotStore.get(level).town();
        if (site.isEmpty() || town.isEmpty()) {
            return;
        }
        find(level, site.orElseThrow(), town.orElseThrow().region());
    }

    static List<FloatingBlocks.Finding> latest(ServerLevel level) {
        FloatingScanCycle cycle = CYCLES.get(level.dimension());
        return cycle == null ? List.of() : cycle.published;
    }

    static long cycleTicks(int slices) {
        return FloatingSweep.cycleTicks(slices);
    }

    static List<FloatingBlocks.Finding> full(
            ServerLevel level, StageSite site, ColumnMask region, int bottom, int top) {
        ColumnMask area = site.enclosure().intersect(region);
        if (area.isEmpty()) {
            return List.of();
        }
        FloatingScan.Scan scan = new FloatingScan.Scan(level, PlotPlanBlocks.of(site));
        List<FloatingBlocks.Finding> found = new ArrayList<>();
        Set<BlockCoord> seen = new LinkedHashSet<>();
        Map<BlockState, Boolean> candidates = new HashMap<>();
        for (SectionSlice slice : slices(area, bottom, top, WHOLE_CYCLE)) {
            scanSlice(level, area, slice, scan, candidates, seen, found);
        }
        collectPlaced(level, area, bottom, top, scan, seen, found);
        return List.copyOf(found);
    }

    private List<FloatingBlocks.Finding> step(ServerLevel level, StageSite site, ColumnMask area, int bottom, int top) {
        Area next = new Area(area.minX(), area.minZ(), area.width(), area.depth(), bottom, top);
        if (!next.equals(this.area)) {
            reset(next, area, bottom, top);
        }
        FloatingScan.Scan scan = new FloatingScan.Scan(level, PlotPlanBlocks.of(site));
        Map<BlockState, Boolean> candidates = new HashMap<>();
        SectionSweep.Batch batch = sweep.step();
        for (SectionSlice slice : batch.slices()) {
            scanSlice(level, area, slice, scan, candidates, seen, pending);
        }
        collectPlaced(level, area, bottom, top, scan, seen, pending);
        if (batch.cycleDone()) {
            published = List.copyOf(pending);
            pending = new ArrayList<>();
            seen = new LinkedHashSet<>();
        }
        return published;
    }

    private void reset(Area next, ColumnMask area, int bottom, int top) {
        this.area = next;
        sweep = new SectionSweep(box(area, bottom, top), center(area, bottom, top), SLICES_PER_STEP);
        pending = new ArrayList<>();
        published = List.of();
        seen = new LinkedHashSet<>();
    }

    private static List<SectionSlice> slices(ColumnMask area, int bottom, int top, int perStep) {
        return new SectionSweep(box(area, bottom, top), center(area, bottom, top), perStep)
                .step()
                .slices();
    }

    private static BlockBox box(ColumnMask area, int bottom, int top) {
        return new BlockBox(
                new BlockCoord(area.minX(), bottom, area.minZ()),
                new BlockCoord(area.minX() + area.width() - 1, top, area.minZ() + area.depth() - 1));
    }

    private static BlockCoord center(ColumnMask area, int bottom, int top) {
        return new BlockCoord(area.minX() + area.width() / 2, (bottom + top) / 2, area.minZ() + area.depth() / 2);
    }

    private static void scanSlice(
            ServerLevel level,
            ColumnMask area,
            SectionSlice slice,
            FloatingScan.Scan scan,
            Map<BlockState, Boolean> candidates,
            Set<BlockCoord> seen,
            List<FloatingBlocks.Finding> found) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(slice.sectionX(), slice.sectionZ());
        if (chunk == null) {
            return;
        }
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(slice.sectionY()));
        if (section.hasOnlyAir()
                || !section.maybeHas(state -> candidates.computeIfAbsent(state, known -> candidate(known, scan)))) {
            return;
        }
        for (int x = slice.bounds().min().x(); x <= slice.bounds().max().x(); x++) {
            for (int y = slice.bounds().min().y(); y <= slice.bounds().max().y(); y++) {
                for (int z = slice.bounds().min().z(); z <= slice.bounds().max().z(); z++) {
                    if (!area.contains(x, z)) {
                        continue;
                    }
                    BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                    if (!candidates.computeIfAbsent(state, known -> candidate(known, scan))) {
                        continue;
                    }
                    BlockCoord at = new BlockCoord(x, y, z);
                    if (!seen.add(at)) {
                        continue;
                    }
                    FloatingBlocks.classify(at, scan)
                            .ifPresent(finding -> FloatingScan.classifyTree(finding, scan, seen, found));
                }
            }
        }
    }

    private static void collectPlaced(
            ServerLevel level,
            ColumnMask area,
            int bottom,
            int top,
            FloatingScan.Scan scan,
            Set<BlockCoord> seen,
            List<FloatingBlocks.Finding> found) {
        for (BlockCoord cell : FloatingScan.placed(level, area)) {
            if (cell.y() >= bottom && cell.y() <= top && area.contains(cell.x(), cell.z()) && seen.add(cell)) {
                FloatingBlocks.classify(cell, scan)
                        .ifPresent(finding -> FloatingScan.classifyTree(finding, scan, seen, found));
            }
        }
    }

    private static boolean candidate(BlockState state, FloatingScan.Scan scan) {
        if (state.isAir()) {
            return false;
        }
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || !scan.natural(BlockStates.idOf(state));
    }
}
