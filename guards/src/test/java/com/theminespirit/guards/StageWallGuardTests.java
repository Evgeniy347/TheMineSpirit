package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.building.PresetLibrary;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.StageSchemes;
import com.theminespirit.core.town.YardScheme;
import java.util.List;
import org.junit.jupiter.api.Test;

class StageWallGuardTests {
    private static final PresetLibrary PRESETS = DataTables.INSTANCE.presets().current();

    @Test
    void wallStageThree_LevelMapping_UsesWallAndGateFragments() {
        assertEquals(
                StageSchemes.WALL,
                StageSchemes.wall(),
                "кейс BUILD_FENCE берёт фрагменты каменной стены, а не деревянного забора: временного забора "
                        + "в этапах больше нет"
                        + " Иначе тест wallStageThree_LevelMapping_UsesWallAndGateFragments перестанет ловить эту поломку. При смене wallStageThree_LevelMapping_UsesWallAndGateFragments проверь это утверждение.");
        assertTrue(
                StageSchemes.WALL.stream().anyMatch(id -> id.startsWith("wall_3")),
                "у этапа 3 нет ни одного фрагмента каменной стены wall_3: строить финальную границу нечем"
                        + " Иначе тест wallStageThree_LevelMapping_UsesWallAndGateFragments перестанет ловить эту поломку. При смене wallStageThree_LevelMapping_UsesWallAndGateFragments проверь это утверждение.");
        assertTrue(
                StageSchemes.WALL.stream().anyMatch(id -> id.startsWith("gate_3")),
                "у этапа 3 нет фрагмента ворот gate_3: калитка забора не держит проход каменной стены"
                        + " Иначе тест wallStageThree_LevelMapping_UsesWallAndGateFragments перестанет ловить эту поломку. При смене wallStageThree_LevelMapping_UsesWallAndGateFragments проверь это утверждение.");
        List<String> missing = StageSchemes.WALL.stream()
                .filter(id -> PRESETS.byId(id).isEmpty())
                .toList();
        assertEquals(
                List.of(),
                missing,
                "фрагменты стены и ворот названы в StageSchemes, но отсутствуют в presets.json/схемах: "
                        + "строитель упадёт на первой клетке линии. Нет схем"
                        + " Иначе тест wallStageThree_LevelMapping_UsesWallAndGateFragments перестанет ловить эту поломку. При смене wallStageThree_LevelMapping_UsesWallAndGateFragments проверь это утверждение.");
    }

    @Test
    void lightingStageTwo_LevelMapping_UsesSecondLamp() {
        assertEquals(
                "lamp_2",
                YardScheme.lightScheme(2),
                "свет этапа 2 — фонари уровня 2 (lamp_2), а не факел на земле: свет должен пережить дождь и "
                        + "держаться на столбе. Как надо: YardScheme.lightScheme(stage)"
                        + " Иначе тест lightingStageTwo_LevelMapping_UsesSecondLamp перестанет ловить эту поломку. При смене lightingStageTwo_LevelMapping_UsesSecondLamp проверь это утверждение.");
    }
}
