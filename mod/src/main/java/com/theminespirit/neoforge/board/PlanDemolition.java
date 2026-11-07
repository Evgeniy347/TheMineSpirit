package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLog;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.ConfigStamp;
import com.theminespirit.core.board.DemolitionKey;
import com.theminespirit.core.board.DemolitionRetry;
import com.theminespirit.core.board.DemolitionScope;
import com.theminespirit.core.board.StagePlans;
import com.theminespirit.core.board.StateChangeLog;
import com.theminespirit.core.board.SubmitGate;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.FloatingBlocks;
import com.theminespirit.core.town.PlannedBlock;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.core.town.TownPlan;
import com.theminespirit.neoforge.UnreachableCellsStore;
import java.util.Comparator;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

final class PlanDemolition {
    static final String PREFIX = DemolitionKey.PREFIX;
    static final long COOLDOWN = 600;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SHOWN = 4;
    private static final SubmitGate SUBMITS = new SubmitGate();
    private static final StateChangeLog CHANGES = new StateChangeLog();
    private static final DemolitionRetry RETRIES = new DemolitionRetry();

    record Collected(
            List<BlockCoord> remove,
            List<String> notes,
            int excluded,
            int unreachable,
            BlockCoord stand,
            BlockCoord top,
            BlockCoord lowest,
            List<BlockCoord> treeCells) {}

    private PlanDemolition() {}

    static void tick(
            ServerLevel level,
            BoardService service,
            StageSite site,
            TownPlan town,
            long now,
            GraphPost post,
            String cause) {
        if (!service.board().ledger().has(BoardCase.CLEAR_GROUNDS, 1)) {
            return;
        }
        DemolitionScope scope = DemolitionScope.of(cause);
        List<BoardTicket> open = DemolitionHistory.open(service, scope);
        boolean lost = open.stream().anyMatch(ticket -> !service.hasTarget(ticket.id()));
        if (!lost && DemolitionHistory.busy(service, now, scope)) {
            return;
        }
        String world = level.dimension().location().toString();
        boolean yardReady = service.board().ledger().has(BoardCase.STOCK_CHESTS, 1)
                && service.board().ledger().has(BoardCase.CRAFT_FURNACE, 1);
        PlotPlanBlocks.Plan plan = PlotPlanBlocks.of(site);
        List<FloatingBlocks.Finding> findings = FloatingScanCycle.find(level, site, town.region());
        Collected base = DemolitionCollect.collect(
                new DemolitionCollect.Ctx(
                        level, service, site, plan, yardReady, findings, UnreachableCellsStore.of(level)),
                scope);
        Collected collected =
                scope == DemolitionScope.FLOATING ? base : FarmStray.withFarm(base, level, site, town, plan);
        List<BlockCoord> candidates = collected.treeCells().isEmpty() ? collected.remove() : collected.treeCells();
        List<BlockCoord> stray = collected.treeCells().isEmpty()
                ? DemolitionKey.piece(candidates.stream()
                        .distinct()
                        .sorted(Comparator.comparingInt(BlockCoord::y)
                                .reversed()
                                .thenComparing(Comparator.naturalOrder()))
                        .toList())
                : DemolitionKey.treePiece(
                        candidates.stream()
                                .distinct()
                                .sorted(Comparator.comparingInt(BlockCoord::y)
                                        .reversed()
                                        .thenComparing(Comparator.naturalOrder()))
                                .toList(),
                        collected.stand());
        List<PlannedBlock> blocks = stray.stream()
                .map(cell -> new PlannedBlock(cell, StagePlans.AIR))
                .toList();
        DemolitionTickets.Placement placement =
                new DemolitionTickets.Placement(collected.stand(), collected.top(), collected.lowest());
        if (lost
                && DemolitionRestore.restoreLost(
                        level, service, post, site, open, new DemolitionTickets.Batch(stray, blocks, placement))) {
            return;
        }
        if (stray.isEmpty()) {
            DemolitionTickets.warnExcluded(world, collected.excluded(), collected.notes(), now);
            return;
        }
        String id = DemolitionTickets.idFor(stray);
        if (waiting(world, id, collected, now)) {
            return;
        }
        if (!SUBMITS.allow("demolish:" + scope + ":" + world, RETRIES.signature(id))) {
            DemolitionTickets.warnExcluded(world, collected.excluded(), collected.notes(), now);
            if (CHANGES.changed("tidy-wait:" + world, id)) {
                LOGGER.info(
                        "[tidy] {}: набор уборки не изменился — повторно не выдаём (первый {}); {}",
                        id,
                        stray.get(0),
                        collected.notes());
            }
            return;
        }
        String previous = DemolitionHistory.previous(service, scope);
        if (!DemolitionTickets.submit(post, site, blocks, id, placement, cause)) {
            RETRIES.submitted(id, now, COOLDOWN);
            return;
        }
        RETRIES.submitted(id, now, COOLDOWN);
        DemolitionHistory.supersede(level, service, id, scope);
        if (CHANGES.changed("tidy:" + world, id)) {
            DemolitionTickets.logTidy(id, now, cause, stray, findings, collected, previous);
        }
        service.journal()
                .recordEntry(BoardLog.note(
                        id,
                        BoardCase.DISMANTLE_SHELTER,
                        now,
                        List.of("blocks=" + stray.size(), "first=" + stray.get(0)),
                        null,
                        ConfigStamp.current()));
    }

    static int shown() {
        return SHOWN;
    }

    private static boolean waiting(String world, String id, Collected collected, long now) {
        if (!RETRIES.waiting(id, now)) {
            return false;
        }
        DemolitionTickets.warnExcluded(world, collected.excluded(), collected.notes(), now);
        if (CHANGES.changed("tidy-retry:" + world, id + "@" + RETRIES.until(id))) {
            LOGGER.info(
                    "[tidy] {}: набор уборки ждёт повтора до тика {} — прошлая уборка закрылась без факта "
                            + "мира; {}",
                    id,
                    RETRIES.until(id),
                    collected.notes());
        }
        return true;
    }
}
