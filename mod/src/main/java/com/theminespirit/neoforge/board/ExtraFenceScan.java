package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FenceDemolition;
import com.theminespirit.core.town.OrchardPlot;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

final class ExtraFenceScan {
    private static final int BELOW = 1;
    private static final int ABOVE = 2;
    private static final int NOTE_LIMIT = 2;

    private ExtraFenceScan() {}

    static void collect(
            DemolitionCollect.Ctx ctx, List<BlockCoord> remove, List<BlockCoord> fences, List<String> notes) {
        ServerLevel level = ctx.level();
        StageSite site = ctx.site();
        PlacedBlocksData placed = PlacedBlocksData.get(level);
        Set<Long> ring = keys(site.ring().posts());
        Set<Long> orchards = orchardKeys(site);
        int shown = 0;
        int from = site.anchor().y() - BELOW;
        int to = site.anchor().y() + ABOVE;
        for (ColumnMask.Cell column : site.enclosure().cells()) {
            int x = column.x();
            int z = column.z();
            if (site.fenceStrip().contains(x, z) || ring.contains(key(x, z))) {
                continue;
            }
            boolean inPlacement = site.placements().stream().anyMatch(place -> place.covers(x, z));
            boolean inOrchard = orchards.contains(key(x, z));
            for (int y = from; y <= to; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!level.isLoaded(pos)) {
                    continue;
                }
                BlockState state = level.getBlockState(pos);
                if (!state.is(BlockTags.FENCES) && !state.is(BlockTags.FENCE_GATES)) {
                    continue;
                }
                BlockCoord at = new BlockCoord(x, y, z);
                FenceDemolition.StubFacts facts = new FenceDemolition.StubFacts(
                        ctx.plan().planned(at),
                        inPlacement,
                        inOrchard,
                        false,
                        false,
                        placed.isSpiritMarked(pos),
                        placed.isMarked(pos));
                if (!FenceDemolition.stub(BlockStates.idOf(state), facts)
                        || remove.contains(at)
                        || ctx.unreachable().excluded(at)
                        || !FloatingScan.reachable(level, at)) {
                    continue;
                }
                remove.add(at);
                fences.add(at);
                if (shown++ < NOTE_LIMIT) {
                    notes.add(at + " — заглушка вне плана: в уборку");
                }
            }
        }
    }

    private static Set<Long> orchardKeys(StageSite site) {
        Set<Long> keys = keys(site.orchard().mask().cells());
        for (OrchardPlot plot : site.extraOrchards()) {
            keys.addAll(keys(plot.place().mask().cells()));
        }
        return keys;
    }

    private static Set<Long> keys(List<ColumnMask.Cell> cells) {
        Set<Long> keys = new HashSet<>(cells.size());
        for (ColumnMask.Cell cell : cells) {
            keys.add(key(cell.x(), cell.z()));
        }
        return keys;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
