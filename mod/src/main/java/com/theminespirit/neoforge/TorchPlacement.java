package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.SiteLevels;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TorchRule;
import com.theminespirit.core.town.api.PlanBuilding;
import com.theminespirit.neoforge.board.PlotPlanBlocks;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class TorchPlacement {
    private static final Set<String> AD_HOC = Set.of("minecraft:torch", "minecraft:soul_torch");

    record Spot(BlockCoord cell, @Nullable String stop, boolean failure) {}

    private TorchPlacement() {}

    static @Nullable Spot resolve(Level level, String blockId, BlockCoord wanted) {
        if (!AD_HOC.contains(blockId) || !(level instanceof ServerLevel server)) {
            return null;
        }
        StageSite site = SettlementPlotStore.liveSite(server).orElse(null);
        if (site == null) {
            return null;
        }
        String planned = PlotPlanBlocks.of(site).blocks().get(wanted);
        if (planned != null && planned.contains("torch")) {
            return null;
        }
        int level0 = SiteLevels.level(site);
        BlockCoord cell = wanted;
        if (wanted.y() == level0 + 1 && fenceAt(server, wanted)) {
            cell = new BlockCoord(wanted.x(), level0 + 2, wanted.z());
        }
        BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
        boolean plannedArea = site.protectionBuilding(cell.x(), cell.y(), cell.z()) != PlanBuilding.NONE;
        boolean fenceBelow = fenceAt(server, new BlockCoord(cell.x(), cell.y() - 1, cell.z()));
        if (!TorchRule.adHoc(plannedArea, level0, cell.y(), fenceBelow)) {
            if (cell.y() == level0 + 1 && plannedArea) {
                return new Spot(
                        cell,
                        "клетка " + cell + " в плане поселения: разовый факел не ставится, свет там по схеме",
                        false);
            }
            return new Spot(
                    cell,
                    "факел " + cell
                            + " вне правила света: разово только y0+1 на свободном грунте и y0+2 на верху забора",
                    true);
        }
        BlockState current = server.getBlockState(pos);
        if (!current.isAir() && !current.canBeReplaced()) {
            return new Spot(
                    cell, "клетка " + cell + " занята (" + BlockStates.idOf(current) + "): факел не поставлен", false);
        }
        return new Spot(cell, null, false);
    }

    private static boolean fenceAt(ServerLevel level, BlockCoord cell) {
        return level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z())).is(BlockTags.FENCES);
    }
}
