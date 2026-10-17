package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NoDropGameTests {
    private static final BlockPos SPIRIT_START = new BlockPos(4, 1, 4);
    private static final int SETTLE_TICKS = 5;

    private NoDropGameTests() {
    }

    @GameTest(template = "arena", batch = "no_drop_almost_full", timeoutTicks = 400)
    public static void gather_AlmostFullInventory_MinesWithoutDropping(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(2, 1, 2, Blocks.COBBLESTONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        for (int slot = 1; slot <= 34; slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        spirit.getInventory().setItem(35, new ItemStack(Items.COBBLESTONE, 63));
        int before = GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE);

        SpiritRun.launch(helper, spirit,
                new ScriptCall("gather", Map.of("block", "cobblestone", "count", 1, "radius", 8)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "gather одного булыжника при почти полном инвентаре обязан завершиться успехом: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == before + 1,
                    "в инвентаре обязан появиться добытый булыжник: было " + before + ", стало "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            helper.assertTrue(GameTestFixtures.dropsInArea(helper, Items.COBBLESTONE).isEmpty(),
                    "на земле не должно быть выброшенных булыжников: политика «не выкидывать предметы»");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "no_drop_warehouse", timeoutTicks = 400)
    public static void gather_FullInventoryWithWarehouse_DepositsSurplusMinesAndKeepsPickaxe(
            GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        placeDoubleChest(helper, 5, 6, 1, 4);
        helper.setBlock(2, 1, 2, Blocks.COBBLESTONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        for (int slot = 1; slot <= 35; slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        Container warehouse = chestAt(helper, 5, 1, 4);

        SpiritRun run = SpiritRun.launch(helper, spirit,
                new ScriptCall("gather", Map.of("block", "cobblestone", "count", 1, "radius", 8)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "gather при полном инвентаре с сундуком склада рядом обязан сдать лишнее и продолжить "
                            + "работу: статус " + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи")
                            + ", причина " + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(GameTestFixtures.countInInventory(warehouse, Items.COBBLESTONE) == 35 * 64,
                    "весь булыжник сверх набора обязан уехать на склад (35 стопок по 64), фактически "
                            + GameTestFixtures.countInInventory(warehouse, Items.COBBLESTONE));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.IRON_PICKAXE) == 1,
                    "кирка — текущий инструмент, сдавать её нельзя: без неё работа не продолжится");
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == 1,
                    "после сдачи дух обязан продолжить добычу и подобрать новый булыжник, фактически у духа "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 2)).isAir(),
                    "целевой булыжник обязан быть сломан после сдачи: работа продолжилась, а не остановилась");
            helper.assertTrue(GameTestFixtures.dropsInArea(helper, Items.COBBLESTONE).isEmpty(),
                    "на земле не должно остаться добычи: дух не выкидывает предметы");
            GameTestFixtures.finish(helper);
        });
    }

    @GameTest(template = "arena", batch = "no_drop_full", timeoutTicks = 400)
    public static void gather_FullInventory_FailsWithoutDropping(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        GameTestFixtures.buildFloor(helper, Blocks.STONE);
        helper.setBlock(2, 1, 2, Blocks.COBBLESTONE);
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), SPIRIT_START);
        spirit.getInventory().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        for (int slot = 1; slot <= 35; slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        int before = GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE);

        SpiritRun.launch(helper, spirit,
                new ScriptCall("gather", Map.of("block", "cobblestone", "count", 1, "radius", 8)),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            helper.assertTrue(spirit.taskStatus().orElse(null) == RunStatus.FAILED,
                    "gather при полном инвентаре обязан завершиться отказом: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи"));
            helper.assertTrue(spirit.taskFailureReason().orElse("").contains("инвентарь полон"),
                    "причина отказа обязана называть полный инвентарь: "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE) == before,
                    "инвентарь не должен измениться при отказе: было " + before + ", стало "
                            + GameTestFixtures.countInInventory(spirit.getInventory(), Items.COBBLESTONE));
            helper.assertTrue(GameTestFixtures.dropsInArea(helper, Items.COBBLESTONE).isEmpty(),
                    "на земле не должно быть выброшенных булыжников при отказе");
            helper.assertTrue(helper.getBlockState(new BlockPos(2, 1, 2)).is(Blocks.COBBLESTONE),
                    "блок при отказе не должен быть сломан");
            GameTestFixtures.finish(helper);
        });
    }

    private static void placeDoubleChest(GameTestHelper helper, int westX, int eastX, int y, int z) {
        BlockPos west = helper.absolutePos(new BlockPos(westX, y, z));
        BlockPos east = helper.absolutePos(new BlockPos(eastX, y, z));
        helper.getLevel().setBlock(west,
                Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH)
                        .setValue(ChestBlock.TYPE, ChestType.LEFT),
                3);
        helper.getLevel().setBlock(east,
                Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH)
                        .setValue(ChestBlock.TYPE, ChestType.RIGHT),
                3);
    }

    private static Container chestAt(GameTestHelper helper, int x, int y, int z) {
        ContainerAccess.Open open = ContainerAccess.open(helper.getLevel(), helper.absolutePos(new BlockPos(x, y, z)));
        if (open instanceof ContainerAccess.Opened opened) {
            return opened.container();
        }
        throw new IllegalStateException("Тестовый сундук (" + x + "," + y + "," + z + ") не открылся");
    }
}
