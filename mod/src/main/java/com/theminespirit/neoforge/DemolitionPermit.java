package com.theminespirit.neoforge;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.lease.LeaseRun;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class DemolitionPermit {
    static final DemolitionPermit NONE = new DemolitionPermit(null);
    private final @Nullable LeaseRun lease;

    private DemolitionPermit(@Nullable LeaseRun lease) {
        this.lease = lease;
    }

    static DemolitionPermit open(SpiritView spirit, boolean demolish) {
        return demolish ? new DemolitionPermit(LeaseRun.open(spirit, "demolish")) : NONE;
    }

    @Nullable
    String leaseId() {
        return lease == null ? null : lease.id();
    }

    void claim(Level level, List<BlockCoord> cells) {
        if (lease == null) {
            return;
        }
        for (BlockCoord cell : cells) {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                lease.broken(pos, state);
            }
        }
    }

    void record(Level level, BlockPos pos) {
        if (lease != null) {
            lease.broken(pos, level.getBlockState(pos));
        }
    }

    void commit() {
        if (lease != null) {
            lease.commit();
        }
    }

    void orphan() {
        if (lease != null) {
            lease.orphan();
        }
    }
}
