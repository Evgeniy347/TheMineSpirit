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

    @Test
    void culpritOrAction_AssignedTicket_KeepsId() {
        assertEquals(
                "repair:build_house",
                BoardLog.culpritOrAction("repair:build_house", "dig_out"),
                "Задание доски сильнее имени скрипта: id задания — главная причина правки плана");
    }

    @Test
    void culpritOrAction_SelfEscapeWithoutTicket_NamesRescueAction() {
        assertEquals(
                "самоспасение: dig_out",
                BoardLog.culpritOrAction(null, "dig_out"),
                "Самоспасение не ставит задание доски, но [plot-edit] обязан назвать действие, которым сломан "
                        + "план: в observe24 стояло «задание —», и причину искали по всему логу");
    }

    @Test
    void culpritOrAction_NoTicketAndNoScript_Dash() {
        assertEquals(
                "—",
                BoardLog.culpritOrAction("—", null),
                "Без задания и действия остаётся прочерк: пустое место читается как забытое поле");
    }
}
