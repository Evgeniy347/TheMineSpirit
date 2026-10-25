package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KitActivityGameTests {
    private KitActivityGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void unload_MinerActivity_KeepsPickaxeAndTorchesDropsPlanks(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        spirit.getInventory().setItem(1, new ItemStack(Items.TORCH, 32));
        spirit.getInventory().setItem(2, new ItemStack(Items.OAK_PLANKS, 64));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new SequenceNode(List.of(
                        new FallbackNode(List.of(
                                new ScriptCall("mine_vein", Map.of("ore", "iron")),
                                new ScriptCall("wait", Map.of("ticks", 1)))),
                        new ScriptCall("unload", Map.of("container", "nearest", "radius", 8, "force", true)))),
                1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "unload шахтёра обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_PICKAXE) == 1,
                    "дух-рудокоп обязан оставить кирку: набор шахты держит до двух кирок, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_PICKAXE));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.TORCH) == 32,
                    "дух-рудокоп обязан оставить 32 факела: набор шахты расширяет квоту факелов до 32, " + "фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.TORCH));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_PLANKS) == 0
                            && chestCount(helper, Items.OAK_PLANKS) == 64,
                    "доски вне набора шахты обязаны уйти в сундук: у духа "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_PLANKS)
                            + ", в сундуке " + chestCount(helper, Items.OAK_PLANKS));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void unload_NoActivity_DropsPickaxeKeepsConstant(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        spirit.getInventory().setItem(1, new ItemStack(Items.TORCH, 32));
        spirit.getInventory().setItem(2, new ItemStack(Items.OAK_PLANKS, 64));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("unload", Map.of("container", "nearest", "radius", 8, "force", true)),
                1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "unload без деятельности обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_PICKAXE) == 0
                            && chestCount(helper, Items.IRON_PICKAXE) == 1,
                    "без деятельности кирка вне постоянной части и обязана уйти в сундук: у духа "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_PICKAXE)
                            + ", в сундуке " + chestCount(helper, Items.IRON_PICKAXE));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.TORCH) == 24
                            && chestCount(helper, Items.TORCH) == 8,
                    "без деятельности действует постоянная квота факелов 24: у духа "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.TORCH)
                            + ", в сундуке " + chestCount(helper, Items.TORCH));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_PLANKS) == 0
                            && chestCount(helper, Items.OAK_PLANKS) == 64,
                    "доски вне постоянной части обязаны уйти в сундук");
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void unload_Child_DropsEverythingExceptFood(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(6, 1, 4, Blocks.CHEST);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.setChild(true);
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
        spirit.getInventory().setItem(1, new ItemStack(Items.BOW));
        spirit.getInventory().setItem(2, new ItemStack(Items.ARROW, 16));
        spirit.getInventory().setItem(3, new ItemStack(Items.BREAD, 16));
        spirit.getInventory().setItem(4, new ItemStack(Items.TORCH, 24));
        spirit.getInventory().setItem(5, new ItemStack(Items.IRON_PICKAXE));
        spirit.getInventory().setItem(6, new ItemStack(Items.OAK_PLANKS, 64));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall("unload", Map.of("container", "nearest", "radius", 8, "force", true)),
                1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "unload ребёнка обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.BREAD) == 16,
                    "у ребёнка остаётся еда: детский набор держит только её, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.BREAD));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_SWORD) == 0
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.BOW) == 0
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.ARROW) == 0
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.TORCH) == 0
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_PICKAXE) == 0
                            && GameTestFixtures.countInInventory(spirit.getInventory(), Items.OAK_PLANKS) == 0,
                    "ребёнок выгружает всё, кроме еды: у него остались меч, лук, стрелы, факелы, кирка и доски");
            helper.assertTrue(
                    chestCount(helper, Items.IRON_SWORD) == 1
                            && chestCount(helper, Items.BOW) == 1
                            && chestCount(helper, Items.ARROW) == 16
                            && chestCount(helper, Items.TORCH) == 24
                            && chestCount(helper, Items.IRON_PICKAXE) == 1
                            && chestCount(helper, Items.OAK_PLANKS) == 64,
                    "выгруженное ребёнком обязано оказаться в сундуке");
            GameTestFixtures.finish(helper);
        });
    }

    private static int chestCount(GameTestHelper helper, Item item) {
        int total = 0;
        Container chest = accessChest(helper);
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).is(item)) {
                total += chest.getItem(slot).getCount();
            }
        }
        return total;
    }

    private static Container accessChest(GameTestHelper helper) {
        ContainerAccess.Open open = ContainerAccess.open(helper.getLevel(), helper.absolutePos(new BlockPos(6, 1, 4)));
        if (open instanceof ContainerAccess.Opened opened) {
            return opened.container();
        }
        throw new IllegalStateException("Тестовый сундук не открылся");
    }
}
