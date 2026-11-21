package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TerrainRasterTests {
    @Test
    void of_TwoChunks_RasterCoversBothWithColors() {
        TerrainRaster raster = TerrainRaster.of(List.of(tile(0, 0, 12), tile(1, 0, 1)));
        assertEquals(
                0,
                raster.x1(),
                "Растр начинается с первого чанка"
                        + " Иначе тест of_TwoChunks_RasterCoversBothWithColors перестанет ловить эту поломку. При смене of_TwoChunks_RasterCoversBothWithColors проверь это утверждение.");
        assertEquals(
                0,
                raster.z1(),
                "Растр начинается с первого чанка"
                        + " Иначе тест of_TwoChunks_RasterCoversBothWithColors перестанет ловить эту поломку. При смене of_TwoChunks_RasterCoversBothWithColors проверь это утверждение.");
        assertEquals(
                32,
                raster.width(),
                "Два чанка в ряд — 32 колонки"
                        + " Иначе тест of_TwoChunks_RasterCoversBothWithColors перестанет ловить эту поломку. При смене of_TwoChunks_RasterCoversBothWithColors проверь это утверждение.");
        assertEquals(
                16,
                raster.height(),
                "Один чанк в глубину — 16 колонок"
                        + " Иначе тест of_TwoChunks_RasterCoversBothWithColors перестанет ловить эту поломку. При смене of_TwoChunks_RasterCoversBothWithColors проверь это утверждение.");
        assertEquals(
                TerrainColor.argb(12),
                raster.argb(3, 5),
                "Вода первого чанка — цвет воды"
                        + " Иначе тест of_TwoChunks_RasterCoversBothWithColors перестанет ловить эту поломку. При смене of_TwoChunks_RasterCoversBothWithColors проверь это утверждение.");
        assertEquals(
                TerrainColor.argb(1),
                raster.argb(20, 5),
                "Трава второго чанка — цвет травы"
                        + " Иначе тест of_TwoChunks_RasterCoversBothWithColors перестанет ловить эту поломку. При смене of_TwoChunks_RasterCoversBothWithColors проверь это утверждение.");
        assertEquals(
                0,
                raster.argb(-1, 0),
                "За растром — прозрачно"
                        + " Иначе тест of_TwoChunks_RasterCoversBothWithColors перестанет ловить эту поломку. При смене of_TwoChunks_RasterCoversBothWithColors проверь это утверждение.");
    }

    @Test
    void of_UnknownColumns_TransparentInsideKnownTile() {
        byte[] ids = new byte[TerrainTile.COLUMNS];
        Arrays.fill(ids, (byte) 0xFF);
        ids[0] = 12;
        TerrainRaster raster = TerrainRaster.of(List.of(new TerrainTile(0, 0, ids)));
        assertEquals(
                TerrainColor.argb(12),
                raster.argb(0, 0),
                "Известная колонка получает свой цвет"
                        + " Иначе тест of_UnknownColumns_TransparentInsideKnownTile перестанет ловить эту поломку. При смене of_UnknownColumns_TransparentInsideKnownTile проверь это утверждение.");
        assertEquals(
                0,
                raster.argb(1, 0),
                "Неизвестная колонка прозрачна, а не чёрная"
                        + " Иначе тест of_UnknownColumns_TransparentInsideKnownTile перестанет ловить эту поломку. При смене of_UnknownColumns_TransparentInsideKnownTile проверь это утверждение.");
    }

    @Test
    void of_AllUnknown_EmptyRaster() {
        byte[] ids = new byte[TerrainTile.COLUMNS];
        Arrays.fill(ids, (byte) 0xFF);
        assertSame(
                TerrainRaster.EMPTY,
                TerrainRaster.of(List.of(new TerrainTile(5, 5, ids))),
                "Нечего рисовать — пусто"
                        + " Иначе тест of_AllUnknown_EmptyRaster перестанет ловить эту поломку. При смене of_AllUnknown_EmptyRaster проверь это утверждение.");
        assertSame(
                TerrainRaster.EMPTY,
                TerrainRaster.of(List.of()),
                "Нет тайлов — пусто"
                        + " Иначе тест of_AllUnknown_EmptyRaster перестанет ловить эту поломку. При смене of_AllUnknown_EmptyRaster проверь это утверждение.");
    }

    @Test
    void tile_WrongColumnsOrForeignCell_Rejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TerrainTile(0, 0, new byte[10]),
                "Тайл 16×16 несёт ровно 256 колонок"
                        + " Иначе тест tile_WrongColumnsOrForeignCell_Rejected перестанет ловить эту поломку. При смене tile_WrongColumnsOrForeignCell_Rejected проверь это утверждение.");
        TerrainTile tile = tile(0, 0, 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> tile.id(16, 0),
                "Колонка за тайлом — ошибка сбора"
                        + " Иначе тест tile_WrongColumnsOrForeignCell_Rejected перестанет ловить эту поломку. При смене tile_WrongColumnsOrForeignCell_Rejected проверь это утверждение.");
    }

    @Test
    void argbBeforeStart_OutOfBounds_Transparent() {
        TerrainRaster raster = TerrainRaster.of(List.of(tile(0, 0, 11)));
        assertEquals(
                0,
                raster.argb(0, -1),
                "Строка выше растра прозрачна"
                        + " Иначе тест argbBeforeStart_OutOfBounds_Transparent перестанет ловить эту поломку. При смене argbBeforeStart_OutOfBounds_Transparent проверь это утверждение.");
        assertEquals(
                0,
                raster.argb(16, 0),
                "Колонка правее растра прозрачна"
                        + " Иначе тест argbBeforeStart_OutOfBounds_Transparent перестанет ловить эту поломку. При смене argbBeforeStart_OutOfBounds_Transparent проверь это утверждение.");
    }

    private static TerrainTile tile(int chunkX, int chunkZ, int id) {
        byte[] ids = new byte[TerrainTile.COLUMNS];
        Arrays.fill(ids, (byte) id);
        return new TerrainTile(chunkX, chunkZ, ids);
    }
}
