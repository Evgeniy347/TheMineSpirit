package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MineFluidsTests {
    private static final BlockCoord CELL = new BlockCoord(5, 10, 5);

    @Test
    void plugsBefore_WaterInWall_PlugsSourceBeforeOpening() {
        BlockCoord wall = new BlockCoord(5, 10, 6);

        assertEquals(
                List.of(wall),
                MineFluids.plugsBefore(CELL, Set.of(), wall::equals),
                "вода в стене рядом с вскрываемой клеткой перекрывается булыжником до ломки — иначе проход затопит "
                        + "(INC-19, дух утонул)");
    }

    @Test
    void plugsBefore_WaterInCellAndCeiling_PlugsCellFirst() {
        BlockCoord ceiling = new BlockCoord(5, 11, 5);
        Set<BlockCoord> water = Set.of(CELL, ceiling);

        assertEquals(
                List.of(CELL, ceiling),
                MineFluids.plugsBefore(CELL, Set.of(), water::contains),
                "вода в самой клетке — пробка в неё первой: она станет опорой пробке потолка; порядок важен");
    }

    @Test
    void plugsBefore_OpenPassageNeighbour_NotPlugged() {
        BlockCoord passage = new BlockCoord(4, 10, 5);

        assertEquals(
                List.of(),
                MineFluids.plugsBefore(CELL, Set.of(passage), passage::equals),
                "уже открытый проход не закладывается — дух бы замуровал себе выход");
    }

    @Test
    void plugsBefore_DryRock_NothingToPlug() {
        assertEquals(List.of(), MineFluids.plugsBefore(CELL, Set.of(), cell -> false), "сухая порода — пробок нет");
    }
}
