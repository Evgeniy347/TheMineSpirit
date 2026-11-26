package com.theminespirit.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

final class BreakReach {
    private static final double EYE_HEIGHT = 1.62;

    private BreakReach() {}

    static boolean reachableFrom(BlockPos feet, BlockPos target) {
        Vec3 eyes = new Vec3(feet.getX() + 0.5, feet.getY() + EYE_HEIGHT, feet.getZ() + 0.5);
        return eyes.distanceTo(Vec3.atCenterOf(target)) <= BlockBreaker.REACH;
    }
}
