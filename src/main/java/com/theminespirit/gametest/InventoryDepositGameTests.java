package com.theminespirit.gametest;

import static com.theminespirit.gametest.InventorySceneSupport.accessChest;
import static com.theminespirit.gametest.InventorySceneSupport.chestCount;
import static com.theminespirit.gametest.InventorySceneSupport.itemStacks;
import static com.theminespirit.gametest.InventorySceneSupport.pickaxeStacks;
import static com.theminespirit.gametest.InventorySceneSupport.placeDoubleChest;
import static com.theminespirit.gametest.InventorySceneSupport.snapshotChest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class InventoryDepositGameTests {
    private InventoryDepositGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void deposit_TenCobblestone_MovesAllAndSorts(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "deposit",
                        Map.of("container", "nearest", "item", "minecraft:cobblestone", "count", 10, "radius", 8)),
                1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "deposit 10 булыжника обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    run.spiritTicks() <= 120,
                    "сценарий обязан укладываться в 120 тиков духа, заняло " + run.spiritTicks());
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 0,
                    "у духа не должно остаться булыжника: все 10 уехали в сундук");
            helper.assertTrue(
                    chestCount(helper, Items.COBBLESTONE) == 10,
                    "в сундуке обязано оказаться 10 булыжника, фактически " + chestCount(helper, Items.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void deposit_MessyChest_MergesPartialStacks(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        Container chest = accessChest(helper);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        chest.setItem(7, new ItemStack(Items.COBBLESTONE, 7));
        chest.setItem(20, new ItemStack(Items.COBBLESTONE, 3));
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "deposit",
                        Map.of("container", "nearest", "item", "minecraft:cobblestone", "count", 10, "radius", 8)),
                1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "deposit в сундук с разбросанными стопками обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    chestCount(helper, Items.COBBLESTONE) == 25,
                    "сумма булыжника обязана сохраниться: было 5+7+3, дух положил 10, стало "
                            + chestCount(helper, Items.COBBLESTONE) + " вместо 25");
            helper.assertTrue(
                    itemStacks(helper, Items.COBBLESTONE) == 1,
                    "разрозненные неполные стопки обязаны слиться в одну: стопок булыжника "
                            + itemStacks(helper, Items.COBBLESTONE) + " вместо 1");
            helper.assertTrue(
                    chest.getItem(0).is(Items.COBBLESTONE) && chest.getItem(0).getCount() == 25,
                    "собранная стопка обязана лежать первой и содержать все 25, слот 0 — " + chest.getItem(0));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void deposit_DoubleChest_Uses54Slots(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        placeDoubleChest(helper, 5, 6, 1, 4);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(3, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        spirit.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 36));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "deposit",
                        Map.of("container", "nearest", "item", "minecraft:cobblestone", "count", 100, "radius", 8)),
                1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "deposit в двойной сундук обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    chestCount(helper, Items.COBBLESTONE) == 100,
                    "в двойном сундуке обязано оказаться 100 булыжника, фактически "
                            + chestCount(helper, Items.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void sortChest_MessyChest_MergesAndKeepsVariants(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        Container chest = accessChest(helper);
        for (int slot = 0; slot < 20; slot++) {
            chest.setItem(slot, new ItemStack(Items.COBBLESTONE, slot + 1));
        }
        chest.setItem(20, new ItemStack(Items.IRON_PICKAXE));
        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        worn.setDamageValue(5);
        chest.setItem(21, worn);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        Map<String, Object> params = Map.of("container", "nearest", "radius", 8);
        SpiritRun first = SpiritRun.launch(helper, spirit, new ScriptCall("sort_chest", params), 1);
        AtomicBoolean relaunched = new AtomicBoolean();
        AtomicReference<SpiritRun> second = new AtomicReference<>();
        AtomicReference<List<String>> afterFirst = new AtomicReference<>();

        helper.succeedWhen(() -> {
            first.assertStarted();
            if (!relaunched.get()) {
                helper.assertTrue(
                        spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                        "первая sort_chest обязана завершиться успехом, причина: "
                                + spirit.taskFailureReason().orElse("нет"));
                afterFirst.set(snapshotChest(helper));
                second.set(SpiritRun.launch(helper, spirit, new ScriptCall("sort_chest", params), spirit.tickCount));
                relaunched.set(true);
            }
            helper.assertTrue(
                    second.get() != null,
                    "повторная сортировка обязана быть запущена после первой: иначе идемпотентность не проверена");
            second.get().assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "повторный sort_chest обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    snapshotChest(helper).equals(afterFirst.get()),
                    "повторная сортировка обязана ничего не менять (идемпотентность): иначе каждый вызов "
                            + "тасовал бы сундук заново");
            helper.assertTrue(
                    chestCount(helper, Items.COBBLESTONE) == 210,
                    "сумма булыжника обязана сохраниться (1+…+20 = 210), фактически "
                            + chestCount(helper, Items.COBBLESTONE));
            helper.assertTrue(
                    pickaxeStacks(helper) == 2,
                    "целая и повреждённая кирки обязаны остаться разными стопками: слияние разных вариантов "
                            + "потеряло бы вещь игрока");
            GameTestFixtures.finish(helper);
        });
    }
}
