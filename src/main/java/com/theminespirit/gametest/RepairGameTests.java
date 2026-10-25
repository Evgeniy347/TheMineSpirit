package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
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
    private static final int SETTLE_TICKS = 1;

    private RepairGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void repair_AnvilWithIngot_RaisesDurability(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.ANVIL);
        spirit.getInventory().addItem(damaged(Items.IRON_PICKAXE, 100));
        spirit.getInventory().addItem(new ItemStack(Items.IRON_INGOT, 1));

        SpiritRun run = SpiritRun.launch(
                helper, spirit, new ScriptCall("repair_tool", Map.of("item", "minecraft:iron_pickaxe")), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "ремонт наковальней обязан завершиться успехом, статус: "
                            + spirit.taskStatus().orElse(null) + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            int durability = bestDurability(spirit, Items.IRON_PICKAXE);
            int expected = 212;
            helper.assertTrue(
                    durability == expected,
                    "150 + 25% от 250 (62) = 212: наковальня добавляет четверть максимума за слиток, " + "фактически "
                            + durability);
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_INGOT) == 0,
                    "слиток обязан быть израсходован на ремонт, осталось "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_INGOT));
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void repair_LightlyAndHeavilyWorn_RepairsHeavilyWorn(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.ANVIL);
        spirit.getInventory().addItem(damaged(Items.IRON_PICKAXE, 20));
        spirit.getInventory().addItem(damaged(Items.IRON_PICKAXE, 200));
        spirit.getInventory().addItem(new ItemStack(Items.IRON_INGOT, 1));

        SpiritRun run = SpiritRun.launch(
                helper, spirit, new ScriptCall("repair_tool", Map.of("item", "minecraft:iron_pickaxe")), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "Чинится самый изношенный предмет (50 из 250), а не почти целый (230 из 250, выше порога): иначе "
                            + "ремонт проваливается «ещё крепок», а изношенный остаётся (INC-29); статус "
                            + spirit.taskStatus().orElse(null) + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    worstDurability(spirit, Items.IRON_PICKAXE) > 50,
                    "Изношенная кирка стала крепче: " + worstDurability(spirit, Items.IRON_PICKAXE));
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void repair_TwoDamagedWoodenPickaxes_Combines(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.getInventory().addItem(damaged(Items.WOODEN_PICKAXE, 30));
        spirit.getInventory().addItem(damaged(Items.WOODEN_PICKAXE, 40));

        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("repair_tool", Map.of("item", "minecraft:wooden_pickaxe")),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "комбинирование двух деревянных кирок обязано завершиться успехом, статус: "
                            + spirit.taskStatus().orElse(null) + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.WOODEN_PICKAXE) == 1,
                    "после комбинирования обязана остаться одна кирка, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.WOODEN_PICKAXE));
            int durability = bestDurability(spirit, Items.WOODEN_PICKAXE);
            int expected = 51;
            helper.assertTrue(
                    durability == expected,
                    "19 + 29 + 5% от 59 (3) = 51: точило складывает прочность и добавляет бонус, " + "фактически "
                            + durability);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 200)
    public static void repair_SingleDamagedIronPickaxe_Fails(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(1, 1, 1));
        spirit.getInventory().addItem(damaged(Items.IRON_PICKAXE, 50));

        SpiritRun run = SpiritRun.launch(
                helper, spirit, new ScriptCall("repair_tool", Map.of("item", "minecraft:iron_pickaxe")), SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.FAILED,
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

    private static int worstDurability(SpiritEntity spirit, Item item) {
        int worst = Integer.MAX_VALUE;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.is(item)) {
                worst = Math.min(worst, stack.getMaxDamage() - stack.getDamageValue());
            }
        }
        return worst;
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
