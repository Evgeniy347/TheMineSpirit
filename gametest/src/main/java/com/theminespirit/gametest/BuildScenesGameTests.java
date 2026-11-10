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
