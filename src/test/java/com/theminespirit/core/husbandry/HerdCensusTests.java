package com.theminespirit.core.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HerdCensusTests {
    static Stream<Arguments> censusRows() {
        BlockCoord here = new BlockCoord(0, 64, 0);
        BlockCoord there = new BlockCoord(5, 64, 5);
        return Stream.of(
                Arguments.of(List.of(), 0, 0, 0, "пустое стадо даёт пустую сводку"),
                Arguments.of(
                        List.of(cow("a", false, here, true), cow("b", false, there, true)), 2, 0, 0,
                        "две взрослые коровы в загоне"),
                Arguments.of(
                        List.of(cow("a", true, here, true), cow("b", true, there, true)), 0, 2, 0,
                        "только детёныши: все в графе детёнышей"),
                Arguments.of(
                        List.of(cow("a", false, here, false), cow("b", false, there, false)), 2, 0, 2,
                        "все вне загона"),
                Arguments.of(
                        List.of(cow("a", false, here, true), sheep("b", false, there, true),
                                pig("c", true, here, false), chicken("d", false, there, true)),
                        3, 1, 1, "смесь видов: взрослые, детёныш и один вне загона"),
                Arguments.of(
                        List.of(sheep("a", false, here, true), sheep("b", true, here, true),
                                sheep("c", true, there, false)),
                        1, 2, 1, "один вид: взрослый, детёныш в загоне и детёныш вне"),
                Arguments.of(
                        List.of(pig("a", false, here, false), pig("b", true, there, false)), 1, 1, 2,
                        "свиньи вне загона: взрослая и детёныш"),
                Arguments.of(
                        List.of(chicken("a", true, here, true), chicken("b", true, there, true),
                                chicken("c", true, here, false)),
                        0, 3, 1, "только цыплята, один вне загона"),
                Arguments.of(
                        List.of(cow("a", false, here, false), cow("b", true, there, true)), 1, 1, 1,
                        "взрослая вне загона, детёныш внутри"),
                Arguments.of(
                        List.of(cow("a", false, here, true), sheep("b", false, here, true), pig("c", false, here, true),
                                chicken("d", false, here, true), cow("e", true, there, false)),
                        4, 1, 1, "полное стадо четырёх видов плюс телёнок вне загона"));
    }

    @ParameterizedTest(name = "{4}")
    @MethodSource("censusRows")
    void summarize_HerdTable_CountsAdultsBabiesAndEscapees(List<AnimalRef> herd, int adults, int babies,
            int outOfPen, String reason) {
        HerdCensus.Census census = HerdCensus.summarize(herd);

        int countedAdults = 0;
        int countedBabies = 0;
        int countedOut = 0;
        for (Species species : Species.all()) {
            countedAdults += census.of(species).adults();
            countedBabies += census.of(species).babies();
            countedOut += census.of(species).outOfPen();
        }
        assertEquals(adults, countedAdults,
                "Взрослых обязано быть " + adults + " (" + reason + "): иначе пастух сведёт не ту пару");
        assertEquals(babies, countedBabies,
                "Детёнышей обязано быть " + babies + " (" + reason + "): детёныш в графе взрослых пойдёт под нож");
        assertEquals(outOfPen, countedOut,
                "Вне загона обязано быть " + outOfPen + " (" + reason + "): иначе отбившихся не вернут приманкой");
        assertEquals(herd.size(), census.total(),
                "Сводка обязана покрывать всё стадо (" + reason + "): потерянное животное не покормят и не защитят");
    }

    @Property(tries = 300)
    void summarize_AnyHerd_TotalsMatchInputs(@ForAll @IntRange(min = 0, max = 30) int cows,
            @ForAll @IntRange(min = 0, max = 30) int sheep, @ForAll boolean baby, @ForAll boolean inPen) {
        List<AnimalRef> herd = new ArrayList<>();
        for (int i = 0; i < cows; i++) {
            herd.add(cow("cow-" + i, baby, new BlockCoord(i, 64, 0), inPen));
        }
        for (int i = 0; i < sheep; i++) {
            herd.add(sheep("sheep-" + i, baby, new BlockCoord(i, 64, 1), inPen));
        }

        HerdCensus.Census census = HerdCensus.summarize(herd);

        int total = 0;
        for (Species species : Species.all()) {
            HerdCensus.Counts counts = census.of(species);
            total += counts.total();
            assertEquals(counts.adults() + counts.babies(), counts.total(),
                    "Детёныш вида " + species + " обязан считаться ровно в одной графе: иначе сумма по виду "
                            + "разойдётся со входом и пастух перекормит стадо");
        }
        assertEquals(herd.size(), total,
                "Сумма по видам обязана равняться числу входов: потерянное животное не покормят и не защитят");
    }

    private static AnimalRef cow(String id, boolean baby, BlockCoord pos, boolean inPen) {
        return new AnimalRef(id, Species.COW, baby, !baby, false, pos, inPen);
    }

    private static AnimalRef sheep(String id, boolean baby, BlockCoord pos, boolean inPen) {
        return new AnimalRef(id, Species.SHEEP, baby, !baby, true, pos, inPen);
    }

    private static AnimalRef pig(String id, boolean baby, BlockCoord pos, boolean inPen) {
        return new AnimalRef(id, Species.PIG, baby, !baby, false, pos, inPen);
    }

    private static AnimalRef chicken(String id, boolean baby, BlockCoord pos, boolean inPen) {
        return new AnimalRef(id, Species.CHICKEN, baby, !baby, false, pos, inPen);
    }
}
