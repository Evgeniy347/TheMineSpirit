package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptResult;
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
public final class BuildResultGameTests {
    private static final int SETTLE_TICKS = 5;

    private BuildResultGameTests() {
    }

    private static void prepare(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
    }

    private static SpiritEntity spawnWith(GameTestHelper helper, BlockPos relative, ItemStack stack) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), relative);
        if (!stack.isEmpty()) {
            spirit.getInventory().setItem(0, stack);
        }
        return spirit;
    }

    private static Map<String, Object> dataOf(GameTestHelper helper, SpiritEntity spirit, String what) {
        helper.assertTrue(spirit.taskResult().orElse(null) instanceof ScriptResult.Data,
                what + " обязан нести Data-итог общим каналом, фактически "
                        + spirit.taskResult().map(result -> result.getClass().getSimpleName()).orElse("пусто"));
        return ((ScriptResult.Data) spirit.taskResult().orElseThrow()).fields();
    }

    @GameTest(template = "arena", batch = "build_result", timeoutTicks = 400)
    public static void fill_SucceededTask_ResultHoldsPlacedCounts(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = spawnWith(helper, new BlockPos(1, 1, 1), new ItemStack(Items.COBBLESTONE, 2));
        BlockPos from = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos to = helper.absolutePos(new BlockPos(5, 1, 4));
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("fill",
                Map.of("block", "cobblestone", "x1", from.getX(), "y1", from.getY(), "z1", from.getZ(),
                        "x2", to.getX(), "y2", to.getY(), "z2", to.getZ())), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "заливка двух клеток обязана завершиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            Map<String, Object> data = dataOf(helper, spirit, "успешный fill");
            helper.assertTrue(Integer.valueOf(2).equals(data.get("placed")) && "cobblestone".equals(data.get("block")),
                    "итог обязан назвать placed=2 и block=cobblestone, фактически " + data);
            helper.assertTrue(Integer.valueOf(0).equals(data.get("skipped"))
                    && Integer.valueOf(0).equals(data.get("failed"))
                    && Integer.valueOf(0).equals(data.get("missing")),
                    "чистая заливка обязана идти без пропусков, провалов и недостачи, фактически " + data);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "build_result", timeoutTicks = 400)
    public static void placeBlock_SucceededTask_ResultHoldsSinglePlaced(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = spawnWith(helper, new BlockPos(1, 1, 1), new ItemStack(Items.COBBLESTONE, 1));
        BlockPos absolute = helper.absolutePos(new BlockPos(3, 1, 3));
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("place_block",
                Map.of("block", "cobblestone", "x", absolute.getX(), "y", absolute.getY(), "z", absolute.getZ())),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "постановка обязана завершиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            Map<String, Object> data = dataOf(helper, spirit, "успешный place_block");
            helper.assertTrue(Integer.valueOf(1).equals(data.get("placed")) && "cobblestone".equals(data.get("block")),
                    "итог обязан назвать placed=1 и block=cobblestone, фактически " + data);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "build_result", timeoutTicks = 400)
    public static void clearArea_SucceededTask_ResultHoldsRemovedCounts(GameTestHelper helper) {
        prepare(helper);
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.COBBLESTONE);
        helper.setBlock(new BlockPos(5, 1, 4), Blocks.COBBLESTONE);
        SpiritEntity spirit = spawnWith(helper, new BlockPos(1, 1, 1), new ItemStack(Items.STONE_PICKAXE, 1));
        BlockPos from = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos to = helper.absolutePos(new BlockPos(5, 1, 4));
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("clear_area",
                Map.of("x1", from.getX(), "y1", from.getY(), "z1", from.getZ(),
                        "x2", to.getX(), "y2", to.getY(), "z2", to.getZ())), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "расчистка двух блоков обязана завершиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            Map<String, Object> data = dataOf(helper, spirit, "успешный clear_area");
            helper.assertTrue(Integer.valueOf(2).equals(data.get("removed"))
                    && Integer.valueOf(0).equals(data.get("placed")),
                    "итог обязан назвать removed=2 и placed=0, фактически " + data);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "build_result", timeoutTicks = 400)
    public static void digArea_SucceededTask_ResultHoldsRemovedCounts(GameTestHelper helper) {
        prepare(helper);
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.STONE);
        SpiritEntity spirit = spawnWith(helper, new BlockPos(1, 1, 1), new ItemStack(Items.STONE_PICKAXE, 1));
        BlockPos absolute = helper.absolutePos(new BlockPos(4, 1, 4));
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("dig_area",
                Map.of("x", absolute.getX(), "y", absolute.getY(), "z", absolute.getZ(), "size", 1)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "копка одного блока обязана завершиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            Map<String, Object> data = dataOf(helper, spirit, "успешный dig_area");
            helper.assertTrue(Integer.valueOf(1).equals(data.get("removed")),
                    "итог обязан назвать removed=1, фактически " + data);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "build_result", timeoutTicks = 400)
    public static void schematic_SucceededTask_ResultHoldsPlacedCounts(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = spawnWith(helper, new BlockPos(1, 1, 1), new ItemStack(Items.COBBLESTONE, 17));
        BlockPos absolute = helper.absolutePos(new BlockPos(3, 1, 3));
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("build_schematic",
                Map.of("file", "fixture_plate", "x", absolute.getX(), "y", absolute.getY(), "z", absolute.getZ())),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "стройка по схеме обязана завершиться успехом, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            Map<String, Object> data = dataOf(helper, spirit, "успешный build_schematic");
            helper.assertTrue(Integer.valueOf(17).equals(data.get("placed")),
                    "итог обязан назвать placed=17, фактически " + data);
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "build_result", timeoutTicks = 400)
    public static void schematic_Shortage_ResultEmptyWithNumberedReason(GameTestHelper helper) {
        prepare(helper);
        SpiritEntity spirit = spawnWith(helper, new BlockPos(1, 1, 1), new ItemStack(Items.COBBLESTONE, 5));
        BlockPos absolute = helper.absolutePos(new BlockPos(3, 1, 3));
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("build_schematic",
                Map.of("file", "fixture_plate", "x", absolute.getX(), "y", absolute.getY(), "z", absolute.getZ())),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "стройка без материала обязана провалиться, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(spirit.taskResult().isEmpty(),
                    "провал обязан идти с пустым итогом: числа живут в причине, фактически "
                            + spirit.taskResult().map(result -> result.getClass().getSimpleName()).orElse("пусто"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("17") && reason.contains("5"),
                    "причина обязана называть числа 17 и 5, фактически: " + reason);
            GameTestFixtures.finish(helper);
        });
    }
}
