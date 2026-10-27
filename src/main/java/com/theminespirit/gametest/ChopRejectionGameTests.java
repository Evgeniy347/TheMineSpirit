package com.theminespirit.gametest;

import static com.theminespirit.gametest.ChopSceneSupport.buildLogHut;
import static com.theminespirit.gametest.ChopSceneSupport.buildLogPillar;
import static com.theminespirit.gametest.ChopSceneSupport.buildOakTree;
import static com.theminespirit.gametest.ChopSceneSupport.clearField;
import static com.theminespirit.gametest.ChopSceneSupport.digPitAround;
import static com.theminespirit.gametest.ChopSceneSupport.prepareField;
import static com.theminespirit.gametest.ChopSceneSupport.snapshotFieldWithoutLeaves;
import static com.theminespirit.gametest.ChopTestSupport.FIELD;
import static com.theminespirit.gametest.ChopTestSupport.LOGGER;
import static com.theminespirit.gametest.ChopTestSupport.SEARCH_RADIUS;
import static com.theminespirit.gametest.ChopTestSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.ChopTestSupport.TOOL_SLOT;
import static com.theminespirit.gametest.ChopTestSupport.chopCall;
import static com.theminespirit.gametest.ChopTestSupport.countBlocksInWorld;
import static com.theminespirit.gametest.ChopTestSupport.spawnChopper;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
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
final class ChopRejectionGameTests {
    private ChopRejectionGameTests() {}

