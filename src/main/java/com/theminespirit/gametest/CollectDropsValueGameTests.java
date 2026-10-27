package com.theminespirit.gametest;

import static com.theminespirit.gametest.CollectDropsSupport.DIAMONDS_PER_HEAP;
import static com.theminespirit.gametest.CollectDropsSupport.DIAMOND_HEAPS;
import static com.theminespirit.gametest.CollectDropsSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.CollectDropsSupport.SPIRIT_START;
import static com.theminespirit.gametest.CollectDropsSupport.dropDiamondRing;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class CollectDropsValueGameTests {
    private CollectDropsValueGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void collectDrops_FullOfCobblestoneByValue_SwapsInDiamond(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        GameTestFixtures.dropAt(helper, Items.DIAMOND, 1, 2.5, 1, 2.5);
        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 6)), SETTLE_TICKS);
        AtomicInteger successAt = new AtomicInteger(-1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            if (spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED && successAt.get() < 0) {
                successAt.set(run.spiritTicks());
            }
            helper.assertTrue(
                    successAt.get() >= 0,
                    "обмен дешёвого на ценное обязан завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    run.spiritTicks() - successAt.get() >= 100,
                    "после успеха обязано пройти 100 тиков: выброшенный булыжник нельзя подбирать обратно, " + "прошло "
                            + (run.spiritTicks() - successAt.get()));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.DIAMOND) == 1,
                    "алмаз обязан оказаться в инвентаре: он ценнее выброшенного булыжника");
            helper.assertTrue(
                    spirit.getInventory().getItem(35).is(Items.DIAMOND),
                    "новая кучка обязана лечь в освободившийся слот 35: иначе слот остался бы пустым, "
                            + "а алмаз размазался бы по инвентарю");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 35 * 64,
                    "в инвентаре обязано остаться 35 стопок булыжника: выброшена ровно одна");
            List<ItemEntity> heaps = GameTestFixtures.dropsInArea(helper, Items.COBBLESTONE);
            helper.assertTrue(
                    heaps.size() == 1 && heaps.get(0).getItem().getCount() == 64,
                    "выброшенная стопка обязана лежать рядом целой и не подбираться обратно: иначе сборщик "
                            + "крутил бы цикл выбросил-подобрал");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.DIAMOND)
                                    + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE)
                                    + GameTestFixtures.countInWorld(helper, Items.DIAMOND)
                                    + GameTestFixtures.countInWorld(helper, Items.COBBLESTONE)
                            == 35 * 64 + 64 + 1,
                    "суммарное число предметов обязано сохраниться: было 36 стопок булыжника и алмаз");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void collectDrops_FullOfIronByValue_RefusesCobblestone(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.IRON_INGOT, 64));
        }
        ItemEntity heap = GameTestFixtures.dropAt(helper, Items.COBBLESTONE, 5, 2.5, 1, 2.5);
        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 6)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "дешёвая кучка при полном инвентаре обязана провалить подбор, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(
                    spirit.taskFailureReason().orElse("").contains("инвентарь полон"),
                    "причина обязана говорить, что инвентарь полон, фактически: "
                            + spirit.taskFailureReason().orElse("нет причины"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_INGOT) == 36 * 64,
                    "железо обязано остаться нетронутым: ничего не выброшено");
            helper.assertTrue(
                    !heap.isRemoved() && heap.getItem().getCount() == 5, "дешёвая кучка обязана остаться в мире целой");
            helper.assertTrue(
                    GameTestFixtures.dropsInArea(helper, Items.IRON_INGOT).isEmpty(),
                    "выброшенных стопок железа быть не должно: новое дешевле лежащего");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
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
        SpiritRun run =
                SpiritRun.launch(helper, spirit, new ScriptCall("collect_drops", Map.of("radius", 6)), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "при защищённом инвентаре подбор обязан провалиться, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(
                    spirit.taskFailureReason().orElse("").contains("инвентарь полон"),
                    "причина обязана говорить, что инвентарь полон, фактически: "
                            + spirit.taskFailureReason().orElse("нет причины"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.SHULKER_BOX) == 31,
                    "все 31 шалкер обязаны остаться при духе: never_give_away защищает их даже от двух алмазов "
                            + "(1000 против 500)");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_SWORD) == 1
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.BOW) == 1
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.ARROW) == 16
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.BREAD) == 16
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.TORCH) == 24,
                    "квоты постоянной части обязаны остаться целыми");
            helper.assertTrue(
                    !heap.isRemoved() && heap.getItem().getCount() == 2, "алмазы обязаны остаться в мире нетронутыми");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
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
            helper.assertTrue(
                    spirit.taskStatus().isPresent(), "дух обязан взять план в работу: без плана нечего защищать");
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "изумруд дешевле железа, а доски нужны плану: подбор обязан провалиться, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(
                    spirit.taskFailureReason().orElse("").contains("инвентарь полон"),
                    "провалиться обязан именно collect_drops с переполнением, а не следующий шаг плана: "
                            + "иначе защита планом не доказана. Причина: "
                            + spirit.taskFailureReason().orElse("нет причины"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_PLANKS) == 64,
                    "доски обязаны остаться все 64: план sequence[collect_drops, craft stick] защищает их "
                            + "через ингредиенты, хотя они дешевле изумруда");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_INGOT) == 35 * 64,
                    "железо обязано остаться нетронутым: ничего не выброшено");
            helper.assertTrue(
                    !heap.isRemoved() && heap.getItem().getCount() == 1, "изумруд обязан остаться в мире нетронутым");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void collectDrops_FullOfCobblestoneByValue_CachesOverflowDecision(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        dropDiamondRing(helper);
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("collect_drops", Map.of("radius", 6, "overflow", "by_value")),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "обмен полного булыжника на алмазы обязан завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.DIAMOND)
                            == DIAMOND_HEAPS * DIAMONDS_PER_HEAP,
                    "все 20 кучек алмазов (по 64) обязаны оказаться в инвентаре: обмен ценного на дешёвое, "
                            + "а не отказ, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.DIAMOND));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 16 * 64,
                    "в инвентаре обязано остаться 16 стопок булыжника (36 слотов − 20 алмазов): каждый алмаз "
                            + "вытеснил ровно одну стопку, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }
}
