package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.api.BoardCase;
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
                "Максимум в тесте, факт из production: у шахты 4 уровня — проход, расширение, " + "ступеньки, рельсы"
                        + " Иначе тест maxLevel_FromClasspath_MatchesLiterals перестанет ловить эту поломку. При смене maxLevel_FromClasspath_MatchesLiterals проверь это утверждение.");
        assertEquals(
                3,
                BuildingLevels.maxLevel(BoardCase.BUILD_PEN),
                "Максимум в тесте, факт из production: у загона 3 уровня — 4×5, 7×7, больше"
                        + " Иначе тест maxLevel_FromClasspath_MatchesLiterals перестанет ловить эту поломку. При смене maxLevel_FromClasspath_MatchesLiterals проверь это утверждение.");
        assertEquals(
                4,
                BuildingLevels.maxLevel(BoardCase.ENCHANT_ROOM),
                "Максимум в тесте, факт из production: у чаровальни 4 уровня — стол, 15 полок, "
                        + "рост здания, несколько чаровален"
                        + " Иначе тест maxLevel_FromClasspath_MatchesLiterals перестанет ловить эту поломку. При смене maxLevel_FromClasspath_MatchesLiterals проверь это утверждение.");
        assertEquals(
                4,
                BuildingLevels.maxLevel(BoardCase.BUILD_HOUSE),
                "Максимум в тесте, факт из production: у дома 4 уровня"
                        + " Иначе тест maxLevel_FromClasspath_MatchesLiterals перестанет ловить эту поломку. При смене maxLevel_FromClasspath_MatchesLiterals проверь это утверждение.");
    }

    @Test
    void maxLevel_CaseWithoutLevels_HasExactlyOne() {
        assertEquals(
                1,
                BuildingLevels.maxLevel(BoardCase.FILL_CRATER),
                "Кейс без строки в levels имеет ровно один уровень: план строится один раз, "
                        + "уровни — только у построек с развитием"
                        + " Иначе тест maxLevel_CaseWithoutLevels_HasExactlyOne перестанет ловить эту поломку. При смене maxLevel_CaseWithoutLevels_HasExactlyOne проверь это утверждение.");
        assertFalse(
                BuildingLevels.hasLevels(BoardCase.FILL_CRATER),
                "Кейс без уровней не считается уровневым: проверка видит разницу между "
                        + "«один уровень» и «развивающаяся постройка»"
                        + " Иначе тест maxLevel_CaseWithoutLevels_HasExactlyOne перестанет ловить эту поломку. При смене maxLevel_CaseWithoutLevels_HasExactlyOne проверь это утверждение.");
        assertTrue(
                BuildingLevels.hasLevels(BoardCase.BUILD_MINE),
                "Шахта — уровневый кейс: развитие идёт по уровням, а не новой цепочкой заданий"
                        + " Иначе тест maxLevel_CaseWithoutLevels_HasExactlyOne перестанет ловить эту поломку. При смене maxLevel_CaseWithoutLevels_HasExactlyOne проверь это утверждение.");
    }

    @Test
    void nextLevel_EmptyLedger_ReturnsFirst() {
        BoardLedger ledger = new BoardLedger();

        assertEquals(
                OptionalInt.of(1),
                BuildingLevels.nextLevel(BoardCase.BUILD_MINE, ledger),
                "Без построенных уровней следующий — первый: стройка начинается с уровня 1"
                        + " Иначе тест nextLevel_EmptyLedger_ReturnsFirst перестанет ловить эту поломку. При смене nextLevel_EmptyLedger_ReturnsFirst проверь это утверждение.");
    }

    @Test
    void nextLevel_FirstDone_ReturnsSecond() {
        BoardLedger ledger = new BoardLedger();
        ledger.note(BoardCase.BUILD_MINE, 1);

        assertEquals(
                OptionalInt.of(2),
                BuildingLevels.nextLevel(BoardCase.BUILD_MINE, ledger),
                "После уровня 1 доступен уровень 2: развитие идёт по порядку"
                        + " Иначе тест nextLevel_FirstDone_ReturnsSecond перестанет ловить эту поломку. При смене nextLevel_FirstDone_ReturnsSecond проверь это утверждение.");
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
                "Все 4 уровня шахты стоят — следующего нет: доска не плодит лишние задания"
                        + " Иначе тест nextLevel_AllDone_ReturnsEmpty перестанет ловить эту поломку. При смене nextLevel_AllDone_ReturnsEmpty проверь это утверждение.");
    }

    @Test
    void nextLevel_HigherWithoutLower_ReturnsLower() {
        BoardLedger ledger = new BoardLedger();
        ledger.note(BoardCase.BUILD_MINE, 3);

        assertEquals(
                OptionalInt.of(1),
                BuildingLevels.nextLevel(BoardCase.BUILD_MINE, ledger),
                "Отметка уровня 3 без уровня 1 не открывает пропуск: следующий — первый "
                        + "непостроенный, иначе уровень прыгал бы через дыру"
                        + " При смене nextLevel_HigherWithoutLower_ReturnsLower проверь это утверждение.");
    }

    @Test
    void state_FirstLevelNotBuilt_IsNew() {
        assertEquals(
                BuildingLevels.State.NEW,
                BuildingLevels.state(BoardCase.BUILD_HOUSE, 1, new BoardLedger()),
                "Пока уровень не стоит и ниже ничего нет, постройка новая: это не улучшение"
                        + " Иначе тест state_FirstLevelNotBuilt_IsNew перестанет ловить эту поломку. При смене state_FirstLevelNotBuilt_IsNew проверь это утверждение.");
    }

    @Test
    void state_LowerLevelStands_IsUpgrading() {
        BoardLedger ledger = new BoardLedger();
        ledger.note(BoardCase.BUILD_HOUSE, 1);

        assertEquals(
                BuildingLevels.State.UPGRADING,
                BuildingLevels.state(BoardCase.BUILD_HOUSE, 2, ledger),
                "Уровень 2 поверх стоящего уровня 1 — улучшение на месте, а не новая стройка"
                        + " Иначе тест state_LowerLevelStands_IsUpgrading перестанет ловить эту поломку. При смене state_LowerLevelStands_IsUpgrading проверь это утверждение.");
    }

    @Test
    void state_LevelStands_IsStanding() {
        BoardLedger ledger = new BoardLedger();
        ledger.note(BoardCase.BUILD_HOUSE, 2);

        assertEquals(
                BuildingLevels.State.STANDING,
                BuildingLevels.state(BoardCase.BUILD_HOUSE, 2, ledger),
                "Отмеченный уровень стоит: повторно его не строят"
                        + " Иначе тест state_LevelStands_IsStanding перестанет ловить эту поломку. При смене state_LevelStands_IsStanding проверь это утверждение.");
    }

    @Test
    void state_GapBelow_IsNew() {
        BoardLedger ledger = new BoardLedger();
        ledger.note(BoardCase.BUILD_HOUSE, 3);

        assertEquals(
                BuildingLevels.State.NEW,
                BuildingLevels.state(BoardCase.BUILD_HOUSE, 2, ledger),
                "Уровень 2 без стоящего уровня 1 — не улучшение: дыру в уровнях улучшением не закрывают"
                        + " Иначе тест state_GapBelow_IsNew перестанет ловить эту поломку. При смене state_GapBelow_IsNew проверь это утверждение.");
    }

    @Test
    void state_NullArgs_AreRejected() {
        assertThrows(
                NullPointerException.class,
                () -> BuildingLevels.state(null, 1, new BoardLedger()),
                "Кейс null обязан отвергаться: без него неизвестно, чьё состояние считается"
                        + " Иначе тест state_NullArgs_AreRejected перестанет ловить эту поломку. При смене state_NullArgs_AreRejected проверь это утверждение.");
        assertThrows(
                NullPointerException.class,
                () -> BuildingLevels.state(BoardCase.BUILD_HOUSE, 1, null),
                "Ledger null обязан отвергаться: без него неизвестно, что стоит"
                        + " Иначе тест state_NullArgs_AreRejected перестанет ловить эту поломку. При смене state_NullArgs_AreRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> BuildingLevels.state(BoardCase.BUILD_HOUSE, 0, new BoardLedger()),
                "Нулевого уровня нет: состояние ниже первого не считается"
                        + " Иначе тест state_NullArgs_AreRejected перестанет ловить эту поломку. При смене state_NullArgs_AreRejected проверь это утверждение.");
    }

    @Test
    void nextLevel_NullArgs_AreRejected() {

        assertThrows(
                NullPointerException.class,
                () -> BuildingLevels.maxLevel(null),
                "Кейс null обязан отвергаться: без него неизвестно, сколько уровней у постройки"
                        + " Иначе тест nextLevel_NullArgs_AreRejected перестанет ловить эту поломку. При смене nextLevel_NullArgs_AreRejected проверь это утверждение.");
        assertThrows(
                NullPointerException.class,
                () -> BuildingLevels.hasLevels(null),
                "Кейс null обязан отвергаться: проверка уровней идёт по имени кейса"
                        + " Иначе тест nextLevel_NullArgs_AreRejected перестанет ловить эту поломку. При смене nextLevel_NullArgs_AreRejected проверь это утверждение.");
        assertThrows(
                NullPointerException.class,
                () -> BuildingLevels.nextLevel(BoardCase.BUILD_MINE, null),
                "Ledger null обязан отвергаться: без него неизвестно, что уже построено"
                        + " Иначе тест nextLevel_NullArgs_AreRejected перестанет ловить эту поломку. При смене nextLevel_NullArgs_AreRejected проверь это утверждение.");
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
                    "Следующий уровень лежит в [1, " + max + "]: получен " + next.getAsInt() + " для " + boardCase
                            + " Иначе тест nextLevel_AnyLedger_StaysSequential перестанет ловить эту поломку. При смене nextLevel_AnyLedger_StaysSequential проверь это утверждение.");
            for (int level = 1; level < next.getAsInt(); level++) {
                assertTrue(
                        ledger.has(boardCase, level),
                        "Все уровни ниже следующего построены: " + boardCase + " уровень " + level
                                + " пропущен, а уровень " + next.getAsInt() + " уже доступен"
                                + " Иначе тест nextLevel_AnyLedger_StaysSequential перестанет ловить эту поломку. При смене nextLevel_AnyLedger_StaysSequential проверь это утверждение.");
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
