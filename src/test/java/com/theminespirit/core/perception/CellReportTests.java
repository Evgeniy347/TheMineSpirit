package com.theminespirit.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CellReportTests {
    private static final BlockCoord POS = new BlockCoord(4, 65, 4);

    private static Sighting at(String kind, String label) {
        return new Sighting(kind, label, POS, "");
    }

    static Stream<Arguments> cells() {
        return Stream.of(
                Arguments.of("воздух", Optional.of(new LoadedCell("minecraft:air", false, List.of())),
                        List.of(at("air", "minecraft:air"))),
                Arguments.of("воздух пещеры", Optional.of(new LoadedCell("minecraft:cave_air", false, List.of())),
                        List.of(at("air", "minecraft:cave_air"))),
                Arguments.of("блок", Optional.of(new LoadedCell("minecraft:stone", false, List.of())),
                        List.of(at("block", "minecraft:stone"))),
                Arguments.of("сундук", Optional.of(new LoadedCell("minecraft:chest", true, List.of())),
                        List.of(at("block", "minecraft:chest"), at("container", "minecraft:chest"))),
                Arguments.of("корова в воздухе", Optional.of(new LoadedCell("minecraft:air", false,
                        List.of("minecraft:cow"))), List.of(at("air", "minecraft:air"), at("entity", "minecraft:cow"))),
                Arguments.of("две сущности в блоке", Optional.of(new LoadedCell("minecraft:grass_block", false,
                        List.of("minecraft:cow", "minecraft:sheep"))),
                        List.of(at("block", "minecraft:grass_block"), at("entity", "minecraft:cow"),
                                at("entity", "minecraft:sheep"))),
                Arguments.of("чанк не загружен", Optional.empty(),
                        List.of(new Sighting("unloaded", "chunk_not_loaded", POS,
                                "чанк не загружен, клетка пропущена"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cells")
    void describe_Cell_ListsBlockContainerEntitiesOrSkip(String scene, Optional<LoadedCell> cell,
            List<Sighting> expected) {
        Finding finding = CellReport.describe(POS, cell);

        assertEquals(new Finding("look_at", expected), finding,
                scene + ": описание клетки обязано называть блок, контейнер и сущности, а незагруженную клетку — "
                        + "пропускать; иначе ИИ примет пропуск за воздух");
    }

    @Test
    void loadedCell_NullEntityType_IsRejected() {
        List<String> withNull = new ArrayList<>();
        withNull.add(null);

        assertThrows(NullPointerException.class, () -> new LoadedCell("minecraft:air", false, withNull),
                "сущность без типа обязана отклоняться: описание клетки не может назвать безымянное");
    }
}
