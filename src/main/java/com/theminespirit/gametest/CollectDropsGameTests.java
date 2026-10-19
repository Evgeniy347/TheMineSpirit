package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.pickup.OverflowMode;
import com.theminespirit.core.pickup.PickupTuning;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.neoforge.DropCollector;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CollectDropsGameTests {
    private static final BlockPos SPIRIT_START = new BlockPos(4, 1, 4);
    private static final int PILLAR_HEIGHT = 6;
    private static final int SETTLE_TICKS = 1;
    private static final int PILLAR_SETTLE_TICKS = 10;
    private static final int ENDLESS_PICKUP_DELAY = 32767;
    private static final int DIAMOND_HEAPS = 20;
    private static final int DIAMONDS_PER_HEAP = 64;

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
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "drops_filter", timeoutTicks = 400)
    public static void collectDrops_LogFilter_PicksLogsAndLeavesCobblestone(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        GameTestFixtures.dropAt(helper, Items.OAK_LOG, 4, 2.5, 1, 2.5);
        GameTestFixtures.dropAt(helper, Items.BIRCH_LOG, 2, 6.5, 1, 2.5);
        // Булыжник в 6 блоках от старта и от пути к брёвнам — дальше радиуса магнита (5), но внутри radius 6:
        // фильтр collect_drops решает сам, магнит его не подбирает.
        ItemEntity cobblestone = GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 3, 4.5, 1, 10.5);
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
            GameTestFixtures.finish(helper);
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
        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("collect_drops", Map.of("radius", 6, "overflow", "fail")), SETTLE_TICKS);

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
            GameTestFixtures.finish(helper);
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
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "drops_pit", timeoutTicks = 400)
    public static void collectDrops_ItemAtBottomOfFourDeepPit_PulledByMagnetWithoutDescending(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        for (int y = 0; y >= -3; y--) {
            helper.setBlock(4, y, 2, Blocks.AIR);
        }
        helper.setBlock(4, -4, 2, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        ItemEntity drop = GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 1, 4.5, -3, 2.5);
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 6)),
                SETTLE_TICKS);
        double startY = spirit.getY();

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "предмет на дне ямы в радиусе магнита (5) притягивается без спуска (упрощение 44.1): collect_drops "
                            + "обязан завершиться успехом, статус: " + spirit.taskStatus().map(RunStatus::name)
                            .orElse("нет задачи") + ", причина " + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(Math.abs(spirit.getY() - startY) < 0.1,
                    "дух не должен спускаться в яму за предметом: y был " + startY + ", стал " + spirit.getY());
            helper.assertTrue(drop.isRemoved()
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 1,
                    "предмет со дна ямы обязан оказаться в инвентаре, а не на дне");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "drops_corner", timeoutTicks = 400)
    public static void collectDrops_PreyInWebSealedNearestStand_ReachesItFromAnotherStand(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(5, 1, 5, Blocks.COBWEB);
        helper.setBlock(2, 1, 5, Blocks.STONE);
        helper.setBlock(2, 2, 5, Blocks.STONE);
        helper.setBlock(3, 1, 4, Blocks.STONE);
        helper.setBlock(3, 2, 4, Blocks.STONE);
        helper.setBlock(3, 1, 6, Blocks.STONE);
        helper.setBlock(3, 2, 6, Blocks.STONE);
        helper.setBlock(4, 1, 5, Blocks.STONE);
        helper.setBlock(4, 2, 5, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 5));
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 1, 5.5, 1.5, 5.5);
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 6)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "предмет в углу за запечатанной стоянкой обязан подобраться с другой стоянки, статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 1,
                    "булыжник из паутины обязан попасть в инвентарь, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "drops_endless_delay", timeoutTicks = 400)
    public static void collectDrops_HeapWithEndlessPickupDelay_IsSkippedAfterBoundedWait(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        ItemEntity display = GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 1, 3.5, 1, 4.5);
        display.setPickUpDelay(ENDLESS_PICKUP_DELAY);
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 2, 7.5, 1, 4.5);
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 6)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "кучка с бесконечной задержкой обязана провалить итог collect_drops, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            String reason = spirit.taskFailureReason().orElse("");
            helper.assertTrue(reason.contains("не дотянулся до 1"),
                    "причина обязана называть одну недоступную кучку, фактически: " + reason);
            helper.assertTrue(run.spiritTicks() <= 120,
                    "ожидание задержки ограничено 40 тиками: итог за 120 тиков духа, а заняло " + run.spiritTicks());
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 2,
                    "обычная кучка обязана быть подобрана, пока дух не ждёт вечную: в инвентаре "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            helper.assertTrue(!display.isRemoved() && display.getItem().getCount() == 1,
                    "кучка с бесконечной задержкой не должна быть тронута");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "drops_foreign_target", timeoutTicks = 400)
    public static void collectDrops_HeapAddressedToAnotherPlayer_IsLeftUntouched(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        ItemEntity foreign = GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 4, 3.5, 1, 4.5);
        foreign.setTarget(UUID.randomUUID());
        GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 2, 7.5, 1, 4.5);
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 6)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "чужая кучка не входит в подбор: collect_drops обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 2,
                    "в инвентаре обязана быть только обычная кучка (2): вещь, адресованную игроку, дух не забирает");
            helper.assertTrue(!foreign.isRemoved() && foreign.getItem().getCount() == 4,
                    "кучка, адресованная другому игроку, обязана остаться нетронутой");
            GameTestFixtures.finish(helper);
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
            GameTestFixtures.finish(helper);
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
            GameTestFixtures.finish(helper);
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
                new BlockCoord(start.getX(), start.getY(), start.getZ()), 6, PickupTuning.DEFAULT,
                OverflowMode.FAIL);
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
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "drops_overflow_take", timeoutTicks = 400)
    public static void collectDrops_FullOfCobblestoneByValue_SwapsInDiamond(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        GameTestFixtures.dropAt(helper, Items.DIAMOND, 1, 2.5, 1, 2.5);
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 6)),
                SETTLE_TICKS);
        AtomicInteger successAt = new AtomicInteger(-1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            if (spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED && successAt.get() < 0) {
                successAt.set(run.spiritTicks());
            }
            helper.assertTrue(successAt.get() >= 0,
                    "обмен дешёвого на ценное обязан завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(run.spiritTicks() - successAt.get() >= 100,
                    "после успеха обязано пройти 100 тиков: выброшенный булыжник нельзя подбирать обратно, "
                            + "прошло " + (run.spiritTicks() - successAt.get()));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.DIAMOND) == 1,
                    "алмаз обязан оказаться в инвентаре: он ценнее выброшенного булыжника");
            helper.assertTrue(spirit.getInventory().getItem(35).is(Items.DIAMOND),
                    "новая кучка обязана лечь в освободившийся слот 35: иначе слот остался бы пустым, "
                            + "а алмаз размазался бы по инвентарю");
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE)
                    == 35 * 64,
                    "в инвентаре обязано остаться 35 стопок булыжника: выброшена ровно одна");
            List<ItemEntity> heaps = GameTestFixtures.dropsInArea(helper, Items.COBBLESTONE);
            helper.assertTrue(heaps.size() == 1 && heaps.get(0).getItem().getCount() == 64,
                    "выброшенная стопка обязана лежать рядом целой и не подбираться обратно: иначе сборщик "
                            + "крутил бы цикл выбросил-подобрал");
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.DIAMOND)
                    + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE)
                    + GameTestFixtures.countInWorld(helper, Items.DIAMOND)
                    + GameTestFixtures.countInWorld(helper, Items.COBBLESTONE) == 35 * 64 + 64 + 1,
                    "суммарное число предметов обязано сохраниться: было 36 стопок булыжника и алмаз");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "drops_overflow_cheap", timeoutTicks = 400)
    public static void collectDrops_FullOfIronByValue_RefusesCobblestone(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.IRON_INGOT, 64));
        }
        ItemEntity heap = GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 5, 2.5, 1, 2.5);
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 6)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "дешёвая кучка при полном инвентаре обязана провалить подбор, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(spirit.taskFailureReason().orElse("").contains("инвентарь полон"),
                    "причина обязана говорить, что инвентарь полон, фактически: "
                            + spirit.taskFailureReason().orElse("нет причины"));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_INGOT)
                    == 36 * 64,
                    "железо обязано остаться нетронутым: ничего не выброшено");
            helper.assertTrue(!heap.isRemoved() && heap.getItem().getCount() == 5,
                    "дешёвая кучка обязана остаться в мире целой");
            helper.assertTrue(GameTestFixtures.dropsInArea(helper, Items.IRON_INGOT).isEmpty(),
                    "выброшенных стопок железа быть не должно: новое дешевле лежащего");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "drops_overflow_kit", timeoutTicks = 400)
    public static void collectDrops_InventoryOfKitAndShulkersByValue_RefusesDiamonds(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        spirit.getInventory().setItem(1, new ItemStack(Items.BOW));
        spirit.getInventory().setItem(2, new ItemStack(Items.ARROW, 16));
        spirit.getInventory().setItem(3, new ItemStack(Items.BREAD, 16));
        spirit.getInventory().setItem(4, new ItemStack(Items.TORCH, 24));
        for (int slot = 5; slot < 36; slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.SHULKER_BOX));
        }
        ItemEntity heap = GameTestFixtures.dropAt(helper, Items.DIAMOND, 2, 2.5, 1, 2.5);
        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 6)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "при защищённом инвентаре подбор обязан провалиться, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(spirit.taskFailureReason().orElse("").contains("инвентарь полон"),
                    "причина обязана говорить, что инвентарь полон, фактически: "
                            + spirit.taskFailureReason().orElse("нет причины"));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.SHULKER_BOX) == 31,
                    "все 31 шалкер обязаны остаться при духе: never_give_away защищает их даже от двух алмазов "
                            + "(1000 против 500)");
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_SWORD) == 1
                    && GameTestFixtures.countInInventory(spirit.getInventory(), Items.BOW) == 1
                    && GameTestFixtures.countInInventory(spirit.getInventory(), Items.ARROW) == 16
                    && GameTestFixtures.countInInventory(spirit.getInventory(), Items.BREAD) == 16
                    && GameTestFixtures.countInInventory(spirit.getInventory(), Items.TORCH) == 24,
                    "квоты постоянной части обязаны остаться целыми");
            helper.assertTrue(!heap.isRemoved() && heap.getItem().getCount() == 2,
                    "алмазы обязаны остаться в мире нетронутыми");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "drops_overflow_plan", timeoutTicks = 400)
    public static void collectDrops_PlanNeedsPlanksByValue_PlanMaterialKept(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        for (int slot = 0; slot < 35; slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.IRON_INGOT, 64));
        }
        spirit.getInventory().setItem(35, new ItemStack(Items.OAK_PLANKS, 64));
        ItemEntity heap = GameTestFixtures.dropAt(helper, Items.EMERALD, 1, 2.5, 1, 2.5);
        AtomicBoolean launched = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (!launched.get() && spirit.tickCount >= SETTLE_TICKS) {
                launched.set(true);
                spirit.startTask(new SequenceNode(List.of(
                        new ScriptCall("collect_drops", Map.of("radius", 6)),
                        new ScriptCall("craft", Map.of("item", "minecraft:stick", "count", 1)))));
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(spirit.taskStatus().isPresent(),
                    "дух обязан взять план в работу: без плана нечего защищать");
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "изумруд дешевле железа, а доски нужны плану: подбор обязан провалиться, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(spirit.taskFailureReason().orElse("").contains("инвентарь полон"),
                    "провалиться обязан именно collect_drops с переполнением, а не следующий шаг плана: "
                            + "иначе защита планом не доказана. Причина: "
                            + spirit.taskFailureReason().orElse("нет причины"));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_PLANKS) == 64,
                    "доски обязаны остаться все 64: план sequence[collect_drops, craft stick] защищает их "
                            + "через ингредиенты, хотя они дешевле изумруда");
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_INGOT)
                    == 35 * 64,
                    "железо обязано остаться нетронутым: ничего не выброшено");
            helper.assertTrue(!heap.isRemoved() && heap.getItem().getCount() == 1,
                    "изумруд обязан остаться в мире нетронутым");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "drops_overflow_cache", timeoutTicks = 400)
    public static void collectDrops_FullOfCobblestoneByValue_CachesOverflowDecision(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        dropDiamondRing(helper);
        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("collect_drops", Map.of("radius", 6, "overflow", "by_value")), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "обмен полного булыжника на алмазы обязан завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.DIAMOND)
                            == DIAMOND_HEAPS * DIAMONDS_PER_HEAP,
                    "все 20 кучек алмазов (по 64) обязаны оказаться в инвентаре: обмен ценного на дешёвое, "
                            + "а не отказ, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.DIAMOND));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 16 * 64,
                    "в инвентаре обязано остаться 16 стопок булыжника (36 слотов − 20 алмазов): каждый алмаз "
                            + "вытеснил ровно одну стопку, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }

    private static void dropDiamondRing(GameTestHelper helper) {
        int[][] ring = {{2, 2}, {2, 1}, {2, 0}, {2, -1}, {2, -2}, {1, -2}, {0, -2}, {-1, -2}, {-2, -2}, {-2, -1},
                {-2, 0}, {-2, 1}, {-2, 2}, {-1, 2}, {0, 2}, {1, 2}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] cell : ring) {
            GameTestFixtures.dropAt(helper, Items.DIAMOND, DIAMONDS_PER_HEAP,
                    SPIRIT_START.getX() + cell[0] + 0.5, 1, SPIRIT_START.getZ() + cell[1] + 0.5);
        }
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
