package com.theminespirit.neoforge;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.lease.LeaseKind;
import com.theminespirit.core.board.lease.LeaseUndo;
import com.theminespirit.core.town.PlotProtection;
import com.theminespirit.neoforge.lease.LeaseStates;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

final class UndoCells {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ServerLevel level;
    private final SpiritEntity spirit;
    private final String leaseId;

    UndoCells(ServerLevel level, SpiritEntity spirit, String leaseId) {
        this.level = level;
        this.spirit = spirit;
        this.leaseId = leaseId;
    }

    void breakPlaced(BlockPos pos, BlockState state) {
        BreakNotice.broken(level, pos, state, spirit, "UNDO_LEASE", PlotProtection.Ground.NONE);
        SpiritBlocks.destroy(level, pos, true, spirit, "UNDO_LEASE");
        SpiritGesture.workOn(spirit, pos);
    }

    void restore(LeaseUndo.Step step, BlockPos pos, BlockState state) {
        boolean free = state.isAir() || state.canBeReplaced();
        String current = step.kind() == LeaseKind.STATE ? LeaseStates.text(state) : BlockStates.idOf(state);
        if (LeaseUndo.blocked(step, free, current)) {
            LOGGER.info("[lease] откат {} пропустил клетку {}: занята блоком {}", leaseId, step.pos(), current);
            return;
        }
        if (step.kind() == LeaseKind.BROKEN) {
            SpiritBlocks.restore(level, pos, BlockStates.stateFor(step.before()), spirit, "UNDO_LEASE");
        } else {
            LeaseStates.apply(level, pos, step.before());
        }
    }
}
