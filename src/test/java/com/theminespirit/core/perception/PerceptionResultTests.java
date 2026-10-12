package com.theminespirit.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.observation.EntityRef;
import com.theminespirit.core.observation.ItemCount;
import com.theminespirit.core.observation.ScanRadius;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PerceptionResultTests {
    private static final ScanRadius RADIUS = ScanRadius.of(6);
    private static final List<ItemCount> BLOCKS = List.of(new ItemCount("minecraft:iron_ore", 3));
    private static final List<EntityRef> ENTITIES = List.of(new EntityRef("minecraft:cow", new BlockCoord(1, 64, 2)));
    private static final List<HazardRef> HAZARDS = List.of(new HazardRef(HazardType.LAVA, new BlockCoord(0, 63, 4), 4.0));

    static Stream<Arguments> combinations() {
        return Stream.of(
                Arguments.of(PerceptionType.BLOCKS, List.of(), List.of(), List.of(), true),
                Arguments.of(PerceptionType.ENTITIES, List.of(), List.of(), List.of(), true),
                Arguments.of(PerceptionType.HAZARDS, List.of(), List.of(), List.of(), true),
                Arguments.of(PerceptionType.BLOCKS, BLOCKS, List.of(), List.of(), true),
                Arguments.of(PerceptionType.ENTITIES, List.of(), ENTITIES, List.of(), true),
                Arguments.of(PerceptionType.HAZARDS, List.of(), List.of(), HAZARDS, true),
                Arguments.of(PerceptionType.BLOCKS, BLOCKS, ENTITIES, HAZARDS, false),
                Arguments.of(PerceptionType.BLOCKS, List.of(), List.of(), HAZARDS, false),
                Arguments.of(PerceptionType.ENTITIES, BLOCKS, ENTITIES, List.of(), false),
                Arguments.of(PerceptionType.HAZARDS, List.of(), ENTITIES, HAZARDS, false));
    }

    @ParameterizedTest(name = "{0}: блоков={1}, сущностей={2}, опасностей={3} -> принят={4}")
    @MethodSource("combinations")
    void constructor_TypeWithLists_AcceptedOnlyWhenListsBelongToType(PerceptionType type, List<ItemCount> blocks,
            List<EntityRef> entities, List<HazardRef> hazards, boolean accepted) {
        if (!accepted) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> new PerceptionResult(type, blocks, entities, hazards, RADIUS),
                    "результат типа " + type + " с чужими списками обязан отклоняться: иначе непонятно, на какой "
                            + "запрос отвечали");
            assertTrue(error.getMessage().contains(type.name()),
                    "отказ обязан называть тип результата " + type + ": " + error.getMessage());
            return;
        }
        PerceptionResult result = new PerceptionResult(type, blocks, entities, hazards, RADIUS);

        assertEquals(blocks, result.blocks(), "блоки результата обязаны сохраняться как заданы");
        assertEquals(entities, result.entities(), "сущности результата обязаны сохраняться как заданы");
        assertEquals(hazards, result.hazards(), "опасности результата обязаны сохраняться как заданы");
        assertEquals(RADIUS, result.radius(), "радиус результата обязан сохраняться как задан");
    }

    @Test
    void factories_EachType_FillOnlyOwnList() {
        assertEquals(new PerceptionResult(PerceptionType.BLOCKS, BLOCKS, List.of(), List.of(), RADIUS),
                PerceptionResult.blocks(RADIUS, BLOCKS), "фабрика blocks обязана класть данные в blocks");
        assertEquals(new PerceptionResult(PerceptionType.ENTITIES, List.of(), ENTITIES, List.of(), RADIUS),
                PerceptionResult.entities(RADIUS, ENTITIES), "фабрика entities обязана класть данные в entities");
        assertEquals(new PerceptionResult(PerceptionType.HAZARDS, List.of(), List.of(), HAZARDS, RADIUS),
                PerceptionResult.hazards(RADIUS, HAZARDS), "фабрика hazards обязана класть данные в hazards");
    }

    @Test
    void constructor_ListWithNull_IsRejected() {
        List<ItemCount> withNull = Arrays.asList(new ItemCount("minecraft:stone", 1), null);

        NullPointerException error = assertThrows(NullPointerException.class,
                () -> PerceptionResult.blocks(RADIUS, withNull),
                "список блоков с null обязан отклоняться: потребитель результата обходит списки без проверок");
        assertTrue(error.getMessage().contains("blocks"), "отказ обязан называть список: " + error.getMessage());
    }

    @Test
    void constructor_SourceListChangedAfterwards_ResultKeepsItsOwnCopy() {
        List<ItemCount> source = new ArrayList<>(BLOCKS);
        PerceptionResult result = PerceptionResult.blocks(RADIUS, source);

        source.add(new ItemCount("minecraft:stone", 9));

        assertEquals(BLOCKS, result.blocks(),
                "результат обязан хранить собственную копию списка: правка источника после тика сканера не должна "
                        + "менять уже отданный результат");
    }

    @Test
    void hazardRef_BadDistance_IsRejected() {
        BlockCoord pos = new BlockCoord(0, 0, 0);

        assertThrows(IllegalArgumentException.class, () -> new HazardRef(HazardType.WATER, pos, -0.5),
                "отрицательное расстояние обязано отклоняться: расстояние — длина, а не смещение");
        assertThrows(IllegalArgumentException.class, () -> new HazardRef(HazardType.WATER, pos, Double.NaN),
                "NaN обязан отклоняться: с ним ломается сравнение опасностей по близости");
        assertThrows(IllegalArgumentException.class,
                () -> new HazardRef(HazardType.WATER, pos, Double.POSITIVE_INFINITY),
                "бесконечность обязана отклоняться: опасность в радиусе скана имеет конечное расстояние");
        assertEquals(0.0, new HazardRef(HazardType.DROP, pos, 0.0).distance(),
                "нулевое расстояние допустимо: опасность может быть под ногами");
    }
}
