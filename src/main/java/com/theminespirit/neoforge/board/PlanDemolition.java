package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardOutcome;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.StagePlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownPlan;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

final class PlanDemolition {
    static final String PREFIX = "demolish:";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long COOLDOWN = 600;
    private static final int BATCH = 48;
    private static final int SHOWN = 4;
    private static final Set<BoardStatus> OPEN =
            EnumSet.of(BoardStatus.PENDING, BoardStatus.WAITING_RESOURCES, BoardStatus.ASSIGNED);

    private PlanDemolition() {}

    static void tick(ServerLevel level, BoardService service, StageSite site, TownPlan town, long now) {
        if (!service.board().ledger().has(BoardCase.CLEAR_GROUNDS, 1) || busy(service, now)) {
            return;
        }
        boolean yardReady = service.board().ledger().has(BoardCase.STOCK_CHESTS, 1)
                && service.board().ledger().has(BoardCase.CRAFT_FURNACE, 1);
        List<BlockCoord> stray = StrayBlocks.find(level, site, town.region()).stream()
                .filter(cell -> yardReady || !station(level.getBlockState(pos(cell))))
                .sorted(Comparator.comparingInt(BlockCoord::y).reversed().thenComparing(Comparator.naturalOrder()))
                .limit(BATCH)
                .toList();
        if (stray.isEmpty()) {
            return;
        }
        String id = PREFIX + now;
        List<PlannedBlock> blocks = stray.stream()
                .map(cell -> new PlannedBlock(cell, StagePlans.AIR))
                .toList();
        BoardOutcome outcome = service.submit(
                new BoardTicket(
                        id,
                        BoardCase.DISMANTLE_SHELTER,
                        Optional.empty(),
                        BoardRules.priorityOf(BoardCase.DISMANTLE_SHELTER),
                        now,
                        1,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        Map.of()),
                TicketTarget.at(stray.get(0)).withWork(new StageWork(site, blocks)));
        if (outcome != BoardOutcome.ACCEPTED) {
            return;
        }
        LOGGER.info(
                "[plot] {}: снос {} блоков духов вне плана поселения, первые {}",
                id,
                stray.size(),
                stray.stream().limit(SHOWN).toList());
        service.journal()
                .recordEntry(BoardLog.note(
                        id,
                        BoardCase.DISMANTLE_SHELTER,
                        now,
                        List.of("blocks=" + stray.size(), "first=" + stray.get(0)),
                        null,
                        ConfigStamp.current()));
    }

    private static boolean busy(BoardService service, long now) {
        return service.board().all().stream()
                .filter(ticket -> ticket.id().startsWith(PREFIX))
                .anyMatch(ticket ->
                        OPEN.contains(service.board().statusOf(ticket.id()).orElse(BoardStatus.DONE))
                                || now - ticket.createdAt() < COOLDOWN);
    }

    private static boolean station(BlockState state) {
        return state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.FURNACE);
    }

    private static BlockPos pos(BlockCoord cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }
}
