package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BoardLogCulpritTests {
    @Test
    void culprit_AssignedTicket_NamesTaskCulprit() {
        BoardTicket fence = new BoardTicket(
                "more3_fence:-183,69,-505",
                BoardCase.BUILD_FENCE,
                Optional.empty(),
                50,
                0L,
                1,
                "always",
                List.of(),
                Map.of());

        assertEquals(
                "more3_fence:-183,69,-505",
                BoardLog.culprit(List.of(fence)),
                "Строка [plot-edit] обязана называть задание, которым сломан блок плана: в observe23 было видно "
                        + "только духа, и причину искали по всему логу");
    }

    @Test
    void culprit_NoAssignedTicket_Dash() {
        assertEquals(
                "—",
                BoardLog.culprit(List.of()),
                "Без задания в строке обязан стоять прочерк: пустое место читается как забытое поле");
    }
}
