package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.Species;
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
                        + "(ответ владельца 16.10). Пятая голова того же вида не ловится");
        assertEquals(2, HerdCap.minBreed(), "Предел в тесте, факт из production: для разведения нужна пара");
    }

    @Test
    void reached_ThreeHoused_CanCapture() {
        assertTrue(
                HerdCap.canCapture(Species.COW, 3, 0), "Три коровы в загоне — четвёртую ловим: лимит 4 ещё не выбран");
        assertFalse(HerdCap.reached(Species.COW, 3, 0), "Три коровы не заполняют лимит: загон ещё примет голову");
    }

    @Test
    void reached_FourHoused_Reached() {
        assertTrue(
                HerdCap.reached(Species.COW, 4, 0),
                "Четыре коровы — лимит выбран: новых голов вида не ловим и загон не строим");
    }

    @Test
    void reached_FiveHoused_Reached() {
        assertTrue(
                HerdCap.reached(Species.COW, 5, 0),
                "Пять коров — лимит превышен (защита от гонки): доска обязана остановить отлов, "
                        + "а не считать перебор нормой");
    }

    @Test
    void reached_CaughtInShelter_Counts() {
        assertTrue(
                HerdCap.reached(Species.PIG, 2, 2),
                "Две свиньи в загоне и две пойманные в укрытии — лимит 4 выбран: пойманное заняло "
                        + "место вида, иначе укрытие обходило бы лимит");
        assertTrue(
                HerdCap.canCapture(Species.PIG, 2, 1),
                "Две в загоне и одна пойманная — третью ловим: до лимита ещё место");
    }

    @Test
    void reached_OtherSpecies_Independent() {
        assertTrue(
                HerdCap.reached(Species.COW, 4, 0),
                "Четыре коровы — лимит выбран: это условие независимости по видам, полные коровы " + "не мешают овцам");
        assertTrue(
                HerdCap.canCapture(Species.SHEEP, 0, 0),
                "Лимит по видам: полные коровы не мешают ловить овец — у каждого вида свой запас");
    }

    @Test
    void reached_NullOrNegative_AreRejected() {
        assertThrows(
                NullPointerException.class,
                () -> HerdCap.reached(null, 0, 0),
                "Вид null обязан отвергаться: без него неизвестно, чей запас считать");
        assertThrows(
                IllegalArgumentException.class,
                () -> HerdCap.reached(Species.COW, -1, 0),
                "Отрицательное поголовье обязано падать: минус коров прятал бы перебор");
        assertThrows(
                IllegalArgumentException.class,
                () -> HerdCap.reached(Species.COW, 0, -1),
                "Отрицательное пойманное обязано падать: минус голов прятал бы перебор");
    }

    @Test
    void herdKey_UnknownKey_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> HerdCap.herdKey("maxPerSpeciesX"),
                "Опечатка в ключе стада обязана падать: молча потерянный ключ оставил бы лимит "
                        + "по умолчанию без ведома настройщика");
    }

    @Property
    void reached_AnyCounts_MatchesSumAgainstLimit(@ForAll("counts") int housed, @ForAll("counts") int caught) {
        int max = HerdCap.maxPerSpecies();

        assertEquals(
                housed + caught >= max,
                HerdCap.reached(Species.COW, housed, caught),
                "Лимит срабатывает ровно при заполнении: housed + caught >= " + max
                        + ", иначе доска либо ловит лишнее, либо держит загон пустым");
        assertEquals(
                !HerdCap.reached(Species.COW, housed, caught),
                HerdCap.canCapture(Species.COW, housed, caught),
                "Отлов разрешён ровно тогда, когда лимит не выбран");
    }

    @Property
    void reached_MoreAnimals_NeverLowersLimit(@ForAll("counts") int housed, @ForAll("counts") int caught) {
        boolean atLimit = HerdCap.reached(Species.COW, housed, caught);

        assertTrue(
                !atLimit || HerdCap.reached(Species.COW, housed + 1, caught),
                "Лимит монотонен: добавление головы не снимает достигнутый лимит");
    }

    @Provide
    Arbitrary<Integer> counts() {
        return Arbitraries.integers().between(0, 6);
    }
}
