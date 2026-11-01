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
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.ColumnMask;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

final class PlotAudit {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long COOLDOWN = 600;
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

    static void tick(ServerLevel level, BoardService service, StageSite site, long now) {
        YardFloorWatch.tick(level, service, site, now);
        if (!service.board().ledger().has(BoardCase.CLEAR_GROUNDS, 1)
                || open(service, EXCLUSIVE)
                || recent(service, now)
                || open(service, WORKS)) {
            return;
        }
        BlockCoord anchor = site.anchor();
        List<BlockCoord> extras = new ArrayList<>();
        for (ColumnMask.Cell column : site.level().cells()) {
            int x = column.x();
            int z = column.z();
            if (site.ringPost(x, z)) {
                continue;
            }
            boolean mine = site.mineColumn(x, z);
            boolean planned = site.houseColumn(x, z);
            for (int dy = -1; dy < PlotCells.FREE_HEIGHT; dy++) {
                BlockCoord cell = new BlockCoord(x, anchor.y() + dy, z);
                PlotCells.defect(new PlotCells.Cell(dy, mine, planned), found(level.getBlockState(pos(cell))))
                        .filter(PlotCells.Defect.EXTRA::equals)
                        .ifPresent(defect -> extras.add(cell));
            }
        }
        if (extras.isEmpty()) {
            return;
        }
        String id = LevelRepair.ID + ":" + now;
        BoardOutcome outcome = service.submit(
                new BoardTicket(
                        id,
                        BoardCase.REPAIR_BUILDING,
                        Optional.empty(),
                        Math.max(
                                BoardRules.priorityOf(BoardCase.REPAIR_BUILDING),
                                BoardRules.priorityOf(BoardCase.REPAIR_FENCE)),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                TicketTarget.at(site.workYard())
                        .withWork(new StageWork(site, List.of()))
                        .withLeveling(LevelRepair.leveling(List.of(), extras)));
        if (outcome != BoardOutcome.ACCEPTED) {
            return;
        }
        LOGGER.info(
                "[plot] восстановление выравнивания {}: лишних блоков выше уровня {} (ожидается air), первые {}",
                id,
                extras.size(),
                extras.stream()
                        .limit(3)
                        .map(cell -> cell + " стоят "
                                + BuiltInRegistries.BLOCK
                                        .getKey(level.getBlockState(pos(cell)).getBlock())
                                        .toString())
                        .toList());
        service.journal()
                .recordEntry(BoardLog.note(
                        LevelRepair.ID,
                        BoardCase.REPAIR_BUILDING,
                        now,
                        List.of("extras=" + extras.size()),
                        null,
                        ConfigStamp.current()));
    }

    private static boolean recent(BoardService service, long now) {
        String prefix = LevelRepair.ID + ":";
        return service.board().all().stream()
                .map(BoardTicket::id)
                .filter(id -> id.startsWith(prefix))
                .mapToLong(id -> Long.parseLong(id.substring(prefix.length())))
                .anyMatch(tick -> now - tick < COOLDOWN);
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
