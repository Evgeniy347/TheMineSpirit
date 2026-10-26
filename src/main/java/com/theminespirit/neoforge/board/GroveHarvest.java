package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.GrovePlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.WoodRules;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.policy.GroveTrees;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import org.slf4j.Logger;

final class GroveHarvest {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int BASE_BELOW = 1;
    private static final int BASE_ABOVE = 2;
    private static final Set<BoardStatus> OPEN =
            EnumSet.of(BoardStatus.PENDING, BoardStatus.WAITING_RESOURCES, BoardStatus.ASSIGNED, BoardStatus.BLOCKED);

    private GroveHarvest() {}

    static void tick(ServerLevel level, BoardService service, StageSite stage, long now) {
        WoodRules rules = DataTables.INSTANCE.wood().current();
        if (now % rules.groveCheckTicks() != 0) {
            return;
        }
        for (BlockCoord spot : stage.saplings()) {
            String id = GrovePlans.id(spot);
            if (OPEN.contains(service.board().statusOf(id).orElse(BoardStatus.DONE))) {
                continue;
            }
            Optional<BlockCoord> base = base(level, stage, spot);
            if (base.isEmpty()) {
                continue;
            }
            int logs = trunk(level, base.orElseThrow());
            if (GrovePlans.mature(logs, rules)) {
                submit(service, id, base.orElseThrow(), logs, now);
            }
        }
    }

    private static Optional<BlockCoord> base(ServerLevel level, StageSite stage, BlockCoord spot) {
        int anchor = stage.anchor().y();
        for (int y = anchor - BASE_BELOW; y <= anchor + BASE_ABOVE; y++) {
            BlockCoord cell = new BlockCoord(spot.x(), y, spot.z());
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            if (level.isLoaded(pos) && level.getBlockState(pos).is(BlockTags.LOGS)) {
                return GroveTrees.planned(level, cell) ? Optional.of(cell) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static int trunk(ServerLevel level, BlockCoord base) {
        int logs = 0;
        BlockPos pos = new BlockPos(base.x(), base.y(), base.z());
        while (logs <= StageSite.GROVE_TREE_HEIGHT && level.getBlockState(pos).is(BlockTags.LOGS)) {
            logs++;
            pos = pos.above();
        }
        return logs;
    }

    private static void submit(BoardService service, String id, BlockCoord base, int logs, long now) {
        LOGGER.info("[grove] дерево рощи у {} зрелое ({} брёвен ствола): рубка и посадка на то же место", base, logs);
        service.submit(
                new BoardTicket(
                        id,
                        BoardCase.HARVEST_GROVE,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.HARVEST_GROVE),
                        now,
                        1,
                        id,
                        List.of(),
                        Map.of()),
                TicketTarget.at(base));
    }
}
