package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StatusNoteTests {
    @Test
    void line_AssignedWithChosenSpirit_NamesTicketAttemptsWaitReasonAndChoice() {
        String line = StatusNote.line(
                BoardLog.Event.ASSIGNED,
                "chop_jungle_log:2",
                BoardCase.GATHER_MATERIALS,
                3,
                "spirit-1 — занят: goto",
                "ближайший свободный дух");

        assertTrue(line.contains("ASSIGNED"), "Строка обязана называть переход ASSIGNED: по ней читают ход выдачи");
        assertTrue(line.contains("chop_jungle_log:2"), "Строка обязана называть id задания: без него лог бесполезен");
        assertTrue(line.contains("GATHER_MATERIALS"), "Строка обязана называть кейс: по нему видно род работы");
        assertTrue(line.contains("попыток=3"), "Строка обязана называть попытки: по ним видно, голодает ли задание");
        assertTrue(
                line.contains("waitReason=spirit-1 — занят: goto"),
                "Строка обязана повторять waitReason: по ней видно, почему задание ждало");
        assertTrue(
                line.contains("выбор=ближайший свободный дух"),
                "Строка обязана называть причину выбора духа: без неё не понять, почему взял именно он");
    }

    @Test
    void line_AssignedWithTarget_NamesTargetAndChoice() {
        String line = StatusNote.line(
                BoardLog.Event.ASSIGNED,
                "first:build_mine",
                BoardCase.BUILD_MINE,
                1,
                null,
                "spirit-2 — ближайший свободный дух",
                "цель=(-198,58,-530) предмет=— количество=1 попыток=1");

        assertTrue(
                line.contains("подробности=цель=(-198,58,-530)"),
                "В строке выдачи есть цель задания и расчёт выбора: без них не понять, куда и почему пошёл дух "
                        + "(п.4 отчёта); строка: " + line);
    }

    @Test
    void line_BlockedWithDetails_NamesReasonAndHistory() {
        String line = StatusNote.line(
                BoardLog.Event.BLOCKED,
                "first:build_mine",
                BoardCase.BUILD_MINE,
                5,
                null,
                null,
                "причина=стоянка за преградой история=тик 100: стоянка за преградой");

        assertTrue(line.contains("BLOCKED"), "Строка обязана называть переход BLOCKED");
        assertTrue(
                line.contains("история=тик 100: стоянка за преградой"),
                "BLOCKED показывает историю попыток и причину: waitReason у заблокированного пуст (п.4 отчёта); "
                        + "строка: " + line);
    }

    @Test
    void line_BlockedWithoutHolder_ShowsAttemptsAndEmptyChoice() {
        String line = StatusNote.line(BoardLog.Event.BLOCKED, "first:build_mine", BoardCase.BUILD_MINE, 5, null, null);

        assertTrue(line.contains("BLOCKED"), "Строка обязана называть переход BLOCKED");
        assertTrue(line.contains("попыток=5"), "Строка обязана называть число провалов");
        assertTrue(line.contains("waitReason=—"), "Пустая причина ожидания пишется прочерком, а не пропуском поля");
        assertTrue(line.contains("выбор=—"), "Без выбора духа поле пишется прочерком: поля строки постоянны");
    }
}
