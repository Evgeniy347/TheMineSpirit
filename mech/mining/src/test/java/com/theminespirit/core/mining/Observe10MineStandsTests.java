package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Observe10MineStandsTests {
    @Test
    void cellStands_Observe10ShaftWallNotSeenFromSpirit_AllStandsSeeCell() throws IOException {
        Observe10Window window = Observe10Window.load("mine-wall.json");
        BlockCoord feet = window.spirit();
        BlockCoord cell = window.cell();

        assertFalse(
                MineDigOrder.visible(feet, cell, List.of(), window),
                "Живой отказ observe10: клетка " + cell + " из " + feet + " не видна — тест обязан воспроизвести "
                        + "этот отказ, иначе он проверяет другой случай");

        MineCellPlan.Plan plan =
                MineCellPlan.of(cell, feet, window, new MineCellPlan.Seen(null, false, false, false), stand -> true);

        assertTrue(
                plan.kind() == MineCellPlan.Kind.APPROACH,
                "Невидимая клетка шахты требует подхода, а не отказа: MineBuildScript падал на «не виден из глаз», "
                        + "хотя рядом есть стоянка — получили " + plan.kind());
        assertFalse(
                plan.stands().isEmpty(), "Подход обязан предложить стоянку: пустой список повторил бы отказ observe10");
        for (BlockCoord stand : plan.stands()) {
            assertTrue(
                    MineDigOrder.visible(stand, cell, List.of(), window),
                    "Стоянка " + stand + " обязана видеть клетку " + cell + ": иначе подход не убирает причину "
                            + "отказа «не виден из глаз»");
            assertFalse(
                    MineCellStands.occupies(stand, cell),
                    "Стоянка " + stand + " не может стоять в самой клетке " + cell + ": дух не ломает блок собой");
        }
    }

    @Test
    void cellPlan_Observe10SpiritInWallCell_WaitsInsteadOfFailing() throws IOException {
        Observe10Window window = Observe10Window.load("mine-head.json");
        BlockCoord feet = window.spirit();
        BlockCoord cell = window.cell();

        assertTrue(
                MineCellStands.occupies(feet, cell),
                "Дух стоит под клеткой стенки шахты: тест обязан взять живое состояние observe10");
        MineCellPlan.Plan plan = MineCellPlan.of(
                cell, feet, window, new MineCellPlan.Seen(window.occupantType(), false, true, true), stand -> true);

        assertTrue(
                plan.kind() == MineCellPlan.Kind.WAIT,
                "Клетку " + cell + " занимает другое существо: дух обязан ждать/обходить, а не проваливать шахту "
                        + "сразу — получили " + plan.kind());
        assertTrue(
                plan.reason().contains(window.occupantType()),
                "Отказ обязан назвать тип существа '" + window.occupantType() + "': без него в логе observe10 "
                        + "не понять, кого ждать — причина: " + plan.reason());
    }

    @Test
    void veinStands_Observe10CoalSealedByDirt_NoVisibleStandFound() throws IOException {
        Observe10Window window = Observe10Window.load("vein-coal.json");
        BlockCoord feet = window.spirit();
        BlockCoord coal = window.target();

        assertTrue(
                MineCellStands.of(coal, feet, window, stand -> true).isEmpty(),
                "Уголь " + coal + " закрыт породой сверху: стоянки с прямой видимостью нет, и mine_vein обязан "
                        + "честно сказать это, а не искать стоянку по одному расстоянию 3600 тиков");

        assertTrue(
                MineVeinStands.next(List.of(coal), feet, window, Set.of(), Set.of())
                        .isEmpty(),
                "Подход по одному расстоянию выбирал стоянку без видимости угля: mine_vein 3600 тиков не двигался "
                        + "(observe10, graph:make:coal). Стоянка обязана видеть цель");
    }
}
