package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.DropCollector;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CollectDropsGameTests {
    private static final BlockPos SPIRIT_START = new BlockPos(4, 1, 4);
    private static final int PILLAR_HEIGHT = 4;
    private static final int SETTLE_TICKS = 1;
    private static final int PILLAR_SETTLE_TICKS = 10;

    private CollectDropsGameTests() {
    }

    @GameTest(template = "arena", batch = "drops_cobblestone", timeoutTicks = 400)
    public static void collectDrops_ThreeHeapsInRadius_PicksUpAllNine(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        spawnCobblestoneHeaps(helper);
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 6)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED && run.spiritTicks() <= 200,
                    "collect_drops обязан завершиться успехом за 200 тиков духа, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            int inInventory = GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE);
            helper.assertTrue(inInventory == 9,
                    "в инвентаре должно оказаться 9 булыжника (5+3+1), фактически " + inInventory);
            helper.assertTrue(GameTestFixtures.dropsInArea(helper, Items.COBBLESTONE).isEmpty(),
                    "после подбора в области не должно остаться кучек булыжника: предметы не подобранные, "
                            + "а скрипт завершился — ложный успех");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "drops_filter", timeoutTicks = 400)
    public static void collectDrops_LogFilter_PicksLogsAndLeavesCobblestone(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        GameTestFixtures.dropAt(helper, Items.OAK_LOG, 4, 2.5, 1, 2.5);
        GameTestFixtures.dropAt(helper, Items.BIRCH_LOG, 2, 6.5, 1, 2.5);
        ItemEntity cobblestone = GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 3, 2.5, 1, 6.5);
        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("collect_drops", Map.of("radius", 6, "filter", List.of("*_log"))), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "collect_drops с фильтром обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_LOG) == 4
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.BIRCH_LOG) == 2,
                    "оба вида брёвен обязаны оказаться в инвентаре (4 дуба, 2 берёзы)");
            helper.assertTrue(!cobblestone.isRemoved() && cobblestone.getItem().getCount() == 3,
                    "булыжник не проходит фильтр *_log: его подбирать нельзя, а кучка обязана остаться нетронутой");
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 0,
                    "в инвентарь не должно попасть ни одного булыжника");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "drops_full", timeoutTicks = 400)
    public static void collectDrops_InventoryFullOfOtherStacks_FailsWithoutLosingItems(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        ItemEntity heap = GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 5, 2.5, 1, 2.5);
        int totalBefore = totalItems(helper, spirit);
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 6)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "полный инвентарь обязан провалить collect_drops, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("инвентарь полон") && reason.contains("cobblestone"),
                    "причина обязана называть переполнение и предмет, фактически: " + reason);
            helper.assertTrue(!heap.isRemoved() && heap.getItem().getCount() == 5,
                    "кучка, которая не поместилась, обязана остаться в мире целой: предметы не выбрасываются и не пропадают");
            helper.assertTrue(totalItems(helper, spirit) == totalBefore,
                    "суммарное число предметов (инвентарь + мир) обязано остаться прежним: было " + totalBefore
                            + ", стало " + totalItems(helper, spirit));
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "drops_pillar", timeoutTicks = 400)
    public static void collectDrops_HeapOnTallPillar_PicksRestAndFailsUnreachable(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int y = 1; y <= PILLAR_HEIGHT; y++) {
            helper.setBlock(7, y, 4, Blocks.STONE);
        }
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 2, 2.5, 1, 4.5);
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 3, 4.5, 1, 6.5);
        ItemEntity onPillar = GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 4, 7.5, PILLAR_HEIGHT + 1, 4.5);
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 6)),
                PILLAR_SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "недостижимая кучка обязана провалить collect_drops, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("не дотянулся"),
                    "причина обязана говорить о недостижимых предметах, фактически: " + reason);
            int took = run.spiritTicks();
            helper.assertTrue(took <= 120,
                    "недостижимая кучка обязана определяться детектором застревания за 120 тиков, а не тайм-аутом "
                            + "маршрута: заняло " + took);
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 5,
                    "достижимые кучки (2+3) обязаны быть подобраны до провала, в инвентаре "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            helper.assertTrue(!onPillar.isRemoved() && onPillar.getItem().getCount() == 4,
                    "кучка на столбе обязана остаться целой");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "drops_nothing", timeoutTicks = 400)
    public static void collectDrops_NothingToPickUp_SucceedsWithinTwoTicks(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 6)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "без предметов вокруг collect_drops обязан завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            int took = run.spiritTicks();
            helper.assertTrue(took <= 2,
                    "успех без подбора обязан наступить сразу, не позже чем через 2 тика, а наступил через " + took);
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "drops_radius", timeoutTicks = 400)
    public static void collectDrops_HeapBeyondRadius_IsLeftUntouched(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 2, 5.5, 1, 4.5);
        ItemEntity far = GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 7, 8.5, 1, 4.5);
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 3)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "collect_drops с радиусом 3 обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 2,
                    "в инвентаре обязана оказаться только ближняя кучка (2), фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            helper.assertTrue(!far.isRemoved() && far.getItem().getCount() == 7,
                    "кучка за пределами радиуса, считая от точки старта духа, не должна трогаться");
            GameTestFixtures.reset(helper);
        });
    }

    @GameTest(template = "arena", batch = "drops_scale", timeoutTicks = 400)
    public static void dropCollector_ThreeHeaps_QueriesEntitiesOncePerRefreshPeriod(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 5, 1.5, 1, 2.5);
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 3, 7.5, 1, 2.5);
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 1, 4.5, 1, 7.5);
        BlockPos start = helper.absolutePos(SPIRIT_START);
        DropCollector collector = new DropCollector(spirit, IdFilter.matchAll(),
                new BlockCoord(start.getX(), start.getY(), start.getZ()), 6, PickupTuning.DEFAULT);
        AtomicInteger ticks = new AtomicInteger();
        AtomicInteger lastSpiritTick = new AtomicInteger(SETTLE_TICKS - 1);
        AtomicReference<DropCollector.State> state = new AtomicReference<>(DropCollector.State.RUNNING);
        helper.onEachTick(() -> {
            if (state.get() == DropCollector.State.RUNNING && spirit.tickCount >= SETTLE_TICKS
                    && spirit.tickCount != lastSpiritTick.get()) {
                lastSpiritTick.set(spirit.tickCount);
                ticks.incrementAndGet();
                state.set(collector.tick());
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(state.get() == DropCollector.State.DONE,
                    "DropCollector обязан закончить успехом, состояние " + state.get() + ", причина: "
                            + collector.failureReason());
            helper.assertTrue(collector.collected() == 9, "коллектор обязан подобрать 9 предметов, подобрал "
                    + collector.collected());
            int allowed = (ticks.get() + PickupTuning.REFRESH_TICKS - 1) / PickupTuning.REFRESH_TICKS + 1;
            helper.assertTrue(collector.entityQueries() <= allowed,
                    "за " + ticks.get() + " тиков допустимо " + allowed + " запросов сущностей (раз в "
                            + PickupTuning.REFRESH_TICKS + " тиков и один финальный), а сделано "
                            + collector.entityQueries() + ": запрос каждый тик нагружает сервер");
            helper.assertTrue(collector.entityQueries() >= 2,
                    "тест на масштаб бессмыслен без повторных опросов: запросов " + collector.entityQueries());
            GameTestFixtures.reset(helper);
        });
    }

    private static void spawnCobblestoneHeaps(GameTestHelper helper) {
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 5, 1.5, 1, 2.5);
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 3, 7.5, 1, 2.5);
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 1, 4.5, 1, 7.5);
    }

    private static int totalItems(GameTestHelper helper, SpiritEntity spirit) {
        int total = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            total += spirit.getInventory().getItem(slot).getCount();
        }
        return total + GameTestFixtures.countInWorld(helper, Items.COBBLESTONE);
    }
}
