package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.PlotCells;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.StageSite;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class PlotAudit {
    private static final int AUDIT_CACHE_TICKS = 600;
    private static final int HOLE_DEPTH_MAX = 64;

    record Audit(List<BlockCoord> holes, Map<BlockCoord, Integer> depths, List<BlockCoord> extras) {
        Audit {
            holes = List.copyOf(holes);
            depths = Map.copyOf(depths);
            extras = List.copyOf(extras);
        }
    }

    private static @Nullable Audit cachedAudit;
    private static @Nullable StageSite cachedSite;
    private static long cachedAt = Long.MIN_VALUE;

    private PlotAudit() {}

    static Audit audit(ServerLevel level, StageSite site, long now) {
        Audit cached = cachedAudit;
        if (cached != null && site.equals(cachedSite) && now >= cachedAt && now - cachedAt < AUDIT_CACHE_TICKS) {
            return cached;
        }
        BlockCoord anchor = site.anchor();
        List<BlockCoord> holes = new ArrayList<>();
        List<BlockCoord> extras = new ArrayList<>();
        Map<BlockCoord, Integer> depths = new LinkedHashMap<>();
        for (ColumnMask.Cell column : site.level().cells()) {
            int x = column.x();
            int z = column.z();
            if (site.ringPost(x, z)) {
                continue;
            }
            boolean mine = site.mineColumn(x, z);
            boolean planned = site.houseColumn(x, z);
            for (int dy = -1; dy < PlotCells.FREE_HEIGHT; dy++) {
                BlockCoord cell = new BlockCoord(x, anchor.y() + dy, z);
                PlotCells.defect(new PlotCells.Cell(dy, mine, planned), found(level.getBlockState(pos(cell))))
                        .ifPresent(defect -> {
                            if (defect == PlotCells.Defect.EXTRA) {
                                extras.add(cell);
                            } else {
                                holes.add(cell);
                                depths.put(cell, holeDepth(level, cell));
                            }
                        });
            }
        }
        Audit audit = new Audit(holes, depths, extras);
        cachedAudit = audit;
        cachedSite = site;
        cachedAt = now;
        return audit;
    }

    private static int holeDepth(ServerLevel level, BlockCoord cell) {
        int depth = 0;
        for (int y = cell.y() - 1; y > level.getMinBuildHeight() && depth < HOLE_DEPTH_MAX; y--) {
            BlockPos pos = new BlockPos(cell.x(), y, cell.z());
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                break;
            }
            depth++;
        }
        return depth;
    }

    private static PlotCells.Found found(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return PlotCells.Found.OPEN;
        }
        if (state.is(Blocks.COBBLESTONE)) {
            return PlotCells.Found.COBBLESTONE;
        }
        boolean natural = state.is(BlockTags.DIRT)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY);
        return natural ? PlotCells.Found.NATURAL : PlotCells.Found.OTHER;
    }

    private static BlockPos pos(BlockCoord cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }
}
