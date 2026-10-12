package com.theminespirit.neoforge;

import com.theminespirit.core.forestry.ChopTuning;
import com.theminespirit.core.forestry.TreeSpecies;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

final class Replanter {
    enum State {
        RUNNING,
        DONE
    }

    private final SpiritEntity spirit;
    private final Item saplingItem;
    private final BlockState saplingState;
    private final List<BlockPos> targets;
    private final Approach approach;
    private int index;
    private int planted;
    private int skipped;
    private boolean lackedSaplings;

    Replanter(SpiritEntity spirit, TreeSpecies species, List<BlockPos> cells) {
        this.spirit = spirit;
        this.saplingItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(species.saplingId()));
        this.saplingState = Block.byItem(saplingItem).defaultBlockState();
        this.approach = new Approach(spirit);
        List<BlockPos> placeable = new ArrayList<>();
        for (BlockPos cell : cells) {
            if (canPlantAt(cell)) {
                placeable.add(cell);
            }
        }
        if (inventoryCount() < placeable.size()) {
            lackedSaplings = true;
            placeable.clear();
        }
        this.targets = List.copyOf(placeable);
    }

    State tick() {
        if (index >= targets.size()) {
            return State.DONE;
        }
        BlockPos cell = targets.get(index);
        if (!withinReach(cell)) {
            if (!approach.isActive()) {
                approach.begin(Vec3.atCenterOf(cell), ChopTuning.STAND_DISTANCE);
            }
            switch (approach.tick()) {
                case MOVING -> {
                    return State.RUNNING;
                }
                case ARRIVED -> {
                }
                case STUCK, TIMEOUT -> {
                    skipped++;
                    index++;
                    return State.RUNNING;
                }
            }
        }
        plant(cell);
        index++;
        return State.RUNNING;
    }

    void cancel() {
        approach.cancel();
    }

    int planted() {
        return planted;
    }

    int skipped() {
        return skipped;
    }

    boolean lackedSaplings() {
        return lackedSaplings;
    }

    private void plant(BlockPos cell) {
        if (!canPlantAt(cell) || !takeSapling()) {
            skipped++;
            return;
        }
        Level level = spirit.level();
        level.setBlock(cell, saplingState, Block.UPDATE_ALL);
        level.gameEvent(GameEvent.BLOCK_PLACE, cell, GameEvent.Context.of(spirit, saplingState));
        planted++;
    }

    private boolean canPlantAt(BlockPos cell) {
        Level level = spirit.level();
        if (level.getChunkSource().getChunkNow(cell.getX() >> 4, cell.getZ() >> 4) == null) {
            return false;
        }
        return level.getBlockState(cell).canBeReplaced() && saplingState.canSurvive(level, cell);
    }

    private boolean withinReach(BlockPos cell) {
        return spirit.getEyePosition().distanceTo(Vec3.atCenterOf(cell)) <= BlockBreaker.REACH;
    }

    private int inventoryCount() {
        Container inventory = spirit.getInventory();
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(saplingItem)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private boolean takeSapling() {
        Container inventory = spirit.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(saplingItem)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }
}
