package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardLedger;
import com.theminespirit.core.board.BoardPriorities;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.BuildingLevels;
import com.theminespirit.core.board.MineDescentBottom;
import com.theminespirit.core.board.PriorityContext;
import com.theminespirit.core.board.SettlementPlot;
import com.theminespirit.core.board.SettlementStage;
import com.theminespirit.core.board.TicketPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

/**
 * Уровни шахты: следующий выдаётся после выполненного предыдущего и держится гейтом
 * материалов. Первый уровень ставит цепочка первых шагов; здесь — расширение, ступеньки
 * и рельсы, чтобы уровень не прыгал через непостроенный.
 */
final class MineLevels {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<String> STAGE_ONE_PREFIXES = List.of("first:", "fence:", "clear:");
    // Уровень 2 — только выработка (dig_area): материал не расходует, булыжник даёт сам. Гейт «stone ×32»
    // ставил supply:stone, а камень (не булыжник) добычей не получить — цикл FAILED→POSTED (INC-28).
    // Уровень 3 кладёт пол из булыжника 7×7 (fill в TicketPlans) — гейт на то, что он тратит.
    private static final Map<Integer, Map<String, Integer>> LEVEL_MATERIALS = Map.of(
            3, Map.of("cobblestone", 49),
            4, Map.of("iron_ingot", 16));

    private MineLevels() {}

    static void tick(ServerLevel level, BoardService service, List<SpiritEntity> spirits, long now) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty()) {
            return;
        }
        OptionalInt next =
                BuildingLevels.nextLevel(BoardCase.BUILD_MINE, service.board().ledger());
        if (next.isEmpty() || next.getAsInt() < 2) {
            return;
        }
        int levelValue = next.getAsInt();
        if (!SettlementStage.allows(
                BoardCase.BUILD_MINE, levelValue, service.board().ledger())) {
            return;
        }
        // Этап 1 ещё строится (куски ограды, части расчистки): отметку журнала ставит первый кусок, и выработка
        // уровня 2 начиналась посреди этапа 1 и рыла яму у спуска в участке (сцена 56).
        boolean stageOneBusy = service.board().all().stream()
                .anyMatch(ticket ->
                        STAGE_ONE_PREFIXES.stream().anyMatch(ticket.id()::startsWith) && active(service, ticket.id()));
        if (stageOneBusy) {
            return;
        }
        String id = "mine_level_" + levelValue;
        if (active(service, id)) {
            return;
        }
        // Спуск стоит от якоря участка (FirstStepsRunner), а не от дома духа: дом на прежней вершине холма на 4 выше
        // участка, низ спуска считался выше настоящего, и выработка уровня 2 рыла яму в участке (сцена 56).
        BlockCoord home = SettlementPlotStore.get(level)
                .plot()
                .map(SettlementPlot::anchor)
                .orElseGet(() -> homeOf(spirits));
        BlockCoord site;
        try {
            site = MineDescentBottom.standOf(home).orElse(null);
        } catch (IllegalStateException | IllegalArgumentException unbuildable) {
            LOGGER.warn(
                    "Уровень шахты {} не поставлен: низ спуска не считается от дома {}: {}",
                    levelValue,
                    home,
                    unbuildable.getMessage());
            return;
        }
        if (site == null) {
            LOGGER.warn(
                    "Уровень шахты {} не поставлен: нет схемы спуска {}", levelValue, TicketPlans.MINE_DESCENT_SCHEME);
            return;
        }
        service.submit(
                new BoardTicket(
                        id,
                        BoardCase.BUILD_MINE,
                        Optional.empty(),
                        BoardPriorities.effective(BoardCase.BUILD_MINE, PriorityContext.none()),
                        now,
                        levelValue,
                        BoardTicket.ALWAYS_CAUSE,
                        List.of(BoardLedger.key(BoardCase.BUILD_MINE, levelValue - 1)),
                        LEVEL_MATERIALS.getOrDefault(levelValue, Map.of())),
                TicketTarget.at(site));
    }

    private static boolean active(BoardService service, String id) {
        BoardStatus status = service.board().statusOf(id).orElse(null);
        return status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED;
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
