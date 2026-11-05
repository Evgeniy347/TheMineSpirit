package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.StageCatalog;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FarmScenesGameTests {
    private static final int SETTLE_TICKS = 5;

    private FarmScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_farm_arena", timeoutTicks = 2953)
    public static void farmScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(helper, List.of("arena"), FarmSafetyGameTests.class, HusbandryCullGameTests.class);
    }

    // [флак] замер ~2500 тиков: 132 клетки грядки (пахота, посев, вода); бюджет 4000 ≈ 1,6×
    @GameTest(template = "big_arena", batch = "scene_farm_field", timeoutTicks = 4000)
    public static void wheatField_OnGround_BuildsFarmlandSownAndWatered(GameTestHelper helper) {
        GameTestFixtures.reset(helper, 16);
        for (int x = -1; x <= 15; x++) {
            for (int z = -1; z <= 15; z++) {
                helper.setBlock(x, 0, z, Blocks.GRASS_BLOCK);
            }
        }
        int levelY = 1;
        int originY = StageCatalog.originY("wheat_field", levelY);
        BlockPos anchor = new BlockPos(1, originY, 1);
        BlockPos absolute = helper.absolutePos(anchor);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(9, 1, 9));
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_HOE));
        spirit.getInventory().setItem(1, new ItemStack(Items.IRON_SHOVEL));
        spirit.getInventory().setItem(2, new ItemStack(Items.WHEAT_SEEDS, 64));
        spirit.getInventory().setItem(3, new ItemStack(Items.WATER_BUCKET));
        spirit.getInventory().setItem(4, new ItemStack(Items.WATER_BUCKET));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "build_schematic",
                        Map.of(
                                "file", "wheat_field_1",
                                "x", absolute.getX(),
                                "y", absolute.getY(),
                                "z", absolute.getZ())),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "грядка 5×10 обязана построиться целиком, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            int farmland = count(helper, Blocks.FARMLAND);
            helper.assertTrue(farmland == 48, "пашни обязано быть 48 клеток, фактически " + farmland);
            int water = count(helper, Blocks.WATER);
            helper.assertTrue(water == 2, "источников воды по схеме два, фактически " + water);
            int wheat = count(helper, Blocks.WHEAT);
            helper.assertTrue(wheat == 48, "посевов пшеницы обязано быть 48, фактически " + wheat);
            GameTestFixtures.finish(helper, 16);
        });
    }

    private static int count(GameTestHelper helper, Block block) {
        int total = 0;
        for (int x = -2; x <= 18; x++) {
            for (int y = 0; y <= 12; y++) {
                for (int z = -2; z <= 18; z++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(block)) {
                        total++;
                    }
                }
            }
        }
        return total;
    }
}
