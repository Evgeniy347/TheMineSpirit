package com.theminespirit.gametest;

import static com.theminespirit.gametest.CollectDropsSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.CollectDropsSupport.SPIRIT_START;
import static com.theminespirit.gametest.CollectDropsSupport.spawnCobblestoneHeaps;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class CollectDropsBasicsGameTests {
    private CollectDropsBasicsGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void collectDrops_ThreeHeapsInRadius_PicksUpAllNine(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        spawnCobblestoneHeaps(helper);
        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 6)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED && run.spiritTicks() <= 200,
                    "collect_drops обязан завершиться успехом за 200 тиков духа, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            int inInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE);
            helper.assertTrue(
                    inInventory == 9, "в инвентаре должно оказаться 9 булыжника (5+3+1), фактически " + inInventory);
            helper.assertTrue(
                    GameTestFixtures.dropsInArea(helper, Items.COBBLESTONE).isEmpty(),
                    "после подбора в области не должно остаться кучек булыжника: предметы не подобранные, "
                            + "а скрипт завершился — ложный успех");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void collectDrops_LogFilter_PicksLogsAndLeavesCobblestone(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        GameTestFixtures.dropAt(helper, Items.OAK_LOG, 4, 2.5, 1, 2.5);
        GameTestFixtures.dropAt(helper, Items.BIRCH_LOG, 2, 6.5, 1, 2.5);
        ItemEntity cobblestone = GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 3, 4.5, 1, 10.5);
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("collect_drops", Map.of("radius", 6, "filter", List.of("*_log"))),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "collect_drops с фильтром обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG) == 4
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.BIRCH_LOG) == 2,
                    "оба вида брёвен обязаны оказаться в инвентаре (4 дуба, 2 берёзы)");
            helper.assertTrue(
                    !cobblestone.isRemoved() && cobblestone.getItem().getCount() == 3,
                    "булыжник не проходит фильтр *_log: его подбирать нельзя, а кучка обязана остаться нетронутой");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 0,
                    "в инвентарь не должно попасть ни одного булыжника");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void collectDrops_NothingToPickUp_SucceedsWithinTwoTicks(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 6)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "без предметов вокруг collect_drops обязан завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            int took = run.spiritTicks();
            helper.assertTrue(
                    took <= 2,
                    "успех без подбора обязан наступить сразу, не позже чем через 2 тика, а наступил через " + took);
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void collectDrops_HeapBeyondRadius_IsLeftUntouched(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 2, 5.5, 1, 4.5);
        ItemEntity far = GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 7, 8.5, 1, 4.5);
        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 3)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "collect_drops с радиусом 3 обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 2,
                    "в инвентаре обязана оказаться только ближняя кучка (2), фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            helper.assertTrue(
                    !far.isRemoved() && far.getItem().getCount() == 7,
                    "кучка за пределами радиуса, считая от точки старта духа, не должна трогаться");
            GameTestFixtures.finish(helper);
        });
    }
}
