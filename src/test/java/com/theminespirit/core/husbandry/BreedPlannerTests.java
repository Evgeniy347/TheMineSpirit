package com.theminespirit.core.husbandry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BreedPlannerTests {
    private static final BlockCoord SPIRIT = new BlockCoord(0, 64, 0);
    private static final Map<String, Integer> FED = Map.of("minecraft:wheat", 4);
    private static final Map<String, Integer> EMPTY = Map.of();

    static Stream<Arguments> breedRows() {
        BlockCoord near = new BlockCoord(1, 64, 0);
        BlockCoord mid = new BlockCoord(3, 64, 0);
        BlockCoord far = new BlockCoord(6, 64, 0);
        return Stream.of(
                Arguments.of(List.of(cow("a", false, true, near), cow("b", false, true, mid)), FED, true, "Feed",
                        "a", "пара готова и корм есть: кормят ближайших двоих"),
                Arguments.of(List.of(cow("a", false, true, near)), FED, true, "NothingToDo", "готов",
                        "готов только один: пару сводить не из кого"),
                Arguments.of(List.of(cow("a", false, true, near), cow("b", false, true, mid)), EMPTY, true,
                        "NeedsMoreFeed", "minecraft:wheat", "нет корма: несут пшеницу"),
                Arguments.of(List.of(cow("a", false, true, near), cow("b", false, true, mid)),
                        Map.of("minecraft:wheat", 1), true, "NeedsMoreFeed", "minecraft:wheat",
                        "корма хватает на одного: не хватает одной единицы"),
                Arguments.of(List.of(cow("a", true, false, near), cow("b", true, false, mid)), FED, true,
                        "NothingToDo", "детёныши", "все детёныши: пару сводят только из взрослых"),
                Arguments.of(List.of(), FED, true, "NothingToDo", "нет COW",
                        "пустое стадо: сводить некого"),
                Arguments.of(List.of(cow("a", false, true, near), cow("b", false, true, mid),
                        cow("c", true, false, far)), FED, true, "NothingToDo", "детёныши",
                        "детёнышей держат: новых пар не сводят"),
                Arguments.of(List.of(cow("a", false, true, near), cow("b", false, true, mid),
                        cow("c", true, false, far)), FED, false, "Feed", "a",
                        "молодняк не держат: пара сводится при детёныше в стаде"),
                Arguments.of(List.of(cow("a", false, true, far), chicken("x", false, true, near),
                        cow("b", false, true, mid)), FED, true, "Feed", "b",
                        "чужой вид не мешает: пара подбирается внутри коров"),
                Arguments.of(List.of(cow("a", false, true, far), cow("b", false, true, mid),
                        cow("c", false, true, near)), FED, true, "Feed", "c",
                        "трое готовых: берутся двое ближайших к духу"),
                Arguments.of(List.of(cow("a", false, false, near), cow("b", false, false, mid)), FED, true,
                        "NothingToDo", "готов", "взрослые не готовы: пару сводить не из кого"),
                Arguments.of(List.of(cow("a", false, true, near), cow("b", false, true, mid),
                        pig("p", false, true, near)), Map.of("minecraft:wheat", 2), true, "Feed", "a",
                        "ровно две единицы корма: паре хватает впритык"));
    }

    @ParameterizedTest(name = "{5}")
    @MethodSource("breedRows")
    void next_HerdTable_PicksDeterministicPairOrRefusal(List<AnimalRef> herd, Map<String, Integer> inventory,
            boolean keepBabies, String outcome, String fragment, String reason) {
        BreedPlanner.Outcome result = BreedPlanner.next(herd, SPIRIT, Species.COW, inventory, keepBabies);

        switch (outcome) {
            case "Feed" -> {
                assertInstanceOf(BreedPlanner.Feed.class, result,
                        "Пара обязана сводиться (" + reason + "): иначе стадо не растёт");
                BreedPlanner.Feed feed = (BreedPlanner.Feed) result;
                assertEquals(fragment, feed.targetId(),
                        "Первой кормят ближайшую к духу (" + reason + "): иначе дух мечется между головами");
            }
            case "NeedsMoreFeed" -> {
                assertInstanceOf(BreedPlanner.NeedsMoreFeed.class, result,
                        "Без корма обязан проситься корм (" + reason + "): иначе дух будет толкать голодное стадо");
                assertEquals(fragment, ((BreedPlanner.NeedsMoreFeed) result).feedId(),
                        "Просить обязаны пшеницу (" + reason + "): чужой корм коровы не едят");
            }
            default -> {
                assertInstanceOf(BreedPlanner.NothingToDo.class, result,
                        "Простой обязан объясняться (" + reason + "): иначе стратег повторит бессмысленный вызов");
                assertTrue(((BreedPlanner.NothingToDo) result).reason().contains(fragment),
                        "Причина обязана содержать «" + fragment + "» (" + reason + "). Фактически: "
                                + ((BreedPlanner.NothingToDo) result).reason());
            }
        }
    }

    @Property(tries = 200)
    void next_FeedIssued_InventoryCoversPair(@ForAll @IntRange(min = 0, max = 6) int wheat,
            @ForAll @IntRange(min = 2, max = 5) int cows) {
        List<AnimalRef> herd = new ArrayList<>();
        for (int i = 0; i < cows; i++) {
            herd.add(cow("cow-" + i, false, true, new BlockCoord(i, 64, 0)));
        }

        BreedPlanner.Outcome result = BreedPlanner.next(herd, SPIRIT, Species.COW,
                Map.of("minecraft:wheat", wheat), false);

        if (result instanceof BreedPlanner.Feed) {
            assertTrue(wheat >= 2,
                    "Разрешение кормить выдаётся только когда корма хватает на пару: иначе вторую голову кормить "
                            + "нечем и пара не сойдётся. Пшеницы: " + wheat);
        } else {
            assertTrue(wheat < 2,
                    "При нехватке корма кормить запрещено: пара не сойдётся, корм уйдёт впустую. Пшеницы: " + wheat);
        }
    }

    @Property(tries = 200)
    void next_SameHerd_ResultIsDeterministic(@ForAll @IntRange(min = 2, max = 6) int cows) {
        List<AnimalRef> herd = new ArrayList<>();
        for (int i = 0; i < cows; i++) {
            herd.add(cow("cow-" + i, false, true, new BlockCoord(i * 2, 64, i)));
        }
        List<AnimalRef> shuffled = new ArrayList<>(herd);
        Collections.shuffle(shuffled);

        BreedPlanner.Outcome first = BreedPlanner.next(herd, SPIRIT, Species.COW, FED, false);
        BreedPlanner.Outcome second = BreedPlanner.next(shuffled, SPIRIT, Species.COW, FED, false);

        assertEquals(first, second,
                "Результат обязан зависеть только от стада, а не от порядка снимка: иначе дух при каждом опросе "
                        + "будет менять пару и не сведёт ни одну");
    }

    private static AnimalRef cow(String id, boolean baby, boolean ready, BlockCoord pos) {
        return new AnimalRef(id, Species.COW, baby, ready, false, pos, true);
    }

    private static AnimalRef pig(String id, boolean baby, boolean ready, BlockCoord pos) {
        return new AnimalRef(id, Species.PIG, baby, ready, false, pos, true);
    }

    private static AnimalRef chicken(String id, boolean baby, boolean ready, BlockCoord pos) {
        return new AnimalRef(id, Species.CHICKEN, baby, ready, false, pos, true);
    }
}
