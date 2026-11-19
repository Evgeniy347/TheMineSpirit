package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.LevelRepair;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.graph.GraphTickets;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.SettlementScan;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

final class MoundLots {
    private static final Comparator<BlockCoord> ORDER = Comparator.comparingInt(BlockCoord::y)
            .thenComparingInt(BlockCoord::x)
            .thenComparingInt(BlockCoord::z);

    private MoundLots() {}

    static boolean post(
            GraphPost post, GraphTickets.Ticket ticket, SettlementScan.Strays found, int priority, int batch) {
        if (found.mounds().isEmpty()) {
            return false;
        }
        List<BlockCoord> extras =
                found.mounds().stream().sorted(ORDER.reversed()).limit(batch).toList();
        BlockCoord first = next(post, extras);
        if (first == null) {
            return true;
        }
        post.submit(
                post.ticket(
                        "world:level_lot:" + WorldPost.key(first),
                        BoardCase.REPAIR_BUILDING,
                        Optional.empty(),
                        priority,
                        ticket.id(),
                        post.items(ticket.task())),
                TicketTarget.at(first).withLeveling(LevelRepair.leveling(List.of(), extras)));
        return true;
    }

    private static @Nullable BlockCoord next(GraphPost post, List<BlockCoord> extras) {
        for (BlockCoord at : extras) {
            String id = "world:level_lot:" + WorldPost.key(at);
            if (post.open(id)) {
                continue;
            }
            if (post.done(id)
                    && post.level
                            .getBlockState(new BlockPos(at.x(), at.y(), at.z()))
                            .isAir()) {
                continue;
            }
            return at;
        }
        return null;
    }
}
