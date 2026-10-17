package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.policy.PolicyConfig;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChopTargetGameTests {
    private static final String FIELD = "arena";
    private static final int SETTLE_TICKS = 1;

    private ChopTargetGameTests() {
    }

    @GameTest(template = FIELD, batch = "chop_target_basic", timeoutTicks = 300)
    public static void chopTarget_OakByCoordinate_ChoppedCompletely(GameTestHelper helper) {
        ChopTreeGameTests.prepareField(helper);
        ChopTreeGameTests.buildOakTree(helper, 4, 4, 5);
        SpiritEntity spirit = ChopTreeGameTests.spawnChopper(helper);
        BlockPos target = helper.absolutePos(new BlockPos(4, 1, 4));

        SpiritRun run = SpiritRun.launch(helper, spirit, chopTargetCall(target), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "chop_target по координате ствола обязан завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));

            int logsInInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            helper.assertTrue(logsInInventory == 5,
                    "в инвентаре должно быть 5 дубовых брёвен, фактически " + logsInInventory);

            int logsInWorld = ChopTreeGameTests.countBlocksInWorld(helper, Blocks.OAK_LOG);
            helper.assertTrue(logsInWorld == 0,
                    "дерево по координате обязано быть срублено целиком, осталось брёвен " + logsInWorld);

            int droppedLogs = GameTestFixtures.countInWorld(helper, Items.OAK_LOG);
            helper.assertTrue(droppedLogs == 0,
                    "сборщик обязан подобрать все выпавшие брёвна, осталось валяться " + droppedLogs);

            int leavesInWorld = ChopTreeGameTests.countBlocksInWorld(helper, Blocks.OAK_LEAVES);
            helper.assertTrue(leavesInWorld > 0,
                    "листва должна остаться целой, осталось " + leavesInWorld + " блоков листьев");

            ChopTreeGameTests.clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_target_neighbour", timeoutTicks = 400)
    public static void chopTarget_OakWithBirchNextToIt_LeavesNeighbourUntouched(GameTestHelper helper) {
        ChopTreeGameTests.prepareField(helper);
        ChopTreeGameTests.buildOakTree(helper, 4, 4, 5);
        ChopTreeGameTests.buildBirchTree(helper, 8, 4, 5);
        SpiritEntity spirit = ChopTreeGameTests.spawnChopper(helper);
        BlockPos target = helper.absolutePos(new BlockPos(4, 1, 4));

        SpiritRun run = SpiritRun.launch(helper, spirit, chopTargetCall(target), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "chop_target обязан срубить дерево по координате, причина: "
                            + spirit.taskFailureReason().orElse("нет"));

            int oakLogs = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            int birchLogs = GameTestFixtures.countInInventory(spirit.getInventory(), Items.BIRCH_LOG);
            helper.assertTrue(oakLogs == 5 && birchLogs == 0,
                    "срубить обязано только дуб: дуб=" + oakLogs + ", берёза=" + birchLogs);

            int oakInWorld = ChopTreeGameTests.countBlocksInWorld(helper, Blocks.OAK_LOG);
            int birchInWorld = ChopTreeGameTests.countBlocksInWorld(helper, Blocks.BIRCH_LOG);
            helper.assertTrue(oakInWorld == 0 && birchInWorld == 5,
                    "соседняя берёза обязана уцелеть целиком: дуб в мире=" + oakInWorld + ", берёза в мире=" + birchInWorld);

            ChopTreeGameTests.clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_target_no_tree", timeoutTicks = 200)
    public static void chopTarget_CoordinateWithoutTree_FailsWithReason(GameTestHelper helper) {
        ChopTreeGameTests.prepareField(helper);
        SpiritEntity spirit = ChopTreeGameTests.spawnChopper(helper);
        BlockPos target = helper.absolutePos(new BlockPos(5, 1, 5));

        SpiritRun run = SpiritRun.launch(helper, spirit, chopTargetCall(target), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "координата без дерева обязана дать FAILED, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(spirit.taskFailureReason().orElse("").contains("нет дерева"),
                    "причина обязана называть отсутствие дерева по координате, фактически: "
                            + spirit.taskFailureReason().orElse("нет"));

            ChopTreeGameTests.clearField(helper);
        });
    }

    @GameTest(template = FIELD, batch = "chop_target_protected", timeoutTicks = 300)
    public static void chopTarget_TreeInProtectedZone_RefusesToChop(GameTestHelper helper) {
        ChopTreeGameTests.prepareField(helper);
        ChopTreeGameTests.buildOakTree(helper, 4, 4, 5);
        SpiritEntity spirit = ChopTreeGameTests.spawnChopper(helper);
        BlockPos from = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos to = helper.absolutePos(new BlockPos(5, 8, 5));
        PolicyConfig.setProtectedZones(List.of(helper.getLevel().dimension().location() + " " + from.getX() + " "
                + from.getY() + " " + from.getZ() + " " + to.getX() + " " + to.getY() + " " + to.getZ()));
        BlockPos target = helper.absolutePos(new BlockPos(4, 1, 4));

        SpiritRun run = SpiritRun.launch(helper, spirit, chopTargetCall(target), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED
                            && spirit.taskFailureReason().orElse("").contains("защищено"),
                    "дерево в защищённой зоне — отказ: статус " + spirit.taskStatus() + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(ChopTreeGameTests.countBlocksInWorld(helper, Blocks.OAK_LOG) == 5,
                    "защищённое дерево обязано уцелеть, осталось брёвен "
                            + ChopTreeGameTests.countBlocksInWorld(helper, Blocks.OAK_LOG));

            PolicyConfig.setProtectedZones(List.of());
            GameTestFixtures.finish(helper);
        });
    }

    private static ScriptCall chopTargetCall(BlockPos target) {
        return new ScriptCall("chop_target", Map.of("x", target.getX(), "y", target.getY(), "z", target.getZ(),
                "replant", false));
    }
}
