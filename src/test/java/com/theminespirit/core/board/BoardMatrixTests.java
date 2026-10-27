package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class BoardMatrixTests {
    private static final Set<BoardCase> NEEDS_PICKAXE =
            EnumSet.of(BoardCase.CLEAR_GROUNDS, BoardCase.BUILD_MINE, BoardCase.MINE_LEFT_ORE);

    static Stream<BoardCase> cases() {
        return Arrays.stream(BoardCase.values()).filter(boardCase -> !BoardActivities.isActivity(boardCase));
    }

    static BoardMatrix.Outcome expected(BoardMatrix.Situation situation) {
        BoardCase boardCase = situation.boardCase();
        boolean pickaxe = situation.tool() == BoardMatrix.Tool.WOODEN_PICKAXE
                || situation.tool() == BoardMatrix.Tool.STONE_PICKAXE;
        boolean needy = boardCase != BoardCase.EAT_TO_FULL || situation.state() == BoardMatrix.State.HUNGRY;
        boolean ready = situation.dependency() != BoardMatrix.Dependency.PENDING
                && situation.materials() != BoardMatrix.Materials.MISSING
                && (pickaxe || !NEEDS_PICKAXE.contains(boardCase))
                && situation.owner() != BoardMatrix.Owner.FOREIGN
                && situation.state() != BoardMatrix.State.COMBAT
                && situation.state() != BoardMatrix.State.HOLDS_OTHER
                && situation.state() != BoardMatrix.State.PAUSED
                && needy;
        return ready ? BoardMatrix.Outcome.ISSUED : BoardMatrix.Outcome.WAITS;
    }

    @ParameterizedTest
    @MethodSource("cases")
    void matrix_EverySituation_MatchesSpec(BoardCase boardCase) {
        List<String> diverged = new ArrayList<>();
        for (BoardMatrix.Situation situation : BoardMatrix.situations(boardCase)) {
            BoardMatrix.Result actual = BoardMatrix.run(situation);
            BoardMatrix.Outcome want = expected(situation);
            if (actual.outcome() != want) {
                diverged.add(situation + " ждали " + want + ", доска: " + actual.outcome() + " " + actual.why());
            }
        }
        assertEquals(
                List.of(),
                diverged.subList(0, Math.min(6, diverged.size())),
                "Доска расходится со спецификацией выдачи в " + diverged.size() + " ситуациях кейса " + boardCase);
    }
}
