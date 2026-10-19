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
 */
public final class EvergreenRules {
    private EvergreenRules() {
    }

    public static Map<String, Integer> supplyThresholds() {
        return DataTables.INSTANCE.board().current().supplies();
    }

    /**
     * Решение о выдаче адресному духу: задание без владельца доступно любому применимому,
     * адресное — только своему. Неприменимый владелец не получает задание, но оно остаётся
     * доступным другому, если владельца нет.
     */
    public static boolean issuedTo(BoardTicket ticket, UUID spirit, EatNeed eat, SpiritView view) {
        Objects.requireNonNull(ticket, "Задание выдачи не может быть null: без него непонятно, что выдаём");
        Objects.requireNonNull(spirit, "Дух выдачи не может быть null: выдача идёт конкретному духу");
        if (ticket.assignee().isPresent() && !ticket.assignee().orElseThrow().equals(spirit)) {
            return false;
        }
        return Applicability.applicable(ticket.boardCase(), eat, view);
    }
}
