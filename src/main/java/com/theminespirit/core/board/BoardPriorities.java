package com.theminespirit.core.board;

import com.theminespirit.core.reload.DataTables;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Эффективный приоритет задания: база кейса из таблицы плюс дельты сработавших
 * модификаторов ситуации, с зажимом в [0, MAX_PRIORITY]. Все числа — из таблицы
 * board через DataTables; здесь только значения на случай отсутствия таблицы.
 */
public final class BoardPriorities {
    public static final String PEN_CAUGHT_ANIMAL = "pen_caught_animal";
    public static final String PEN_MISSING_BAIT = "pen_missing_bait";
    public static final String PEN_SPECIES_FULL = "pen_species_full";
    public static final String LURE_BAIT_READY = "lure_bait_ready";
    public static final String RELOCATE_BUSY = "relocate_busy";

    public static final Set<String> MODIFIER_KEYS =
            Set.of(PEN_CAUGHT_ANIMAL, PEN_MISSING_BAIT, PEN_SPECIES_FULL, LURE_BAIT_READY, RELOCATE_BUSY);

    private static final Map<String, Integer> DEFAULT_MODIFIERS = Map.of(
            PEN_CAUGHT_ANIMAL, 15,
            PEN_MISSING_BAIT, 10,
            PEN_SPECIES_FULL, -65,
            LURE_BAIT_READY, 10,
            RELOCATE_BUSY, -20);

    private BoardPriorities() {}

    static String modifierKey(String key) {
        if (!MODIFIER_KEYS.contains(key)) {
            throw new IllegalArgumentException("Неизвестный модификатор '" + key + "': допустимы " + MODIFIER_KEYS
                    + ", сверьтесь с таблицей модификаторов board.json");
        }
        return key;
    }

    public static int effective(BoardCase boardCase, PriorityContext context) {
        if (boardCase == null) {
            throw new IllegalArgumentException("Приоритет кейса не задан: доска не решит, что важнее, без кейса");
        }
        if (context == null) {
            throw new IllegalArgumentException("Ситуация не может быть null: модификаторы считаются по флагам, "
                    + "пустую ситуацию задают PriorityContext.none()");
        }
        int total = BoardRules.priorityOf(boardCase);
        for (String modifier : applied(boardCase, context)) {
            total += delta(modifier);
        }
        return Math.min(BoardTuning.MAX_PRIORITY, Math.max(0, total));
    }

    public static List<String> applied(BoardCase boardCase, PriorityContext context) {
        if (boardCase == null || context == null) {
            throw new IllegalArgumentException(
                    "Кейс и ситуация обязаны быть заданы: по ним видно, какие модификаторы сработали");
        }
        List<String> fired = new ArrayList<>(2);
        if (boardCase == BoardCase.BUILD_PEN) {
            if (context.caughtAnimal() && !context.penBuilt()) {
                fired.add(PEN_CAUGHT_ANIMAL);
            }
            if (context.baitMissing()) {
                fired.add(PEN_MISSING_BAIT);
            }
            if (context.speciesFull()) {
                fired.add(PEN_SPECIES_FULL);
            }
        }
        if (boardCase == BoardCase.LURE_ANIMAL && context.penBuilt() && !context.baitMissing()) {
            fired.add(LURE_BAIT_READY);
        }
        if (boardCase == BoardCase.RELOCATE_BUILDING && context.activeRepair()) {
            fired.add(RELOCATE_BUSY);
        }
        return List.copyOf(fired);
    }

    private static int delta(String modifier) {
        Integer value = DataTables.INSTANCE.board().current().modifiers().get(modifier);
        if (value != null) {
            return value;
        }
        Integer fallback = DEFAULT_MODIFIERS.get(modifier);
        if (fallback == null) {
            throw new IllegalArgumentException("Модификатор приоритета '" + modifier + "' неизвестен: его нет ни в "
                    + "таблице board, ни в умолчаниях — проверьте имя модификатора в правилах доски");
        }
        return fallback;
    }
}
