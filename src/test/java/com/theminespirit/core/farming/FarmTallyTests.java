package com.theminespirit.core.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FarmTallyTests {
    static Stream<Arguments> tallyRows() {
        return Stream.of(
                Arguments.of(FarmJob.TILL, new FarmTally(12, 12, 0, 0, 0, 0, 0, 0, 0), true,
                        "всё вспахано", ""),
                Arguments.of(FarmJob.TILL, new FarmTally(5, 12, 0, 4, 0, 0, 3, 0, 0), false,
                        "частичная вспашка", "вспахано 5 из 12"),
                Arguments.of(FarmJob.TILL, new FarmTally(0, 5, 0, 0, 0, 2, 0, 0, 0), false,
                        "отказ без мотыги до первого действия", "нет мотыги"),
                Arguments.of(FarmJob.TILL, new FarmTally(1, 3, 0, 0, 0, 1, 0, 0, 0), false,
                        "мотыга сломалась посреди вспашки", "мотыга сломалась: вспахано 1 из 3"),
                Arguments.of(FarmJob.TILL, new FarmTally(0, 0, 4, 0, 0, 0, 2, 0, 0), false,
                        "пахать нечего", "подходящей земли для вспашки нет"),
                Arguments.of(FarmJob.PLANT, new FarmTally(9, 9, 0, 0, 0, 0, 0, 0, 0), true,
                        "всё посажено", ""),
                Arguments.of(FarmJob.PLANT, new FarmTally(3, 9, 0, 0, 6, 0, 0, 0, 0), false,
                        "семян не хватило", "посажено 3 из 9"),
                Arguments.of(FarmJob.PLANT, new FarmTally(0, 0, 2, 0, 0, 0, 0, 0, 0), false,
                        "сажать некуда", "пустой вспаханной земли нет"),
                Arguments.of(FarmJob.HARVEST, new FarmTally(5, 9, 4, 0, 0, 0, 0, 0, 5), false,
                        "собрано меньше зрелого", "собрано 5 из 9 зрелых клеток"),
                Arguments.of(FarmJob.HARVEST, new FarmTally(0, 0, 4, 0, 0, 0, 0, 0, 5), false,
                        "зрелого нет", "зрелых посевов в радиусе 5 нет; растёт: 4"),
                Arguments.of(FarmJob.HARVEST, new FarmTally(9, 9, 4, 0, 0, 0, 0, 0, 5), true,
                        "всё зрелое собрано, растущее не мешает успеху", ""),
                Arguments.of(FarmJob.HARVEST, new FarmTally(7, 9, 0, 0, 2, 0, 0, 0, 5), false,
                        "две клетки остались пустыми без семян", "нет семян: 2"),
                Arguments.of(FarmJob.BONE_MEAL, new FarmTally(16, 16, 0, 0, 0, 0, 0, 0, 0), true,
                        "всё подкормлено", ""),
                Arguments.of(FarmJob.BONE_MEAL, new FarmTally(2, 5, 0, 0, 3, 0, 0, 0, 0), false,
                        "муки не хватило", "подкормлено 2 из 5"),
                Arguments.of(FarmJob.BONE_MEAL, new FarmTally(0, 0, 3, 0, 0, 0, 0, 0, 0), false,
                        "растущих нет", "недозрелых посевов нет"),
                Arguments.of(FarmJob.TILL, new FarmTally(0, 3, 0, 0, 0, 0, 0, 3, 0), false,
                        "до клеток не дойти", "недостижимо: 3"));
    }

    @ParameterizedTest(name = "{2}")
    @MethodSource("tallyRows")
    void outcome_TallyTable_MatchesContract(FarmJob job, FarmTally tally, boolean succeeded, String reason,
            String fragment) {
        FarmTally.Outcome outcome = tally.outcome(job);

        if (succeeded) {
            assertInstanceOf(FarmTally.Succeeded.class, outcome,
                    "Полная работа обязана завершиться успехом (" + reason + "): иначе стратег повторит сделанное");
            return;
        }
        assertInstanceOf(FarmTally.Failed.class, outcome,
                "Неполная работа обязана завершиться провалом (" + reason + "): иначе стратег решит, что делать нечего");
        assertTrue(((FarmTally.Failed) outcome).text().contains(fragment),
                "Текст провала обязан содержать «" + fragment + "» (" + reason + "): по числам стратег решает, "
                        + "ждать роста, нести семена или звать духа к другой грядке. Фактический: "
                        + ((FarmTally.Failed) outcome).text());
    }

    @Property
    void outcome_FailedText_ContainsLabelledCounters(@ForAll FarmJob job,
            @ForAll @IntRange(min = 0, max = 20) int done, @ForAll @IntRange(min = 0, max = 20) int requested,
            @ForAll @IntRange(min = 0, max = 5) int ineligible, @ForAll @IntRange(min = 0, max = 5) int noStand,
            @ForAll @IntRange(min = 0, max = 5) int noSeeds, @ForAll @IntRange(min = 0, max = 5) int noTool,
            @ForAll @IntRange(min = 0, max = 5) int dry, @ForAll @IntRange(min = 0, max = 5) int unreachable,
            @ForAll @IntRange(min = 0, max = 10) int radius) {
        FarmTally tally = new FarmTally(done, requested, ineligible, noStand, noSeeds, noTool, dry, unreachable, radius);

        if (!(tally.outcome(job) instanceof FarmTally.Failed failed)) {
            return;
        }
        String text = failed.text();
        if (requested > 0 || job != FarmJob.HARVEST) {
            assertTrue(text.contains(done + " из " + requested),
                    "Прогресс обязан связывать сделано и запрошено одной связкой: иначе «2» из «нет семян: 2» "
                            + "сойдёт за «2 из 12». Текст: " + text);
        }
        assertPair(text, job == FarmJob.HARVEST ? "ещё растёт" : "не подходит", ineligible);
        assertPair(text, "без места для духа", noStand);
        assertPair(text, job == FarmJob.BONE_MEAL ? "нет костной муки" : "нет семян", noSeeds);
        assertPair(text, "без инструмента", noTool);
        assertPair(text, "без воды", dry);
        assertPair(text, "недостижимо", unreachable);
    }

    private static void assertPair(String text, String label, int count) {
        if (count > 0) {
            assertTrue(text.contains(label + ": " + count),
                    "Каждое ненулевое число обязано стоять при своей метке «" + label + "»: стратег читает числа, "
                            + "а не угадывает их. Текст: " + text);
        }
    }

    @Property
    void outcome_FullWorkWithoutBlocks_IsSucceeded(@ForAll FarmJob job,
            @ForAll @IntRange(min = 1, max = 100) int requested,
            @ForAll @IntRange(min = 0, max = 10) int radius) {
        FarmTally tally = new FarmTally(requested, requested, 0, 0, 0, 0, 0, 0, radius);

        assertEquals(new FarmTally.Succeeded(), tally.outcome(job),
                "Вся подходящая работа без блокировок обязана завершиться успехом: иначе стратег зациклит "
                        + "готовую грядку");
    }
}
