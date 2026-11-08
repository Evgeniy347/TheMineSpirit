package com.theminespirit.neoforge.board;

import com.theminespirit.core.movement.PitExitPlanner;
import com.theminespirit.core.movement.PitWalkOut;
import com.theminespirit.core.movement.SurfaceLevel;
import com.theminespirit.core.movement.ToolBreakCost;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.DigOutScript;
import com.theminespirit.neoforge.PickaxeCheck;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.movement.CheapBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

final class PitRescue {
    private static final int SURFACE_RADIUS = 3;
    private static final String GOTO = "goto";
    private static final String DIG_OUT = "dig_out";
    private static final String PILLAR = "pillar_up";

    private PitRescue() {}

    static boolean escaped(SpiritEntity spirit) {
        Level level = spirit.level();
        BlockPos feet = spirit.blockPosition();
        List<Integer> heights = new ArrayList<>();
        int highest = feet.getY();
        for (int dx = -SURFACE_RADIUS; dx <= SURFACE_RADIUS; dx++) {
            for (int dz = -SURFACE_RADIUS; dz <= SURFACE_RADIUS; dz++) {
                if (dx != 0 || dz != 0) {
                    int height = top(level, feet.getX() + dx, feet.getZ() + dz);
                    heights.add(height);
                    highest = Math.max(highest, height);
                }
            }
        }
        boolean open = DigOutScript.open(level, feet);
        boolean walkExit = (!open || feet.getY() + SurfaceLevel.MARGIN < highest) && walkOut(level, feet);
        return SurfaceLevel.escaped(open, feet.getY(), heights, walkExit);
    }

    private static boolean walkOut(Level level, BlockPos feet) {
        return PitWalkOut.exists(new PitWalkOutView(level), feet.getX(), feet.getZ(), feet.getY());
    }

    static Optional<PitExitPlanner.Plan> trapped(SpiritEntity spirit) {
        Level level = spirit.level();
        BlockPos feet = spirit.blockPosition();
        if (top(level, feet.getX(), feet.getZ()) <= feet.getY()) {
            return Optional.empty();
        }
        PitExitPlanner.Plan plan = PitExitPlanner.plan(
                new BlockCoord(feet.getX(), feet.getY(), feet.getZ()), new PitWorldView(level), supplies(spirit));
        if (plan.method() == PitExitPlanner.Method.WALK && plan.exit().y() <= feet.getY() + 1) {
            return Optional.empty();
        }
        return Optional.of(plan);
    }

    static TaskNode plan(SpiritEntity spirit, PitExitPlanner.Plan exit) {
        List<TaskNode> steps = new ArrayList<>();
        BlockCoord target = exit.exit();
        switch (exit.method()) {
            case WALK -> steps.add(gotoPoint(target));
            case PILLAR -> {
                steps.add(new ScriptCall(PILLAR, Map.of("height", exit.rise())));
                steps.add(gotoPoint(target));
            }
            case STAIRS -> {
                steps.add(new ScriptCall(DIG_OUT, Map.of("x", target.x(), "y", target.y(), "z", target.z())));
                steps.add(gotoPoint(target));
            }
            case IMPOSSIBLE ->
                throw new IllegalArgumentException(
                        "План невозможного выхода не выполняется: духу нужен помощник (" + exit.why() + ")");
        }
        return new SequenceNode(steps);
    }

    static PitExitPlanner.Plan escapeAfterClear(SpiritEntity spirit) {
        Level level = spirit.level();
        BlockPos feet = spirit.blockPosition();
        BlockCoord from = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        PitWorldView live = new PitWorldView(level);
        PitExitPlanner.World cleared = cell -> inClearBox(cell, feet) ? PitExitPlanner.Kind.AIR : live.at(cell);
        return PitExitPlanner.plan(from, cleared, supplies(spirit));
    }

    private static boolean inClearBox(BlockCoord cell, BlockPos feet) {
        return Math.abs(cell.x() - feet.getX()) <= 1
                && Math.abs(cell.z() - feet.getZ()) <= 1
                && cell.y() >= feet.getY()
                && cell.y() <= feet.getY() + 1;
    }

    private static PitExitPlanner.Supplies supplies(SpiritEntity spirit) {
        Map<String, Integer> counts = CheapBlocks.counts(spirit);
        int blocks = 0;
        for (String id : ToolBreakCost.CHEAP_ORDER) {
            blocks += counts.getOrDefault(id, 0);
        }
        return new PitExitPlanner.Supplies(blocks, PickaxeCheck.has(spirit));
    }

    private static ScriptCall gotoPoint(BlockCoord point) {
        return new ScriptCall(GOTO, Map.of("x", point.x(), "y", point.y(), "z", point.z()));
    }

    private static int top(Level level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }
}
