package com.theminespirit.gametest;

import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

final class DigOutRoofChecks {
    private static final int TOP = 12;
    private static final int POCKET = 5;
    private static final String DIG_OUT = "dig_out";

    private DigOutRoofChecks() {}

    static SpiritEntity spawnEmbeddedUnderRoof(GameTestHelper helper, BlockCoord[] homeTarget) {
        BlockPos home = helper.absolutePos(new BlockPos(8, TOP + 1, 5));
        helper.setBlock(new BlockPos(8, TOP + 2, 5), Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(3, POCKET, 8));
        spirit.getInventory().setItem(0, new ItemStack(Items.WOODEN_PICKAXE));
        spirit.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 32));
        spirit.startTask(new ScriptCall("wait", Map.of("ticks", CatalogLimits.WAIT_TICKS_MAX)));
        homeTarget[0] = new BlockCoord(home.getX(), home.getY(), home.getZ());
        spirit.setHome(homeTarget[0]);
        return spirit;
    }

    static void checkTarget(GameTestHelper helper, SpiritEntity spirit, BlockCoord[] homeTarget) {
        if (spirit.isRemoved() || homeTarget[1] != null) {
            return;
        }
        Optional<BlockCoord> goal = digTarget(spirit.currentPlan());
        if (goal.isEmpty()) {
            return;
        }
        homeTarget[1] = goal.orElseThrow();
        helper.assertTrue(
                homeTarget[1].equals(homeTarget[0]),
                "dig_out обязан идти в стоянку дома " + homeTarget[0] + ", а не на крышу над ней: цель " + homeTarget[1]
                        + " — это верх колонки дома, дух полезет на крышу");
        spirit.discard();
    }

    static SpiritEntity spawnAboveHomeWithLowerGoal(GameTestHelper helper) {
        BlockPos feet = new BlockPos(2, 11, 8);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    helper.setBlock(feet.offset(dx, dy, dz), Blocks.AIR);
                }
            }
        }
        helper.setBlock(feet.below(), Blocks.DIRT);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), feet);
        spirit.getInventory().setItem(0, new ItemStack(Items.DIRT, 16));
        BlockPos goal = helper.absolutePos(new BlockPos(2, 6, 8));
        spirit.startTask(
                new ScriptCall(DIG_OUT, Map.of("x", goal.getX(), "y", goal.getY(), "z", goal.getZ(), "rescue", true)));
        return spirit;
    }

    static void checkFloor(GameTestHelper helper, SpiritEntity spirit, String[] refusal) {
        if (spirit.isRemoved() || refusal[0] != null) {
            return;
        }
        if (spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED) {
            helper.fail("dig_out с целью ниже духа прошёл вверх: спуска нет, а лестница вверх в цель не ведёт");
        }
        if (spirit.taskStatus().orElse(null) != RunStatus.FAILED) {
            return;
        }
        String why = spirit.taskFailureReason().orElse("");
        refusal[0] = why;
        helper.assertTrue(
                why.contains("ступень не подложить"),
                "ступень без опоры обязана отвергаться до постановки, а не пустой постановкой в журнале — " + why);
        spirit.discard();
    }

    static void cleanup(GameTestHelper helper) {
        helper.setBlock(new BlockPos(8, TOP + 2, 5), Blocks.AIR);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    helper.setBlock(new BlockPos(2, 11, 8).offset(dx, dy, dz), Blocks.STONE);
                }
            }
        }
        helper.setBlock(new BlockPos(3, POCKET, 8), Blocks.STONE);
        helper.setBlock(new BlockPos(3, POCKET + 1, 8), Blocks.STONE);
    }

    private static Optional<BlockCoord> digTarget(Optional<TaskNode> plan) {
        return plan.flatMap(DigOutRoofChecks::digTarget);
    }

    private static Optional<BlockCoord> digTarget(TaskNode node) {
        if (node instanceof ScriptCall call && DIG_OUT.equals(call.script())) {
            Object x = call.params().get("x");
            Object y = call.params().get("y");
            Object z = call.params().get("z");
            if (x instanceof Number atX && y instanceof Number atY && z instanceof Number atZ) {
                return Optional.of(new BlockCoord(atX.intValue(), atY.intValue(), atZ.intValue()));
            }
        }
        if (node instanceof SequenceNode sequence) {
            for (TaskNode step : sequence.steps()) {
                Optional<BlockCoord> found = digTarget(step);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }
}
