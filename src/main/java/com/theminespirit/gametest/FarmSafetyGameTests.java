package com.theminespirit.gametest;

import static com.theminespirit.gametest.FarmCropSupport.countBlock;
import static com.theminespirit.gametest.FarmTestSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.FarmTestSupport.START;
import static com.theminespirit.gametest.FarmTestSupport.WATER;
import static com.theminespirit.gametest.FarmTestSupport.bed;
import static com.theminespirit.gametest.FarmTestSupport.boneMealCall;
import static com.theminespirit.gametest.FarmTestSupport.harvestCall;
import static com.theminespirit.gametest.FarmTestSupport.plantCall;
import static com.theminespirit.gametest.FarmTestSupport.spawnWith;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.farming.CropKind;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class FarmSafetyGameTests {
    private FarmSafetyGameTests() {}

    private static void arenaFloor(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    // [флак] замер 974 тика: посадка, подкормка и три сбора 9 клеток; потолок 1950 ≈ 2× замера
    @SceneStep(template = "arena", timeoutTicks = 1950)
    public static void farmCycle_PlantGrowHarvestThreeTimes_KeepsFarmlandAndSeeds(GameTestHelper helper) {
        arenaFloor(helper);
        helper.setBlock(WATER, Blocks.WATER);
        List<BlockPos> cells = bed();
        for (BlockPos cell : cells) {
            helper.setBlock(cell, Blocks.FARMLAND);
        }
        SpiritEntity spirit = spawnWith(
                helper,
                START,
                new ItemStack(Items.WHEAT_SEEDS, 17),
                new ItemStack(Items.BONE_MEAL, 64),
                new ItemStack(Items.BONE_MEAL, 64));
        List<TaskNode> steps = new ArrayList<>();
        steps.add(plantCall(CropKind.WHEAT, 64));
        for (int round = 0; round < 3; round++) {
            steps.add(boneMealCall(CropKind.WHEAT, 256));
            steps.add(harvestCall(CropKind.WHEAT, true));
        }
        SpiritRun.launch(helper, spirit, new SequenceNode(steps), SETTLE_TICKS);
        int seedsAfterPlanting = 17 - 9;

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "цикл посадка, подкормка и три сбора обязан завершиться успехом: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            int seeds = GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT_SEEDS);
            helper.assertTrue(
                    seeds >= seedsAfterPlanting,
                    "после трёх кругов семян обязано быть не меньше, чем после посадки (" + seedsAfterPlanting
                            + "): самоподдерживающаяся грядка не теряет семян, а осталось " + seeds);
            helper.assertTrue(
                    countBlock(helper, Blocks.FARMLAND) == 9,
                    "пашни обязано остаться 9, фактически " + countBlock(helper, Blocks.FARMLAND));
            for (BlockPos cell : cells) {
                helper.assertTrue(
                        helper.getBlockState(cell.above()).is(Blocks.WHEAT),
                        "после цикла пустых клеток быть не должно, а над " + cell + " "
                                + helper.getBlockState(cell.above()).getBlock());
            }
            int wheat = GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT);
            helper.assertTrue(
                    wheat == 27, "за три сбора по 9 клеток обязано быть собрано 27 пшеницы, фактически " + wheat);
            GameTestFixtures.finish(helper);
        });
    }
}
