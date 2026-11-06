package com.theminespirit.gametest;

import static com.theminespirit.gametest.MineSceneSupport.SETTLE_TICKS;
import static com.theminespirit.gametest.MineSceneSupport.SPIRIT_START;
import static com.theminespirit.gametest.MineSceneSupport.gatherCall;
import static com.theminespirit.gametest.MineSceneSupport.mineCall;
import static com.theminespirit.gametest.MineSceneSupport.spawnWith;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.neoforge.SpiritEntity;
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
final class MineVeinDurabilityGameTests {
    private MineVeinDurabilityGameTests() {}

    // [флак] полный инвентарь, путь до сундука и добыча руды: под нагрузкой машины запас двойной.
    @SceneStep(template = "arena", timeoutTicks = 600)
    public static void mineVein_FullInventoryNearChest_DepositsAndContinues(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = spawnWith(helper, SPIRIT_START, Items.IRON_PICKAXE);
        for (int slot = 1; slot < spirit.getInventory().getContainerSize(); slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        helper.setBlock(new BlockPos(7, 1, 7), Blocks.CHEST);
        helper.setBlock(new BlockPos(4, 2, 4), Blocks.IRON_ORE);
        SpiritRun run = SpiritRun.launch(helper, spirit, mineCall("iron", 1), SETTLE_TICKS);

        SpiritEntity spare = spawnWith(helper, new BlockPos(6, 1, 6), Items.IRON_PICKAXE);
        ItemStack wornPick = spare.getInventory().getItem(0);
        wornPick.setDamageValue(wornPick.getMaxDamage() - 1);
        spare.getInventory().setItem(1, new ItemStack(Items.STONE_PICKAXE));
        helper.setBlock(new BlockPos(7, 2, 6), Blocks.IRON_ORE);
        SpiritRun spareRun = SpiritRun.launch(helper, spare, mineCall("iron", 1), SETTLE_TICKS);

        helper.setBlock(new BlockPos(2, 1, 7), Blocks.DIRT);
        helper.setBlock(new BlockPos(3, 1, 7), Blocks.DIRT);
        helper.setBlock(new BlockPos(2, 1, 8), Blocks.DIRT);
        SpiritEntity digger = spawnWith(helper, new BlockPos(3, 1, 6), Items.WOODEN_SHOVEL);
        ItemStack wornShovel = digger.getInventory().getItem(0);
        wornShovel.setDamageValue(wornShovel.getMaxDamage() - 1);
        SpiritRun digRun = SpiritRun.launch(helper, digger, gatherCall("dirt", 2), SETTLE_TICKS);

        SpiritEntity refiller = spawnWith(helper, new BlockPos(2, 1, 2), Items.WOODEN_PICKAXE);
        ItemStack refillWornPick = refiller.getInventory().getItem(0);
        refillWornPick.setDamageValue(refillWornPick.getMaxDamage() - 1);
        SpiritRun refillRun = startRefillScene(helper, refiller);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "при полном инвентаре дух обязан сдать лишнее на склад и продолжить добычу, статус "
                            + spirit.taskStatus().orElse(null) + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON) == 1,
                    "после сдачи руда обязана оказаться в инвентаре: добыча продолжилась, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.RAW_IRON));
            var chest = helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(7, 1, 7)));
            helper.assertTrue(
                    chest instanceof Container container
                            && GameTestFixtures.countInInventory(container, Items.DIRT) > 0,
                    "лишняя земля обязана уехать в сундук: иначе дух не освободил место и бросил задачу");
            spareRun.assertStarted();
            helper.assertTrue(
                    spare.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "кирка сломалась посреди жилы — запасная кирка дорубает работу, статус "
                            + spare.taskStatus().orElse(null) + ", причина: "
                            + spare.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spare.getInventory(), Items.RAW_IRON) == 1,
                    "работа не брошена: руда добыта запасной киркой, фактически "
                            + GameTestFixtures.countInInventory(spare.getInventory(), Items.RAW_IRON));
            digRun.assertStarted();
            helper.assertTrue(
                    digger.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "лопата сломалась — землю продолжают руками, статус "
                            + digger.taskStatus().orElse(null) + ", причина: "
                            + digger.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(digger.getInventory(), Items.DIRT) >= 2,
                    "работа не брошена: земля добыта руками, фактически "
                            + GameTestFixtures.countInInventory(digger.getInventory(), Items.DIRT));
            refillRun.assertStarted();
            helper.assertTrue(
                    refiller.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "кирка сломалась, запасной в инвентаре нет — дух обязан взять кирку из сундука и "
                            + "продолжить добычу, статус "
                            + refiller.taskStatus().orElse(null) + ", причина: "
                            + refiller.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(refiller.getInventory(), Items.COBBLESTONE) >= 2,
                    "работа не брошена: камень добыт после замены кирки, фактически "
                            + GameTestFixtures.countInInventory(refiller.getInventory(), Items.COBBLESTONE));
            GameTestFixtures.finish(helper);
        });
    }

    private static SpiritRun startRefillScene(GameTestHelper helper, SpiritEntity refiller) {
        helper.setBlock(new BlockPos(8, 1, 2), Blocks.CHEST);
        var pickChest = helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(8, 1, 2)));
        helper.assertTrue(
                pickChest instanceof Container,
                "сундук с запасной киркой обязан появиться на сцене: без него проверка замены бессмысленна");
        if (pickChest instanceof Container container) {
            container.setItem(0, new ItemStack(Items.STONE_PICKAXE));
        }
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
        return SpiritRun.launch(helper, refiller, gatherCall("stone", 2), SETTLE_TICKS);
    }
}
