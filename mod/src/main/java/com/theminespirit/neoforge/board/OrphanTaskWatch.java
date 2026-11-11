package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.TaskStall;
import com.theminespirit.core.engine.RunStatus;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import net.minecraft.core.BlockPos;

final class OrphanTaskWatch {
    private final TaskStall stall = new TaskStall();
    private final BoardIncidents incidents = new BoardIncidents();

    void watch(Board board, List<SpiritEntity> spirits) {
        for (SpiritEntity spirit : spirits) {
            if (spirit.isRemoved() || !board.assignedTo(spirit.getUUID()).isEmpty()) {
                stall.forget(spirit.getUUID());
                continue;
            }
            watch(board, spirit);
        }
    }

    private void watch(Board board, SpiritEntity spirit) {
        if (spirit.taskStatus().orElse(null) != RunStatus.RUNNING) {
            stall.forget(spirit.getUUID());
            return;
        }
        BlockPos feet = spirit.blockPosition();
        BlockCoord at = new BlockCoord(feet.getX(), feet.getY(), feet.getZ());
        String script = spirit.currentTaskScript().orElse(null);
        String key = spirit.taskProgress()
                .map(p -> p.step() + "/" + p.currentScript() + "/" + p.done())
                .orElse("-");
        if (!stall.noProgress(spirit.getUUID(), key, at, board.now(), false)) {
            return;
        }
        String reason = "задание без билета доски: "
                + StallText.reason(
                        false,
                        script,
                        key,
                        feet.toShortString(),
                        spirit.tactics().wantsControl(),
                        spirit.taskSuspendReason().orElse(null));
        incidents.orphaned(spirit, reason, board.now());
        stall.forget(spirit.getUUID());
        spirit.cancelTask();
    }
}
