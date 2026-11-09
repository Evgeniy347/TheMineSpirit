package com.theminespirit.neoforge;

import com.theminespirit.core.mining.SafeMinePlan;
import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

final class SafeMineFill {
    enum Result {
        RUNNING,
        DONE,
        FAILED
    }

    private static final List<String> FILL_BLOCKS = List.of(
            "minecraft:cobblestone",
            "minecraft:stone",
            "minecraft:dirt",
            "minecraft:gravel",
            "minecraft:sand",
            "minecraft:oak_planks");

    private final SpiritEntity spirit;
    private final Level level;
    private final BlockPlacer placer;

    private int cursor;
    private int filled;
    private String fillBlock;
    private List<BlockCoord> queue = List.of();
    private String failure;

    SafeMineFill(SpiritEntity spirit, Level level, BlockPlacer placer) {
        this.spirit = spirit;
        this.level = level;
        this.placer = placer;
    }

    void prepare(SafeMinePlan plan, int stepIndex) {
        cursor = 0;
        fillBlock = null;
        Set<BlockCoord> cells = new LinkedHashSet<>(plan.steps().get(stepIndex).fill());
        if (stepIndex + 1 < plan.steps().size()) {
            cells.addAll(plan.steps().get(stepIndex + 1).fill());
        }
        queue = List.copyOf(cells);
    }

    boolean isEmpty() {
        return queue.isEmpty();
    }

    void resetCursor() {
        cursor = 0;
        fillBlock = null;
    }

    int filled() {
        return filled;
    }

    void filled(int value) {
        filled = value;
    }

    String failure() {
        return failure;
    }

    Result tick() {
        List<BlockCoord> fill = queue;
        while (cursor < fill.size()) {
            BlockCoord cell = fill.get(cursor);
            BlockPos pos = toPos(cell);
            if (level.getBlockState(pos).getFluidState().isEmpty()) {
                cursor++;
                continue;
            }
            if (fillBlock == null || BlockItems.countIn(spirit.getInventory(), fillBlock) < 1) {
                fillBlock = pickFillBlock();
                if (fillBlock == null) {
                    failure = "нет блока для заделки опасной клетки " + cell + ": нужен булыжник, земля или доски";
                    return Result.FAILED;
                }
            }
            BlockPlacer.Begin begin = placer.begin(pos, BlockStates.stateFor(fillBlock), fillBlock);
            if (begin instanceof BlockPlacer.Started) {
                filled++;
                cursor++;
                return Result.RUNNING;
            }
            if (begin instanceof BlockPlacer.Protected protectedPlace) {
                failure = "ставить заделку запрещено: " + protectedPlace.reason();
                return Result.FAILED;
            }
            BlockPlacer.Refused refused = (BlockPlacer.Refused) begin;
            failure = "не удалось заложить клетку " + cell + ": " + refused.message();
            return Result.FAILED;
        }
        return Result.DONE;
    }

    private String pickFillBlock() {
        for (String id : FILL_BLOCKS) {
            if (BlockItems.countIn(spirit.getInventory(), id) >= 1) {
                return id;
            }
        }
        return null;
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
