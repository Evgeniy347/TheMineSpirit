package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;

final class HarvestStands {
    private HarvestStands() {}

    static boolean reachable(SpiritEntity spirit, BlockCoord stand) {
        Level level = spirit.level();
        if (leaves(level, stand)
                || leaves(level, new BlockCoord(stand.x(), stand.y() - 1, stand.z()))
                || leaves(level, new BlockCoord(stand.x(), stand.y() + 1, stand.z()))) {
            return false;
        }
        Path path = spirit.getNavigation().createPath(new BlockPos(stand.x(), stand.y(), stand.z()), 1);
        return path != null && path.canReach();
    }

    private static boolean leaves(Level level, BlockCoord cell) {
        return level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z())).is(BlockTags.LEAVES);
    }
}
