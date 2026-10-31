package com.theminespirit.neoforge.board;

import com.theminespirit.core.board.Board;
import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.DispatchRound;
import com.theminespirit.core.board.GroundsPlan;
import com.theminespirit.core.board.SettlementStage;
import com.theminespirit.core.board.TicketPlans;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

final class StageReopen {
    static final List<BoardCase> STEPS = List.of(
            BoardCase.CRAFTING_STATION,
            BoardCase.CLEAR_GROUNDS,
            BoardCase.STOCK_CHESTS,
            BoardCase.BUILD_MINE,
            BoardCase.CRAFT_FURNACE,
            BoardCase.LIGHTING,
            BoardCase.BUILD_FENCE,
            BoardCase.BUILD_HOUSE,
            BoardCase.BUILD_ORCHARD,
            BoardCase.BUILD_STREETS,
            BoardCase.CRAFT_SMITHING_TABLE,
            BoardCase.CRAFT_ANVIL);
    private static final String FIRST_PREFIX = "first:";
    private static final String CLEAR_PART_PREFIX = "clear:";
    private static final String FENCE_PART_PREFIX = "fence:";

    private StageReopen() {}

    static List<BoardCase> missing(Board board, List<BoardCase> order, int limit) {
        Objects.requireNonNull(board, "Доска не может быть null: шаги этапа переоткрываются по ней");
        Objects.requireNonNull(order, "Порядок шагов этапа не может быть null: без него не решить, что открыть первым");
        if (limit < 1) {
            throw new IllegalArgumentException("Предел открытых шагов " + limit + ": он считается от числа духов");
        }
        if (SettlementStage.of(board.ledger()) > 1) {
            return List.of();
        }
        Set<String> unfinished = SettlementStage.missing(board.ledger()).keySet();
        int open = DispatchRound.stageCasesOpen(board);
        List<BoardCase> found = new ArrayList<>();
        for (BoardCase step : order) {
            if (!unfinished.contains(step.name()) || onBoard(board, step)) {
                continue;
            }
            if (open >= limit) {
                break;
            }
            found.add(step);
            open++;
        }
        return List.copyOf(found);
    }

    static List<String> ids(BoardCase caseValue) {
        List<String> ids = new ArrayList<>();
        for (int part = 1; part <= partsOf(caseValue); part++) {
            ids.add(partId(caseValue, part));
        }
        return List.copyOf(ids);
    }

    static String partId(BoardCase caseValue, int part) {
        if (part == 1) {
            return FIRST_PREFIX + caseValue.name().toLowerCase(Locale.ROOT);
        }
        return (caseValue == BoardCase.BUILD_FENCE ? FENCE_PART_PREFIX : CLEAR_PART_PREFIX) + part;
    }

    private static int partsOf(BoardCase caseValue) {
        return switch (caseValue) {
            case CLEAR_GROUNDS -> GroundsPlan.CLEAR_PARTS;
            case BUILD_FENCE -> TicketPlans.FENCE_PARTS;
            default -> 1;
        };
    }

    private static boolean onBoard(Board board, BoardCase step) {
        for (String id : ids(step)) {
            if (board.statusOf(id).isPresent()) {
                return true;
            }
        }
        return false;
    }
}