    @SceneStep(template = FIELD, timeoutTicks = 300)
    public static void chopTree_TreeInsideProtectedZone_LogsStayAndSummaryCountsThem(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        SpiritEntity spirit = spawnChopper(helper);
        BlockPos from = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos to = helper.absolutePos(new BlockPos(5, 8, 5));
        GameTestFilter.setProtectedZones(List.of(helper.getLevel().dimension().location() + " " + from.getX() + " "
                + from.getY() + " " + from.getZ() + " " + to.getX() + " " + to.getY() + " " + to.getZ()));
        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) != RunStatus.RUNNING,
                    "chop_tree по защищённому дереву обязан завершиться, а не рубить его");
            helper.assertTrue(
                    countBlocksInWorld(helper, Blocks.OAK_LOG) == 5,
                    "брёвна в защищённой зоне обязаны уцелеть, осталось " + countBlocksInWorld(helper, Blocks.OAK_LOG));
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED
                            && spirit.taskFailureReason().orElse("").contains("пропущено защищённых"),
                    "итог называет число пропущенных защищённых брёвен: " + spirit.taskStatus() + ", "
                            + spirit.taskFailureReason());
            GameTestFilter.setProtectedZones(List.of());
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = 400)
    public static void chopTree_LogHouseNearby_HouseIntact(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        buildLogHut(helper, 8, 8, 3);
        for (int x = 8; x <= 10; x++) {
            for (int z = 8; z <= 10; z++) {
                for (int y = 1; y <= 3; y++) {
                    PlacedBlocksData.get(helper.getLevel()).mark(helper.absolutePos(new BlockPos(x, y, z)));
                }
            }
        }
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "chop_tree обязан срубить выбранное дерево рядом с домом из брёвен, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            int logsInInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG);
            helper.assertTrue(
                    logsInInventory == 5,
                    "дерево срублено целиком: 5 брёвен в инвентаре, фактически " + logsInInventory);
            int houseLogs = countBlocksInWorld(helper, Blocks.OAK_LOG);
            helper.assertTrue(houseLogs == 27, "дом из 27 брёвен рядом обязан уцелеть целиком, осталось " + houseLogs);
            for (int x = 8; x <= 10; x++) {
                for (int z = 8; z <= 10; z++) {
                    for (int y = 1; y <= 3; y++) {
                        PlacedBlocksData.get(helper.getLevel()).unmark(helper.absolutePos(new BlockPos(x, y, z)));
                    }
                }
            }

            clearField(helper);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = 300)
    public static void chopTree_HutAndPillarRejected_NotATreeReturnsFail(GameTestHelper helper) {
        prepareField(helper);
        buildLogHut(helper, 3, 3, 3);
        buildLogPillar(helper, 8, 6, 5);
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("any", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "хижина и столб не деревья: chop_tree обязан вернуть FAILED, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("деревья")
                            && reason.contains("закончились")
                            && reason.contains("отвергнуто не-деревьев"),
                    "причина FAILED обязана упоминать, что деревья закончились и не-деревья отвергнуты: " + reason);

            int totalLogs = countBlocksInWorld(helper, Blocks.OAK_LOG);
            helper.assertTrue(
                    totalLogs == 27 + 5,
                    "хижина (27) + столб (5) = 32 бревна должны остаться нетронутыми, осталось " + totalLogs);

            clearField(helper);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = 300)
    public static void chopTree_CountThreeWithOneTree_FailsWithCountMessage(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 3), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "при count=3 и одном дереве должен быть FAILED, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.startsWith(
                            "срублено деревьев 1 из 3: деревья породы oak в радиусе " + SEARCH_RADIUS + " закончились"),
                    "причина FAILED: 'срублено деревьев 1 из 3: деревья породы oak в радиусе " + SEARCH_RADIUS
                            + " закончились': " + reason);

            clearField(helper);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = 300)
    public static void chopTree_TreeOnIslandWithPit_RejectedUnreachable(GameTestHelper helper) {
        GameTestFixtures.sweepEntities(helper);
        prepareField(helper);
        digPitAround(helper, 6, 6, 5);
        buildOakTree(helper, 6, 6, 5);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(0, 1, 0));
        spirit.getInventory().setItem(TOOL_SLOT, new ItemStack(Items.IRON_AXE));

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "дерево на острове за ямой глубже безопасного падения недоступно: должен быть FAILED, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(
                    reason.contains("недоступных: 1"), "причина FAILED обязана содержать 'недоступных: 1': " + reason);
            helper.assertTrue(
                    run.spiritTicks() <= 150,
                    "недоступное дерево обязано определяться за ≤150 тиков, заняло " + run.spiritTicks());

            LOGGER.info("chop_tree, остров за ямой: дерево признано недоступным за {} тиков духа", run.spiritTicks());
            BlockPos spiritCell = helper.relativePos(spirit.blockPosition());
            helper.assertTrue(
                    spiritCell.getY() >= 1 && (spiritCell.getX() == 0 || spiritCell.getZ() == 0),
                    "дух не должен оказаться в яме: он обязан остаться на кромке (x=0 или z=0, y>=1), " + "а стоит в "
                            + spiritCell);
            helper.assertTrue(
                    countBlocksInWorld(helper, Blocks.OAK_LOG) == 5,
                    "дерево на острове недоступно и обязано остаться целым");

            clearField(helper);
        });
    }

    @SceneStep(template = FIELD, timeoutTicks = 300)
    public static void chopTree_CancelMidChop_WorldUnchangedAfter40Ticks(GameTestHelper helper) {
        prepareField(helper);
        buildOakTree(helper, 4, 4, 5);
        SpiritEntity spirit = spawnChopper(helper);

        SpiritRun run = SpiritRun.launch(helper, spirit, chopCall("oak", 1), SETTLE_TICKS);

        int[] cancelledAt = {-1};
        List<String> frozenWorld = new ArrayList<>();
        BlockPos[] frozenSpirit = new BlockPos[1];

        helper.succeedWhen(() -> {
            run.assertStarted();
            if (cancelledAt[0] < 0) {
                boolean midChop = countBlocksInWorld(helper, Blocks.OAK_LOG) < 5
                        && spirit.taskStatus().orElse(null) == RunStatus.RUNNING;
                helper.assertTrue(midChop, "ожидание середины рубки: хотя бы одно бревно срублено, задача идёт");
                cancelledAt[0] = spirit.tickCount;
                spirit.cancelTask();
                frozenWorld.addAll(snapshotFieldWithoutLeaves(helper));
                frozenSpirit[0] = spirit.blockPosition();
            }
            helper.assertTrue(
                    spirit.tickCount >= cancelledAt[0] + 40,
                    "ожидание 40 тиков после отмены: тик отмены " + cancelledAt[0] + ", тик духа " + spirit.tickCount);
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.CANCELLED,
                    "статус задачи после отмены обязан быть CANCELLED: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(
                    frozenWorld.equals(snapshotFieldWithoutLeaves(helper)),
                    "через 40 тиков после отмены дух не должен менять мир: ни ломки, ни трещин, ни подбора "
                            + "(листва не в счёте: срубленное сверху дерево роняет крону, это механика игры, "
                            + "а не действие духа)");
            helper.assertTrue(
                    frozenSpirit[0].equals(spirit.blockPosition()),
                    "после отмены дух обязан стоять: был в " + frozenSpirit[0] + ", стал в " + spirit.blockPosition());

            clearField(helper);
        });
    }
}
