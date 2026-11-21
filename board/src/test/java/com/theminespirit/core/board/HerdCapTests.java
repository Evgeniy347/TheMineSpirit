package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.api.Species;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class HerdCapTests {

    @Test
    void maxPerSpecies_FromClasspath_MatchesLiteral() {
        assertEquals(
                4,
                HerdCap.maxPerSpecies(),
                "Предел в тесте, факт из production: не больше 4 животных каждого вида "
                        + "(ответ владельца 16.10). Пятая голова того же вида не ловится"
                        + " Иначе тест maxPerSpecies_FromClasspath_MatchesLiteral перестанет ловить эту поломку. При смене maxPerSpecies_FromClasspath_MatchesLiteral проверь это утверждение.");
        assertEquals(
                2,
                HerdCap.minBreed(),
                "Предел в тесте, факт из production: для разведения нужна пара"
                        + " Иначе тест maxPerSpecies_FromClasspath_MatchesLiteral перестанет ловить эту поломку. При смене maxPerSpecies_FromClasspath_MatchesLiteral проверь это утверждение.");
    }

    @Test
    void reached_ThreeHoused_CanCapture() {
        assertTrue(
                HerdCap.canCapture(Species.COW, 3, 0),
                "Три коровы в загоне — четвёртую ловим: лимит 4 ещё не выбран"
                        + " Иначе тест reached_ThreeHoused_CanCapture перестанет ловить эту поломку. При смене reached_ThreeHoused_CanCapture проверь это утверждение.");
        assertFalse(
                HerdCap.reached(Species.COW, 3, 0),
                "Три коровы не заполняют лимит: загон ещё примет голову"
                        + " Иначе тест reached_ThreeHoused_CanCapture перестанет ловить эту поломку. При смене reached_ThreeHoused_CanCapture проверь это утверждение.");
    }

    @Test
    void reached_FourHoused_Reached() {
        assertTrue(
                HerdCap.reached(Species.COW, 4, 0),
                "Четыре коровы — лимит выбран: новых голов вида не ловим и загон не строим"
                        + " Иначе тест reached_FourHoused_Reached перестанет ловить эту поломку. При смене reached_FourHoused_Reached проверь это утверждение.");
    }

    @Test
    void reached_FiveHoused_Reached() {
        assertTrue(
                HerdCap.reached(Species.COW, 5, 0),
                "Пять коров — лимит превышен (защита от гонки): доска обязана остановить отлов, "
                        + "а не считать перебор нормой"
                        + " Иначе тест reached_FiveHoused_Reached перестанет ловить эту поломку. При смене reached_FiveHoused_Reached проверь это утверждение.");
    }

    @Test
    void reached_CaughtInShelter_Counts() {
        assertTrue(
                HerdCap.reached(Species.PIG, 2, 2),
                "Две свиньи в загоне и две пойманные в укрытии — лимит 4 выбран: пойманное заняло "
                        + "место вида, иначе укрытие обходило бы лимит"
                        + " При смене reached_CaughtInShelter_Counts проверь это утверждение.");
        assertTrue(
                HerdCap.canCapture(Species.PIG, 2, 1),
                "Две в загоне и одна пойманная — третью ловим: до лимита ещё место"
                        + " Иначе тест reached_CaughtInShelter_Counts перестанет ловить эту поломку. При смене reached_CaughtInShelter_Counts проверь это утверждение.");
    }

    @Test
    void reached_OtherSpecies_Independent() {
        assertTrue(
                HerdCap.reached(Species.COW, 4, 0),
                "Четыре коровы — лимит выбран: это условие независимости по видам, полные коровы " + "не мешают овцам"
                        + " Иначе тест reached_OtherSpecies_Independent перестанет ловить эту поломку. При смене reached_OtherSpecies_Independent проверь это утверждение.");
        assertTrue(
                HerdCap.canCapture(Species.SHEEP, 0, 0),
                "Лимит по видам: полные коровы не мешают ловить овец — у каждого вида свой запас"
                        + " Иначе тест reached_OtherSpecies_Independent перестанет ловить эту поломку. При смене reached_OtherSpecies_Independent проверь это утверждение.");
    }

    @Test
    void reached_NullOrNegative_AreRejected() {
        assertThrows(
                NullPointerException.class,
                () -> HerdCap.reached(null, 0, 0),
                "Вид null обязан отвергаться: без него неизвестно, чей запас считать"
                        + " Иначе тест reached_NullOrNegative_AreRejected перестанет ловить эту поломку. При смене reached_NullOrNegative_AreRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> HerdCap.reached(Species.COW, -1, 0),
                "Отрицательное поголовье обязано падать: минус коров прятал бы перебор"
                        + " Иначе тест reached_NullOrNegative_AreRejected перестанет ловить эту поломку. При смене reached_NullOrNegative_AreRejected проверь это утверждение.");
        assertThrows(
                IllegalArgumentException.class,
                () -> HerdCap.reached(Species.COW, 0, -1),
                "Отрицательное пойманное обязано падать: минус голов прятал бы перебор"
                        + " Иначе тест reached_NullOrNegative_AreRejected перестанет ловить эту поломку. При смене reached_NullOrNegative_AreRejected проверь это утверждение.");
    }

    @Test
    void herdKey_UnknownKey_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> HerdCap.herdKey("maxPerSpeciesX"),
                "Опечатка в ключе стада обязана падать: молча потерянный ключ оставил бы лимит "
                        + "по умолчанию без ведома настройщика"
                        + " Иначе тест herdKey_UnknownKey_IsRejected перестанет ловить эту поломку. При смене herdKey_UnknownKey_IsRejected проверь это утверждение.");
    }

    @Property
    void reached_AnyCounts_MatchesSumAgainstLimit(@ForAll("counts") int housed, @ForAll("counts") int caught) {
        int max = HerdCap.maxPerSpecies();

        assertEquals(
                housed + caught >= max,
                HerdCap.reached(Species.COW, housed, caught),
                "Лимит срабатывает ровно при заполнении: housed + caught >= " + max
                        + ", иначе доска либо ловит лишнее, либо держит загон пустым"
                        + " При смене reached_AnyCounts_MatchesSumAgainstLimit проверь это утверждение.");
        assertEquals(
                !HerdCap.reached(Species.COW, housed, caught),
                HerdCap.canCapture(Species.COW, housed, caught),
                "Отлов разрешён ровно тогда, когда лимит не выбран"
                        + " Иначе тест reached_AnyCounts_MatchesSumAgainstLimit перестанет ловить эту поломку. При смене reached_AnyCounts_MatchesSumAgainstLimit проверь это утверждение.");
    }

    @Property
    void reached_MoreAnimals_NeverLowersLimit(@ForAll("counts") int housed, @ForAll("counts") int caught) {
        boolean atLimit = HerdCap.reached(Species.COW, housed, caught);

        assertTrue(
                !atLimit || HerdCap.reached(Species.COW, housed + 1, caught),
                "Лимит монотонен: добавление головы не снимает достигнутый лимит"
                        + " Иначе тест reached_MoreAnimals_NeverLowersLimit перестанет ловить эту поломку. При смене reached_MoreAnimals_NeverLowersLimit проверь это утверждение.");
    }

    @Provide
    Arbitrary<Integer> counts() {
        return Arbitraries.integers().between(0, 6);
    }
}
