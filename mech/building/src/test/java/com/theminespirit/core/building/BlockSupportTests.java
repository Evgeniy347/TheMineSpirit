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
                "Блок над крепкой верхней гранью обязан опираться: это опора пола в BlockPlacer и в схемах"
                        + " Иначе тест hasSupport_BelowSturdyTop_IsSupported перестанет ловить эту поломку. При смене hasSupport_BelowSturdyTop_IsSupported проверь это утверждение.");
    }

    @Test
    void hasSupport_HorizontalNeighbourSturdyToTarget_IsSupported() {
        BlockSupport.World world = (x, y, z, face) -> x == 4 && face == BlockSupport.Face.EAST;

        assertTrue(
                BlockSupport.hasSupport(world, 5, 4, 5),
                "Блок рядом с крепкой боковой гранью обязан опираться: иначе полки и навесы не поставить"
                        + " При смене hasSupport_HorizontalNeighbourSturdyToTarget_IsSupported проверь это утверждение.");
    }

    @Test
    void hasSupport_SturdyNeighbourWithWrongFace_IsRejected() {
        BlockSupport.World world = (x, y, z, face) -> x == 4 && face == BlockSupport.Face.UP;

        assertFalse(
                BlockSupport.hasSupport(world, 5, 4, 5),
                "Сосед обязан быть крепок именно к целевой клетке: крепкая верхняя грань соседа сбоку не держит"
                        + " Иначе тест hasSupport_SturdyNeighbourWithWrongFace_IsRejected перестанет ловить эту поломку. При смене hasSupport_SturdyNeighbourWithWrongFace_IsRejected проверь это утверждение.");
    }

    @Test
    void hasSupport_NothingSturdy_IsRejected() {
        BlockSupport.World world = (x, y, z, face) -> false;

        assertFalse(
                BlockSupport.hasSupport(world, 5, 4, 5),
                "Блок в воздухе без опоры обязан отвергаться: иначе факел повиснет и упадёт"
                        + " При смене hasSupport_NothingSturdy_IsRejected проверь это утверждение.");
    }
}
