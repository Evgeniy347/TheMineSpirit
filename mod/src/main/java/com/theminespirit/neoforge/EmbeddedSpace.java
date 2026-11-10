package com.theminespirit.neoforge;

import com.theminespirit.core.movement.Box3;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.movement.PassageWalk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class EmbeddedSpace {
    private static final double EDGE = 1.0e-6;

    private EmbeddedSpace() {}

    public static boolean inside(SpiritEntity spirit) {
        Level level = spirit.level();
        BlockPos feet = spirit.blockPosition();
        if (PassageWalk.passageAt(level, new BlockCoord(feet.getX(), feet.getY(), feet.getZ())) != null) {
            return false;
        }
        return inside(spirit.getBoundingBox(), level.getBlockState(feet).getCollisionShape(level, feet), feet);
    }

    public static List<BlockPos> blocking(SpiritEntity spirit) {
        Level level = spirit.level();
        AABB body = spirit.getBoundingBox();
        int minX = Mth.floor(body.minX + EDGE);
        int maxX = Mth.floor(body.maxX - EDGE);
        int minY = Mth.floor(body.minY + EDGE);
        int maxY = Mth.floor(body.maxY - EDGE);
        int minZ = Mth.floor(body.minZ + EDGE);
        int maxZ = Mth.floor(body.maxZ - EDGE);
        List<BlockPos> blocked = new ArrayList<>(4);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos cell = new BlockPos(x, y, z);
                    if (PassageWalk.passageAt(level, new BlockCoord(x, y, z)) != null) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cell);
                    VoxelShape shape = state.getCollisionShape(level, cell);
                    if (!shape.isEmpty() && inside(body, shape, cell)) {
                        blocked.add(cell);
                    }
                }
            }
        }
        blocked.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed());
        return List.copyOf(blocked);
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
