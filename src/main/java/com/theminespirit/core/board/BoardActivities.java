package com.theminespirit.core.board;

import com.theminespirit.core.intellect.ActivityId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Задания-занятия доски: всё, что дух делает не по заданию доски (фоновое занятие интеллекта, бой тактики,
 * задача REST/стратега), видно на доске одним заданием {@code activity:<дух>} в статусе ASSIGNED. Занятие
 * выдаёт доска (интеллект только предлагает), а снимает — сверка с фактом: дух бросил занятие или его
 * вытеснило задание доски. Занятие не считается в занятости духа для выдачи заданий и не хранится на диске:
 * это живой факт, а не очередь.
 */
public final class BoardActivities {
    public static final String ID_PREFIX = "activity:";
    public static final Set<BoardCase> CASES = EnumSet.of(
            BoardCase.ACTIVITY_CHOP,
            BoardCase.ACTIVITY_COMBAT,
            BoardCase.ACTIVITY_CRAFT,
            BoardCase.ACTIVITY_DEFEND,
            BoardCase.ACTIVITY_EXTERNAL,
            BoardCase.ACTIVITY_FARM,
            BoardCase.ACTIVITY_FORAGE,
            BoardCase.ACTIVITY_MINE,
            BoardCase.ACTIVITY_REST,
            BoardCase.ACTIVITY_SMELT,
            BoardCase.ACTIVITY_UNLOAD);

    private BoardActivities() {}

    public static boolean isActivity(BoardCase boardCase) {
        return CASES.contains(boardCase);
    }

    public static String idOf(UUID spirit) {
        Objects.requireNonNull(spirit, "Дух занятия не может быть null: у каждого духа одно задание-занятие");
        return ID_PREFIX + spirit;
    }

    public static BoardCase caseOf(ActivityId activity) {
        Objects.requireNonNull(activity, "Занятие интеллекта не может быть null: доска показывает его своим кейсом");
        return switch (activity) {
            case CHOP -> BoardCase.ACTIVITY_CHOP;
            case FARM -> BoardCase.ACTIVITY_FARM;
            case FORAGE -> BoardCase.ACTIVITY_FORAGE;
            case MINE -> BoardCase.ACTIVITY_MINE;
            case REST -> BoardCase.ACTIVITY_REST;
            case DEFEND -> BoardCase.ACTIVITY_DEFEND;
            case UNLOAD -> BoardCase.ACTIVITY_UNLOAD;
            case SMELT -> BoardCase.ACTIVITY_SMELT;
            case CRAFT -> BoardCase.ACTIVITY_CRAFT;
        };
    }

    static BoardTicket ticket(UUID spirit, BoardCase boardCase, long now) {
        if (!isActivity(boardCase)) {
            throw new IllegalArgumentException("Кейс " + boardCase + " — не занятие: задание-занятие строится только "
                    + "из ACTIVITY_*, иначе доска спутает его с очередью");
        }
        return new BoardTicket(
                idOf(spirit),
                boardCase,
                Optional.of(spirit),
                BoardRules.priorityOf(boardCase),
                Math.max(0, now),
                1,
                BoardTicket.ALWAYS_CAUSE,
                List.of(),
                Map.of());
    }
}
