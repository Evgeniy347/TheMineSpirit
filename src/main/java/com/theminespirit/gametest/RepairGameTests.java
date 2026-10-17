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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RepairGameTests {
    private static final int IRON_PICKAXE_MAX = 250;
    private static final int SETTLE_TICKS = 1;

    private RepairGameTests() {
    }

    @GameTest(template = "arena", batch = "repair_two_damaged", timeoutTicks = 200)
    public static void repair_TwoDamagedIronPickaxes_LeavesOneRepaired(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.getInventory().addItem(damagedPickaxe(100));
        spirit.getInventory().addItem(damagedPickaxe(150));

        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("repair", Map.of("item", "minecraft:iron_pickaxe")), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "repair обязан завершиться успехом, статус: " + spirit.taskStatus().orElse(null)
                            + ", причина: " + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_PICKAXE) == 1,
                    "после ремонта обязана остаться одна кирка, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_PICKAXE));
            int durability = bestPickaxeDurability(spirit);
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
        spirit.getInventory().addItem(damagedPickaxe(50));

        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("repair", Map.of("item", "minecraft:iron_pickaxe")), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "repair обязан провалиться без второго такого же предмета, статус: "
                            + spirit.taskStatus().orElse(null) + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
        });
    }

    private static ItemStack damagedPickaxe(int damage) {
        ItemStack stack = new ItemStack(Items.IRON_PICKAXE);
        stack.setDamageValue(damage);
        return stack;
    }

    private static int bestPickaxeDurability(SpiritEntity spirit) {
        int best = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.is(Items.IRON_PICKAXE)) {
                best = Math.max(best, IRON_PICKAXE_MAX - stack.getDamageValue());
            }
        }
        return best;
    }
}
