package com.theminespirit.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScaffoldTrailTests {
    @Test
    void add_TwoClimbs_KeepsCellsOfBothColumns() {
        ScaffoldTrail trail = new ScaffoldTrail();
        trail.add(List.of(new BlockCoord(1, 70, 1), new BlockCoord(1, 71, 1)));
        trail.add(List.of(new BlockCoord(5, 70, 5)));

        assertEquals(
                List.of(new BlockCoord(1, 70, 1), new BlockCoord(1, 71, 1), new BlockCoord(5, 70, 5)),
                trail.cells(),
                "Каждый подъём добавляет клетки к прежним: спуск обязан разобрать все столбы, а не последний");
        assertFalse(trail.empty(), "Две колонки — не пустой след подмостей");
    }

    @Test
    void topDown_MixedColumns_ReturnsHighestFirst() {
        ScaffoldTrail trail = new ScaffoldTrail();
        trail.add(List.of(new BlockCoord(1, 70, 1), new BlockCoord(1, 71, 1)));
        trail.add(List.of(new BlockCoord(5, 72, 5), new BlockCoord(5, 73, 5)));

        assertEquals(
                List.of(
                        new BlockCoord(5, 73, 5),
                        new BlockCoord(5, 72, 5),
                        new BlockCoord(1, 71, 1),
                        new BlockCoord(1, 70, 1)),
                trail.topDown(),
                "Спуск идёт сверху вниз через все колонки: нижняя клетка держит духа последней");
    }

    @Test
    void add_RepeatedColumn_DoesNotDuplicateCells() {
        ScaffoldTrail trail = new ScaffoldTrail();
        trail.add(List.of(new BlockCoord(1, 70, 1)));
        trail.add(List.of(new BlockCoord(1, 70, 1)));

        assertEquals(
                List.of(new BlockCoord(1, 70, 1)),
                trail.cells(),
                "Повторный подъём по той же клетке не записывает её дважды: иначе разбор ударил бы блок дважды");
    }

    @Test
    void empty_NoClimbs_HasNothingToDismantle() {
        assertTrue(new ScaffoldTrail().empty(), "Без подъёмов подмостей нет");
    }
}
