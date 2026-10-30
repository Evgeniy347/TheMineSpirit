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
    void fenceStageTwo_LevelMapping_UsesWallAndGateFragments() {
        assertEquals(
                StageSchemes.WALL,
                StageSchemes.fence(2),
                "этап 2 поднимает забор до каменной стены: тот же кейс BUILD_FENCE берёт фрагменты стены, а не "
                        + "деревянного забора. Как надо: StageSchemes.fence(stage) для этапа 2 и выше — набор WALL");
        assertTrue(
                StageSchemes.WALL.stream().anyMatch(id -> id.startsWith("wall_3")),
                "у этапа 2 нет ни одного фрагмента каменной стены wall_3: строить финальную границу нечем");
        assertTrue(
                StageSchemes.WALL.stream().anyMatch(id -> id.startsWith("gate_3")),
                "у этапа 2 нет фрагмента ворот gate_3: калитка забора не держит проход каменной стены");
        List<String> missing = StageSchemes.WALL.stream()
                .filter(id -> PRESETS.byId(id).isEmpty())
                .toList();
        assertEquals(
                List.of(),
                missing,
                "фрагменты стены и ворот названы в StageSchemes, но отсутствуют в presets.json/схемах: "
                        + "строитель упадёт на первой клетке линии. Нет схем");
    }

    @Test
    void lightingStageTwo_LevelMapping_UsesSecondLamp() {
        assertEquals(
                "lamp_2",
                YardScheme.lightScheme(2),
                "свет этапа 2 — фонари уровня 2 (lamp_2), а не факел на земле: свет должен пережить дождь и "
                        + "держаться на столбе. Как надо: YardScheme.lightScheme(stage)");
    }
}
