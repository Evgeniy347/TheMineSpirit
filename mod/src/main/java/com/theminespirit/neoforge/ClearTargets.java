package com.theminespirit.neoforge;

import com.theminespirit.core.board.lease.LeaseBook;
import com.theminespirit.core.mining.api.ClearScaffold;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.lease.LeaseData;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class ClearTargets {
    private static final int LEASE_SMALL = 4096;
    private static final int LEASE_SLICE = 20_000;

    private final Level level;
    private final @Nullable LeaseBook leases;
    private final boolean keepGround;
    private final @Nullable String exemptLease;
    private int leaseIndex = -1;
    private long leaseScanned = -1;

    ClearTargets(Level level, @Nullable String exemptLease, boolean keepGround) {
        this.level = level;
        this.keepGround = keepGround;
        this.exemptLease = exemptLease;
        this.leases =
                level instanceof ServerLevel server ? LeaseData.get(server).book() : null;
    }

    boolean clear(BlockCoord cell) {
        BlockState state = level.getBlockState(new BlockPos(cell.x(), cell.y(), cell.z()));
        return state.isAir() || (keepGround && PlotGround.natural(state));
    }

    boolean leased(BlockCoord cell) {
        if (leases == null) {
            return false;
        }
        return offLimits(leases.owner(cell).filter(lease -> !Objects.equals(lease.id(), exemptLease)));
    }

    static boolean offLimits(Optional<?> leaseOwner) {
        return leaseOwner.isPresent();
    }

    void dropLeased(List<BlockCoord> remaining) {
        if (leases == null) {
            return;
        }
        if (leaseIndex < 0) {
            long revision = leases.revision();
            if (revision == leaseScanned) {
                return;
            }
            leaseScanned = revision;
            if (remaining.size() <= LEASE_SMALL) {
                List<BlockCoord> left = ClearScaffold.withoutLeased(remaining, this::leased);
                if (left.size() != remaining.size()) {
                    remaining.clear();
                    remaining.addAll(left);
                }
                return;
            }
            leaseIndex = remaining.size() - 1;
        }
        int index = Math.min(leaseIndex, remaining.size() - 1);
        int budget = LEASE_SLICE;
        while (index >= 0 && budget-- > 0) {
            BlockCoord cell = remaining.get(index);
            if (leases.occupied(cell) && leased(cell)) {
                remaining.remove(index);
            } else {
                index--;
            }
        }
        leaseIndex = index;
    }

    List<BlockCoord> work(List<BlockCoord> remaining) {
        List<BlockCoord> work = new ArrayList<>(remaining.size());
        for (BlockCoord cell : remaining) {
            if (!leased(cell) && !clear(cell)) {
                work.add(cell);
            }
        }
        return work;
    }
}
