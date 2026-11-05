package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GatherConsolidationTests {
    private static final BlockCoord AT = new BlockCoord(0, 64, 0);

    private static GatherDemand.Need need(String id, String item, int count, int priority) {
        return new GatherDemand.Need(id, item, id, count, priority, AT);
    }

    @Test
    void standingStrippedLogAndFreshChop_FreshChopSubmitted_OldStrippedRemoved() {
        GatherConsolidation.Result result = GatherConsolidation.of(
                List.of(need("graph:strip_jungle_log", "stripped_jungle_log", 32, 95)),
                List.of(need("graph:chop_jungle_log", "jungle_log", 64, 80)));

        assertEquals(
                List.of("graph:chop_jungle_log"),
                result.submit().stream().map(GatherDemand.Need::id).toList(),
                "Стоящая заявка на ошкуренное бревно не должна мешать новой рубке: на породу ставится одна работа");
        assertTrue(
                result.suppressed().contains("graph:strip_jungle_log"),
                "Старая лесная заявка обязана сниматься с доски, а не висеть рядом с рубкой: " + result);
    }

    @Test
    void standingSaplingsAndFreshChop_FreshChopSubmitted_SaplingsWait() {
        GatherConsolidation.Result result = GatherConsolidation.of(
                List.of(need("graph:gather_jungle_sapling", "jungle_sapling", 6, 95)),
                List.of(need("graph:chop_jungle_log", "jungle_log", 64, 80)));

        assertEquals(
                List.of("jungle_log"),
                result.submit().stream().map(GatherDemand.Need::item).toList(),
                "Саженцы ждут, пока рубят брёвна той же породы: второй лесной работы на породу не ставят");
        assertTrue(
                result.suppressed().contains("graph:gather_jungle_sapling"),
                "Заявка на саженцы снимается со доски до конца рубки: " + result);
    }

    @Test
    void standingLogAndFreshPlanks_PlanksSubmitted_LogStays() {
        GatherConsolidation.Result result = GatherConsolidation.of(
                List.of(need("graph:chop_jungle_log", "jungle_log", 64, 80)),
                List.of(need("graph:craft_jungle_planks", "jungle_planks", 8, 90)));

        assertEquals(
                List.of("jungle_planks"),
                result.submit().stream().map(GatherDemand.Need::item).toList(),
                "Доски — отдельный крафт: новая заявка на доски ставится, даже если рубка брёвен уже стоит");
        assertTrue(
                result.suppressed().isEmpty(),
                "Доски и бревно одной породы живут вместе: не снимаем ни рубку, ни крафт: " + result);
    }

    @Test
    void standingLogAndFreshSapling_SaplingNotSubmitted_LogStays() {
        GatherConsolidation.Result result = GatherConsolidation.of(
                List.of(need("graph:chop_jungle_log", "jungle_log", 64, 80)),
                List.of(need("graph:gather_jungle_sapling", "jungle_sapling", 6, 95)));

        assertTrue(
                result.submit().isEmpty(),
                "Новая заявка на саженцы ждёт рубки, которая уже стоит: второй лесной работы не появляется");
        assertTrue(
                result.suppressed().contains("graph:gather_jungle_sapling"),
                "Новая заявка на саженцы снимается до конца рубки: " + result);
    }

    @Test
    void board_SupersededStandingLogAndSapling_RemovedByRetain() {
        String strip = "graph:strip_jungle_log";
        String sapling = "graph:gather_jungle_sapling";
        String chop = "graph:chop_jungle_log";
        Board board = new Board(() -> 0);
        board.submit(ticket(strip, 95));
        board.submit(ticket(sapling, 95));
        board.submit(ticket(chop, 80));
        GatherConsolidation.Result result = GatherConsolidation.of(
                List.of(need(strip, "stripped_jungle_log", 32, 95), need(sapling, "jungle_sapling", 6, 95)),
                List.of(need(chop, "jungle_log", 64, 80)));

        Set<String> live = new HashSet<>(Set.of(strip, sapling, chop));
        live.removeAll(result.suppressed());
        board.retain(live, 10);

        assertEquals(
                BoardStatus.CANCELLED,
                board.statusOf(strip).orElse(null),
                "Сквозь доску: старая заявка strip_* снимается, когда по той же породе стоит рубка");
        assertEquals(
                BoardStatus.CANCELLED,
                board.statusOf(sapling).orElse(null),
                "Сквозь доску: старая заявка на саженцы снимается, когда по той же породе стоит рубка");
        assertEquals(
                BoardStatus.PENDING,
                board.statusOf(chop).orElse(null),
                "Сквозь доску: заявка на брёвна остаётся живой и ждёт исполнителя");
    }

    private static BoardTicket ticket(String id, int priority) {
        return new BoardTicket(
                id, BoardCase.GATHER_MATERIALS, Optional.empty(), priority, 0, 1, id, List.of(), Map.of());
    }
}
