package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArmorEquipGameTests {
    private ArmorEquipGameTests() {
    }

    @GameTest(template = "arena", batch = "armor_auto_chest", timeoutTicks = 200)
    public static void armor_LeatherAndIronChest_EquipsIron(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.LEATHER_CHESTPLATE));
        spirit.getInventory().setItem(1, new ItemStack(Items.IRON_CHESTPLATE));
        PolicyScene scene = PolicyScene.launch(helper, spirit, 2, () -> {
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(spirit.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),
                    "Дух с кожаной и железной кирасой в инвентаре обязан сам надеть железную (6 очков против 3), "
                            + "в слоте груди " + spirit.getItemBySlot(EquipmentSlot.CHEST));
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "armor_auto_helm", timeoutTicks = 200)
    public static void armor_LeatherWornDiamondPickedUp_EquipsDiamondAndKeepsLeather(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        spirit.getInventory().setItem(0, new ItemStack(Items.DIAMOND_HELMET));
        PolicyScene scene = PolicyScene.launch(helper, spirit, 2, () -> {
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            helper.assertTrue(spirit.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET),
                    "Надета кожаная шапка, в инвентаре алмазный шлем: дух обязан надеть алмазный (3 очка и "
                            + "крепость 2 против 1 очка кожаного), в слоте головы "
                            + spirit.getItemBySlot(EquipmentSlot.HEAD));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.LEATHER_HELMET) == 1,
                    "Снятая кожаная шапка обязана остаться в инвентаре, а не исчезнуть: иначе дух теряет вещь");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "armor_auto_broken", timeoutTicks = 200)
    public static void armor_NearlyBrokenWorn_ReplacedByWholeInInventory(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        ItemStack worn = new ItemStack(Items.IRON_CHESTPLATE);
        worn.setDamageValue(worn.getMaxDamage() - 10);
        spirit.setItemSlot(EquipmentSlot.CHEST, worn);
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_CHESTPLATE));
        PolicyScene scene = PolicyScene.launch(helper, spirit, 2, () -> {
        });

        helper.succeedWhen(() -> {
            scene.assertStarted();
            ItemStack chest = spirit.getItemBySlot(EquipmentSlot.CHEST);
            helper.assertTrue(chest.is(Items.IRON_CHESTPLATE) && chest.getDamageValue() == 0,
                    "Почти сломанная кираса (доля прочности ниже порога) обязана уступить целой такой же "
                            + "из инвентаря: в слоте груди " + chest + " с уроном " + chest.getDamageValue());
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_CHESTPLATE) == 1,
                    "Снятая почти сломанная кираса обязана остаться в инвентаре");
            GameTestFixtures.finish(helper);
        });
    }
}