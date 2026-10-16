package com.theminespirit.neoforge.fortify;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.BlockBreaker;
import com.theminespirit.neoforge.SpiritEntity;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class DismantleExecutor {
    private final SpiritEntity spirit;
    private final List<BlockCoord> pending;
    private final BlockBreaker breaker;
    private BlockPos active;
    private int removed;
    private boolean done;

    public DismantleExecutor(SpiritEntity spirit, List<BlockCoord> ownTopDown) {
        if (spirit == null) {
            throw new IllegalArgumentException("Разборщик без духа не работает: снимать некому");
        }
        if (ownTopDown == null) {
            throw new IllegalArgumentException("Разборщик без списка клеток не работает: пустой — пустым списком");
        }
        this.spirit = spirit;
        this.pending = new ArrayList<>(ownTopDown);
        this.breaker = new BlockBreaker(spirit);
    }

    public boolean done() {
        return done;
    }

    public int removed() {
        return removed;
    }

    public void tick() {
        if (done) {
            return;
        }
        if (active != null) {
            BlockBreaker.Progress progress = breaker.tick();
            if (progress == BlockBreaker.Progress.BROKEN || progress == BlockBreaker.Progress.GONE) {
                unmark(active);
                active = null;
                removed++;
            } else if (progress == BlockBreaker.Progress.BREAKING) {
                return;
            }
        }
        while (!pending.isEmpty()) {
            BlockCoord cell = pending.remove(0);
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (!isSpiritMarked(pos)) {
                continue;
            }
            if (spirit.level().getBlockState(pos).isAir()) {
                unmark(pos);
                continue;
            }
            BlockBreaker.Begin begin = breaker.begin(pos);
            if (begin instanceof BlockBreaker.Started) {
                active = pos;
                return;
            }
        }
        done = true;
    }

    public void cancel() {
        breaker.cancel();
        active = null;
        done = true;
    }

    private boolean isSpiritMarked(BlockPos pos) {
        return spirit.level() instanceof ServerLevel server && PlacedBlocksData.get(server).isSpiritMarked(pos);
    }

    private void unmark(BlockPos pos) {
        if (spirit.level() instanceof ServerLevel server) {
            PlacedBlocksData.get(server).unmarkSpirit(pos);
        }
    }
}