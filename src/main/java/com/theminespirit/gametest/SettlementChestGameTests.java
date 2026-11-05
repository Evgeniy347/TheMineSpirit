package com.theminespirit.gametest;

import com.theminespirit.core.ModIdentity;
import com.theminespirit.core.board.graph.SupplyGraph;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.engine.ScriptResult;
import com.theminespirit.core.inventory.ContainerSnapshot;
import com.theminespirit.core.inventory.Stack;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.neoforge.ModEntities;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.GraphFactInputs;
import com.theminespirit.neoforge.board.StockReader;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SettlementChestGameTests {
    private static final int SETTLE_TICKS = 5;
    private static final int SUPPLY_DISTANCE = 60;
    private static final int BUILD_X = 20;
    private static final int BUILD_Z = 9;

    private SettlementChestGameTests() {}

    // [флак] дух идёт 60 блоков до склада и обратно: замер ~600 тиков, запас 2× на движение и заходы.
    @GameTest(template = "settlement_arena", batch = "settlement_chest_supply", timeoutTicks = 1600)
    public static void supply_ChestSixtyBlocksFromBuild_SpiritBringsMaterial(GameTestHelper helper) {
        GameTestFixtures.reset(helper);
        floor(helper);
        ServerLevel level = helper.getLevel();
        clearRegistry(level);
        assertGraphSeesEveryChest(helper);
        BlockPos chest = new BlockPos(BUILD_X + SUPPLY_DISTANCE, 1, BUILD_Z);
        helper.setBlock(chest, Blocks.CHEST);
        ChestBlockEntity warehouse = (ChestBlockEntity) level.getBlockEntity(helper.absolutePos(chest));
        warehouse.setItem(0, new ItemStack(Items.COBBLESTONE, 17));
        placeEvent(helper, chest);

        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(BUILD_X + 2, 1, BUILD_Z));
        BlockPos origin = helper.absolutePos(new BlockPos(BUILD_X, 1, BUILD_Z));
        SpiritRun run = SpiritRun.launch(
                helper,
                spirit,
                new ScriptCall(
                        "build_schematic",
                        Map.of("file", "fixture_plate", "x", origin.getX(), "y", origin.getY(), "z", origin.getZ())),
                SETTLE_TICKS);

        helper.succeedWhen(() -> {
            run.assertStarted();
            helper.assertTrue(
                    spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                    "дух обязан принести материал из сундука в " + SUPPLY_DISTANCE
                            + " блоках и достроить плиту: статус "
                            + spirit.taskStatus().map(RunStatus::name).orElse("нет задачи") + ", причина "
                            + spirit.taskFailureReason().orElse("нет"));
            helper.assertTrue(
                    spirit.taskResult().orElse(null) instanceof ScriptResult.Data data
                            && Integer.valueOf(17).equals(data.fields().get("placed")),
                    "все 17 блоков схемы обязаны встать из принесённого материала, итог "
                            + spirit.taskResult().map(Object::toString).orElse("пусто"));
            helper.assertTrue(
                    warehouse.isEmpty(),
                    "склад в " + SUPPLY_DISTANCE + " блоках обязан опустеть: материал принесён из него, осталось "
                            + warehouse.getItem(0));
            SettlementChestStore.get(level).forget(helper.absolutePos(chest));
            GameTestFixtures.finish(helper);
        });
    }

    private static void assertGraphSeesEveryChest(GameTestHelper helper) {
        assertUntrackedChestVisible(helper);
        assertDoubleChestCountedOnce(helper);
        assertSpiritItemsVisible(helper);
    }

    private static void assertUntrackedChestVisible(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos chest = new BlockPos(2, 1, 2);
        helper.setBlock(chest, Blocks.CHEST);
        ChestBlockEntity entity = (ChestBlockEntity) level.getBlockEntity(helper.absolutePos(chest));
        entity.setItem(0, new ItemStack(Items.WOODEN_AXE, 1));

        List<ContainerSnapshot> found = StockReader.chests(level, around(helper, chest));
        helper.assertTrue(
                count(found, "minecraft:wooden_axe") == 1,
                "Сундук с топором, не попавший в реестр, обязан быть виден графу: " + contents(found));
        helper.setBlock(chest, Blocks.AIR);
    }

    private static void assertDoubleChestCountedOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos left = new BlockPos(4, 1, 2);
        BlockPos right = new BlockPos(5, 1, 2);
        helper.setBlock(left, Blocks.CHEST);
        helper.setBlock(right, Blocks.CHEST);
        BlockPos absoluteLeft = helper.absolutePos(left);
        BlockPos absoluteRight = helper.absolutePos(right);
        level.setBlock(
                absoluteLeft,
                Blocks.CHEST
                        .defaultBlockState()
                        .setValue(ChestBlock.FACING, Direction.NORTH)
                        .setValue(ChestBlock.TYPE, ChestType.LEFT),
                2);
        level.setBlock(
                absoluteRight,
                Blocks.CHEST
                        .defaultBlockState()
                        .setValue(ChestBlock.FACING, Direction.NORTH)
                        .setValue(ChestBlock.TYPE, ChestType.RIGHT),
                2);
        helper.assertTrue(
                level.getBlockState(absoluteRight).getValue(ChestBlock.TYPE) == ChestType.RIGHT,
                "Правая половина двойного сундука обязана остаться правой: " + level.getBlockState(absoluteRight));
        ((ChestBlockEntity) level.getBlockEntity(absoluteLeft)).setItem(0, new ItemStack(Items.STONE_AXE, 1));
        SettlementChestStore store = SettlementChestStore.get(level);
        store.track(absoluteLeft);
        store.track(absoluteRight);

        List<ContainerSnapshot> found = StockReader.chests(level, around(helper, left));
        long halves = found.stream()
                .filter(snapshot -> at(helper, snapshot, left) || at(helper, snapshot, right))
                .count();
        helper.assertTrue(halves == 1, "Двойной сундук обязан дать один снимок, а не два: " + halves);
        helper.assertTrue(
                count(found, "minecraft:stone_axe") == 1,
                "Топор двойного сундука обязан считаться один раз: " + contents(found));
        store.forget(absoluteLeft);
        store.forget(absoluteRight);
        helper.setBlock(left, Blocks.AIR);
        helper.setBlock(right, Blocks.AIR);
    }

    private static void assertSpiritItemsVisible(GameTestHelper helper) {
        SpiritEntity spirit = helper.spawn(ModEntities.SPIRIT.get(), new BlockPos(4, 1, 4));
        spirit.getInventory().setItem(0, new ItemStack(Items.STONE_AXE, 1));
        SupplyGraph graph = SupplyGraph.loadDefault();
        helper.assertTrue(
                GraphFactInputs.inventory(spirit).getOrDefault("minecraft:stone_axe", 0) == 1,
                "Топор в инвентаре духа обязан быть виден один раз: " + GraphFactInputs.inventory(spirit));
        helper.assertTrue(
                GraphFactInputs.tools(spirit, graph).stream().anyMatch(tool -> "axe".equals(tool.kind())),
                "Топор в инвентаре духа обязан считаться инструментом графа");

        ItemStack hand = spirit.getInventory().removeItem(0, 1);
        spirit.holdInMainHand(hand);
        helper.assertTrue(
                GraphFactInputs.inventory(spirit).getOrDefault("minecraft:stone_axe", 0) == 1,
                "Топор в руке обязан быть виден ровно один раз: " + GraphFactInputs.inventory(spirit));
        helper.assertTrue(
                GraphFactInputs.tools(spirit, graph).stream().anyMatch(tool -> "axe".equals(tool.kind())),
                "Топор в руке обязан считаться инструментом графа");

        spirit.getInventory().setItem(0, new ItemStack(Items.STONE_AXE, 1));
        spirit.showCopyInMainHand(spirit.getInventory().getItem(0));
        helper.assertTrue(
                GraphFactInputs.inventory(spirit).getOrDefault("minecraft:stone_axe", 0) == 1,
                "Копия в руке не удваивает предмет, который лежит в инвентаре");
        spirit.discard();
    }

    private static ColumnMask around(GameTestHelper helper, BlockPos relative) {
        BlockPos at = helper.absolutePos(relative);
        return ColumnMask.rect(at.getX() - 4, at.getZ() - 4, at.getX() + 4, at.getZ() + 4);
    }

    private static boolean at(GameTestHelper helper, ContainerSnapshot snapshot, BlockPos relative) {
        BlockPos at = helper.absolutePos(relative);
        return snapshot.pos().x() == at.getX() && snapshot.pos().z() == at.getZ();
    }

    private static int count(List<ContainerSnapshot> snapshots, String item) {
        int total = 0;
        for (ContainerSnapshot snapshot : snapshots) {
            for (Stack stack : snapshot.slots()) {
                if (stack != null && item.equals(stack.item())) {
                    total += stack.count();
                }
            }
        }
        return total;
    }

    private static String contents(List<ContainerSnapshot> snapshots) {
        return snapshots.stream()
                .flatMap(snapshot -> snapshot.slots().stream())
                .filter(Objects::nonNull)
                .map(stack -> stack.item() + "×" + stack.count())
                .toList()
                .toString();
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 1; x <= 128; x++) {
            for (int z = 8; z <= 10; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        for (int x = BUILD_X - 4; x <= BUILD_X + 6; x++) {
            for (int z = 6; z <= 16; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static void clearRegistry(ServerLevel level) {
        SettlementChestStore store = SettlementChestStore.get(level);
        for (BlockCoord pos : store.chests().all()) {
            store.forget(new BlockPos(pos.x(), pos.y(), pos.z()));
        }
    }

    private static void placeEvent(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(relative);
        NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(
                BlockSnapshot.create(level.dimension(), level, absolute), Blocks.AIR.defaultBlockState(), null));
    }
}
