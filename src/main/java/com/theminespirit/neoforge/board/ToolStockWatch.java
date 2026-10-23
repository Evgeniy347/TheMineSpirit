package com.theminespirit.neoforge.board;

import com.mojang.logging.LogUtils;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.BoardRules;
import com.theminespirit.core.board.BoardStatus;
import com.theminespirit.core.board.BoardTicket;
import com.theminespirit.core.board.MaterialSource;
import com.theminespirit.core.board.ResourceGate;
import com.theminespirit.core.board.StockNeed;
import com.theminespirit.core.board.StockView;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.ToolSet;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.neoforge.SpiritConfig;
import com.theminespirit.neoforge.SpiritEntity;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;

/**
 * Постоянный запас деревянного набора: первые шаги делают инструмент один раз ({@code first:*}
 * после DONE не переиздаётся), а сломанный или отданный инструмент доска восполняет сама — задание
 * {@code stock:<кейс>} на нехватку до набора на духа. Ставится только после шага первых шагов этого инструмента
 * (не спорит с цепочкой) и только когда дерева в запасе хватает (иначе невыполнимо — дух не получает его).
 */
public final class ToolStockWatch {
    public static final String PREFIX = "stock:";
    private static final Logger LOGGER = LogUtils.getLogger();

    private ToolStockWatch() {}

    public static void tick(
            ServerLevel level, BoardService service, List<SpiritEntity> spirits, StockView stock, long now) {
        if (!SpiritConfig.boardEnabled() || spirits.isEmpty()) {
            return;
        }
        Optional<BlockCoord> home = spirits.stream()
                .map(SpiritEntity::home)
                .flatMap(Optional::stream)
                .findFirst();
        if (home.isEmpty()) {
            return;
        }
        Map<BoardCase, Integer> shortages = ToolSet.woodenShortages(stock.available(), ToolSet.count(spirits.size()));
        MaterialSource source = null;
        for (Map.Entry<BoardCase, Integer> entry : shortages.entrySet()) {
            BoardCase caseValue = entry.getKey();
            String id = ticketId(caseValue);
            if (!service.board().ledger().has(caseValue, 1) || active(service, id)) {
                continue;
            }
            TicketTarget target = TicketTarget.at(home.orElseThrow()).withToolCount(1);
            if (source == null) {
                source = FirstStepsRunner.materialSource(level);
            }
            Map<String, Integer> missing =
                    ResourceGate.missing(new StockNeed(FirstStepsRunner.materials(source, caseValue, target)), stock);
            if (!missing.isEmpty()) {
                LOGGER.debug("Запас инструмента {} ждёт материалов: {}", caseValue, missing);
                continue;
            }
            LOGGER.info("Поставлено: восполнить {} (до набора на духа не хватает {})", caseValue, entry.getValue());
            service.submit(
                    new BoardTicket(
                            id,
                            caseValue,
                            Optional.empty(),
                            BoardRules.priorityOf(caseValue),
                            now,
                            1,
                            BoardTicket.ALWAYS_CAUSE,
                            List.of(),
                            Map.of()),
                    target);
        }
    }

    public static String ticketId(BoardCase caseValue) {
        return PREFIX + caseValue.name().toLowerCase(Locale.ROOT);
    }

    private static boolean active(BoardService service, String id) {
        BoardStatus status = service.board().statusOf(id).orElse(null);
        return status == BoardStatus.PENDING
                || status == BoardStatus.WAITING_RESOURCES
                || status == BoardStatus.ASSIGNED;
    }
}
