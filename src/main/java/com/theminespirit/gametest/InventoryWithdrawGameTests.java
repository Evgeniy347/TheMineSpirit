package com.theminespirit.gametest;

import static com.theminespirit.gametest.InventorySceneSupport.accessChest;
import static com.theminespirit.gametest.InventorySceneSupport.chestCount;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
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
final class InventoryWithdrawGameTests {
    private InventoryWithdrawGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void withdraw_FiveIngotsFromMessyChest_TakesAndSorts(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        Container chest = accessChest(helper);
        chest.setItem(0, new ItemStack(Items.DIRT, 64));
        chest.setItem(2, new ItemStack(Items.IRON_INGOT, 4));
        chest.setItem(5, new ItemStack(Items.IRON_INGOT, 3));
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "withdraw",
                        Map.of("container", "nearest", "item", "minecraft:iron_ingot", "count", 5, "radius", 8)),
                1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "withdraw 5 слитков обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_INGOT) == 5,
                    "у духа обязано оказаться 5 слитков железа");
            helper.assertTrue(
                    chestCount(helper, Items.IRON_INGOT) == 2,
                    "в сундуке обязаны остаться 2 слитка (было 7, забрали 5), фактически "
                            + chestCount(helper, Items.IRON_INGOT));
            helper.assertTrue(
                    chest.getItem(0).is(Items.IRON_INGOT) && chest.getItem(0).getCount() == 2,
                    "остаток слитков обязан быть слит в одну стопку и стоять первым (материалы раньше блоков), "
                            + "иначе сундук не отсортирован: слот 0 — " + chest.getItem(0));
            helper.assertTrue(
                    chest.getItem(1).is(Items.DIRT) && chest.getItem(2).isEmpty(),
                    "земля обязана стоять второй без дырок между стопками: слот 1 — " + chest.getItem(1) + ", слот 2 — "
                            + chest.getItem(2));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void unload_FullInventory_MovesOnlySurplus(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        spirit.getInventory().setItem(1, new ItemStack(Items.BOW));
        spirit.getInventory().setItem(2, new ItemStack(Items.ARROW, 16));
        for (int slot = 0; slot < 5; slot++) {
            spirit.getInventory().setItem(3 + slot, new ItemStack(Items.BREAD, 4));
        }
        for (int slot = 0; slot < 16; slot++) {
            spirit.getInventory().setItem(8 + slot, new ItemStack(Items.TORCH, 4));
        }
        for (int slot = 0; slot < 3; slot++) {
            spirit.getInventory().setItem(24 + slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        spirit.getInventory().setItem(27, new ItemStack(Items.DIAMOND, 5));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("unload", Map.of("container", "nearest", "radius", 8, "force", false)),
                1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "unload обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    run.spiritTicks() <= 120,
                    "сценарий обязан укладываться в 120 тиков духа, заняло " + run.spiritTicks());
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_SWORD) == 1
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.BOW) == 1
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.ARROW) == 16,
                    "постоянная часть остаётся целиком: меч, лук и 16 стрел");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.BREAD) == 16
                            && chestCount(helper, Items.BREAD) == 4,
                    "из 20 хлеба 16 остаются, 4 лишних уходят: у духа "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.BREAD)
                            + ", в сундуке " + chestCount(helper, Items.BREAD));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.TORCH) == 24
                            && chestCount(helper, Items.TORCH) == 40,
                    "из 64 факелов 24 остаются, 40 уходят: у духа "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.TORCH)
                            + ", в сундуке " + chestCount(helper, Items.TORCH));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 0
                            && chestCount(helper, Items.COBBLESTONE) == 192,
                    "булыжник вне постоянной части уходит целиком: у духа "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE)
                            + ", в сундуке " + chestCount(helper, Items.COBBLESTONE));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.DIAMOND) == 0
                            && chestCount(helper, Items.DIAMOND) == 5,
                    "алмазы не входят в постоянную часть и уходят целиком");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void unload_BelowTrigger_SucceedsWithoutTouching(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        spirit.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 5));
        spirit.getInventory().setItem(2, new ItemStack(Items.BREAD, 20));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("unload", Map.of("container", "nearest", "radius", 8, "force", false)),
                1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "при занятых слотах меньше 24 разгрузка не нужна и завершается успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(
                    chestCount(helper, Items.COBBLESTONE) == 0
                            && chestCount(helper, Items.DIAMOND) == 0
                            && chestCount(helper, Items.BREAD) == 0,
                    "без триггера сундук обязан остаться пустым: разгрузка ничего не делает");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.BREAD) == 20,
                    "инвентарь духа обязан остаться нетронутым");
            GameTestFixtures.finish(helper);
        });
    }
}
