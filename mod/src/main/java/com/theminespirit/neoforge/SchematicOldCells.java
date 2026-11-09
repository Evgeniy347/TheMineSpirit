package com.theminespirit.neoforge;

import com.theminespirit.core.building.BuildRotation;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicUpgrade;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlanBuilding;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

final class SchematicOldCells {
    private SchematicOldCells() {}

    static List<BlockCoord> extra(
            Level level,
            BlockCoord origin,
            Schematic schematic,
            BuildRotation rotation,
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int minY,
            int maxY) {
        if (!(level instanceof ServerLevel server)) {
            return List.of();
        }
        StageSite site = SettlementPlotStore.get(server).site().orElse(null);
        if (site == null) {
            return List.of();
        }
        PlanBuilding own = site.protectionBuilding(origin.x(), origin.y(), origin.z());
        if (own == PlanBuilding.NONE) {
            return List.of();
        }
        PlacedBlocksData placed = PlacedBlocksData.get(server);
        List<BlockCoord> previous = new ArrayList<>();
        for (int y = minY; y <= maxY + StageSite.BUILDING_HEIGHT; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (site.protectionBuilding(x, y, z) != own) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    if (placed.isSpiritMarked(pos) && !level.getBlockState(pos).isAir()) {
                        previous.add(new BlockCoord(x, y, z));
                    }
                }
            }
        }
        return SchematicUpgrade.extraPositions(previous, schematic, origin, rotation);
    }
}
