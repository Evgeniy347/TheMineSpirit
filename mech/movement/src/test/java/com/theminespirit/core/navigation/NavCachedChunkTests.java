// lib: Из Baritone (github.com/cabaletta/baritone, cache/CachedChunk.java), лицензия LGPL-3.0.
package com.theminespirit.core.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class NavCachedChunkTests {
    private static final int MIN_Y = -16;
    private static final int HEIGHT = 32;

    @Test
    void pack_WorldWithWaterAndStone_StoresTwoBitTypes() {
        NavCachedChunk chunk = NavCachedChunk.pack(0, 0, MIN_Y, HEIGHT, (x, y, z) -> {
            if (y < 0) {
                return NavBlockType.SOLID;
            }
            if (x == 2 && y == 1 && z == 3) {
                return NavBlockType.WATER;
            }
            if (x == 4 && y == 1 && z == 5) {
                return NavBlockType.AVOID;
            }
            return NavBlockType.AIR;
        });

        assertEquals(
                NavBlockType.SOLID,
                chunk.type(0, -1, 0),
                "пол под ногами — твёрдый"
                        + " Иначе тест pack_WorldWithWaterAndStone_StoresTwoBitTypes перестанет ловить эту поломку. При смене pack_WorldWithWaterAndStone_StoresTwoBitTypes проверь это утверждение.");
        assertEquals(
                NavBlockType.AIR,
                chunk.type(0, 1, 0),
                "воздух над полом"
                        + " Иначе тест pack_WorldWithWaterAndStone_StoresTwoBitTypes перестанет ловить эту поломку. При смене pack_WorldWithWaterAndStone_StoresTwoBitTypes проверь это утверждение.");
        assertEquals(
                NavBlockType.WATER,
                chunk.type(2, 1, 3),
                "вода сохранена отдельным типом"
                        + " Иначе тест pack_WorldWithWaterAndStone_StoresTwoBitTypes перестанет ловить эту поломку. При смене pack_WorldWithWaterAndStone_StoresTwoBitTypes проверь это утверждение.");
        assertEquals(
                NavBlockType.AVOID,
                chunk.type(4, 1, 5),
                "опасный блок сохранён как AVOID"
                        + " Иначе тест pack_WorldWithWaterAndStone_StoresTwoBitTypes перестанет ловить эту поломку. При смене pack_WorldWithWaterAndStone_StoresTwoBitTypes проверь это утверждение.");
        assertEquals(
                NavBlockType.AIR,
                chunk.type(0, 100, 0),
                "за пределами высоты — воздух"
                        + " Иначе тест pack_WorldWithWaterAndStone_StoresTwoBitTypes перестанет ловить эту поломку. При смене pack_WorldWithWaterAndStone_StoresTwoBitTypes проверь это утверждение.");
        assertEquals(
                16 * 16 * HEIGHT * 2 / 8,
                chunk.sizeInBytes(),
                "2 бита на блок: 2 КБ на 32 блока высоты"
                        + " Иначе тест pack_WorldWithWaterAndStone_StoresTwoBitTypes перестанет ловить эту поломку. При смене pack_WorldWithWaterAndStone_StoresTwoBitTypes проверь это утверждение.");
    }

    @Test
    void constructor_DataWrongSize_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new NavCachedChunk(0, 0, MIN_Y, HEIGHT, new long[1]),
                "чанк с чужим размером данных не должен притворяться целым: позиции разъедутся"
                        + " Иначе тест constructor_DataWrongSize_IsRejected перестанет ловить эту поломку. При смене constructor_DataWrongSize_IsRejected проверь это утверждение.");
    }

    @Test
    void fromBits_OutsideTwoBits_IsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> NavBlockType.fromBits(4),
                "2 бита вмещают только 0…3"
                        + " Иначе тест fromBits_OutsideTwoBits_IsRejected перестанет ловить эту поломку. При смене fromBits_OutsideTwoBits_IsRejected проверь это утверждение.");
    }
}
