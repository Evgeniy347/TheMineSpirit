package com.theminespirit.core.board;

import com.theminespirit.core.reload.DataTables;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Постоянные задания доски: набор кейсов, которые источники держат на доске всегда,
 * и пороги запасов склада. Причина таких заданий — {@link BoardTicket#ALWAYS_CAUSE},
 * поэтому проверка актуальности их не снимает. Числа порогов — таблица board
 * ({@code supplies}); набор кейсов — код, новый постоянный кейс встаёт сюда явно.
 * Постоянный запас нужен только живому заданию или этапу поселения: книги и бумага ждут
 * первого дома (шахта теперь — одно из первых дел), полки — первого уровня чаровальни, иначе доска просит их на старте впустую.
 */
public final class EvergreenRules {
    private static final Map<String, SupplyStage> SUPPLY_STAGES = Map.of(
            "book", new SupplyStage(BoardCase.BUILD_HOUSE, 1),
            "paper", new SupplyStage(BoardCase.BUILD_HOUSE, 1),
            "bookshelf", new SupplyStage(BoardCase.ENCHANT_ROOM, 1));

    private EvergreenRules() {}

    public static Map<String, Integer> supplyThresholds() {
        return DataTables.INSTANCE.board().current().supplies();
    }

    public static boolean supplyNeeded(String item, Board board) {
        Objects.requireNonNull(item, "Предмет запаса не может быть null: без него непонятно, что держать");
        Objects.requireNonNull(board, "Доска не может быть null: этап и спрос читаются по её заданиям");
        String shortItem = shortId(item);
        for (BoardTicket ticket : board.all()) {
            BoardStatus status = board.statusOf(ticket.id()).orElse(null);
            if (status != BoardStatus.PENDING
                    && status != BoardStatus.WAITING_RESOURCES
                    && status != BoardStatus.ASSIGNED) {
                continue;
            }
            if (ticket.requiredItems().containsKey(shortItem)
                    || ticket.requiredItems().containsKey(item)) {
                return true;
            }
        }
        SupplyStage stage = SUPPLY_STAGES.get(shortItem);
        return stage == null || board.ledger().has(stage.boardCase(), stage.level());
    }

    private static String shortId(String item) {
        return item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item;
    }

    private record SupplyStage(BoardCase boardCase, int level) {}

    public static boolean issuedTo(BoardTicket ticket, UUID spirit, EatNeed eat, SpiritView view) {
        Objects.requireNonNull(ticket, "Задание выдачи не может быть null: без него непонятно, что выдаём");
        Objects.requireNonNull(spirit, "Дух выдачи не может быть null: выдача идёт конкретному духу");
        if (ticket.assignee().isPresent() && !ticket.assignee().orElseThrow().equals(spirit)) {
            return false;
        }
        return Applicability.applicable(ticket.boardCase(), eat, view);
    }
}
