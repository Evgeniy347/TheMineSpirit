package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class DemoPlacementTests {
    @Test
    void compute_LookSouth_TurnsEntryToPlayerAndPutsChestAtAnchor() {
        DemoPlacement placement = DemoPlacement.compute(10, 64, 10, 0, 1, 2, 0, 5, 3, 5, null);

        assertEquals(BuildRotation.NONE, placement.rotation(),
                "Схема с входом на северной стороне и игроком, смотрящим на юг, не поворачивается: "
                        + "вход и так смотрит на игрока");
        assertEquals(new BlockCoord(8, 64, 11), placement.origin(),
                "Начало постройки обязано ставить клетку входа на одну клетку за сундуком по взгляду игрока");
        assertEquals(new BlockCoord(10, 64, 10), placement.chest(),
                "Сундук ставится в клетку-якорь (блок под взглядом игрока)");
        assertEquals(5, placement.sizeX(),
                "Без поворота сторона постройки совпадает со схемой");
        assertEquals(5, placement.sizeZ(),
                "Без поворота сторона постройки совпадает со схемой");
    }

    @Test
    void compute_VolumeCleared_IsBuildPlusOneAllAround() {
        DemoPlacement placement = DemoPlacement.compute(10, 64, 10, 0, 1, 2, 0, 5, 3, 5, null);

        assertEquals(245L, placement.clearBox().volume(),
                "Объём очистки — постройка 5×3×5 плюс по блоку вокруг: (5+2)*(3+2)*(5+2)=245. "
                        + "Меньше — останется мусор у стен, больше — дух снесёт лишнее");
        assertEquals(49L, placement.floorBox().volume(),
                "Пол выравнивается по всему расширенному фундаменту: (5+2)*(5+2)=49, "
                        + "иначе под кольцом вокруг постройки останется яма");
        assertEquals(placement.clearBox().minY(), placement.floorBox().minY(),
                "Пол лежит нижним слоем очищенного объёма: под фундамент выравнивается именно он");
    }

    @Test
    void compute_LookEast_TurnsEntryToPlayer() {
        DemoPlacement placement = DemoPlacement.compute(10, 64, 10, 1, 0, 2, 0, 5, 3, 5, null);

        assertEquals(BuildRotation.COUNTERCLOCKWISE_90, placement.rotation(),
                "Игрок смотрит на восток — вход на северной стороне обязан повернуться на запад, к игроку");
        BuildRotation.Cell entry = placement.rotation().rotate(2, 0, 5, 5);
        assertEquals(11, placement.origin().x() + entry.x(),
                "Клетка входа обязана встать на одну клетку за сундуком по взгляду игрока (ось X)");
        assertEquals(10, placement.origin().z() + entry.z(),
                "Клетка входа обязана встать на одну клетку за сундуком по взгляду игрока (ось Z)");
    }

    @Test
    void compute_ExplicitRotation_OverridesLook() {
        DemoPlacement placement = DemoPlacement.compute(10, 64, 10, 0, 1, 2, 0, 5, 3, 5,
                BuildRotation.CLOCKWISE_180);

        assertEquals(BuildRotation.CLOCKWISE_180, placement.rotation(),
                "Явный поворот обязан перебивать вывод из взгляда игрока: иначе параметр поворота не работает");
        BuildRotation.Cell entry = BuildRotation.CLOCKWISE_180.rotate(2, 0, 5, 5);
        assertEquals(new BlockCoord(10 + 0 - entry.x(), 64, 10 + 1 - entry.z()), placement.origin(),
                "Начало постройки обязано считаться с явным поворотом");
    }

    @Test
    void compute_EntryInDepth_KeepsNoRotation() {
        DemoPlacement placement = DemoPlacement.compute(0, 64, 0, 1, 0, 1, 1, 3, 2, 3, null);

        assertEquals(BuildRotation.NONE, placement.rotation(),
                "Вход внутри схемы (колодец) не задаёт сторону: поворот не выводится из взгляда, постройка "
                        + "остаётся как в схеме");
    }

    @Test
    void compute_EntryOnSouthEdge_LooksNorthToPlayer() {
        DemoPlacement placement = DemoPlacement.compute(0, 64, 0, 0, 1, 4, 8, 9, 4, 9, null);

        assertEquals(BuildRotation.CLOCKWISE_180, placement.rotation(),
                "Вход на южной стороне (чаровальня) при взгляде на юг обязан перевернуть схему, чтобы вход "
                        + "смотрел на игрока");
    }
}
