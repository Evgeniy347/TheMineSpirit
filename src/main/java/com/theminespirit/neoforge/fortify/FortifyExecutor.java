package com.theminespirit.neoforge.fortify;

import com.theminespirit.core.building.FortifyBatcher;
import com.theminespirit.core.coordinator.CoordinatorTuning;
import com.theminespirit.core.coordinator.WorkTarget;
import com.theminespirit.core.coordinator.WorkKind;
import com.theminespirit.core.intellect.FortifyOrder;
import com.theminespirit.core.intellect.FortifyTuning;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.BlockPlacer;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.coordinator.WorkReserveHub;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class FortifyExecutor {
    private final SpiritEntity spirit;
    private final List<List<BlockCoord>> batches;
    private final Set<BlockCoord> reserved = new LinkedHashSet<>();
    private int batchIndex;
    private int placed;
    private boolean done;

    public FortifyExecutor(SpiritEntity spirit, FortifyOrder order) {
        if (spirit == null) {
            throw new IllegalArgumentException("Исполнитель укрепления без духа не работает: ставить некому");
        }
        if (order == null) {
            throw new IllegalArgumentException("Исполнитель укрепления без приказа не работает: неизвестно, что ставить");
        }
        this.spirit = spirit;
        this.batches = FortifyBatcher.batches(order.cells(), FortifyTuning.FORTIFY_BLOCKS_PER_TICK);
    }

    public boolean done() {
        return done;
    }

    public int placed() {
        return placed;
    }

    public void tick() {
        if (done) {
            return;
        }
        if (batchIndex >= batches.size()) {
            releaseAll();
            done = true;
            return;
        }
        List<BlockCoord> batch = deferSelf(batches.get(batchIndex));
        if (batch.isEmpty() && allRemainingAreSelf(batchIndex)) {
            releaseAll();
            done = true;
            return;
        }
        Optional<String> material = FortifyMaterial.placeableBlockId(spirit.getInventory());
        if (material.isEmpty()) {
            releaseAll();
            done = true;
            return;
        }
        BlockState state = BlockStates.stateFor(material.get());
        int limit = Math.min(FortifyTuning.FORTIFY_BLOCKS_PER_TICK, batch.size());
        int placedThisTick = 0;
        for (BlockCoord cell : batch) {
            if (placedThisTick >= limit) {
                break;
            }
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (!reserve(cell)) {
                continue;
            }
            BlockPlacer.Begin begin = new BlockPlacer(spirit).begin(pos, state, material.get());
            if (begin instanceof BlockPlacer.Started) {
                placedThisTick++;
                placed++;
            } else if (begin instanceof BlockPlacer.Refused refused
                    && refused.reason() == BlockPlacer.RefuseReason.OCCUPIED) {
                placedThisTick++;
            } else {
                release(cell);
            }
        }
        batchIndex++;
        if (batchIndex >= batches.size()) {
            releaseAll();
            done = true;
        }
    }

    private List<BlockCoord> deferSelf(List<BlockCoord> batch) {
        Set<BlockCoord> occupied = new LinkedHashSet<>();
        occupied.add(new BlockCoord((int) Math.floor(spirit.getX()), (int) Math.floor(spirit.getY()),
                (int) Math.floor(spirit.getZ())));
        occupied.add(new BlockCoord((int) Math.floor(spirit.getX()), (int) Math.floor(spirit.getY()) + 1,
                (int) Math.floor(spirit.getZ())));
        List<BlockCoord> safe = new ArrayList<>();
        for (BlockCoord cell : batch) {
            if (!occupied.contains(cell)) {
                safe.add(cell);
            }
        }
        return safe;
    }

    private boolean allRemainingAreSelf(int fromBatch) {
        for (int index = fromBatch; index < batches.size(); index++) {
            if (!deferSelf(batches.get(index)).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean reserve(BlockCoord cell) {
        WorkTarget target = new WorkTarget(WorkKind.CELL, cell);
        if (!WorkReserveHub.reserve(spirit, target)) {
            return false;
        }
        reserved.add(cell);
        return true;
    }

    private void release(BlockCoord cell) {
        WorkReserveHub.release(spirit, new WorkTarget(WorkKind.CELL, cell));
        reserved.remove(cell);
    }

    public void releaseAll() {
        for (BlockCoord cell : new ArrayList<>(reserved)) {
            WorkReserveHub.release(spirit, new WorkTarget(WorkKind.CELL, cell));
        }
        reserved.clear();
    }
}