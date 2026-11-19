package com.theminespirit.neoforge;

import com.theminespirit.core.mining.MineCellPlan;
import com.theminespirit.core.observation.BlockCoord;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;

final class MineCellDig {
    private final MineAimStep aim;
    private final BlockBreaker breaker;
    private final MineObstacleClear obstructions;
    private final MineBuildCells.Host host;

    MineCellDig(MineAimStep aim, BlockBreaker breaker, MineObstacleClear obstructions, MineBuildCells.Host host) {
        this.aim = aim;
        this.breaker = breaker;
        this.obstructions = obstructions;
        this.host = host;
    }

    void startBreak(BlockCoord cell, OptionalInt slot) {
        BlockPos pos = toPos(cell);
        BlockBreaker.Begin begin =
                slot.isPresent() ? breaker.beginWithSlot(pos, slot.getAsInt()) : breaker.beginExcavate(pos);
        if (begin instanceof BlockBreaker.Started) {
            host.breaking();
            return;
        }
        if (begin instanceof BlockBreaker.Refused refused) {
            if (refused.reason() == BlockBreaker.RefuseReason.AIR) {
                return;
            }
            if (refused.reason() == BlockBreaker.RefuseReason.OUT_OF_REACH) {
                aimAt(cell, new MineCellPlan.Seen(aim.occupantText(cell), false, true, false));
                return;
            }
            if (refused.reason() == BlockBreaker.RefuseReason.NO_SIGHT) {
                aimAt(cell, new MineCellPlan.Seen(aim.occupantText(cell), false, false, true));
                return;
            }
            host.failed("клетку шахты " + cell + " не выломать: " + refused.message() + aim.context(cell));
            return;
        }
        host.failed("клетку шахты " + cell + " не выломать: " + ((BlockBreaker.Protected) begin).reason()
                + aim.context(cell));
    }

    void aimAt(BlockCoord cell, MineCellPlan.Seen seen) {
        MineAimStep.Begin begin = aim.begin(cell, seen);
        switch (begin.next()) {
            case READY -> {}
            case AIM -> host.aim();
            case REFUSE -> {
                if (obstructions.clear(cell)) {
                    host.breaking();
                } else {
                    host.failed(begin.failure());
                }
            }
        }
    }

    private static BlockPos toPos(BlockCoord cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }
}
