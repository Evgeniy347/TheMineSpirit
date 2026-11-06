package com.theminespirit.neoforge;

import com.theminespirit.core.movement.Box3;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.movement.PassageWalk;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class EmbeddedSpace {
    private EmbeddedSpace() {}

    public static boolean inside(SpiritEntity spirit) {
        Level level = spirit.level();
        BlockPos feet = spirit.blockPosition();
        if (PassageWalk.passageAt(level, new BlockCoord(feet.getX(), feet.getY(), feet.getZ())) != null) {
            return false;
        }
        return inside(spirit.getBoundingBox(), level.getBlockState(feet).getCollisionShape(level, feet), feet);
    }

    public static boolean inside(AABB body, VoxelShape shape, BlockPos pos) {
        if (shape.isEmpty()) {
            return false;
        }
        AABB moved = shape.move(pos.getX(), pos.getY(), pos.getZ()).bounds();
        Box3 bodyBox = new Box3(body.minX, body.minY, body.minZ, body.maxX, body.maxY, body.maxZ);
        return bodyBox.intersects(new Box3(moved.minX, moved.minY, moved.minZ, moved.maxX, moved.maxY, moved.maxZ));
    }
}
