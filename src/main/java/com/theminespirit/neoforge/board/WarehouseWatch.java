package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardPriorities;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.PriorityContext;
import com.theminespirit.core.board.SettlementStage;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.WarehouseRules;
import com.theminespirit.core.building.Preset;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.server.level.ServerLevel;

/**
 * Источник доски «склад»: следующий уровень ставится на новом месте, а после постройки старший
 * уровень порождает задачу переноса вещей и сноса прежнего. Не больше трёх складов не последнего
 * уровня — правило {@link WarehouseRules}; последний уровень не ограничен.
 */
public final class WarehouseWatch {
    private WarehouseWatch() {}

    public static void tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits, long now) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty()) {
            return;
        }
        // Склад — этап 2: пока строится этап 1 (куски ограды, части расчистки), он не открывается — его материалы
        // (книги, бумага) уводили духов с ограды (сцена 56).
        if (!houseBuilt(service) || FirstStepsRunner.stageOneBuilding(service)) {
            return;
        }
        BlockCoord home = homeOf(spirits);
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
                        materials(WarehouseRules.scheme(next))),
                TicketTarget.at(WarehouseRules.site(home, next)));
    }

    private static void submitRelocations(BoardService service, BlockCoord home, Set<Integer> standing, long now) {
        if (standing.isEmpty()) {
            return;
        }
        int highest = standing.stream().max(Integer::compareTo).orElseThrow();
        for (int old : WarehouseRules.relocateOldLevels(standing)) {
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
                    TicketTarget.at(WarehouseRules.site(home, highest))
                            .withRelocate(WarehouseRules.site(home, old), WarehouseRules.site(home, highest))
                            .withScheme(WarehouseRules.scheme(highest)));
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

    private static Map<String, Integer> materials(String scheme) {
        Optional<Preset> preset = DataTables.INSTANCE.presets().current().byId(scheme);
        if (preset.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> materials = new LinkedHashMap<>();
        preset.orElseThrow().materials().forEach((item, count) -> materials.merge(shortId(item), count, Integer::sum));
        return Map.copyOf(materials);
    }

    private static String shortId(String item) {
        return item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item;
    }

    private static BlockCoord homeOf(List<SpiritEntity> spirits) {
        for (SpiritEntity spirit : spirits) {
            Optional<BlockCoord> home = spirit.home();
            if (home.isPresent()) {
                return home.orElseThrow();
            }
        }
        return new BlockCoord(0, 64, 0);
    }
}
