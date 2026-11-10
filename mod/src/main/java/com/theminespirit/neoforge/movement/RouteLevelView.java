package com.theminespirit.neoforge.movement;

import com.theminespirit.core.mining.BreakTiming;
import com.theminespirit.core.mining.ToolChoice;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.route.RouteCell;
import com.theminespirit.core.route.RouteWorld;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.MagmaBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class RouteLevelView implements RouteWorld {
    private static final double FLAT_HEIGHT = 0.2;
    private static final double FULL_HEIGHT = 1.0;
    private final SpiritEntity spirit;
    private final Level level;
    private final boolean rescue;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final Map<BlockState, Integer> breakTicks = new HashMap<>();
    private final Map<Long, Integer> cells = new HashMap<>();
    private long cellsTick = Long.MIN_VALUE;

    public RouteLevelView(SpiritEntity spirit) {
        this(spirit, false);
    }

    public RouteLevelView(SpiritEntity spirit, boolean rescue) {
        this.spirit = spirit;
        this.level = spirit.level();
        this.rescue = rescue;
    }

    public static int blocks(SpiritEntity spirit) {
        int total = 0;
        for (int count : CheapBlocks.counts(spirit).values()) {
            total += count;
        }
        return total;
    }

    @Override
    public int cell(int x, int y, int z) {
        long tick = level.getGameTime();
        if (tick != cellsTick) {
            cells.clear();
            cellsTick = tick;
        }
        long key = BlockPos.asLong(x, y, z);
        Integer known = cells.get(key);
        if (known != null) {
            return known;
        }
        int value = evaluate(x, y, z);
        cells.put(key, value);
        return value;
    }

    private int evaluate(int x, int y, int z) {
        cursor.set(x, y, z);
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight() || !level.isLoaded(cursor)) {
            return RouteCell.BLOCKED;
        }
        BlockState state = level.getBlockState(cursor);
        if (state.is(BlockTags.LEAVES)) {
            return RouteCell.BLOCKED;
        }
        FluidState fluid = state.getFluidState();
        if (fluid.is(FluidTags.LAVA) || hazard(state)) {
            return RouteCell.LAVA;
        }
        if (state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof TrapDoorBlock) {
            return RouteCell.BLOCKED;
        }
        VoxelShape shape = state.getCollisionShape(level, cursor);
        boolean flat = shape.isEmpty() || shape.max(Direction.Axis.Y) < FLAT_HEIGHT;
        if (flat && fluid.is(FluidTags.WATER)) {
            return RouteCell.WATER;
        }
        if (flat) {
            return state.is(BlockTags.CLIMBABLE) ? RouteCell.CLIMB : RouteCell.AIR;
        }
        int ticks = breakTicks(state);
        if (ticks < 0) {
            return RouteCell.BLOCKED;
        }
        boolean tall = shape.max(Direction.Axis.Y) > FULL_HEIGHT;
        return tall ? RouteCell.tall(ticks) : RouteCell.solid(ticks);
    }

    private int breakTicks(BlockState state) {
        double hardness = state.getDestroySpeed(level, cursor);
        if (hardness < 0) {
            return -1;
        }
        BlockProtection.Decision decision = rescue
                ? spirit.protectionGate().checkEscape(cursor.immutable(), state, true)
                : spirit.protectionGate().checkBreak(cursor.immutable(), state);
        if (decision instanceof BlockProtection.Refused) {
            return -1;
        }
        return breakTicks.computeIfAbsent(state, known -> fastest(known, hardness));
    }

    private int fastest(BlockState state, double hardness) {
        boolean needsTool = state.requiresCorrectToolForDrops();
        int best = BreakTiming.ticks(hardness, ToolChoice.HAND_SPEED, !needsTool);
        Container inventory = spirit.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            best = Math.min(best, ticksWith(inventory.getItem(slot), state, hardness, needsTool));
        }
        return Math.min(best, ticksWith(spirit.getMainHandItem(), state, hardness, needsTool));
    }

    private static int ticksWith(ItemStack stack, BlockState state, double hardness, boolean needsTool) {
        if (stack.isEmpty()) {
            return Integer.MAX_VALUE;
        }
        float speed = stack.getDestroySpeed(state);
        boolean correct = !needsTool || stack.isCorrectToolForDrops(state);
        return BreakTiming.ticks(hardness, Math.max(ToolChoice.HAND_SPEED, speed), correct);
    }

    private static boolean hazard(BlockState state) {
        return state.getBlock() instanceof BaseFireBlock
                || state.getBlock() instanceof MagmaBlock
                || state.getBlock() instanceof CactusBlock
                || state.getBlock() instanceof SweetBerryBushBlock
                || state.getBlock() instanceof WebBlock
                || state.getBlock() instanceof PowderSnowBlock;
    }
}
