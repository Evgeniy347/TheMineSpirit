package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StrayGroupsTests {
    @Test
    void groups_ConnectedFence_IsOnePieceWithCount() {
        List<StrayGroups.Group> groups = StrayGroups.of(
                List.of(
                        new StrayGroups.Cell(new BlockCoord(1, 70, 1), "minecraft:oak_fence"),
                        new StrayGroups.Cell(new BlockCoord(2, 70, 1), "minecraft:oak_fence"),
                        new StrayGroups.Cell(new BlockCoord(3, 70, 1), "minecraft:oak_fence")),
                Map.of());

        assertEquals(1, groups.size(), "связный забор — один кусок, а не три");
        assertEquals(3, groups.get(0).size(), "в куске видно, сколько блоков сносить");
        assertEquals("oak_fence", groups.get(0).type(), "тип куска называется по преобладающему блоку");
        assertEquals(Map.of("oak_fence", 3), groups.get(0).blocks(), "состав куска — по видам блоков");
        assertEquals(new BlockCoord(1, 70, 1), groups.get(0).position(), "кусок начинается с нижней левой клетки");
    }

    @Test
    void groups_SeparatePieces_AreDifferentGroups() {
        List<StrayGroups.Group> groups = StrayGroups.of(
                List.of(
                        new StrayGroups.Cell(new BlockCoord(1, 70, 1), "minecraft:cobblestone"),
                        new StrayGroups.Cell(new BlockCoord(9, 70, 9), "minecraft:oak_planks")),
                Map.of());

        assertEquals(2, groups.size(), "разнесённые куски лишнего сносят отдельно");
    }

    @Test
    void groups_BuilderFromJournal_IsKeptOtherwiseNull() {
        List<StrayGroups.Cell> cells = List.of(
                new StrayGroups.Cell(new BlockCoord(1, 70, 1), "minecraft:oak_fence"),
                new StrayGroups.Cell(new BlockCoord(2, 70, 1), "minecraft:oak_fence"));

        List<StrayGroups.Group> known = StrayGroups.of(cells, Map.of(new BlockCoord(2, 70, 1), "spirit-7"));
        List<StrayGroups.Group> unknown = StrayGroups.of(cells, Map.of());

        assertEquals("spirit-7", known.get(0).builder(), "строитель берётся из журнала, если запись есть");
        assertNull(unknown.get(0).builder(), "без записи журнала строитель остаётся неизвестным, а не выдуман");
    }

    @Test
    void groups_DiagonalCells_AreNotConnected() {
        List<StrayGroups.Group> groups = StrayGroups.of(
                List.of(
                        new StrayGroups.Cell(new BlockCoord(1, 70, 1), "minecraft:oak_fence"),
                        new StrayGroups.Cell(new BlockCoord(2, 70, 2), "minecraft:oak_fence")),
                Map.of());

        assertEquals(2, groups.size(), "связность — по граням: диагональ не делает забор одним куском");
    }
}
