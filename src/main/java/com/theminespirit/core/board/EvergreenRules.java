package com.theminespirit.core.board;

import com.theminespirit.core.reload.DataTables;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Постоянные задания доски: набор кейсов, которые источники держат на доске всегда,
 * и пороги запасов склада. Причина таких заданий — {@link BoardTicket#ALWAYS_CAUSE},
 * поэтому проверка актуальности их не снимает. Числа порогов — таблица board
 * ({@code supplies}); набор кейсов — код, новый постоянный кейс встаёт сюда явно.
 */
public final class EvergreenRules {
    public static final Set<BoardCase> PERMANENT = Set.of(
            BoardCase.EAT_TO_FULL,
            BoardCase.RESTOCK_FOOD,
            BoardCase.REPAIR_FENCE,
            BoardCase.REPAIR_BUILDING,
            BoardCase.FILL_CRATER,
            BoardCase.ENCHANT_SHELVES);

    private EvergreenRules() {
    }

    public static boolean permanent(BoardCase boardCase) {
        Objects.requireNonNull(boardCase, "Кейс постоянного набора не может быть null: без него "
                + "непонятно, всегда ли задание висит на доске");
        return PERMANENT.contains(boardCase);
    }

    public static Set<BoardCase> permanentCases() {
        return PERMANENT;
    }

    public static Map<String, Integer> supplyThresholds() {
        return DataTables.INSTANCE.board().current().supplies();
    }

    public static int supplyThreshold(String item) {
        Objects.requireNonNull(item, "Предмет порога не может быть null: без него неизвестно, "
                + "сколько держать на складе");
        return supplyThresholds().getOrDefault(item, 0);
    }

    public static int supplyShortfall(String item, int have) {
        if (have < 0) {
            throw new IllegalArgumentException("Наличие " + have + " отрицательное: склад не бывает "
                    + "в минусе, отрицательное число прятало бы перебор");
        }
        return Math.max(0, supplyThreshold(item) - have);
    }

    public static boolean issuedTo(BoardTicket ticket, UUID spirit, EatNeed eat, SpiritView view) {
        Objects.requireNonNull(ticket, "Задание выдачи не может быть null: без него непонятно, что выдаём");
        Objects.requireNonNull(spirit, "Дух выдачи не может быть null: выдача идёт конкретному духу");
        if (ticket.assignee().isPresent() && !ticket.assignee().orElseThrow().equals(spirit)) {
            return false;
        }
        return Applicability.applicable(ticket.boardCase(), eat, view);
    }
}
