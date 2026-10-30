package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardPriorities;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.PriorityContext;
import com.theminespirit.core.board.SettlementStage;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.UpgradePlan;
import com.theminespirit.core.board.WarehouseRules;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.StageSite;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.server.level.ServerLevel;

public final class WarehouseWatch {
    private static final String STORAGE = "storage";

    private WarehouseWatch() {}

    public static void tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits, long now) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty()) {
            return;
        }
        if (!houseBuilt(service) || FirstStepsRunner.stageOneBuilding(service)) {
            return;
        }
        BlockCoord home = storageLot(level).orElseGet(() -> homeOf(spirits));
        Set<Integer> standing = doneLevels(service);
        submitRelocations(service, home, standing, now);
        int next = WarehouseRules.nextLevel(standing);
        if (next == 0
                || !WarehouseRules.canBuild(next, standing)
                || !SettlementStage.allows(
                        BoardCase.BUILD_WAREHOUSE, next, service.board().ledger())) {
            return;
        }
        String id = WarehouseRules.buildTicketId(next);
        if (service.board().find(id).isPresent() || active(service, id)) {
            return;
        }
        service.submit(
                new BoardTicket(
                        id,
                        BoardCase.BUILD_WAREHOUSE,
                        Optional.empty(),
                        BoardPriorities.effective(BoardCase.BUILD_WAREHOUSE, PriorityContext.none()),
                        now,
                        next,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(),
                        UpgradePlan.required(
                                next > 1 ? WarehouseRules.scheme(next - 1) : null, WarehouseRules.scheme(next))),
                TicketTarget.at(WarehouseRules.site(home, next)));
    }

    private static void submitRelocations(BoardService service, BlockCoord home, Set<Integer> standing, long now) {
        if (standing.isEmpty()) {
            return;
        }
        int highest = standing.stream().max(Integer::compareTo).orElseThrow();
        for (int old : WarehouseRules.relocateOldLevels(standing)) {
            BlockCoord from = WarehouseRules.site(home, old);
            BlockCoord to = WarehouseRules.site(home, highest);
            if (from.equals(to)) {
                continue;
            }
            String id = WarehouseRules.relocateTicketId(old);
            if (service.board().find(id).isPresent() || active(service, id)) {
                continue;
            }
            service.submit(
                    new BoardTicket(
                            id,
                            BoardCase.RELOCATE_BUILDING,
                            Optional.empty(),
                            BoardPriorities.effective(BoardCase.RELOCATE_BUILDING, PriorityContext.none()),
                            now,
                            1,
                            "warehouse:level:" + old,
                            List.of(),
                            Map.of()),
                    TicketTarget.at(to).withRelocate(from, to).withScheme(WarehouseRules.scheme(highest)));
        }
    }

    private static boolean houseBuilt(BoardService service) {
        for (BoardTicket ticket : service.board().all()) {
            if (ticket.boardCase() == BoardCase.BUILD_HOUSE
                    && service.board().statusOf(ticket.id()).orElse(null) == BoardStatus.DONE) {
                return true;
            }
        }
        return false;
    }

    private static Set<Integer> doneLevels(BoardService service) {
        Set<Integer> done = new TreeSet<>();
        for (BoardTicket ticket : service.board().all()) {
            if (ticket.boardCase() != BoardCase.BUILD_WAREHOUSE) {
                continue;
            }
            if (service.board().statusOf(ticket.id()).orElse(null) == BoardStatus.DONE) {
                done.add(ticket.level());
            }
        }
        return done;
    }

    private static boolean active(BoardService service, String id) {
        BoardStatus status = service.board().statusOf(id).orElse(null);
        return status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED;
    }

    private static BlockCoord homeOf(List<SpiritEntity> spirits) {
        return spirits.stream()
                .flatMap(spirit -> spirit.home().stream())
                .findFirst()
                .orElse(new BlockCoord(0, 64, 0));
    }

    private static Optional<BlockCoord> storageLot(ServerLevel level) {
        SettlementPlotStore store = SettlementPlotStore.get(level);
        Optional<Placement> storage = store.town().flatMap(town -> town.placement(STORAGE));
        Optional<StageSite> site = store.site();
        if (storage.isEmpty() || site.isEmpty()) {
            return Optional.empty();
        }
        Placement place = storage.orElseThrow();
        return Optional.of(new BlockCoord(place.x(), site.orElseThrow().anchor().y(), place.z()));
    }
}
