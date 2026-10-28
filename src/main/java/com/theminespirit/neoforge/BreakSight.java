package com.theminespirit.neoforge;

import com.theminespirit.core.building.BlockSight;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.tactics.Point;
import com.theminespirit.core.tactics.adapter.Visibility;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

final class BreakSight {
    private BreakSight() {}

    static Visibility visible(SpiritEntity spirit, BlockPos target, boolean leavesPass) {
        Predicate<BlockCoord> passable = leavesPass ? cell -> leaves(spirit.level(), cell) : cell -> false;
        return BlockSight.visible(
                point(spirit.getEyePosition()),
                new BlockCoord(target.getX(), target.getY(), target.getZ()),
                new LoadedTerrain(spirit.level()),
                passable);
    }

    static boolean retryable(BlockBreaker.RefuseReason reason) {
        return reason == BlockBreaker.RefuseReason.OUT_OF_REACH || reason == BlockBreaker.RefuseReason.NO_SIGHT;
    }

    private static boolean leaves(Level level, BlockCoord cell) {
        return level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z())).is(BlockTags.LEAVES);
    }

    private static Point point(Vec3 vec) {
        return new Point(vec.x, vec.y, vec.z);
    }
}
