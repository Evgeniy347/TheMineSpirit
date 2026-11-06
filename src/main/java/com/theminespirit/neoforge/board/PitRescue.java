package com.theminespirit.neoforge.board;

import com.theminespirit.core.movement.PitEscape;
import com.theminespirit.core.movement.SurfaceLevel;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.DigOutScript;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

final class PitRescue {
    private static final int SURFACE_RADIUS = 3;

    private PitRescue() {}

    static boolean escaped(SpiritEntity spirit) {
        Level level = spirit.level();
        BlockPos feet = spirit.blockPosition();
        List<Integer> heights = new ArrayList<>();
        for (int dx = -SURFACE_RADIUS; dx <= SURFACE_RADIUS; dx++) {
            for (int dz = -SURFACE_RADIUS; dz <= SURFACE_RADIUS; dz++) {
                if (dx != 0 || dz != 0) {
                    heights.add(top(level, feet.getX() + dx, feet.getZ() + dz));
                }
            }
        }
        return SurfaceLevel.escaped(DigOutScript.open(level, feet), feet.getY(), heights);
    }

    static Optional<PitEscape.Exit> trapped(SpiritEntity spirit) {
        Level level = spirit.level();
        BlockPos feet = spirit.blockPosition();
        if (top(level, feet.getX(), feet.getZ()) > feet.getY()) {
            return Optional.empty();
        }
        return PitEscape.exit(
                (dx, dz) -> dx == 0 && dz == 0 ? feet.getY() : top(level, feet.getX() + dx, feet.getZ() + dz));
    }

    static TaskNode plan(SpiritEntity spirit, PitEscape.Exit exit) {
        BlockPos feet = spirit.blockPosition();
        int standX = feet.getX() + exit.dx();
        int standZ = feet.getZ() + exit.dz();
        int rimY = top(spirit.level(), feet.getX() + exit.rimDx(), feet.getZ() + exit.rimDz());
        int blocks = spirit.getInventory().countItem(Items.DIRT)
                + spirit.getInventory().countItem(Items.COBBLESTONE);
        List<TaskNode> steps = new ArrayList<>();
        if (exit.dx() != 0 || exit.dz() != 0) {
            steps.add(
                    new ScriptCall("goto", Map.of("x", standX, "y", top(spirit.level(), standX, standZ), "z", standZ)));
        }
        if (blocks >= exit.rise()) {
            steps.add(new ScriptCall("pillar_up", Map.of("height", exit.rise())));
        } else {
            steps.add(new ScriptCall(
                    "dig_out", Map.of("x", feet.getX() + exit.rimDx(), "y", rimY, "z", feet.getZ() + exit.rimDz())));
        }
        return new SequenceNode(steps);
    }

    private static int top(Level level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }
}
