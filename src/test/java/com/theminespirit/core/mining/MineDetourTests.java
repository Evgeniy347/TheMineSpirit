package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MineDetourTests {
    private static final MineColumn PARENT = MineColumn.passage(0, 0, 0);
    private static final MineColumn BLOCKED = MineColumn.passage(1, -1, 0);
    private static final MineColumn FORWARD = MineColumn.passage(2, -2, 0);

    @Test
    void around_DescentStepWithOre_SideColumnsKeepStepHeights() {
        List<MineColumn> detour =
                MineDetour.around(PARENT, BLOCKED, FORWARD, cell -> false).orElseThrow();

        assertEquals(3, detour.size(), "обход ступени — три колонки сбоку: у родителя, у руды и у следующей");
        List<MineColumn> path = new ArrayList<>();
        path.add(PARENT);
        path.addAll(detour);
        path.add(FORWARD);
        for (int i = 1; i < path.size(); i++) {
            assertTrue(
                    path.get(i - 1).walksTo(path.get(i)),
                    "путь родитель → обход → следующая обязан идти пешком (шаг ≤ 1 блока, соседние клетки), "
                            + "иначе дух застрянет и не выйдет наверх; разрыв между " + path.get(i - 1) + " и "
                            + path.get(i));
        }
        assertTrue(
                detour.stream().noneMatch(column -> column.contains(new BlockCoord(1, 1, 0))),
                "обход не вскрывает клетки оставленной колонки — руда остаётся на месте");
    }

    @Test
    void around_RightSideHasLuckOre_UsesLeftSide() {
        BlockCoord oreOnRight = new BlockCoord(1, 0, -1);

        List<MineColumn> detour =
                MineDetour.around(PARENT, BLOCKED, FORWARD, oreOnRight::equals).orElseThrow();

        assertTrue(
                detour.stream().allMatch(column -> column.floor().z() == 1),
                "справа снова руда на удачу — обход идёт слева; фактически " + detour);
    }

    @Test
    void around_BothSidesBlocked_ReturnsEmpty() {
        Set<BlockCoord> ores = Set.of(new BlockCoord(1, 0, -1), new BlockCoord(1, 0, 1));

        assertEquals(
                Optional.empty(),
                MineDetour.around(PARENT, BLOCKED, FORWARD, ores::contains),
                "руда с обеих сторон — обхода нет, стройка называет причину, а не копает в руду");
    }

    @Test
    void around_LastColumn_TwoColumnsOnly() {
        List<MineColumn> detour =
                MineDetour.around(PARENT, BLOCKED, null, cell -> false).orElseThrow();

        assertEquals(
                2,
                detour.size(),
                "у последней колонки нет следующей — обход доходит только до её уровня; фактически " + detour);
    }
}
