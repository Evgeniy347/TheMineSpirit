package com.theminespirit.gametest;

import com.theminespirit.core.board.ChestPlans;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.FenceLine;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.SettlementPlot;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.core.town.TownStreets;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.board.ChestDemolition;
import com.theminespirit.neoforge.board.PlotCellStructure;
import com.theminespirit.neoforge.board.SettlementPlotStore;
import com.theminespirit.neoforge.inventory.ContainerAccess;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.AABB;

final class ChestSalvageScene {
    private final GameTestHelper helper;
    private final SpiritEntity spirit;
    private final BlockPos left;
    private final BlockPos right;
    private final Container warehouse;
    private final ChestCapacityScene capacity;
    private SpiritRun run;
    private final int cobblestone;

    ChestSalvageScene(GameTestHelper helper, SpiritEntity spirit) {
        this.helper = helper;
        this.spirit = spirit;
        ServerLevel level = helper.getLevel();
        for (int x = 9; x <= 15; x++) {
            for (int z = 3; z <= 5; z++) {
                helper.setBlock(x, 0, z, Blocks.STONE);
            }
        }
        left = helper.absolutePos(new BlockPos(10, 1, 4));
        right = helper.absolutePos(new BlockPos(11, 1, 4));
        pair(new BlockPos(10, 1, 4));
        helper.setBlock(new BlockPos(10, 2, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(11, 2, 4), Blocks.STONE);
        pair(new BlockPos(13, 1, 4));
        var opened = ContainerAccess.open(level, helper.absolutePos(new BlockPos(13, 1, 4)));
        helper.assertTrue(opened instanceof ContainerAccess.Opened, "Склад для разгрузки обязан открываться");
        warehouse = ((ContainerAccess.Opened) opened).container();
        warehouse.clearContent();
        SettlementChestStore.get(level).track(helper.absolutePos(new BlockPos(13, 1, 4)));
        checkProtection();
        ChestBlockEntity first = (ChestBlockEntity) level.getBlockEntity(left);
        ChestBlockEntity second = (ChestBlockEntity) level.getBlockEntity(right);
        ItemStack treasure = new ItemStack(Items.DIAMOND, 5);
        treasure.set(DataComponents.CUSTOM_NAME, Component.literal("Содержимое лишнего сундука"));
        first.setItem(0, treasure);
        second.setItem(0, new ItemStack(Items.IRON_INGOT, 15));
        helper.assertTrue(
                ContainerAccess.open(level, left) instanceof ContainerAccess.Refused,
                "Чтение для сноса не разрешает обычный доступ сквозь закрытую крышку");
        var inspected = ContainerAccess.inspect(level, left);
        helper.assertTrue(
                inspected instanceof ContainerAccess.Opened contents
                        && contents.container().getContainerSize() == 54
                        && count(contents.container(), Items.DIAMOND) == 5
                        && count(contents.container(), Items.IRON_INGOT) == 15,
                "Снимок закрытого двойного сундука учитывает обе половины без потери содержимого");
        int slots = spirit.getInventory().getContainerSize();
        for (int slot = 0; slot < slots; slot++) {
            spirit.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        cobblestone = slots * 64;
        capacity = new ChestCapacityScene(helper, spirit, left, right, helper.absolutePos(new BlockPos(13, 1, 4)));
        helper.onEachTick(() -> {
            if (run == null) {
                return;
            }
            if (run.started() && level.getBlockState(left).isAir()) {
                helper.assertTrue(
                        count(warehouse, Items.COBBLESTONE) > 0,
                        "Полный инвентарь разгружается до первого слома: иначе при недоступном складе вещи останутся на земле");
            }
            if (run.started() && level.getBlockState(right).isAir()) {
                helper.assertTrue(
                        total(Items.DIAMOND) == 5,
                        "Вторую половину нельзя сломать, пока содержимое первой не подобрано или не сдано на склад");
            }
        });
    }

    void assertDone() {
        capacity.assertDone();
        if (run == null) {
            run = SpiritRun.launch(helper, spirit, ChestPlans.breakOnly(List.of(coord(left), coord(right))), 5);
        }
        run.assertStarted();
        helper.assertTrue(
                spirit.taskStatus().orElse(null) == RunStatus.SUCCEEDED,
                "Разбор закрытого сундука с полным инвентарём не завершён: "
                        + spirit.taskStatus().orElse(null) + ", "
                        + spirit.taskFailureReason().orElse("нет причины"));
        ServerLevel level = helper.getLevel();
        helper.assertTrue(
                level.getBlockState(left).isAir() && level.getBlockState(right).isAir(),
                "Обе внеплановые половины должны быть разобраны");
        helper.assertTrue(
                level.getBlockState(left.above()).is(Blocks.STONE)
                        && level.getBlockState(right.above()).is(Blocks.STONE),
                "Разбор сундука не должен снимать перекрытие над ним");
        helper.assertTrue(
                total(Items.DIAMOND) == 5 && total(Items.IRON_INGOT) == 15,
                "Всё содержимое обеих половин должно сохраниться у духа или на складе");
        helper.assertTrue(
                total(Items.CHEST) == 2 && total(Items.COBBLESTONE) == cobblestone,
                "Оба сундука подобраны, а прежний инвентарь разгружен без выбрасывания");
        helper.assertTrue(
                named(spirit.getInventory()) || named(warehouse),
                "Подбор обязан сохранять компоненты предметов, включая собственное имя");
        helper.assertTrue(
                level.getEntitiesOfClass(
                                ItemEntity.class,
                                new AABB(left).minmax(new AABB(right)).inflate(5))
                        .isEmpty(),
                "После завершения рядом не должно остаться выпавших вещей");
    }

    private void checkProtection() {
        ChestSalvageProtection.check(helper, spirit, left, right);
        ServerLevel level = helper.getLevel();
        var second = (ChestBlockEntity) level.getBlockEntity(right);
        second.setLootTable(BuiltInLootTables.SIMPLE_DUNGEON);
        helper.assertTrue(
                ContainerAccess.inspect(level, left) instanceof ContainerAccess.Refused
                        && !ChestDemolition.removable(level, coord(left))
                        && second.getLootTable() != null,
                "Чужая нераскрытая добыча второй половины не генерируется и не разбирается");
        second.setLootTable(null);
        helper.assertTrue(
                ChestDemolition.removable(level, coord(left)) && ChestDemolition.removable(level, coord(right)),
                "Сундук вне клеток схем склада разбирается без реестра и заказов");
        checkMineFloor();
    }

    private void checkMineFloor() {
        ServerLevel level = helper.getLevel();
        int x = left.getX();
        int z = left.getZ();
        int y = 68;
        BlockPos probe = new BlockPos(x, y - 1, z);
        var before = level.getBlockState(probe);
        level.setBlockAndUpdate(probe, Blocks.CHEST.defaultBlockState());
        ColumnMask area = ColumnMask.rect(x - 30, z - 30, x + 30, z + 30);
        ColumnMask street = ColumnMask.rect(x - 30, z - 30, x + 30, z - 30);
        BlockCoord anchor = new BlockCoord(x, y, z);
        TownPlan plan = new TownPlan(
                anchor,
                area,
                new TownStreets(street, street),
                List.of(
                        new Placement("hut", "дом", 1, x - 20, z - 20, 7, 9, Placement.Facing.NORTH),
                        new Placement("yard", "двор", 1, x - 20, z - 10, 7, 5, Placement.Facing.NORTH),
                        new Placement("mine", "шахта", 1, x - 1, z - 1, 5, 15, Placement.Facing.NORTH),
                        new Placement("orchard", "сад", 1, x + 10, z + 10, 13, 13, Placement.Facing.NORTH)),
                new FenceLine(List.of(), List.of()),
                List.of());
        SettlementPlotStore plots = SettlementPlotStore.get(level);
        plots.fixTown(plan);
        plots.settle(new SettlementPlot(anchor, y, y, 1, "oak"));
        try {
            helper.assertTrue(
                    PlotCellStructure.isStructure(level, probe),
                    "Спорная клетка должна совпадать с плановым полом шахты, как в живом поселении");
            helper.assertTrue(
                    ChestDemolition.removable(level, coord(probe)),
                    "В схеме шахты здесь пол, а не сундук: внеплановый сундук можно разобрать");
            helper.assertTrue(
                    spirit.protectionGate().checkDemolish(probe, level.getBlockState(probe))
                            instanceof BlockProtection.Allowed,
                    "Разбор внепланового сундука на месте пола шахты получает основание именно этой постройки");
            helper.assertTrue(
                    spirit.protectionGate().checkDemolish(probe, Blocks.STONE.defaultBlockState())
                            instanceof BlockProtection.Refused,
                    "Разрешение сноса сундука не разрешает ломать сам плановый пол шахты");
            ChestGraphScene.check(helper, spirit, probe);
        } finally {
            plots.clear();
            level.setBlockAndUpdate(probe, before);
        }
    }

    private void pair(BlockPos relative) {
        helper.setBlock(
                relative,
                Blocks.CHEST
                        .defaultBlockState()
                        .setValue(ChestBlock.FACING, Direction.NORTH)
                        .setValue(ChestBlock.TYPE, ChestType.LEFT));
        helper.setBlock(
                relative.east(),
                Blocks.CHEST
                        .defaultBlockState()
                        .setValue(ChestBlock.FACING, Direction.NORTH)
                        .setValue(ChestBlock.TYPE, ChestType.RIGHT));
    }

    private int total(Item item) {
        return count(spirit.getInventory(), item) + count(warehouse, item);
    }

    private static int count(Container container, Item item) {
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean named(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(Items.DIAMOND)
                    && Component.literal("Содержимое лишнего сундука").equals(stack.get(DataComponents.CUSTOM_NAME))) {
                return true;
            }
        }
        return false;
    }

    private static BlockCoord coord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }
}
