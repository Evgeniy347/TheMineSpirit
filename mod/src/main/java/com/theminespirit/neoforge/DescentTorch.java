package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

final class DescentTorch {
    private static final String TORCH_BLOCK_ID = "minecraft:torch";

    private final Level level;
    private final BlockPlacer placer;

    DescentTorch(Level level, BlockPlacer placer) {
        this.level = level;
        this.placer = placer;
    }

    @Nullable
    String place(BlockCoord stand) {
        BlockPos standPos = toPos(stand);
        if (level.getBlockState(standPos).is(Blocks.TORCH)) {
            return null;
        }
        BlockPlacer.Begin begin = placer.begin(standPos, Blocks.TORCH.defaultBlockState(), TORCH_BLOCK_ID);
        if (begin instanceof BlockPlacer.Started) {
            return null;
        }
        if (begin instanceof BlockPlacer.Protected protectedPlace) {
            return "ставить факел запрещено: " + protectedPlace.reason();
        }
        BlockPlacer.Refused refused = (BlockPlacer.Refused) begin;
        return switch (refused.reason()) {
            case NO_MATERIAL -> "нет факелов в инвентаре: ступень " + stand + " осталась без света";
            case OCCUPIED -> "клетка ступени " + stand + " занята другим блоком: факел не поставлен";
            default -> refused.message();
        };
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
