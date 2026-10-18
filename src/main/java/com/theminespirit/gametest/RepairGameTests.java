package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.repair.RepairCalculator;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RepairGameTests {
    private static final int IRON_PICKAXE_MAX = 250;
    private static final int WOODEN_PICKAXE_MAX = 59;
    private static final int SETTLE_TICKS = 1;

    private RepairGameTests() {
    }

    @GameTest(template = "arena", batch = "repair_anvil", timeoutTicks = 200)
    public static void repair_AnvilWithIngot_RaisesDurability(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.ANVIL);
        spirit.getInventory().addItem(damaged(Items.IRON_PICKAXE, 100));
        spirit.getInventory().addItem(new ItemStack(Items.IRON_INGOT, 1));

        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("repair_tool", Map.of("item", "minecraft:iron_pickaxe")), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "ремонт наковальней обязан завершиться успехом, статус: " + spirit.taskStatus().orElse(null)
                            + ", причина: " + spirit.taskFailureReason().orElse("нет"));
            int durability = bestDurability(spirit, Items.IRON_PICKAXE);
            int expected = RepairCalculator.anvilResult(150, IRON_PICKAXE_MAX, RepairCalculator.ANVIL_REPAIR_PERCENT);
            helper.assertTrue(durability == expected,
                    "прочность кирки после ремонта наковальней обязана быть " + expected + ", фактически "
                            + durability);
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_INGOT) == 0,
                    "слиток обязан быть израсходован на ремонт, осталось "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_INGOT));
        });
    }

    @GameTest(template = "arena", batch = "repair_wooden", timeoutTicks = 200)
    public static void repair_TwoDamagedWoodenPickaxes_Combines(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.getInventory().addItem(damaged(Items.WOODEN_PICKAXE, 30));
        spirit.getInventory().addItem(damaged(Items.WOODEN_PICKAXE, 40));

        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("repair_tool", Map.of("item", "minecraft:wooden_pickaxe")), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "комбинирование двух деревянных кирок обязано завершиться успехом, статус: "
                            + spirit.taskStatus().orElse(null) + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.WOODEN_PICKAXE) == 1,
                    "после комбинирования обязана остаться одна кирка, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.WOODEN_PICKAXE));
            int durability = bestDurability(spirit, Items.WOODEN_PICKAXE);
            int expected = RepairCalculator.grindstoneResult(19, 29, WOODEN_PICKAXE_MAX);
            helper.assertTrue(durability == expected,
                    "прочность объединённой кирки обязана быть " + expected + ", фактически " + durability);
        });
    }

    @GameTest(template = "arena", batch = "repair_two_damaged", timeoutTicks = 200)
    public static void repair_TwoDamagedIronPickaxes_LeavesOneRepaired(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.getInventory().addItem(damaged(Items.IRON_PICKAXE, 100));
        spirit.getInventory().addItem(damaged(Items.IRON_PICKAXE, 150));

        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("repair_tool", Map.of("item", "minecraft:iron_pickaxe")), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "ремонт обязан завершиться успехом, статус: " + spirit.taskStatus().orElse(null)
                            + ", причина: " + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_PICKAXE) == 1,
                    "после ремонта обязана остаться одна кирка, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_PICKAXE));
            int durability = bestDurability(spirit, Items.IRON_PICKAXE);
            int expected = RepairCalculator.grindstoneResult(150, 100, IRON_PICKAXE_MAX);
            helper.assertTrue(durability == expected,
                    "прочность починенной кирки обязана быть " + expected + ", фактически " + durability);
        });
    }

    @GameTest(template = "arena", batch = "repair_single_damaged", timeoutTicks = 200)
    public static void repair_SingleDamagedIronPickaxe_Fails(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.getInventory().addItem(damaged(Items.IRON_PICKAXE, 50));

        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("repair_tool", Map.of("item", "minecraft:iron_pickaxe")), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "ремонт обязан провалиться без материала и второго предмета, статус: "
                            + spirit.taskStatus().orElse(null) + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
        });
    }

    private static ItemStack damaged(Item item, int damage) {
        ItemStack stack = new ItemStack(item);
        stack.setDamageValue(damage);
        return stack;
    }

    private static int bestDurability(SpiritEntity spirit, Item item) {
        int best = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.is(item)) {
                best = Math.max(best, stack.getMaxDamage() - stack.getDamageValue());
            }
        }
        return best;
    }
}
