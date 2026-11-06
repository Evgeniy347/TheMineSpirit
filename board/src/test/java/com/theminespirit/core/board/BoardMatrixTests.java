package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class BoardMatrixTests {
    static Stream<BoardCase> cases() {
        return Arrays.stream(BoardCase.values()).filter(boardCase -> !BoardActivities.isActivity(boardCase));
    }

    static BoardMatrix.Outcome expected(BoardMatrix.Situation situation) {
        BoardCase boardCase = situation.boardCase();
        boolean needy = boardCase != BoardCase.EAT_TO_FULL || situation.state() == BoardMatrix.State.HUNGRY;
        boolean ready = situation.dependency() != BoardMatrix.Dependency.PENDING
                && situation.materials() != BoardMatrix.Materials.MISSING
                && situation.owner() != BoardMatrix.Owner.FOREIGN
                && situation.state() != BoardMatrix.State.COMBAT
                && situation.state() != BoardMatrix.State.HOLDS_OTHER
                && situation.state() != BoardMatrix.State.PAUSED
                && needy;
        return ready ? BoardMatrix.Outcome.ISSUED : BoardMatrix.Outcome.WAITS;
    }

    @Tag("mutation")
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
