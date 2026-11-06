package com.theminespirit.neoforge;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;

final class PortalFrame {
    static final int WIDTH = 4;
    static final int HEIGHT = 5;

    private PortalFrame() {}

    record Spot(BlockPos bottom) {}

    static Optional<Spot> at(Level level, BlockPos base) {
        Optional<Spot> alongX = ring(level, base, true);
        return alongX.isPresent() ? alongX : ring(level, base, false);
    }

    static boolean isLit(Level level, Spot spot) {
        BlockPos bottom = spot.bottom().above();
        return level.getBlockState(bottom).getBlock() instanceof NetherPortalBlock;
    }

    private static Optional<Spot> ring(Level level, BlockPos base, boolean alongX) {
        boolean lit = false;
        for (int across = 0; across < WIDTH; across++) {
            for (int up = 0; up < HEIGHT; up++) {
                BlockPos cell = cell(base, across, up, alongX);
                boolean border = across == 0 || across == WIDTH - 1 || up == 0 || up == HEIGHT - 1;
                if (border) {
                    if (!level.getBlockState(cell).is(Blocks.OBSIDIAN)) {
                        return Optional.empty();
                    }
                } else {
                    BlockState inside = level.getBlockState(cell);
                    if (inside.getBlock() instanceof NetherPortalBlock) {
                        lit = true;
                    } else if (!inside.isAir()) {
                        return Optional.empty();
                    }
                }
            }
        }
        if (lit) {
            return Optional.empty();
        }
        BlockPos bottom = alongX
                ? new BlockPos(base.getX() + 1, base.getY(), base.getZ())
                : new BlockPos(base.getX(), base.getY(), base.getZ() + 1);
        return Optional.of(new Spot(bottom));
    }

    private static BlockPos cell(BlockPos base, int across, int up, boolean alongX) {
        return alongX
                ? new BlockPos(base.getX() + across, base.getY() + up, base.getZ())
                : new BlockPos(base.getX(), base.getY() + up, base.getZ() + across);
    }
}
