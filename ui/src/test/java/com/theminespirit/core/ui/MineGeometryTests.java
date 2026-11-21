package com.theminespirit.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.building.MineLayout;
import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class MineGeometryTests {
    private static final MineLayout LAYOUT = new MineLayout(
            List.of(new BlockCoord(0, 9, 0), new BlockCoord(1, 8, 0), new BlockCoord(2, 7, 0)),
            List.of(new BlockCoord(0, 10, 0), new BlockCoord(1, 9, 0), new BlockCoord(2, 8, 0)),
            List.of(),
            new BlockCoord(0, 10, 0));

    @Test
    void of_StairLayout_BoundsCoverDescentAndTunnel() {
        MineGeometry geometry = MineGeometry.of(LAYOUT, 3);
        assertEquals(
                List.of(0, 0, 4, 2),
                List.of(
                        geometry.bounds().x1(),
                        geometry.bounds().z1(),
                        geometry.bounds().x2(),
                        geometry.bounds().z2()),
                "Границы среза охватывают спуск и куб хода у нижней ступени"
                        + " Иначе тест of_StairLayout_BoundsCoverDescentAndTunnel перестанет ловить эту поломку. При смене of_StairLayout_BoundsCoverDescentAndTunnel проверь это утверждение.");
        assertEquals(
                List.of(7, 10),
                List.of(geometry.minY(), geometry.maxY()),
                "Высоты от пола спуска до верха входа"
                        + " Иначе тест of_StairLayout_BoundsCoverDescentAndTunnel перестанет ловить эту поломку. При смене of_StairLayout_BoundsCoverDescentAndTunnel проверь это утверждение.");
        assertEquals(
                8,
                geometry.defaultY(),
                "По умолчанию срез — на уровне нижней ступени"
                        + " Иначе тест of_StairLayout_BoundsCoverDescentAndTunnel перестанет ловить эту поломку. При смене of_StairLayout_BoundsCoverDescentAndTunnel проверь это утверждение.");
        assertEquals(
                List.of(2, 8, 0, 4, 10, 2),
                List.of(
                        geometry.tunnel().minX(),
                        geometry.tunnel().minY(),
                        geometry.tunnel().minZ(),
                        geometry.tunnel().maxX(),
                        geometry.tunnel().maxY(),
                        geometry.tunnel().maxZ()),
                "Куб хода растёт от нижней клетки воздуха спуска"
                        + " Иначе тест of_StairLayout_BoundsCoverDescentAndTunnel перестанет ловить эту поломку. При смене of_StairLayout_BoundsCoverDescentAndTunnel проверь это утверждение.");
    }

    @Test
    void of_WithoutAir_TunnelFromEntrance() {
        MineLayout bare = new MineLayout(List.of(), List.of(), List.of(), new BlockCoord(5, 20, 5));
        MineGeometry geometry = MineGeometry.of(bare, 3);
        assertEquals(
                List.of(5, 5, 7, 7),
                List.of(
                        geometry.bounds().x1(),
                        geometry.bounds().z1(),
                        geometry.bounds().x2(),
                        geometry.bounds().z2()),
                "Без выкопанного спуска карта показывает хотя бы куб хода от входа"
                        + " Иначе тест of_WithoutAir_TunnelFromEntrance перестанет ловить эту поломку. При смене of_WithoutAir_TunnelFromEntrance проверь это утверждение.");
        assertEquals(
                20,
                geometry.defaultY(),
                "Срез по умолчанию — на высоте входа"
                        + " Иначе тест of_WithoutAir_TunnelFromEntrance перестанет ловить эту поломку. При смене of_WithoutAir_TunnelFromEntrance проверь это утверждение.");
    }
}
