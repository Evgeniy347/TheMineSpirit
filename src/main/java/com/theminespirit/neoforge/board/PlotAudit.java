package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.LevelRepair;
import com.theminespirit.core.board.PlotCells;
import com.theminespirit.core.board.PlotLeveling;
import com.theminespirit.core.board.SettlementLayout;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

final class PlotAudit {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long COOLDOWN = 6000;
    private static final Set<BoardCase> WORKS = EnumSet.of(
            BoardCase.CLEAR_GROUNDS,
            BoardCase.BUILD_MINE,
            BoardCase.BUILD_FENCE,
            BoardCase.BUILD_HOUSE,
            BoardCase.LIGHTING);
    private static final Set<BoardCase> EXCLUSIVE = EnumSet.of(BoardCase.REPAIR_BUILDING, BoardCase.CLEAR_GROUNDS);
    private static final Set<BoardStatus> OPEN =
            EnumSet.of(BoardStatus.PENDING, BoardStatus.WAITING_RESOURCES, BoardStatus.ASSIGNED);

    private PlotAudit() {}

    static void tick(ServerLevel level, BoardService service, BlockCoord anchor, long now) {
        if (!service.board().ledger().has(BoardCase.CLEAR_GROUNDS, 1)
                || open(service, EXCLUSIVE)
                || recent(service, now)) {
            return;
        }
        boolean building = open(service, WORKS);
        List<BlockCoord> holes = new ArrayList<>();
        List<BlockCoord> extras = new ArrayList<>();
        int half = SettlementLayout.LEVEL_HALF;
        for (int x = anchor.x() - half; x <= anchor.x() + half; x++) {
            for (int z = anchor.z() - half; z <= anchor.z() + half; z++) {
                boolean mine = SettlementLayout.mineColumn(anchor, x, z) || SettlementLayout.minePassage(anchor, x, z);
                boolean planned = SettlementLayout.houseColumn(anchor, x, z) || fence(anchor, x, z);
                for (int dy = -1; dy < PlotCells.FREE_HEIGHT; dy++) {
                    BlockCoord cell = new BlockCoord(x, anchor.y() + dy, z);
                    Optional<PlotCells.Defect> defect = PlotCells.defect(
                            new PlotCells.Cell(dy, mine, planned), found(level.getBlockState(pos(cell))));
                    defect.filter(PlotCells.Defect.HOLE::equals).ifPresent(hole -> holes.addAll(down(level, cell)));
                    defect.filter(PlotCells.Defect.EXTRA::equals)
                            .filter(extra -> !building)
                            .ifPresent(extra -> {
                                extras.add(cell);
                                if (cell.y() == anchor.y() - 1) {
                                    holes.add(cell);
                                }
                            });
                }
            }
        }
        if (holes.isEmpty() && extras.isEmpty()) {
            return;
        }
        String id = LevelRepair.ID + ":" + now;
        BoardOutcome outcome = service.submit(
                new BoardTicket(
                        id,
                        BoardCase.REPAIR_BUILDING,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.REPAIR_BUILDING),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                TicketTarget.at(anchor).withLeveling(LevelRepair.leveling(holes, extras)));
        if (outcome != BoardOutcome.ACCEPTED) {
            return;
        }
        LOGGER.info(
                "[plot] восстановление выравнивания {}: дыр {}, лишних блоков {}, первые {} {}",
                id,
                holes.size(),
                extras.size(),
                holes.stream().limit(3).toList(),
                extras.stream().limit(3).toList());
        service.journal()
                .recordEntry(BoardLog.note(
                        LevelRepair.ID,
                        BoardCase.REPAIR_BUILDING,
                        now,
                        List.of("holes=" + holes.size(), "extras=" + extras.size()),
                        null,
                        ConfigStamp.current()));
    }

    private static List<BlockCoord> down(ServerLevel level, BlockCoord top) {
        List<BlockCoord> column = new ArrayList<>();
        column.add(top);
        for (int depth = 1; depth <= PlotLeveling.MAX_SHIFT; depth++) {
            BlockCoord below = new BlockCoord(top.x(), top.y() - depth, top.z());
            if (found(level.getBlockState(pos(below))) != PlotCells.Found.OPEN) {
                break;
            }
            column.add(below);
        }
        return column;
    }

    private static boolean recent(BoardService service, long now) {
        String prefix = LevelRepair.ID + ":";
        return service.board().all().stream()
                .map(BoardTicket::id)
                .filter(id -> id.startsWith(prefix))
                .mapToLong(id -> Long.parseLong(id.substring(prefix.length())))
                .anyMatch(tick -> now - tick < COOLDOWN);
    }

    private static boolean fence(BlockCoord anchor, int x, int z) {
        int dx = Math.abs(x - anchor.x());
        int dz = Math.abs(z - anchor.z());
        int half = SettlementLayout.HALF;
        return (dx == half && dz <= half) || (dz == half && dx <= half);
    }

    private static boolean open(BoardService service, Set<BoardCase> cases) {
        return service.board().all().stream()
                .filter(ticket -> cases.contains(ticket.boardCase()))
                .anyMatch(ticket -> service.board()
                        .statusOf(ticket.id())
                        .map(OPEN::contains)
                        .orElse(false));
    }

    private static PlotCells.Found found(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return PlotCells.Found.OPEN;
        }
        if (state.is(Blocks.COBBLESTONE)) {
            return PlotCells.Found.COBBLESTONE;
        }
        boolean natural = state.is(BlockTags.DIRT)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY);
        return natural ? PlotCells.Found.NATURAL : PlotCells.Found.OTHER;
    }

    private static BlockPos pos(BlockCoord cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }
}
