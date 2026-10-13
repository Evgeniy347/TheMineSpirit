package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BuildOrderTests {
    @Test
    void placement_LowerBeforeUpper_KeepsBottomUp() {
        BlockCoord lower = new BlockCoord(0, 1, 0);
        BlockCoord upper = new BlockCoord(0, 3, 0);

        List<BlockCoord> ordered = BuildOrder.placement(List.of(upper, lower), Set.of());

        assertTrue(ordered.indexOf(lower) < ordered.indexOf(upper),
                "Блок нижнего слоя обязан ставиться раньше верхнего: иначе верхний ставится без опоры");
    }

    @Test
    void placement_OccupiedBySpirit_MovesToTail() {
        BlockCoord floor = new BlockCoord(0, 1, 0);
        BlockCoord feet = new BlockCoord(0, 2, 0);
        BlockCoord head = new BlockCoord(0, 3, 0);

        List<BlockCoord> ordered = BuildOrder.placement(List.of(feet, floor, head), Set.of(feet, head));

        assertEquals(List.of(floor, feet, head), ordered,
                "Ячейки духа обязаны уходить в хвост: иначе дух замуровывает себя и остаётся внутри стены");
    }

    @Test
    void placement_AnyInput_IsPermutation() {
        List<BlockCoord> cells = List.of(new BlockCoord(1, 2, 1), new BlockCoord(0, 1, 0), new BlockCoord(0, 3, 2));

        List<BlockCoord> ordered = BuildOrder.placement(cells, Set.of());

        assertEquals(new HashSet<>(cells), new HashSet<>(ordered),
                "Раскладка обязана быть перестановкой входа: потеря ячейки даёт дырку, дубль — двойной расход");
        assertEquals(cells.size(), ordered.size(),
                "Раскладка обязана сохранять число ячеек: иначе часть коробки не строится");
    }

    @Test
    void placement_EmptyInput_GivesEmpty() {
        assertEquals(List.of(), BuildOrder.placement(List.of(), Set.of(new BlockCoord(0, 1, 0))),
                "Пустой вход обязан давать пустой выход: иначе пустая стройка ставит лишний блок");
    }

    @Test
    void placement_SeveralOccupied_KeepRelativeOrder() {
        BlockCoord first = new BlockCoord(0, 1, 0);
        BlockCoord second = new BlockCoord(1, 1, 0);
        BlockCoord free = new BlockCoord(0, 5, 0);

        List<BlockCoord> ordered = BuildOrder.placement(List.of(second, free, first), Set.of(first, second));

        assertTrue(ordered.indexOf(free) < ordered.indexOf(first) && ordered.indexOf(first) < ordered.indexOf(second),
                "Несколько занятых ячеек обязаны сохранять относительный порядок в хвосте: иначе порядок становится недетерминированным");
    }
}
