package com.theminespirit.neoforge.demo;

import com.theminespirit.core.building.DemoPlacement;
import com.theminespirit.core.building.Schematic;
import com.theminespirit.core.building.SchematicBlocks;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.script.CatalogLimits;
import com.theminespirit.neoforge.BlockItems;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import org.jspecify.annotations.Nullable;

final class DemoStock {
    private static final int WITHDRAW_RADIUS = 8;

    private final ServerLevel level;
    private final SpiritEntity spirit;

    DemoStock(ServerLevel level, SpiritEntity spirit) {
        this.level = level;
        this.spirit = spirit;
    }

    static Map<String, Object> withdrawParams(Withdrawal withdrawal) {
        Map<String, Object> params = new HashMap<>();
        BlockPos chest = withdrawal.chest();
        params.put("container", chest.getX() + "," + chest.getY() + "," + chest.getZ());
        params.put("item", withdrawal.item());
        params.put("count", withdrawal.count());
        params.put("radius", WITHDRAW_RADIUS);
        return params;
    }

    record Withdrawal(BlockPos chest, String item, int count) {}

    List<BlockPos> fillChests(DemoPlan plan) {
        BlockCoord base = plan.placement().chest();
        Direction partner = partnerSide(plan);
        placeSign(plan);
        List<BlockPos> filled = new ArrayList<>();
        ChestBlockEntity chest = null;
        int slot = 0;
        for (Map.Entry<String, Integer> material : plan.materials().entrySet()) {
            Item item = BlockItems.itemFor(material.getKey());
            int left = material.getValue();
            while (left > 0) {
                if (chest == null || slot >= chest.getContainerSize()) {
                    chest = nextChest(plan, base, partner, filled);
                    slot = 0;
                }
                int take = Math.min(left, item.getDefaultMaxStackSize());
                chest.setItem(slot++, new ItemStack(item, take));
                left -= take;
            }
        }
        if (filled.isEmpty()) {
            nextChest(plan, base, partner, filled);
        }
        return List.copyOf(filled);
    }

    private ChestBlockEntity nextChest(
            DemoPlan plan, BlockCoord base, @Nullable Direction partner, List<BlockPos> filled) {
        int index = filled.size();
        BlockPos main = new BlockPos(base.x(), base.y() + (partner == null ? index : index / 2), base.z());
        DemoPlacement.Look look = plan.placement().chestFacing();
        Direction facing = Direction.fromDelta(look.dx(), 0, look.dz());
        if (facing == null || facing.getAxis().isVertical()) {
            facing = Direction.NORTH;
        }
        if (partner == null) {
            filled.add(main);
            return placeChest(main, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), plan);
        }
        ChestType mainType = partner == facing.getClockWise() ? ChestType.LEFT : ChestType.RIGHT;
        BlockState mainState = Blocks.CHEST
                .defaultBlockState()
                .setValue(ChestBlock.FACING, facing)
                .setValue(ChestBlock.TYPE, mainType);
        BlockState otherState = mainState.setValue(ChestBlock.TYPE, mainType.getOpposite());
        BlockPos other = main.relative(partner);
        if (index % 2 == 0) {
            level.setBlock(other, otherState, Block.UPDATE_ALL);
            ChestBlockEntity first = placeChest(main, mainState, plan);
            filled.add(main);
            return first;
        }
        filled.add(other);
        if (!(level.getBlockEntity(other) instanceof ChestBlockEntity second)) {
            throw new IllegalStateException("Вторая половина сундука схемы '" + plan.scheme() + "' не поставлена в "
                    + other.toShortString() + ": блок сундука не создал блочную сущность");
        }
        return second;
    }

    private static @Nullable Direction partnerSide(DemoPlan plan) {
        BlockCoord base = plan.placement().chest();
        BlockCoord partner = plan.placement().chestPartner();
        if (partner == null) {
            return null;
        }
        return Direction.fromDelta(partner.x() - base.x(), 0, partner.z() - base.z());
    }

    private ChestBlockEntity placeChest(BlockPos pos, BlockState state, DemoPlan plan) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
            throw new IllegalStateException("Сундук материалов схемы '" + plan.scheme() + "' не поставлен в "
                    + pos.toShortString() + ": блок сундука не создал блочную сущность");
        }
        SettlementChestStore.get(level).track(pos);
        return chest;
    }

    private void placeSign(DemoPlan plan) {
        BlockCoord cell = plan.placement().sign();
        if (cell == null) {
            return;
        }
        BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
        if (!level.getBlockState(pos).isAir()) {
            return;
        }
        DemoPlacement.Look look = plan.placement().chestFacing();
        Direction facing = Direction.fromDelta(look.dx(), 0, look.dz());
        BlockState state = Blocks.OAK_SIGN.defaultBlockState();
        if (facing != null && facing.getAxis().isHorizontal()) {
            state = state.setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(facing));
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            sign.setText(sign.getFrontText().setMessage(0, Component.literal(plan.name())), true);
            sign.setChanged();
        }
    }

    List<Withdrawal> withdrawals(DemoPlan plan, List<BlockPos> chests) {
        Map<Item, Integer> need = new LinkedHashMap<>();
        for (Schematic.PlacedEntry entry : plan.entries()) {
            if (SchematicBlocks.isSecondaryPart(entry.block(), entry.state())
                    || !BlockItems.hasBlockItem(entry.block())) {
                continue;
            }
            BlockPos pos =
                    new BlockPos(entry.pos().x(), entry.pos().y(), entry.pos().z());
            if (!level.getBlockState(pos).is(BlockStates.blockFor(entry.block()))) {
                need.merge(BlockItems.itemFor(entry.block()), 1, Integer::sum);
            }
        }
        int freeSlots = 0;
        for (int slot = 0; slot < spirit.getInventory().getContainerSize(); slot++) {
            ItemStack stack = spirit.getInventory().getItem(slot);
            if (stack.isEmpty()) {
                freeSlots++;
            } else {
                need.computeIfPresent(
                        stack.getItem(), (item, count) -> count > stack.getCount() ? count - stack.getCount() : null);
            }
        }
        List<Withdrawal> picked = new ArrayList<>();
        for (BlockPos pos : chests) {
            if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
                continue;
            }
            for (int slot = 0; slot < chest.getContainerSize() && freeSlots > 0; slot++) {
                ItemStack stack = chest.getItem(slot);
                Integer wanted = stack.isEmpty() ? null : need.get(stack.getItem());
                if (wanted == null || wanted <= 0) {
                    continue;
                }
                int take = Math.min(Math.min(wanted, stack.getCount()), CatalogLimits.COUNT_MAX);
                picked.add(new Withdrawal(
                        pos, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), take));
                need.put(stack.getItem(), wanted - take);
                freeSlots -= (take + stack.getMaxStackSize() - 1) / stack.getMaxStackSize();
            }
        }
        return List.copyOf(picked);
    }
}
