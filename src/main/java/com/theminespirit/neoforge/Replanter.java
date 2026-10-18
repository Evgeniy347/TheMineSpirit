package com.theminespirit.neoforge;

import com.theminespirit.core.forestry.AreaReplant;
import com.theminespirit.core.forestry.ChopTuning;
import com.theminespirit.core.forestry.ReplantData;
import com.theminespirit.core.forestry.ReplantPlan;
import com.theminespirit.core.forestry.TreeSpecies;
import com.theminespirit.core.forestry.TreeView;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.policy.BlockProtection;
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
    private final ReplantData data;
    private final TreeView view;
    private final List<BlockPos> trunkCells;
    private final List<BlockPos> targets;
    private final Approach approach;
    private int index;
    private int planted;
    private int skipped;
    private boolean lackedSaplings;
    private final boolean areaAllowed;
    private boolean areaPlanned;

    Replanter(SpiritEntity spirit, TreeSpecies species, List<BlockPos> cells, TreeView view) {
        this.spirit = spirit;
        this.saplingItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(species.saplingId()));
        this.saplingState = Block.byItem(saplingItem).defaultBlockState();
        this.data = ReplantData.loadDefault();
        this.view = view;
        this.trunkCells = List.copyOf(cells);
        this.approach = new Approach(spirit);
        List<BlockCoord> coords = trunkCells.stream().map(Replanter::toCoord).toList();
        ReplantPlan plan = ReplantPlan.of(coords, inventoryCount(), data);
        this.lackedSaplings = plan.cells().isEmpty();
        this.areaAllowed = !plan.lacked();
        this.targets = new ArrayList<>(plan.cells().stream().map(Replanter::toPos).toList());
    }

    State tick() {
        while (true) {
            if (index >= targets.size()) {
                if (!areaPlanned && areaAllowed) {
                    planArea();
                    areaPlanned = true;
                    if (index < targets.size()) {
                        continue;
                    }
                }
                return State.DONE;
            }
            if (!hasSapling()) {
                lackedSaplings = true;
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
                        continue;
                    }
                }
            }
            plant(cell);
            index++;
        }
    }

    void suspend() {
        approach.suspend();
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

    private void planArea() {
        if (inventoryCount() < 1) {
            return;
        }
        List<BlockCoord> excluded = trunkCells.stream().map(Replanter::toCoord).toList();
        BlockCoord base = excluded.stream().min(BlockCoord::compareTo).orElseThrow();
        List<BlockCoord> area = AreaReplant.plan(base, excluded, data, view);
        for (BlockCoord cell : area) {
            targets.add(toPos(cell));
        }
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
        return level.getBlockState(cell).canBeReplaced() && saplingState.canSurvive(level, cell)
                && spirit.protectionGate().checkPlace(cell) instanceof BlockProtection.Allowed;
    }

    private boolean withinReach(BlockPos cell) {
        return spirit.getEyePosition().distanceTo(Vec3.atCenterOf(cell)) <= BlockBreaker.REACH;
    }

    private boolean hasSapling() {
        return inventoryCount() >= 1;
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

    private static BlockCoord toCoord(BlockPos pos) {
        return new BlockCoord(pos.getX(), pos.getY(), pos.getZ());
    }

    private static BlockPos toPos(BlockCoord coord) {
        return new BlockPos(coord.x(), coord.y(), coord.z());
    }
}
