package com.theminespirit.neoforge.board;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.FluidKind;
import com.theminespirit.neoforge.BlockLocator;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;

final class FluidSources {
    private static final int RADIUS = 16;
    private static final int SLICES = 16;

    private FluidSources() {}

    static Optional<BlockCoord> nearest(ServerLevel level, BlockCoord home, FluidKind fluid) {
        BlockPos center = new BlockPos(home.x(), home.y(), home.z());
        BlockLocator.Search search = BlockLocator.search(
                level, center, RADIUS, Set.of(fluid == FluidKind.WATER ? Blocks.WATER : Blocks.LAVA));
        while (!search.done()) {
            for (BlockPos found : search.step(SLICES)) {
                if (source(level, found, fluid)) {
                    return Optional.of(new BlockCoord(found.getX(), found.getY(), found.getZ()));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean source(ServerLevel level, BlockPos pos, FluidKind fluid) {
        return level.isLoaded(pos)
                && level.getFluidState(pos).isSource()
                && level.getFluidState(pos).is(fluid == FluidKind.WATER ? FluidTags.WATER : FluidTags.LAVA);
    }
}
