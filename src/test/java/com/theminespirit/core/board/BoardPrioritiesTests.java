package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.reload.DataTables;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class BoardPrioritiesTests {

    private static BoardConfig config() {
        return DataTables.INSTANCE.board().current();
    }

    @Test
    void effective_NoContext_EqualsBase() {
        for (BoardCase boardCase : BoardCase.values()) {
            int base = config().priorities().get(boardCase.name());

            assertEquals(base, BoardPriorities.effective(boardCase, PriorityContext.none()),
                    "Без ситуации итог равен базе кейса " + boardCase + ": модификаторы без "
                            + "условий не начисляются, иначе доска пересортирует очередь без причины");
        }
    }

    @Test
    void effective_CaughtAnimal_RaisesPen() {
        int expected = config().priorities().get("BUILD_PEN")
                + config().modifiers().get("pen_caught_animal");
        PriorityContext context = new PriorityContext(true, false, false, false, false);

        assertEquals(expected, BoardPriorities.effective(BoardCase.BUILD_PEN, context),
                "Пойманное животное без загона повышает BUILD_PEN на дельту из таблицы: "
                        + "найденных ограждаем сразу, а не ставим в общую очередь строек");
    }

    @Test
    void effective_CaughtAnimalWithPenBuilt_NoRaise() {
        int base = config().priorities().get("BUILD_PEN");
        PriorityContext context = new PriorityContext(true, true, false, false, false);

        assertEquals(base, BoardPriorities.effective(BoardCase.BUILD_PEN, context),
                "Пойманное животное при построенном загоне не повышает BUILD_PEN: загон вида "
                        + "уже стоит, повышать нечего");
    }

    @Test
    void effective_MissingBait_RaisesPen() {
        int expected = config().priorities().get("BUILD_PEN")
                + config().modifiers().get("pen_missing_bait");
        PriorityContext context = new PriorityContext(false, false, true, false, false);

        assertEquals(expected, BoardPriorities.effective(BoardCase.BUILD_PEN, context),
                "Нет приманки — загон строим, пока приманку добывают: BUILD_PEN растёт на "
                        + "дельту из таблицы");
    }

    @Test
    void effective_SpeciesFull_DropsPenToZero() {
        int base = config().priorities().get("BUILD_PEN");
        int delta = config().modifiers().get("pen_species_full");
        PriorityContext context = new PriorityContext(false, false, false, true, false);

        assertEquals(Math.max(0, base + delta),
                BoardPriorities.effective(BoardCase.BUILD_PEN, context),
                "Полный загон вида обнуляет BUILD_PEN (" + base + " + " + delta + "): "
                        + "пятую голову того же вида не селим, доска задание не выдаёт");
        assertEquals(0, BoardPriorities.effective(BoardCase.BUILD_PEN, context),
                "Полный загон даёт ровно 0, а не отрицательное: приоритет вне [0, MAX] "
                        + "сломает сортировку выдачи");
    }

    @Test
    void effective_BaitReady_RaisesLure() {
        int expected = config().priorities().get("LURE_ANIMAL")
                + config().modifiers().get("lure_bait_ready");
        PriorityContext context = new PriorityContext(false, true, false, false, false);

        assertEquals(expected, BoardPriorities.effective(BoardCase.LURE_ANIMAL, context),
                "Загон стоит и приманка есть — LURE_ANIMAL растёт на дельту: приманивать "
                        + "нечем было вчера, а сегодня есть чем");
    }

    @Test
    void effective_ActiveRepair_LowersRelocate() {
        int expected = config().priorities().get("RELOCATE_BUILDING")
                + config().modifiers().get("relocate_busy");
        PriorityContext context = new PriorityContext(false, false, false, false, true);

        assertEquals(expected, BoardPriorities.effective(BoardCase.RELOCATE_BUILDING, context),
                "Невыполненный ремонт понижает перенос: ремонт важнее переделки, стратег ждёт");
    }

    @Test
    void effective_TwoModifiers_SumUp() {
        int expected = config().priorities().get("BUILD_PEN")
                + config().modifiers().get("pen_caught_animal")
                + config().modifiers().get("pen_missing_bait");
        PriorityContext context = new PriorityContext(true, false, true, false, false);

        assertEquals(expected, BoardPriorities.effective(BoardCase.BUILD_PEN, context),
                "Два модификатора на одном задании суммируются: пойманное животное плюс "
                        + "отсутствие приманки повышают загон дважды");
    }

    @Test
    void effective_UnrelatedCase_IgnoresContext() {
        int base = config().priorities().get("FILL_CRATER");
        PriorityContext context = new PriorityContext(true, true, true, true, true);

        assertEquals(base, BoardPriorities.effective(BoardCase.FILL_CRATER, context),
                "Чужие модификаторы воронку не трогают: дельты привязаны к кейсу, иначе "
                        + "пойманная корова повышала бы засыпку ямы");
    }

    @Property
    void effective_AnyContext_StaysWithinBounds(@ForAll("boardCases") BoardCase boardCase,
            @ForAll("contexts") PriorityContext context) {
        int effective = BoardPriorities.effective(boardCase, context);

        assertTrue(effective >= 0 && effective <= BoardTuning.MAX_PRIORITY,
                "Итог обязан лежать в [0, MAX_PRIORITY]: " + boardCase + " при " + context
                        + " дал " + effective + ", выход за границу сломает сортировку выдачи");
    }

    @Property
    void effective_AnyContext_EqualsBasePlusApplied(@ForAll("boardCases") BoardCase boardCase,
            @ForAll("contexts") PriorityContext context) {
        int expected = config().priorities().get(boardCase.name());
        for (String modifier : BoardPriorities.applied(boardCase, context)) {
            expected += config().modifiers().get(modifier);
        }
        expected = Math.min(BoardTuning.MAX_PRIORITY, Math.max(0, expected));

        assertEquals(expected, BoardPriorities.effective(boardCase, context),
                "Итог равен базе плюс сумме сработавших дельт с зажимом: вычисление в тесте, "
                        + "факт из production-функции");
    }

    @Property
    void effective_AnyContext_IsDeterministic(@ForAll("boardCases") BoardCase boardCase,
            @ForAll("contexts") PriorityContext context) {
        assertEquals(BoardPriorities.effective(boardCase, context),
                BoardPriorities.effective(boardCase, context),
                "Итог детерминирован: доска сортирует очередь по итогу, нестабильное значение "
                        + "перетасует выдачу между пересборками");
    }

    @Test
    void applied_EmptyContext_NamesNothing() {
        for (BoardCase boardCase : BoardCase.values()) {
            assertEquals(List.of(), BoardPriorities.applied(boardCase, PriorityContext.none()),
                    "Без ситуации сработавших модификаторов нет: журнал пишет пустой список, "
                            + "а не выдуманные ключи");
        }
    }

    @Provide
    Arbitrary<BoardCase> boardCases() {
        return Arbitraries.of(BoardCase.values());
    }

    @Provide
    Arbitrary<PriorityContext> contexts() {
        Arbitrary<Boolean> flag = Arbitraries.of(true, false);
        return Combinators.combine(flag, flag, flag, flag, flag).as(PriorityContext::new);
    }
}
