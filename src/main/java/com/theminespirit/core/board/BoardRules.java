package com.theminespirit.core.board;

import com.theminespirit.core.reload.DataTables;
import java.util.EnumMap;
import java.util.Map;

/**
 * Приоритеты кейсов — таблица данных, а не ветка в коде: порядок «важнее» читается
 * из одной строки на кейс. Источник истины для работающего мода — JSON-таблица
 * через DataTables (правится датапаком и перезагружается по /reload); здесь значения
 * по умолчанию. Кейс без строки — сирота: доска не решит, что важнее, поэтому
 * обращение падает, а не проваливается молча.
 */
public final class BoardRules {
    private static final Map<BoardCase, Integer> PRIORITIES = buildPriorities();

    private BoardRules() {
    }

    public static int priorityOf(BoardCase boardCase) {
        if (boardCase == null) {
            throw new IllegalArgumentException(
                    "Приоритет кейса не задан: доска не решит, что важнее, без кейса");
        }
        Integer priority = DataTables.INSTANCE.board().current().priorities().get(boardCase.name());
        if (priority != null) {
            return priority;
        }
        Integer fallback = PRIORITIES.get(boardCase);
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalArgumentException("Приоритет кейса не задан: " + boardCase
                + " отсутствует в таблице приоритетов — добавьте строку в BoardRules");
    }

    private static Map<BoardCase, Integer> buildPriorities() {
        Map<BoardCase, Integer> priorities = new EnumMap<>(BoardCase.class);
        priorities.put(BoardCase.REPAIR_FENCE, 100);
        priorities.put(BoardCase.REPAIR_BUILDING, 90);
        priorities.put(BoardCase.EAT_TO_FULL, 80);
        priorities.put(BoardCase.CAPTURE_ANIMAL, 75);
        priorities.put(BoardCase.RESTOCK_FOOD, 70);
        priorities.put(BoardCase.BUILD_PEN, 65);
        priorities.put(BoardCase.LURE_ANIMAL, 65);
        priorities.put(BoardCase.DISMANTLE_SHELTER, 65);
        priorities.put(BoardCase.RELOCATE_BUILDING, 60);
        priorities.put(BoardCase.BREED_ANIMALS, 55);
        priorities.put(BoardCase.HERD_CULL, 50);
        priorities.put(BoardCase.COLLECT_DROPS, 55);
        priorities.put(BoardCase.CLEAR_GROUNDS, 50);
        priorities.put(BoardCase.BUILD_FENCE, 50);
        priorities.put(BoardCase.LIGHTING, 50);
        priorities.put(BoardCase.BUILD_HOUSE, 50);
        priorities.put(BoardCase.BUILD_MINE, 50);
        priorities.put(BoardCase.ENCHANT_ROOM, 50);
        priorities.put(BoardCase.SUPPLY_SITE, 40);
        priorities.put(BoardCase.CRAFT_TOOLS, 40);
        priorities.put(BoardCase.CRAFTING_STATION, 40);
        priorities.put(BoardCase.STOCK_CHESTS, 40);
        priorities.put(BoardCase.FILL_CRATER, 35);
        priorities.put(BoardCase.ENCHANT_SHELVES, 30);
        return Map.copyOf(priorities);
    }
}
