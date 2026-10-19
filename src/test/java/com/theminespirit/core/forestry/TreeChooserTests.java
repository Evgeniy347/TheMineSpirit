package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TreeChooserTests {
    @Test
    void next_NearestLog_IsChosen() {
        List<BlockCoord> logs = List.of(new BlockCoord(2, 0, 0), new BlockCoord(1, 0, 0));

        assertEquals(
                Optional.of(new BlockCoord(1, 0, 0)),
                TreeChooser.next(0, 0, 0, logs, Set.of()),
                "TreeChooser обязан выбрать ближайшее бревно: дух начинает рубку с ближайшего дерева, "
                        + "чтобы не ходить через весь лес");
    }

    @Test
    void next_KnownNearest_IsSkipped() {
        List<BlockCoord> logs = List.of(new BlockCoord(2, 0, 0), new BlockCoord(1, 0, 0));

        assertEquals(
                Optional.of(new BlockCoord(2, 0, 0)),
                TreeChooser.next(0, 0, 0, logs, Set.of(new BlockCoord(1, 0, 0))),
                "Разобранное или отвергнутое бревно обязано исключаться из выбора: иначе по одному дереву "
                        + "запускается повторный разбор на каждом его бревне");
    }

    @Test
    void next_EqualDistance_PrefersLowerY() {
        List<BlockCoord> logs = List.of(new BlockCoord(1, 1, 0), new BlockCoord(1, -1, 0));

        assertEquals(
                Optional.of(new BlockCoord(1, -1, 0)),
                TreeChooser.next(0, 0, 0, logs, Set.of()),
                "Ничья по расстоянию решается в пользу более низкого бревна: нижнее ближе к основанию "
                        + "и даёт больше срубленного ствола за один заход");
    }

    @Test
    void next_EqualDistanceAndY_PrefersLowerXThenZ() {
        List<BlockCoord> logs = List.of(new BlockCoord(1, 0, 1), new BlockCoord(-1, 0, 1));

        assertEquals(
                Optional.of(new BlockCoord(-1, 0, 1)),
                TreeChooser.next(0, 0, 0, logs, Set.of()),
                "Ничья по расстоянию и высоте решается в пользу меньшего x: выбор обязан быть детерминированным, "
                        + "иначе один и тот же лес рубится каждый раз в разном порядке");

        assertEquals(
                Optional.of(new BlockCoord(0, 0, -2)),
                TreeChooser.next(0, 0, 0, List.of(new BlockCoord(0, 0, 2), new BlockCoord(0, 0, -2)), Set.of()),
                "Ничья по x решается в пользу меньшего z: без этого правила выбор зависит от порядка списка");
    }

    @Test
    void next_AllLogsKnown_IsEmpty() {
        List<BlockCoord> logs = List.of(new BlockCoord(1, 0, 0), new BlockCoord(2, 0, 0));

        assertEquals(
                Optional.empty(),
                TreeChooser.next(0, 0, 0, logs, Set.of(new BlockCoord(1, 0, 0), new BlockCoord(2, 0, 0))),
                "Когда все найденные брёвна уже разобраны или отвергнуты, выбирать нечего: пустой результат "
                        + "останавливает рубку без падения");
    }
}
