package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.mining.LeftOreRules;
import com.theminespirit.core.observation.BlockCoord;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupplyGateTests {

    private static final long BACKOFF = BoardTuning.FAIL_BACKOFF_TICKS;

    @Test
    void merge_GrownDemand_KeepsSingleRequestWithNewCount() {
        TicketTarget before = TicketTarget.at(new BlockCoord(0, 64, 0))
                .withSupply("cobblestone", 4)
                .withDeposit(new BlockCoord(1, 64, 0));
        TicketTarget next = TicketTarget.at(new BlockCoord(0, 64, 0)).withSupply("cobblestone", 12);

        TicketTarget merged = SupplyGate.merge(before, next);

        assertEquals(
                12,
                merged.supplyCount(),
                "Повторная заявка обязана поднять количество, а не остаться старой: "
                        + "иначе дух несёт 4 камня при нехватке 12 и доска ставит заявку снова и снова");
        assertEquals(
                new BlockCoord(1, 64, 0),
                merged.deposit(),
                "Точка сдачи не теряется, если в новой заявке её не нашли: консолидация не ломает маршрут");
    }

    @Test
    void merge_SmallerDemand_KeepsExistingRequest() {
        TicketTarget before = TicketTarget.at(new BlockCoord(0, 64, 0)).withSupply("cobblestone", 12);
        TicketTarget next = TicketTarget.at(new BlockCoord(9, 64, 9)).withSupply("cobblestone", 4);

        assertEquals(
                before,
                SupplyGate.merge(before, next),
                "Меньшая заявка не откатывает уже заказанное количество: иначе снабжение топчется на месте");
    }

    @Test
    void isSupply_SupplyPrefixes_OnlySupplyTickets() {
        assertTrue(SupplyGate.isSupply("supply:cobblestone"), "Снабжение со склада — заявка снабжения");
        assertTrue(SupplyGate.isSupply("supply_gather:coal"), "Сбор на склад — та же заявка снабжения");
        assertFalse(SupplyGate.isSupply("graph:make:coal"), "Графовая работа не сливается как снабжение");
    }

    @Test
    void needsDepositPoint_GatherMaterials_StartsWithoutContainer() {
        assertFalse(
                SupplyGate.needsDepositPoint(BoardCase.GATHER_MATERIALS),
                "Добыча не сдаёт сразу и умеет идти без точки сдачи: при пустом складе задание всё равно "
                        + "выходит на доску, а не ждёт сундук бесконечно");
    }

    @Test
    void needsDepositPoint_SupplySite_RequiresContainer() {
        assertTrue(
                SupplyGate.needsDepositPoint(BoardCase.SUPPLY_SITE),
                "Снабжение со склада без контейнера сдавать некуда: задание не ставится, доска ставит сундук");
    }

    @Test
    void decide_StockInsideMinimum_GathersInsteadOfSupply() {
        SupplyGate.Decision decision =
                SupplyGate.decide(Map.of("dirt", 59), Map.of("dirt", 64), 64, Map.of(), 0, BACKOFF);

        assertTrue(
                decision.supply().isEmpty(),
                "Снабжение со склада не ставится, когда весь запас держится минимумом: дух шёл бы к сундуку, "
                        + "брал 0 и закрывал задание без доставки, а нехватка оставалась бы");
        assertEquals(
                List.of(new SupplyGate.Request("dirt", 59)),
                decision.gather(),
                "Нехватку сверх неприкосновенного минимума закрывает добыча, а не снабжение со склада");
    }

    @Test
    void decide_StockAboveMinimum_SuppliesFromStock() {
        SupplyGate.Decision decision =
                SupplyGate.decide(Map.of("cobblestone", 141), Map.of("cobblestone", 160), 64, Map.of(), 0, BACKOFF);

        assertEquals(
                List.of(new SupplyGate.Request("cobblestone", 141)),
                decision.supply(),
                "Материал выше минимума берётся со склада: снабжение разрешено");
        assertTrue(decision.gather().isEmpty(), "Добыча не нужна, пока склад отдаёт материал");
    }

    @Test
    void decide_RecentlyClosedSupply_WaitsForBackoff() {
        Map<String, Long> closedAt = Map.of("supply:cobblestone", 100L);

        SupplyGate.Decision waiting =
                SupplyGate.decide(Map.of("cobblestone", 141), Map.of("cobblestone", 96), 64, closedAt, 150, BACKOFF);
        assertTrue(
                waiting.isEmpty(),
                "Снабжение, только что закрытое без доставки, не пересоздаётся сразу: доска не выдаёт "
                        + "его каждую пересборку");

        SupplyGate.Decision after = SupplyGate.decide(
                Map.of("cobblestone", 141), Map.of("cobblestone", 96), 64, closedAt, 100 + BACKOFF, BACKOFF);
        assertEquals(
                List.of(new SupplyGate.Request("cobblestone", 141)),
                after.supply(),
                "После отката снабжение ставится снова: пауза не запрещает задание навсегда");
    }

    @Test
    void retains_ToolTierSupply_False() {
        BoardTicket stale = new BoardTicket(
                "supply_gather:pickaxe_luck",
                BoardCase.GATHER_MATERIALS,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.GATHER_MATERIALS),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());

        assertFalse(
                SupplyGate.retains(stale, Set.of()),
                "Старое задание добычи маркера кирки снимается с доски, а не живёт вечно по причине always");
    }

    @Test
    void retains_OrdinaryAlwaysTicket_True() {
        BoardTicket ordinary = new BoardTicket(
                "clear:2",
                BoardCase.CLEAR_GROUNDS,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.CLEAR_GROUNDS),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());

        assertTrue(
                SupplyGate.retains(ordinary, Set.of()),
                "Обычное задание с причиной always остаётся на доске: фильтр трогает только маркеры кирки");
    }

    @Test
    void isToolTierSupply_SupplyTicketWithMarker_True() {
        assertTrue(
                SupplyGate.isToolTierSupply("supply_gather:pickaxe_luck"),
                "Старый тикет добычи маркера кирки обязан сниматься с доски: предмета 'pickaxe_luck' не существует");
        assertTrue(
                SupplyGate.isToolTierSupply("supply:pickaxe_stone_tier"),
                "Снабжение маркером кирки — та же ошибка: его тоже снимают");
    }

    @Test
    void isToolTierSupply_OtherTickets_False() {
        assertFalse(
                SupplyGate.isToolTierSupply("supply_gather:dirt"),
                "Обычная добыча не снимается: фильтр трогает только маркеры инструмента");
        assertFalse(
                SupplyGate.isToolTierSupply("left_ore:-188,-2,-448"),
                "Тикет оставленной руды не supply: его гейт по маркеру кирки сохраняется");
    }

    @Test
    void decide_ToolTierMarker_NotGatheredAsItem() {
        SupplyGate.Decision decision = SupplyGate.decide(
                Map.of(LeftOreRules.LUCK_PICKAXE, 1, LeftOreRules.STONE_TIER, 1, "dirt", 2),
                Map.of(),
                0,
                Map.of(),
                0,
                BACKOFF);

        assertTrue(
                decision.supply().isEmpty(),
                "Маркер кирки — требование инструмента, а не предмет склада: снабжение по нему не ставится");
        assertEquals(
                List.of(new SupplyGate.Request("dirt", 2)),
                decision.gather(),
                "Добыча маркера кирки даёт «не найден способ добычи pickaxe_*»: нехватка инструмента ждёт "
                        + "подходящую кирку, а обычные материалы добываются как раньше");
    }

    @Test
    void decide_GatherHeldByMinimum_StillGathers() {
        SupplyGate.Decision decision =
                SupplyGate.decide(Map.of("dirt", 59), Map.of("dirt", 64), 64, Map.of("supply:dirt", 0L), 1, BACKOFF);

        assertEquals(
                List.of(new SupplyGate.Request("dirt", 59)),
                decision.gather(),
                "Минимум не даёт взять материал со склада: добычу нельзя откладывать из-за отката снабжения");
    }

    @Test
    void hasActiveSupply_OpenOrAssigned_True() {
        Board board = new Board(() -> 0L);
        BoardTicket pending = supplyTicket("supply:cobblestone");
        board.submit(pending);
        Map<String, TicketTarget> targets = new HashMap<>();
        targets.put(pending.id(), TicketTarget.at(SITE).withSupply("cobblestone", 4));

        assertTrue(
                SupplyGate.hasActiveSupply(board, targets, "cobblestone"),
                "Открытая заявка снабжения держит предмет: иначе доска ставит вторую такую же каждый пересбор, "
                        + "и духи носят один материал двумя маршрутами");

        board.claim(pending.id(), SPIRIT, 100);
        assertTrue(
                SupplyGate.hasActiveSupply(board, targets, "cobblestone"),
                "Выданная духу заявка тоже активна: пока её несут, новую на тот же предмет не ставят");
    }

    @Test
    void hasActiveSupply_ClosedOrForeign_False() {
        Board board = new Board(() -> 0L);
        BoardTicket pending = supplyTicket("supply:cobblestone");
        board.submit(pending);
        Map<String, TicketTarget> targets = new HashMap<>();
        targets.put(pending.id(), TicketTarget.at(SITE).withSupply("cobblestone", 4));

        board.cancel(pending.id());
        assertFalse(
                SupplyGate.hasActiveSupply(board, targets, "cobblestone"),
                "Закрытая заявка не держит предмет: иначе нехватка после отмены не закрывалась бы никогда");
    }

    @Test
    void hasActiveSupply_OtherItemOrNonSupplyTicket_False() {
        Board board = new Board(() -> 0L);
        BoardTicket coal = supplyTicket("supply:coal");
        BoardTicket graph = new BoardTicket(
                "graph:make:cobblestone",
                BoardCase.GATHER_MATERIALS,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.GATHER_MATERIALS),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
        BoardTicket withoutTarget = supplyTicket("supply:dirt");
        board.submit(coal);
        board.submit(graph);
        board.submit(withoutTarget);
        Map<String, TicketTarget> targets = new HashMap<>();
        targets.put(coal.id(), TicketTarget.at(SITE).withSupply("coal", 4));
        targets.put(graph.id(), TicketTarget.at(SITE).withSupply("cobblestone", 4));

        assertFalse(
                SupplyGate.hasActiveSupply(board, targets, "cobblestone"),
                "Заявка на уголь не держит камень, а графовая работа — не заявка снабжения: иначе камень "
                        + "ждал бы чужого задания");
        assertFalse(
                SupplyGate.hasActiveSupply(board, targets, "dirt"),
                "Заявка без цели-предмета не держит ничего: сравнивать нечего, нужды в добыче это не отменяет");
    }

    private static final BlockCoord SITE = new BlockCoord(0, 64, 0);
    private static final UUID SPIRIT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static BoardTicket supplyTicket(String id) {
        return new BoardTicket(
                id,
                BoardCase.SUPPLY_SITE,
                Optional.empty(),
                BoardRules.priorityOf(BoardCase.SUPPLY_SITE),
                0,
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }
}
