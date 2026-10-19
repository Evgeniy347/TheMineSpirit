package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BuildFarmGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final int GROUND_Y = 0;
    private static final int FEET_Y = 1;
    private static final int PLOT_SIDE = 3;
    private static final int PLOT_CELLS = PLOT_SIDE * PLOT_SIDE;

    private BuildFarmGameTests() {}

    @GameTest(template = "arena", batch = "build_farm_script", timeoutTicks = 400)
    public static void buildFarm_PlotNearWater_TillsAndPlantsEveryCell(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int x = 4; x <= 6; x++) {
            for (int z = 4; z <= 6; z++) {
                helper.setBlock(new BlockPos(x, GROUND_Y, z), Blocks.DIRT);
            }
        }
        helper.setBlock(new BlockPos(2, GROUND_Y, 4), Blocks.WATER);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(5, FEET_Y, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_HOE));
        spirit.getInventory().setItem(1, new ItemStack(Items.WHEAT_SEEDS, PLOT_CELLS));
        BlockPos first = helper.absolutePos(new BlockPos(4, FEET_Y, 4));
        BlockPos second = helper.absolutePos(new BlockPos(6, FEET_Y, 6));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "build_farm",
                        Map.of(
                                "crop",
                                "wheat",
                                "x1",
                                first.getX(),
                                "z1",
                                first.getZ(),
                                "x2",
                                second.getX(),
                                "z2",
                                second.getZ())),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "грядка у воды обязана вспахаться и засеяться целиком: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи")
                            + ", причина " + spirit.taskFailureReason().orElse("нет"));
            for (int x = 4; x <= 6; x++) {
                for (int z = 4; z <= 6; z++) {
                    helper.assertTrue(
                            helper.getBlockState(new BlockPos(x, GROUND_Y, z)).is(Blocks.FARMLAND),
                            "клетка " + x + "," + z + " обязана быть пашней, а там "
                                    + helper.getBlockState(new BlockPos(x, GROUND_Y, z))
                                            .getBlock());
                    helper.assertTrue(
                            helper.getBlockState(new BlockPos(x, FEET_Y, z)).is(Blocks.WHEAT),
                            "над пашней " + x + "," + z + " обязана расти пшеница, а там "
                                    + helper.getBlockState(new BlockPos(x, FEET_Y, z))
                                            .getBlock());
                }
            }
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT_SEEDS) == 0,
                    "девять семян обязаны израсходоваться ровно, осталось "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.WHEAT_SEEDS));
            helper.assertTrue(
                    spirit.getInventory().getItem(0).getDamageValue() == PLOT_CELLS,
                    "износ мотыги обязан быть " + PLOT_CELLS + ", фактически "
                            + spirit.getInventory().getItem(0).getDamageValue());
            GameTestFixtures.finish(helper);
        });
    }
}
