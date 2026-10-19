package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class BuildingLevelsTests {

    @Test
    void maxLevel_FromClasspath_MatchesLiterals() {
        assertEquals(
                4,
                BuildingLevels.maxLevel(BoardCase.BUILD_MINE),
                "Максимум в тесте, факт из production: у шахты 4 уровня — проход, расширение, " + "ступеньки, рельсы");
        assertEquals(
                3,
                BuildingLevels.maxLevel(BoardCase.BUILD_PEN),
                "Максимум в тесте, факт из production: у загона 3 уровня — 4×5, 7×7, больше");
        assertEquals(
                4,
                BuildingLevels.maxLevel(BoardCase.ENCHANT_ROOM),
                "Максимум в тесте, факт из production: у чаровальни 4 уровня — стол, 15 полок, "
                        + "рост здания, несколько чаровален");
        assertEquals(
                4,
                BuildingLevels.maxLevel(BoardCase.BUILD_HOUSE),
                "Максимум в тесте, факт из production: у дома 4 уровня");
    }

    @Test
    void maxLevel_CaseWithoutLevels_HasExactlyOne() {
        assertEquals(
                1,
                BuildingLevels.maxLevel(BoardCase.FILL_CRATER),
                "Кейс без строки в levels имеет ровно один уровень: план строится один раз, "
                        + "уровни — только у построек с развитием");
        assertFalse(
                BuildingLevels.hasLevels(BoardCase.FILL_CRATER),
                "Кейс без уровней не считается уровневым: проверка видит разницу между "
                        + "«один уровень» и «развивающаяся постройка»");
        assertTrue(
                BuildingLevels.hasLevels(BoardCase.BUILD_MINE),
                "Шахта — уровневый кейс: развитие идёт по уровням, а не новой цепочкой заданий");
    }

    @Test
    void nextLevel_EmptyLedger_ReturnsFirst() {
        BoardLedger ledger = new BoardLedger();

        assertEquals(
                OptionalInt.of(1),
                BuildingLevels.nextLevel(BoardCase.BUILD_MINE, ledger),
                "Без построенных уровней следующий — первый: стройка начинается с уровня 1");
    }

    @Test
    void nextLevel_FirstDone_ReturnsSecond() {
        BoardLedger ledger = new BoardLedger();
        ledger.note(BoardCase.BUILD_MINE, 1);

        assertEquals(
                OptionalInt.of(2),
                BuildingLevels.nextLevel(BoardCase.BUILD_MINE, ledger),
                "После уровня 1 доступен уровень 2: развитие идёт по порядку");
    }

    @Test
    void nextLevel_AllDone_ReturnsEmpty() {
        BoardLedger ledger = new BoardLedger();
        ledger.note(BoardCase.BUILD_MINE, 1);
        ledger.note(BoardCase.BUILD_MINE, 2);
        ledger.note(BoardCase.BUILD_MINE, 3);
        ledger.note(BoardCase.BUILD_MINE, 4);

        assertEquals(
                OptionalInt.empty(),
                BuildingLevels.nextLevel(BoardCase.BUILD_MINE, ledger),
                "Все 4 уровня шахты стоят — следующего нет: доска не плодит лишние задания");
    }

    @Test
    void nextLevel_HigherWithoutLower_ReturnsLower() {
        BoardLedger ledger = new BoardLedger();
        ledger.note(BoardCase.BUILD_MINE, 3);

        assertEquals(
                OptionalInt.of(1),
                BuildingLevels.nextLevel(BoardCase.BUILD_MINE, ledger),
                "Отметка уровня 3 без уровня 1 не открывает пропуск: следующий — первый "
                        + "непостроенный, иначе уровень прыгал бы через дыру");
    }

    @Test
    void nextLevel_NullArgs_AreRejected() {

        assertThrows(
                NullPointerException.class,
                () -> BuildingLevels.maxLevel(null),
                "Кейс null обязан отвергаться: без него неизвестно, сколько уровней у постройки");
        assertThrows(
                NullPointerException.class,
                () -> BuildingLevels.hasLevels(null),
                "Кейс null обязан отвергаться: проверка уровней идёт по имени кейса");
        assertThrows(
                NullPointerException.class,
                () -> BuildingLevels.nextLevel(BoardCase.BUILD_MINE, null),
                "Ledger null обязан отвергаться: без него неизвестно, что уже построено");
    }

    @Property
    void nextLevel_AnyLedger_StaysSequential(
            @ForAll("levelledCases") BoardCase boardCase, @ForAll("builtLevels") Set<Integer> built) {
        BoardLedger ledger = new BoardLedger();
        built.forEach(level -> ledger.note(boardCase, level));
        int max = BuildingLevels.maxLevel(boardCase);

        OptionalInt next = BuildingLevels.nextLevel(boardCase, ledger);

        if (next.isPresent()) {
            assertTrue(
                    next.getAsInt() >= 1 && next.getAsInt() <= max,
                    "Следующий уровень лежит в [1, " + max + "]: получен " + next.getAsInt() + " для " + boardCase);
            for (int level = 1; level < next.getAsInt(); level++) {
                assertTrue(
                        ledger.has(boardCase, level),
                        "Все уровни ниже следующего построены: " + boardCase + " уровень " + level
                                + " пропущен, а уровень " + next.getAsInt() + " уже доступен");
            }
        }
    }

    @Provide
    Arbitrary<BoardCase> levelledCases() {
        return Arbitraries.of(
                BoardCase.BUILD_MINE,
                BoardCase.BUILD_PEN,
                BoardCase.ENCHANT_ROOM,
                BoardCase.BUILD_HOUSE,
                BoardCase.FILL_CRATER);
    }

    @Provide
    Arbitrary<Set<Integer>> builtLevels() {
        return Arbitraries.integers().between(1, 4).set().ofMaxSize(4);
    }
}
