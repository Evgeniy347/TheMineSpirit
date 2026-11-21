package com.theminespirit.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FindingTests {
    private static final BlockCoord ORIGIN = new BlockCoord(0, 64, 0);

    static Stream<Arguments> located() {
        return Stream.of(
                Arguments.of(
                        LocateType.BLOCK,
                        "locate_block",
                        "block",
                        "minecraft:diamond_ore",
                        new BlockCoord(3, 64, 4),
                        "расстояние 5.0"),
                Arguments.of(
                        LocateType.TREE,
                        "locate_tree",
                        "tree",
                        "minecraft:oak_log",
                        new BlockCoord(0, 64, 6),
                        "расстояние 6.0"),
                Arguments.of(
                        LocateType.CONTAINER,
                        "locate_container",
                        "container",
                        "minecraft:chest",
                        new BlockCoord(0, 65, -1),
                        "расстояние 1.4"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("located")
    void located_EachType_NamesScriptKindLabelPositionAndDistance(
            LocateType type, String script, String kind, String label, BlockCoord pos, String distance) {
        Finding finding = Finding.located(type, label, pos, ORIGIN);

        assertEquals(
                new Finding(script, List.of(new Sighting(kind, label, pos, distance))),
                finding,
                "находка " + type + " обязана назвать скрипт, вид, id блока, позицию и расстояние: по ним ИИ решает, "
                        + "куда идти"
                        + " Иначе тест located_EachType_NamesScriptKindLabelPositionAndDistance перестанет ловить эту поломку. При смене located_EachType_NamesScriptKindLabelPositionAndDistance проверь это утверждение.");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("located")
    void notFound_EachType_IsEmptyFindingOfSameScript(
            LocateType type, String script, String kind, String label, BlockCoord pos, String distance) {
        Finding finding = Finding.notFound(type);

        assertEquals(
                script,
                finding.script(),
                "«не найдено» обязано называть тот же скрипт, что и находка"
                        + " Иначе тест notFound_EachType_IsEmptyFindingOfSameScript перестанет ловить эту поломку. При смене notFound_EachType_IsEmptyFindingOfSameScript проверь это утверждение.");
        assertTrue(
                finding.isEmpty(),
                "«не найдено» — пустой список находок, а не выдуманная позиция"
                        + " Иначе тест notFound_EachType_IsEmptyFindingOfSameScript перестанет ловить эту поломку. При смене notFound_EachType_IsEmptyFindingOfSameScript проверь это утверждение.");
    }

    @Test
    void constructor_NullSighting_IsRejected() {
        List<Sighting> withNull = new ArrayList<>();
        withNull.add(null);

        assertThrows(
                NullPointerException.class,
                () -> new Finding("recall", withNull),
                "список находок с null обязан отклоняться: пустая находка скрыла бы, что скрипт сломан"
                        + " Иначе тест constructor_NullSighting_IsRejected перестанет ловить эту поломку. При смене constructor_NullSighting_IsRejected проверь это утверждение.");
    }

    @Test
    void constructor_BlankScript_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Finding(" ", List.of()),
                "находка без имени скрипта обязана отклоняться: по имени понятно, на какой вопрос она отвечает"
                        + " Иначе тест constructor_BlankScript_IsRejected перестанет ловить эту поломку. При смене constructor_BlankScript_IsRejected проверь это утверждение.");
    }

    @Test
    void sighting_BlankKind_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Sighting("", "minecraft:chest", ORIGIN, ""),
                "находка без вида обязана отклоняться: по виду ИИ выбирает, как её читать"
                        + " Иначе тест sighting_BlankKind_IsRejected перестанет ловить эту поломку. При смене sighting_BlankKind_IsRejected проверь это утверждение.");
    }
}
