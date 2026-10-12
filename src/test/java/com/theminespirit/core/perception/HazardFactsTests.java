package com.theminespirit.core.perception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HazardFactsTests {
    private static final HazardRef LAVA_NEAR = hazard(HazardType.LAVA, 2, 2.0);
    private static final HazardRef LAVA_FAR = hazard(HazardType.LAVA, 7, 7.0);
    private static final HazardRef WATER_MID = hazard(HazardType.WATER, 4, 4.0);
    private static final HazardRef DROP_NEAR = hazard(HazardType.DROP, 3, 3.0);

    private static HazardRef hazard(HazardType type, int x, double distance) {
        return new HazardRef(type, new BlockCoord(x, 63, 0), distance);
    }

    static Stream<Arguments> scenes() {
        return Stream.of(
                Arguments.of("нет опасностей", List.of(), false, false, false, null),
                Arguments.of("только лава", List.of(LAVA_NEAR), true, false, false, LAVA_NEAR),
                Arguments.of("только вода", List.of(WATER_MID), false, true, false, WATER_MID),
                Arguments.of("только обрыв", List.of(DROP_NEAR), false, false, true, DROP_NEAR),
                Arguments.of("смесь", List.of(LAVA_FAR, WATER_MID, DROP_NEAR), true, true, true, DROP_NEAR),
                Arguments.of("две лавы, ближняя первой", List.of(LAVA_NEAR, LAVA_FAR), true, false, false, LAVA_NEAR),
                Arguments.of("две лавы, ближняя второй", List.of(LAVA_FAR, LAVA_NEAR), true, false, false, LAVA_NEAR),
                Arguments.of("вода и обрыв без лавы", List.of(WATER_MID, DROP_NEAR), false, true, true, DROP_NEAR));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenes")
    void facts_Scene_ReportKindsAndNearest(String scene, List<HazardRef> hazards, boolean lava, boolean water,
            boolean drop, HazardRef nearest) {
        HazardFacts facts = new HazardFacts(hazards);

        assertEquals(lava, facts.hasLava(), "hasLava в сцене «" + scene + "» обязан отражать наличие лавы");
        assertEquals(water, facts.hasWater(), "hasWater в сцене «" + scene + "» обязан отражать наличие воды");
        assertEquals(drop, facts.hasDrop(), "hasDrop в сцене «" + scene + "» обязан отражать наличие обрыва");
        assertEquals(nearest, facts.nearest(),
                "nearest в сцене «" + scene + "» обязан давать опасность с наименьшим расстоянием");
        assertEquals(hazards.size(), facts.count(), "count в сцене «" + scene + "» обязан равняться числу опасностей");
    }

    @Test
    void nearest_EqualDistances_PicksSmallestPositionRegardlessOfOrder() {
        HazardRef west = new HazardRef(HazardType.WATER, new BlockCoord(-3, 63, 0), 3.0);
        HazardRef east = new HazardRef(HazardType.LAVA, new BlockCoord(3, 63, 0), 3.0);

        assertEquals(west, new HazardFacts(List.of(east, west)).nearest(),
                "при равном расстоянии выбор обязан зависеть от позиции, а не от порядка списка: иначе стратег "
                        + "получает разные факты об одной и той же сцене");
        assertEquals(west, new HazardFacts(List.of(west, east)).nearest(),
                "перестановка списка не должна менять ближайшую опасность");
    }

    @Test
    void constructor_SourceListChangedAfterwards_FactsKeepOwnCopy() {
        List<HazardRef> source = new ArrayList<>(List.of(LAVA_NEAR));
        HazardFacts facts = new HazardFacts(source);

        source.add(WATER_MID);

        assertEquals(1, facts.count(), "факты обязаны хранить копию списка: правка источника не меняет уже отданное");
    }

    @Test
    void constructor_NullList_IsRejected() {
        assertThrows(NullPointerException.class, () -> new HazardFacts(null),
                "null вместо списка обязан отклоняться: отсутствие опасностей — пустой список, а не null");
        assertNull(new HazardFacts(List.of()).nearest(), "в пустых фактах ближайшей опасности нет");
    }

    @Test
    void closest_MoreHazardsThanLimit_KeepsNearestInOrder() {
        HazardFacts facts = new HazardFacts(List.of(LAVA_FAR, WATER_MID, LAVA_NEAR, DROP_NEAR));

        HazardFacts closest = facts.closest(2);

        assertEquals(List.of(LAVA_NEAR, DROP_NEAR), closest.hazards(),
                "closest(2) обязан оставить две ближайшие опасности по возрастанию расстояния: иначе отчёт "
                        + "sense_hazard теряет ближайшую опасность при переполнении");
    }

    @Test
    void closest_NegativeLimit_IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new HazardFacts(List.of(LAVA_NEAR)).closest(-1),
                "отрицательный предел отчёта обязан отклоняться: иначе ошибка предела сойдёт за «опасностей нет»");
    }

    @Test
    void maxReported_Constant_IsSixtyFour() {
        assertEquals(64, HazardFacts.MAX_REPORTED,
                "отчёт sense_hazard ограничен 64 ближайшими опасностями: обрыв и озеро дают тысячи клеток, "
                        + "а ИИ нужны ближайшие; при смене числа пересмотри размер результата для планировщика");
    }
}
