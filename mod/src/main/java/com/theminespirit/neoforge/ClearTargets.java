package com.theminespirit.neoforge;

import com.theminespirit.core.board.lease.LeaseBook;
import com.theminespirit.core.mining.ClearScaffold;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.lease.LeaseData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

final class ClearTargets {
    private final Level level;
    private final @Nullable LeaseBook leases;
    private final UUID owner;
    private final boolean keepGround;

    ClearTargets(Level level, UUID owner, boolean keepGround) {
        this.level = level;
        this.owner = owner;
        this.keepGround = keepGround;
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
        return leases.owner(cell).filter(lease -> lease.ownedBy(owner)).isPresent();
    }

    void dropLeased(List<BlockCoord> remaining) {
        List<BlockCoord> left = ClearScaffold.withoutLeased(remaining, this::leased);
        remaining.clear();
        remaining.addAll(left);
    }

    @Nullable
    BlockCoord highestWork(List<BlockCoord> remaining) {
        List<BlockCoord> work = new ArrayList<>(remaining.size());
        for (BlockCoord cell : remaining) {
            if (!leased(cell) && !clear(cell)) {
                work.add(cell);
            }
        }
        return ClearScaffold.highest(work);
    }
}
