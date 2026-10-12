package com.theminespirit.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.IdFilter;
import com.theminespirit.core.observation.ScanRadius;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LocateQueryTests {
    static Stream<Arguments> queries() {
        return Stream.of(
                Arguments.of(LocateType.BLOCK, List.of("minecraft:diamond_ore"), "any", ScanRadius.MIN, true),
                Arguments.of(LocateType.BLOCK, List.of("*_ore"), "any", ScanRadius.MAX, true),
                Arguments.of(LocateType.BLOCK, List.of("*"), "any", 8, true),
                Arguments.of(LocateType.TREE, List.of(), "oak", 8, true),
                Arguments.of(LocateType.TREE, List.of(), "dark_oak", ScanRadius.MAX, true),
                Arguments.of(LocateType.TREE, List.of(), "any", ScanRadius.MIN, true),
                Arguments.of(LocateType.CONTAINER, List.of(), "any", 8, true),
                Arguments.of(LocateType.CONTAINER, List.of("*"), "any", ScanRadius.MAX, true),
                Arguments.of(LocateType.TREE, List.of("*_log"), "oak", 8, false),
                Arguments.of(LocateType.CONTAINER, List.of("minecraft:chest"), "any", 8, false),
                Arguments.of(LocateType.BLOCK, List.of("*_ore"), "oak", 8, false),
                Arguments.of(LocateType.CONTAINER, List.of(), "oak", 8, false),
                Arguments.of(LocateType.TREE, List.of(), "Oak Tree", 8, false),
                Arguments.of(LocateType.TREE, List.of(), "", 8, false),
                Arguments.of(LocateType.BLOCK, List.of("*_ore"), "any", ScanRadius.MIN - 1, false),
                Arguments.of(LocateType.TREE, List.of(), "oak", ScanRadius.MAX + 1, false));
    }

    @ParameterizedTest(name = "{0} {1} вид={2} r={3} -> принят={4}")
    @MethodSource("queries")
    void constructor_TypeFilterSpeciesRadius_AcceptedOnlyWhenConsistent(LocateType type, List<String> patterns,
            String species, int radius, boolean accepted) {
        if (!accepted) {
            assertThrows(IllegalArgumentException.class,
                    () -> new LocateQuery(type, IdFilter.of(patterns), species, new ScanRadius(radius)),
                    "поиск " + type + " с фильтром " + patterns + ", видом '" + species + "' и радиусом " + radius
                            + " обязан отклоняться: иначе скрипт поиска получит параметр, который не применит");
            return;
        }
        LocateQuery query = new LocateQuery(type, IdFilter.of(patterns), species, new ScanRadius(radius));

        assertEquals(type, query.type(), "тип поиска обязан сохраняться как задан: по нему скрипт выбирает поисковик");
        assertEquals(species, query.species(), "вид дерева обязан сохраняться как задан");
        assertEquals(radius, query.radius().value(), "радиус поиска обязан сохраняться как задан");
        assertEquals(IdFilter.of(patterns).patterns(), query.filter().patterns(),
                "фильтр поиска обязан сохраняться как задан: иначе поиск вернёт не тот блок");
    }

    @Test
    void factories_EachType_BuildQueryWithDefaultsForForeignFields() {
        ScanRadius radius = ScanRadius.of(5);

        LocateQuery block = LocateQuery.block(IdFilter.of(List.of("*_ore")), radius);
        assertEquals(LocateType.BLOCK, block.type(), "фабрика block обязана строить поиск BLOCK");
        assertEquals(LocateQuery.ANY_SPECIES, block.species(), "у поиска блока вид обязан быть any");
        LocateQuery tree = LocateQuery.tree("birch", radius);
        assertEquals(LocateType.TREE, tree.type(), "фабрика tree обязана строить поиск TREE");
        assertTrue(tree.filter().matchesAll(), "у поиска дерева фильтр обязан быть «всё»");
        LocateQuery container = LocateQuery.container(radius);
        assertEquals(LocateType.CONTAINER, container.type(), "фабрика container обязана строить поиск CONTAINER");
        assertEquals(LocateQuery.ANY_SPECIES, container.species(), "у поиска контейнера вид обязан быть any");
    }

    @Test
    void anySpecies_Constant_IsLowercaseAny() {
        assertEquals("any", LocateQuery.ANY_SPECIES,
                "значение по умолчанию для вида дерева обязано быть any: так его называет каталог скриптов");
    }
}
