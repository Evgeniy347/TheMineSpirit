package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BuildScenesGameTests {
    private static final int SETTLE_TICKS = 5;

    private BuildScenesGameTests() {}

    @GameTest(template = "arena", batch = "scene_build_arena", timeoutTicks = 20403)
    public static void buildScene_ArenaSteps_AllPass(GameTestHelper helper) {
        SceneSteps.run(
                helper,
                List.of("arena"),
                PresetBuildGameTests.class,
                ShelterBuildGameTests.class,
                WarehouseGameTests.class);
    }

    @GameTest(template = "arena", batch = "scene_build_mound", timeoutTicks = 400)
    public static void buildScene_Farmland_LaysSoilAtPlanLevel(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 10; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
        BlockPos airTarget = new BlockPos(2, 1, 2);
        BlockPos sunk = new BlockPos(8, 1, 8);
        BlockPos sunkTarget = sunk.above();
        helper.setBlock(sunk, Blocks.FARMLAND);
        SpiritEntity airSpirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        SpiritEntity sunkSpirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(7, 1, 7));
        for (SpiritEntity spirit : List.of(airSpirit, sunkSpirit)) {
            spirit.getInventory().setItem(0, new ItemStack(Items.IRON_HOE));
            spirit.getInventory().setItem(1, new ItemStack(Items.DIRT, 3));
        }
        SpiritRun airRun = SpiritRun.launch(helper, airSpirit, placeFarmland(helper, airTarget), SETTLE_TICKS);
        SpiritRun sunkRun = SpiritRun.launch(helper, sunkSpirit, placeFarmland(helper, sunkTarget), SETTLE_TICKS);
        BlockPos airAbsolute = helper.absolutePos(airTarget);
        BlockPos sunkAbsolute = helper.absolutePos(sunkTarget);

        helper.succeedWhen(() -> {
            airRun.assertStarted();
            sunkRun.assertStarted();
            helper.assertTrue(
                    airSpirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "пашня над пустой клеткой обязана встать: сначала кладётся земля, затем тяпка, статус "
                            + airSpirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + airSpirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    sunkSpirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "пашня над старой пашней обязана встать в клетку плана, статус "
                            + sunkSpirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + sunkSpirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    helper.getLevel().getBlockState(airAbsolute).is(Blocks.FARMLAND),
                    "в клетке над воздухом обязана быть пашня, а не воздух: живой отказ «пашня не вышла "
                            + "(в клетке air)»");
            helper.assertTrue(
                    helper.getLevel().getBlockState(sunkAbsolute).is(Blocks.FARMLAND),
                    "в клетке плана над старой пашней обязана быть пашня: поднятая пашня не сползает к старой");
            helper.assertTrue(
                    helper.getLevel().getBlockState(helper.absolutePos(sunk)).is(Blocks.FARMLAND),
                    "старая пашня под планом цела: её оставляем слоем ниже по решению владельца");
            GameTestFixtures.finish(helper);
        });
    }

    private static ScriptCall placeFarmland(GameTestHelper helper, BlockPos target) {
        BlockPos absolute = helper.absolutePos(target);
        return new ScriptCall(
                "place_block",
                Map.of(
                        "block", "minecraft:farmland",
                        "x", absolute.getX(),
                        "y", absolute.getY(),
                        "z", absolute.getZ()));
    }

    @GameTest(template = "arena", batch = "scene_build_mound", timeoutTicks = 400)
    public static void buildScene_SealedMound_ClearsAndPlaces(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 10; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
        BlockPos target = new BlockPos(5, 1, 5);
        helper.setBlock(target, Blocks.DIRT);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(target.relative(side), Blocks.DIRT);
            helper.setBlock(target.relative(side).above(), Blocks.DIRT);
        }
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), target.above());
        spirit.getInventory().setItem(0, new ItemStack(Items.TORCH, 1));
        BlockPos absolute = helper.absolutePos(target);
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "place_block",
                        Map.of(
                                "block", "minecraft:torch",
                                "x", absolute.getX(),
                                "y", absolute.getY(),
                                "z", absolute.getZ())),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Дух в замурованной клетке обязан расчистить цель и поставить блок, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    helper.getLevel().getBlockState(absolute).is(Blocks.TORCH),
                    "В замурованной клетке обязан стоять факел: без расчистки PlaceBlock падал на «место занято»");
            GameTestFixtures.finish(helper);
        });
    }
}
