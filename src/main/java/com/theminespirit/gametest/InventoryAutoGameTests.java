package com.theminespirit.gametest;

import static com.theminespirit.gametest.InventorySceneSupport.chestAt;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
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
final class InventoryAutoGameTests {
    private InventoryAutoGameTests() {}

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void unloadAuto_PartialTorchesInFarChest_TopsItUpAndLeavesNear(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(2, 1, 7, Blocks.CHEST);
        helper.setBlock(5, 1, 4, Blocks.CHEST);
        Container far = chestAt(helper, 2, 1, 7);
        Container near = chestAt(helper, 5, 1, 4);
        far.setItem(0, new ItemStack(Items.TORCH, 10));
        near.setItem(0, new ItemStack(Items.OAK_PLANKS, 64));
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.TORCH, 52));
        SpiritRun run = SpiritRun.launch(
                helper, spirit, new ScriptCall("unload", Map.of("container", "auto", "radius", 8, "force", true)), 1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "unload auto обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(far, Items.TORCH) == 38,
                    "в дальнем сундуке неполная стопка факелов (10) обязана дополниться 28 излишка до 38, "
                            + "фактически " + GameTestFixtures.countInInventory(far, Items.TORCH));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(near, Items.TORCH) == 0,
                    "ближний сундук не тронут: факелов в нём быть не должно, фактически "
                            + GameTestFixtures.countInInventory(near, Items.TORCH));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(near, Items.OAK_PLANKS) == 64,
                    "доски в ближнем сундуке обязаны остаться на месте");
            helper.assertTrue(
                    GameTestFixtures.countInInventory(spirit.getInventory(), Items.TORCH) == 24,
                    "у духа обязаны остаться 24 факела постоянной части, фактически "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.TORCH));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void unloadAuto_FarChestFull_TorchesGoToNearestFree(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(2, 1, 7, Blocks.CHEST);
        helper.setBlock(5, 1, 4, Blocks.CHEST);
        Container far = chestAt(helper, 2, 1, 7);
        Container near = chestAt(helper, 5, 1, 4);
        Item[] fillers = {
            Items.DIRT,
            Items.SAND,
            Items.GRAVEL,
            Items.OAK_LOG,
            Items.BIRCH_LOG,
            Items.SPRUCE_LOG,
            Items.OAK_PLANKS,
            Items.STICK,
            Items.TORCH,
            Items.COAL,
            Items.CHARCOAL,
            Items.IRON_INGOT,
            Items.GOLD_INGOT,
            Items.COPPER_INGOT,
            Items.REDSTONE,
            Items.LAPIS_LAZULI,
            Items.DIAMOND,
            Items.EMERALD,
            Items.BREAD,
            Items.COOKED_BEEF,
            Items.APPLE,
            Items.ARROW,
            Items.BOW,
            Items.STONE,
            Items.ANDESITE,
            Items.GRANITE,
            Items.DIORITE
        };
        for (int slot = 0; slot < 27; slot++) {
            ItemStack filler = new ItemStack(fillers[slot]);
            far.setItem(slot, filler.copyWithCount(filler.getMaxStackSize()));
        }
        near.setItem(0, new ItemStack(Items.OAK_PLANKS, 64));
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.TORCH, 52));
        SpiritRun run = SpiritRun.launch(
                helper, spirit, new ScriptCall("unload", Map.of("container", "auto", "radius", 8, "force", true)), 1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "unload auto при полном дальнем сундуке обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(near, Items.TORCH) == 28,
                    "дальний сундук полон: 28 излишка факелов обязаны лечь в ближний свободный, фактически "
                            + GameTestFixtures.countInInventory(near, Items.TORCH));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(far, Items.TORCH) == 64,
                    "в полный дальний сундук добавился только заполнитель: факелов в нём быть не должно, "
                            + "фактически " + GameTestFixtures.countInInventory(far, Items.TORCH));
            GameTestFixtures.finish(helper);
        });
    }

    @SceneStep(template = "arena", timeoutTicks = 400)
    public static void depositAuto_PartialCobbleInFarChest_TopsItUp(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(2, 1, 7, Blocks.CHEST);
        helper.setBlock(5, 1, 4, Blocks.CHEST);
        Container far = chestAt(helper, 2, 1, 7);
        Container near = chestAt(helper, 5, 1, 4);
        far.setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        near.setItem(0, new ItemStack(Items.OAK_PLANKS, 64));
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "deposit",
                        Map.of("container", "auto", "item", "minecraft:cobblestone", "count", 10, "radius", 8)),
                1);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "deposit auto обязан завершиться успехом, причина: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(far, Items.COBBLESTONE) == 20,
                    "deposit auto обязан дополнить неполную стопку в дальнем сундуке до 20, фактически "
                            + GameTestFixtures.countInInventory(far, Items.COBBLESTONE));
            helper.assertTrue(
                    GameTestFixtures.countInInventory(near, Items.COBBLESTONE) == 0,
                    "ближний сундук не тронут: булыжник идёт к неполной стопке в дальнем");
            GameTestFixtures.finish(helper);
        });
    }
}
