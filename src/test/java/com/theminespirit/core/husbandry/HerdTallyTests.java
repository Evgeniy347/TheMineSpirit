package com.theminespirit.core.husbandry;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HerdTallyTests {
    static Stream<Arguments> tallyRows() {
        return Stream.of(
                Arguments.of(HerdJob.FEED, new HerdTally(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4), false,
                        "ноль по всем — делать нечего", "нечего делать"),
                Arguments.of(HerdJob.BREED, new HerdTally(2, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1), true,
                        "пара сведена", "сведено пар: 1"),
                Arguments.of(HerdJob.BREED, new HerdTally(2, 0, 1, 0, 0, 0, 0, 0, 0, 0, 2), false,
                        "вторая пара не сошлась", "сведено пар: 1 из 2"),
                Arguments.of(HerdJob.FEED, new HerdTally(4, 2, 0, 0, 0, 0, 0, 0, 0, 0, 4), true,
                        "покормлены все, детёныши первыми", "из них детёнышей 2"),
                Arguments.of(HerdJob.SHEAR, new HerdTally(0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 3), true,
                        "все овцы пострижены", "пострижено 3 из 3"),
                Arguments.of(HerdJob.MILK, new HerdTally(0, 0, 0, 0, 2, 0, 0, 0, 0, 0, 4), false,
                        "вёдер не хватило", "подоено 2 из 4"),
                Arguments.of(HerdJob.COLLECT_EGGS, new HerdTally(0, 0, 0, 0, 0, 5, 0, 0, 0, 0, 0), true,
                        "яйца подобраны", "подобрано яиц: 5"),
                Arguments.of(HerdJob.CULL, new HerdTally(0, 0, 0, 0, 0, 0, 2, 3, 0, 0, 3), true,
                        "лишние забиты, осталось трое", "содержится 3 из 3"),
                Arguments.of(HerdJob.CULL, new HerdTally(0, 0, 0, 0, 0, 0, 0, 5, 0, 2, 3), false,
                        "двоих у воды бить нельзя", "содержится 5 из 3"),
                Arguments.of(HerdJob.CULL, new HerdTally(0, 0, 0, 0, 0, 0, 0, 4, 0, 0, 4), true,
                        "лишних нет, стадо в норме", "содержится 4 из 4"),
                Arguments.of(HerdJob.HERD_CHECK, new HerdTally(0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0), true,
                        "отбившийся учтён", "возвращено: 1"),
                Arguments.of(HerdJob.HUNT, new HerdTally(0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 3), true,
                        "дикие забиты", "забито диких: 3 из 3"));
    }

    @ParameterizedTest(name = "{3}")
    @MethodSource("tallyRows")
    void outcome_TallyTable_MatchesContract(HerdJob job, HerdTally tally, boolean succeeded, String reason,
            String fragment) {
        HerdTally.Outcome outcome = tally.outcome(job);

        if (succeeded) {
            assertInstanceOf(HerdTally.Succeeded.class, outcome,
                    "Готовая работа обязана завершиться успехом (" + reason + "): иначе стратег повторит сделанное");
            assertTrue(((HerdTally.Succeeded) outcome).text().contains(fragment),
                    "Текст успеха обязан содержать «" + fragment + "» (" + reason + "): по числам стратег решает, "
                            + "что делать дальше. Фактический: " + ((HerdTally.Succeeded) outcome).text());
            return;
        }
        assertInstanceOf(HerdTally.Failed.class, outcome,
                "Недоделанная работа обязана завершиться провалом (" + reason + "): иначе стратег решит, что делать "
                        + "нечего");
        assertTrue(((HerdTally.Failed) outcome).text().contains(fragment),
                "Текст провала обязан содержать «" + fragment + "» (" + reason + "): по числам стратег решает, ждать "
                        + "ли, нести ли корм. Фактический: " + ((HerdTally.Failed) outcome).text());
    }

    @Property(tries = 200)
    void outcome_AnyText_ContainsEveryNonZeroCounter(@ForAll HerdJob job,
            @ForAll @IntRange(min = 0, max = 9) int fed, @ForAll @IntRange(min = 0, max = 9) int babiesFed,
            @ForAll @IntRange(min = 0, max = 9) int born, @ForAll @IntRange(min = 0, max = 9) int sheared,
            @ForAll @IntRange(min = 0, max = 9) int milked, @ForAll @IntRange(min = 0, max = 9) int eggsPicked,
            @ForAll @IntRange(min = 0, max = 9) int culled, @ForAll @IntRange(min = 0, max = 9) int kept,
            @ForAll @IntRange(min = 0, max = 9) int escapedReturned,
            @ForAll @IntRange(min = 0, max = 9) int skipped) {
        HerdTally tally = new HerdTally(fed, babiesFed, born, sheared, milked, eggsPicked, culled, kept,
                escapedReturned, skipped, 4);
        String text = switch (tally.outcome(job)) {
            case HerdTally.Succeeded success -> success.text();
            case HerdTally.Failed failure -> failure.text();
        };

        assertPair(text, "покормлено", fed);
        assertPair(text, "детёнышей", babiesFed);
        assertPair(text, "родилось", born);
        assertPair(text, "пострижено", sheared);
        assertPair(text, "подоено", milked);
        assertPair(text, "яиц", eggsPicked);
        assertPair(text, "забито", culled);
        assertPair(text, "содержится", kept);
        assertPair(text, "возвращено", escapedReturned);
        assertPair(text, "пропущено опасных", skipped);
    }

    private static void assertPair(String text, String label, int count) {
        if (count > 0) {
            assertTrue(text.contains(label + ": " + count),
                    "Каждое ненулевое число обязано стоять при своей метке «" + label + "»: стратег читает числа, "
                            + "а не угадывает их. Текст: " + text);
        }
    }
}
