package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.mining.ToolChoice;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.BlockBreaker;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
final class InventoryMiscGameTests {
    private InventoryMiscGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void blockBreaker_MainHandPickaxe_BreaksFasterAndWears(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.STONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        BlockPos stone = helper.absolutePos(new BlockPos(6, 1, 4));
        BlockBreaker breaker = BlockBreaker.escaping(spirit);
        helper.assertTrue(
                breaker.begin(stone) instanceof BlockBreaker.Started, "камень в досягаемости обязан начинать ломаться");
        int bareTicks = breaker.requiredTicks();
        spirit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(
                breaker.begin(stone) instanceof BlockBreaker.Started,
                "камень с киркой в руке обязан начинать ломаться");
        int fastTicks = breaker.requiredTicks();
        helper.assertTrue(
                breaker.choice().slot() == ToolChoice.MAIN_HAND,
                "ломка обязана выбрать кирку из основной руки, а не руку или инвентарь: слот "
                        + breaker.choice().slot());
        helper.assertTrue(
                fastTicks < bareTicks,
                "ломка камня железной киркой из руки обязана быть быстрее, чем рукой: рукой " + bareTicks
                        + " тиков, киркой " + fastTicks);
        AtomicReference<BlockBreaker.Progress> progress = new AtomicReference<>(BlockBreaker.Progress.BREAKING);
        helper.onEachTick(() -> {
            if (progress.get() == BlockBreaker.Progress.BREAKING) {
                progress.set(breaker.tick());
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(
                    progress.get() == BlockBreaker.Progress.BROKEN,
                    "кирка из руки обязана доломать камень до конца, состояние " + progress.get());
            helper.assertTrue(
                    spirit.getMainHandItem().getDamageValue() == 1,
                    "износ обязан списываться с кирки в руке: урон должен быть 1, фактически "
                            + spirit.getMainHandItem().getDamageValue());
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 150)
    public static void swapTool_StoneBlock_HoldsPickaxe(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(2, 1, 5));
        spirit.getInventory().setItem(0, new ItemStack(Items.STONE_PICKAXE));

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("swap_tool", Map.of("block", "stone")), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "swap_tool обязан сменить инструмент и завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    spirit.getMainHandItem().is(Items.STONE_PICKAXE),
                    "для камня в руке обязана оказаться каменная кирка, фактически: " + spirit.getMainHandItem());
            TacticsScene.finish(helper, spirit);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 150)
    public static void drinkPotion_HealingPotion_HealsSpirit(GameTestHelper helper) {
        TacticsScene.prepare(helper);
        SpiritEntity spirit = TacticsScene.spirit(helper, new BlockPos(2, 1, 5));
        spirit.setHealth(1.0F);
        spirit.getInventory().setItem(0, PotionContents.createItemStack(Items.POTION, Potions.HEALING));

        SpiritRun run = SpiritRun.launch(helper, spirit, new ScriptCall("drink_potion", Map.of("type", "healing")), 10);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "drink_potion обязан выпить зелье и завершиться успехом, статус: "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    spirit.getHealth() >= 5.0F,
                    "зелье лечения обязано вылечить на 4 единицы с 1 до 5 и выше, фактически: " + spirit.getHealth());
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.POTION) == 0,
                    "выпитое зелье обязано исчезнуть из инвентаря, осталось: "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.POTION));
            TacticsScene.finish(helper, spirit);
        });
    }
}
