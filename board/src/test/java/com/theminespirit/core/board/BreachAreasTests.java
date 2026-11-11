package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BreachAreasTests {
    @Test
    void group_AdjacentCells_SingleArea() {
        List<BreachAreas.Area> areas = BreachAreas.group(
                List.of(cell(0, 64, 0, "dirt"), cell(1, 64, 0, "dirt"), cell(0, 64, 1, "dirt"), cell(1, 64, 1, "dirt")),
                8);

        assertEquals(
                List.of(new BreachAreas.Area("dirt", 0, 64, 0, 1, 64, 1)),
                areas,
                "Четыре соседние клетки — одна площадь: одно задание на группу вместо четырёх place_block. "
                        + "Иначе сотни проломов снова забивают доску");
    }

    @Test
    void group_DifferentBlockOrLevel_NotMerged() {
        List<BreachAreas.Area> areas =
                BreachAreas.group(List.of(cell(0, 64, 0, "dirt"), cell(1, 64, 0, "stone"), cell(0, 65, 0, "dirt")), 8);

        assertEquals(
                List.of(
                        new BreachAreas.Area("dirt", 0, 64, 0, 0, 64, 0),
                        new BreachAreas.Area("stone", 1, 64, 0, 1, 64, 0),
                        new BreachAreas.Area("dirt", 0, 65, 0, 0, 65, 0)),
                areas,
                "Площадь fill — один блок на всю область: разные блоки и разные уровни в одну площадь не "
                        + "смешиваются, иначе заделка поставит не тот блок");
    }

    @Test
    void group_HoleInRow_SplitsArea() {
        List<BreachAreas.Area> areas = BreachAreas.group(
                List.of(cell(0, 64, 0, "dirt"), cell(1, 64, 0, "dirt"), cell(3, 64, 0, "dirt"), cell(4, 64, 0, "dirt")),
                8);

        assertEquals(
                List.of(
                        new BreachAreas.Area("dirt", 0, 64, 0, 1, 64, 0),
                        new BreachAreas.Area("dirt", 3, 64, 0, 4, 64, 0)),
                areas,
                "Дырка между клетками (клетка не пролом) делит площадь: fill не должен засыпать лишнюю клетку");
    }

    @Test
    void group_AreaOverLimit_SplitsWithoutExceedingLimit() {
        List<BreachAreas.Cell> cells = new ArrayList<>();
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 4; z++) {
                cells.add(cell(x, 64, z, "dirt"));
            }
        }

        List<BreachAreas.Area> areas = BreachAreas.group(cells, 8);

        assertEquals(
                24,
                areas.stream().mapToInt(BreachAreas.Area::cells).sum(),
                "Деление площади не теряет и не добавляет клеток");
        assertEquals(
                List.of(8, 8, 8),
                areas.stream().map(BreachAreas.Area::cells).toList(),
                "Порция остаётся верхней границей одной площади: fill на 24 клетки не уходит одним заданием");
        assertEquals(
                List.of(0, 2, 4),
                areas.stream().map(area -> area.min().x()).toList(),
                "Деление идёт по большей стороне и сохраняет порядок: площади не перекрываются");
    }

    @Test
    void group_LimitBelowOne_IsReported() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BreachAreas.group(List.of(cell(0, 64, 0, "dirt")), 0),
                "Предел площади меньше одной клетки — ошибка вызывающего: молчаливая пустая группа оставила бы "
                        + "пролом без задания");
    }

    private static BreachAreas.Cell cell(int x, int y, int z, String block) {
        return new BreachAreas.Cell(new BlockCoord(x, y, z), block);
    }
}
