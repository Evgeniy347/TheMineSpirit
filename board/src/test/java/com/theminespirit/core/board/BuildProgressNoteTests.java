package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
import org.junit.jupiter.api.Test;

class BuildProgressNoteTests {
    @Test
    void line_StageBuild_NamesStageHeightAndLeveling() {
        String line = BuildProgressNote.line(BoardCase.BUILD_FENCE, 2, 69, 12, 40, 34, 8);

        assertTrue(
                line.startsWith("[plot]"),
                "Ход стройки пишется префиксом [plot]: по нему лог фильтруют"
                        + " Иначе тест line_StageBuild_NamesStageHeightAndLeveling перестанет ловить эту поломку. При смене line_StageBuild_NamesStageHeightAndLeveling проверь это утверждение.");
        assertTrue(
                line.contains("BUILD_FENCE"),
                "Строка обязана называть этап: без него не видно, что строится"
                        + " Иначе тест line_StageBuild_NamesStageHeightAndLeveling перестанет ловить эту поломку. При смене line_StageBuild_NamesStageHeightAndLeveling проверь это утверждение.");
        assertTrue(
                line.contains("уровня 2"),
                "Строка обязана называть уровень этапа"
                        + " Иначе тест line_StageBuild_NamesStageHeightAndLeveling перестанет ловить эту поломку. При смене line_StageBuild_NamesStageHeightAndLeveling проверь это утверждение.");
        assertTrue(
                line.contains("поставлено 12 из 40"),
                "Строка обязана показывать ход: поставлено из всего"
                        + " Иначе тест line_StageBuild_NamesStageHeightAndLeveling перестанет ловить эту поломку. При смене line_StageBuild_NamesStageHeightAndLeveling проверь это утверждение.");
        assertTrue(
                line.contains("высота 69"),
                "Строка обязана называть высоту участка: по ней видно выравнивание"
                        + " Иначе тест line_StageBuild_NamesStageHeightAndLeveling перестанет ловить эту поломку. При смене line_StageBuild_NamesStageHeightAndLeveling проверь это утверждение.");
        assertTrue(
                line.contains("срезка 34"),
                "Строка обязана называть объём срезки"
                        + " Иначе тест line_StageBuild_NamesStageHeightAndLeveling перестанет ловить эту поломку. При смене line_StageBuild_NamesStageHeightAndLeveling проверь это утверждение.");
        assertTrue(
                line.contains("подсыпка 8"),
                "Строка обязана называть объём подсыпки"
                        + " Иначе тест line_StageBuild_NamesStageHeightAndLeveling перестанет ловить эту поломку. При смене line_StageBuild_NamesStageHeightAndLeveling проверь это утверждение.");
    }
}
