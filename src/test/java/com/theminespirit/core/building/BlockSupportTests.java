package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BlockSupportTests {
    @Test
    void hasSupport_BelowSturdyTop_IsSupported() {
        BlockSupport.World world = (x, y, z, face) -> y == 3 && face == BlockSupport.Face.UP;

        assertTrue(
                BlockSupport.hasSupport(world, 5, 4, 5),
                "Блок над крепкой верхней гранью обязан опираться: это опора пола в BlockPlacer и в схемах");
    }

    @Test
    void hasSupport_HorizontalNeighbourSturdyToTarget_IsSupported() {
        BlockSupport.World world = (x, y, z, face) -> x == 4 && face == BlockSupport.Face.EAST;

        assertTrue(
                BlockSupport.hasSupport(world, 5, 4, 5),
                "Блок рядом с крепкой боковой гранью обязан опираться: иначе полки и навесы не поставить");
    }

    @Test
    void hasSupport_SturdyNeighbourWithWrongFace_IsRejected() {
        BlockSupport.World world = (x, y, z, face) -> x == 4 && face == BlockSupport.Face.UP;

        assertFalse(
                BlockSupport.hasSupport(world, 5, 4, 5),
                "Сосед обязан быть крепок именно к целевой клетке: крепкая верхняя грань соседа сбоку не держит");
    }

    @Test
    void hasSupport_NothingSturdy_IsRejected() {
        BlockSupport.World world = (x, y, z, face) -> false;

        assertFalse(
                BlockSupport.hasSupport(world, 5, 4, 5),
                "Блок в воздухе без опоры обязан отвергаться: иначе факел повиснет и упадёт");
    }
}
