package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.ForceloadWatch;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

public final class ForceloadGuard {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ForceloadWatch WATCH = new ForceloadWatch();

    private ForceloadGuard() {}

    static void tick(ServerLevel level, List<SpiritEntity> spirits, Board board, Map<String, TicketTarget> targets) {
        var forced = level.getForcedChunks();
        LongPredicate isForced = forced::contains;
        for (SpiritEntity spirit : spirits) {
            if (spirit.isRemoved()) {
                continue;
            }
            BlockPos feet = spirit.blockPosition();
            String taskId = null;
            BlockCoord goal = null;
            for (BoardTicket ticket : board.assignedTo(spirit.getUUID())) {
                TicketTarget target = targets.get(ticket.id());
                if (target != null) {
                    taskId = ticket.id();
                    goal = target.target();
                    break;
                }
            }
            WATCH.check(
                            spirit.getUUID(),
                            spirit.displayName(),
                            new BlockCoord(feet.getX(), feet.getY(), feet.getZ()),
                            taskId,
                            goal,
                            isForced)
                    .ifPresent(text -> LOGGER.warn("[board] {}", text));
        }
    }
}
