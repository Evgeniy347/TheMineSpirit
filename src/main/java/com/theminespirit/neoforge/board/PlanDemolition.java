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
import com.theminespirit.core.town.FloatingBlocks;
import com.theminespirit.core.town.FloatingCleanup;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.StageWork;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.BlockStates;
import com.theminespirit.neoforge.SettlementChestStore;
import com.theminespirit.neoforge.policy.PlacedBlocksData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import org.slf4j.Logger;

final class PlanDemolition {
    static final String PREFIX = "demolish:";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long COOLDOWN = 600;
    private static final int BATCH = 48;
    private static final int SHOWN = 4;
    private static final int PROTECTED_SHOWN = 10;
    private static final Set<BoardStatus> OPEN =
            EnumSet.of(BoardStatus.PENDING, BoardStatus.WAITING_RESOURCES, BoardStatus.ASSIGNED);

    private PlanDemolition() {}

    static void tick(ServerLevel level, BoardService service, StageSite site, TownPlan town, long now) {
        if (!service.board().ledger().has(BoardCase.CLEAR_GROUNDS, 1) || busy(service, now)) {
            return;
        }
        boolean yardReady = service.board().ledger().has(BoardCase.STOCK_CHESTS, 1)
                && service.board().ledger().has(BoardCase.CRAFT_FURNACE, 1);
        PlotPlanBlocks.Plan plan = PlotPlanBlocks.of(site);
        List<FloatingBlocks.Finding> findings = FloatingScan.find(level, site, town.region());
        List<BlockCoord> remove = collect(level, service, site, plan, yardReady, findings);
        if (remove.isEmpty()) {
            return;
        }
        List<BlockCoord> stray = remove.stream()
                .sorted(Comparator.comparingInt(BlockCoord::y).reversed().thenComparing(Comparator.naturalOrder()))
                .limit(BATCH)
                .toList();
        String id = PREFIX + now;
        if (!submit(service, site, stray, now)) {
            return;
        }
        LOGGER.info(
                "[tidy] {}: уборка висящих объектов — {} из {}, первые {}",
                id,
                stray.size(),
                findings.size(),
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

    private static List<BlockCoord> collect(
            ServerLevel level,
            BoardService service,
            StageSite site,
            PlotPlanBlocks.Plan plan,
            boolean yardReady,
            List<FloatingBlocks.Finding> findings) {
        List<BlockCoord> remove = new ArrayList<>();
        int protectedSkipped = 0;
        for (FloatingBlocks.Finding finding : findings) {
            BlockCoord at = finding.at();
            if (finding.kind() == FloatingBlocks.Kind.FENCE && site.ringPost(at.x(), at.z())) {
                continue;
            }
            boolean needed = finding.kind() == FloatingBlocks.Kind.STATION && needed(level, plan, finding.blockId());
            FloatingCleanup.Action action = FloatingCleanup.decide(
                    finding, new FloatingCleanup.Rooms(yardReady, playerBuilding(level, at), empty(level, at), needed));
            switch (action) {
                case SKIP_PROTECTED -> {
                    if (protectedSkipped++ < PROTECTED_SHOWN) {
                        LOGGER.info(
                                "[tidy] {} — постройка игрока, не трогаем: {} ({})",
                                at,
                                finding.reason(),
                                finding.blockId());
                    }
                }
                case DRAIN_CHEST -> {
                    if (track(level, at)) {
                        LOGGER.info("[tidy] сундук {} вне плана не пуст — содержимое уйдёт в сундуки двора", at);
                    }
                }
                case RELOCATE_STATION -> {
                    reopen(service, finding.blockId());
                    LOGGER.info("[tidy] станция {} нужна плану — снимаем и ставим заново по схеме двора", at);
                    remove.add(at);
                }
                case REMOVE -> remove.add(at);
                case SKIP_REGISTERED, SKIP_WAIT -> {}
            }
        }
        return remove;
    }

    private static boolean submit(BoardService service, StageSite site, List<BlockCoord> stray, long now) {
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
        return outcome == BoardOutcome.ACCEPTED;
    }

    private static boolean playerBuilding(ServerLevel level, BlockCoord at) {
        PlacedBlocksData placed = PlacedBlocksData.get(level);
        BlockPos pos = pos(at);
        return placed.isMarked(pos) && !placed.isSpiritMarked(pos);
    }

    private static boolean empty(ServerLevel level, BlockCoord at) {
        return !(level.getBlockEntity(pos(at)) instanceof Container container) || container.isEmpty();
    }

    private static boolean track(ServerLevel level, BlockCoord at) {
        return SettlementChestStore.get(level).track(pos(at));
    }

    private static boolean needed(ServerLevel level, PlotPlanBlocks.Plan plan, String blockId) {
        List<BlockCoord> expected = plan.expected(blockId);
        if (expected.isEmpty()) {
            return false;
        }
        String path = FloatingBlocks.pathOf(blockId);
        return expected.stream()
                .noneMatch(at -> path.equals(FloatingBlocks.pathOf(BlockStates.idOf(level.getBlockState(pos(at))))));
    }

    private static void reopen(BoardService service, String blockId) {
        BoardCase wanted =
                FloatingBlocks.pathOf(blockId).equals("furnace") ? BoardCase.CRAFT_FURNACE : BoardCase.STOCK_CHESTS;
        for (BoardTicket ticket : List.copyOf(service.board().all())) {
            if (ticket.boardCase() != wanted) {
                continue;
            }
            if (service.board().statusOf(ticket.id()).orElse(null) == BoardStatus.DONE) {
                service.reopen(ticket.id());
                LOGGER.info("[tidy] задание {} возвращено в доску: станция встанет по плану", ticket.id());
            }
        }
    }

    private static boolean busy(BoardService service, long now) {
        return service.board().all().stream()
                .filter(ticket -> ticket.id().startsWith(PREFIX))
                .anyMatch(ticket ->
                        OPEN.contains(service.board().statusOf(ticket.id()).orElse(BoardStatus.DONE))
                                || now - ticket.createdAt() < COOLDOWN);
    }

    private static BlockPos pos(BlockCoord cell) {
        return new BlockPos(cell.x(), cell.y(), cell.z());
    }
}
