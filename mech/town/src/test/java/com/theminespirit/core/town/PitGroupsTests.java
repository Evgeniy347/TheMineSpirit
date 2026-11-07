package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PitGroupsTests {
    private static final int LEVEL = 70;

    private static Map<BlockCoord, Integer> pit(int minX, int minZ, int width, int depth) {
        Map<BlockCoord, Integer> pits = new LinkedHashMap<>();
        for (int x = minX; x < minX + width; x++) {
            for (int z = minZ; z < minZ + width; z++) {
                pits.put(new BlockCoord(x, LEVEL - 1, z), depth);
            }
        }
        return pits;
    }

    @Test
    void group_OneWidePit_IsSingleTaskWithSizeBlocksAndDepth() {
        Map<BlockCoord, Integer> pits = pit(4, 4, 3, 3);

        List<PitGroups.Pit> groups = PitGroups.group(pits);

        assertEquals(1, groups.size(), "Девять соседних столбцов — одна впадина, а не девять билетов");
        PitGroups.Pit group = groups.get(0);
        assertEquals("4,4:3x3", group.key(), "Ключ ямы — минимальный столбец и размер следа");
        assertEquals(9, group.columns().size(), "На доске видно число клеток ямы");
        assertEquals(27, group.blocks(), "На доске видно число блоков засыпки");
        assertEquals(3, group.depth(), "На доске видна глубина ямы");
        assertEquals(27, group.cells().size(), "Столбец глубины 3 даёт три клетки подсыпки");
    }

    @Test
    void group_LCornerAndNearHoles_JoinIntoOnePit() {
        Map<BlockCoord, Integer> pits = pit(0, 0, 2, 2);
        pits.putAll(pit(0, 2, 1, 1));
        pits.put(new BlockCoord(4, LEVEL - 1, 0), 1);
        pits.put(new BlockCoord(5, LEVEL - 1, 0), 1);

        List<PitGroups.Pit> groups = PitGroups.group(pits);

        assertEquals(
                1,
                groups.size(),
                "L-образная впадина и дырка через два блока — одна работа: дух не ходит по одной клетке");
        assertEquals(
                11,
                groups.get(0).blocks(),
                "Четыре столбца по два блока, угол и две дырки через два блока — одиннадцать клеток");
    }

    @Test
    void group_TwoDistantPits_TwoTasks() {
        Map<BlockCoord, Integer> pits = pit(0, 0, 2, 2);
        pits.putAll(pit(10, 0, 2, 2));

        List<PitGroups.Pit> groups = PitGroups.group(pits);

        assertEquals(2, groups.size(), "Далёкие впадины — две разные работы");
        assertEquals("0,0:2x2", groups.get(0).key(), "Порядок групп устойчив: сначала меньший столбец");
        assertEquals("10,0:2x2", groups.get(1).key(), "Вторая впадина называется своим столбцом");
    }

    @Test
    void group_PartiallyFilled_KeepsSameId() {
        Map<BlockCoord, Integer> pits = pit(4, 4, 3, 3);
        String before = PitGroups.group(pits).get(0).key();

        Map<BlockCoord, Integer> shallower = pit(4, 4, 3, 2);
        PitGroups.Pit after = PitGroups.group(shallower).get(0);

        assertEquals(before, PitGroups.group(shallower).get(0).key(), "Частичная засыпка не плодит новый id ямы");
        assertEquals(18, after.blocks(), "Продолжение идёт той же задачей, блоков становится меньше");
        assertEquals(2, after.depth(), "Глубина уменьшилась на засыпанный слой");
    }

    @Test
    void group_LiveCraterFixture_OneTask() {
        Map<BlockCoord, Integer> pits = new LinkedHashMap<>();
        for (int x = -198; x <= -196; x++) {
            for (int z = -526; z <= -524; z++) {
                pits.put(new BlockCoord(x, 67, z), 5);
            }
        }

        List<PitGroups.Pit> groups = PitGroups.group(pits);

        assertEquals(1, groups.size(), "Живая воронка 3×3 из снимка — одна задача, а не девять билетов");
        assertEquals(45, groups.get(0).blocks(), "9 столбцов по 5 блоков — 45 блоков одной задачей");
        assertEquals("-198,-526:3x3", groups.get(0).key(), "Ключ воронки — её минимальный столбец и размер");
    }

    @Test
    void group_PitAtLotEdge_KeepsWholeFootprint() {
        Map<BlockCoord, Integer> pits = pit(0, 0, 2, 2);

        PitGroups.Pit group = PitGroups.group(pits).get(0);

        assertEquals("0,0:2x2", group.key(), "Яма у края участка не режется границей: работа одна");
        assertEquals(8, group.blocks(), "Край участка не отменяет клетки самой впадины");
    }
}
