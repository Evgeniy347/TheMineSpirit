package com.theminespirit.core.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class Observe10VeinTunnelTests {
    @Test
    void tunnel_Observe10CoalSealedByRock_PlansStaircasePassToVisibleStand() throws IOException {
        Observe10Window view = Observe10Window.load("vein-coal.json");
        BlockCoord feet = view.spirit();
        BlockCoord coal = view.target();

        assertFalse(
                MineDigOrder.visible(feet, coal, List.of(), view),
                "Живой отказ observe10: уголь " + coal + " из " + feet + " не виден — тест обязан взять именно "
                        + "закрытую руду, иначе он проверяет другой случай");
        assertTrue(
                MineVeinStands.next(List.of(coal), feet, view, Set.of(), Set.of())
                        .isEmpty(),
                "Стоянки с прямой видимостью у закрытого угля нет: без прохода mine_vein снова простоит 3600 тиков");

        MineTunnel.Plan plan = MineTunnel.plan(coal, feet, view, MineTunnel.MAX_STEPS);
        assertTrue(plan.found(), "Закрытый уголь " + coal + " обязан получать проход, а не отказ: " + plan.refusal());
        assertFalse(plan.steps().isEmpty(), "Проход к закрытому углю не может быть пустым: копать нечего");

        BlockCoord current = feet;
        List<BlockCoord> opened = new ArrayList<>();
        for (MineTunnel.Step step : plan.steps()) {
            int dx = Math.abs(step.stand().x() - current.x());
            int dz = Math.abs(step.stand().z() - current.z());
            int dy = step.stand().y() - current.y();
            assertEquals(1, dx + dz, "Шаг прохода " + step.stand() + " из " + current + " идёт не в соседнюю клетку");
            assertTrue(
                    Math.abs(dy) <= 1,
                    "Шаг прохода " + step.stand() + " из " + current + " прыгает по высоте на " + dy
                            + ": лестница меняет высоту на один блок");
            assertNotEquals(coal, step.stand(), "Дух не встаёт в саму руду " + coal + ": руду добывают, а не топчут");
            for (BlockCoord cell : step.dig()) {
                assertNotEquals(coal, cell, "Проход не должен выламывать цель " + coal + " раньше добычи");
                assertTrue(
                        cell.equals(step.stand())
                                || cell.equals(new BlockCoord(
                                        step.stand().x(),
                                        step.stand().y() + 1,
                                        step.stand().z())),
                        "Шаг прохода ломает " + cell + " вне клеток ног/головы стоянки " + step.stand());
                assertTrue(
                        view.cellAt(cell) == Cell.SOLID || view.cellAt(cell) == Cell.FALLING,
                        "Проход ломает " + cell + ", но там " + view.cellAt(cell) + ": породу не прокапывают сквозь "
                                + "воздух и не ломают опасные клетки");
            }
            opened.addAll(step.dig());
            current = step.stand();
        }

        assertTrue(
                current.y() < feet.y(),
                "Уголь " + coal + " лежит ниже духа " + feet + ": проход обязан быть лестницей вниз, а не туннелем "
                        + "на месте");
        assertTrue(
                MineDigOrder.visible(current, coal, opened, view),
                "Дойдя до стоянки " + current + " по выкопанному проходу, дух обязан видеть уголь " + coal
                        + ": иначе mine_vein снова не начнёт добычу");
        assertTrue(
                Math.abs(coal.x() - current.x()) <= 3
                        && Math.abs(coal.z() - current.z()) <= 3
                        && coal.y() - current.y() <= 15
                        && coal.y() - current.y() >= -3,
                "Стоянка " + current + " слишком далеко от угля " + coal + ": рукой не достать, добыча не начнётся");
    }

    @Test
    void tunnel_ProtectedCellOnDirectPath_RouteBypassesItOrRefuses() throws IOException {
        Observe10Window view = Observe10Window.load("vein-coal.json");
        BlockCoord feet = view.spirit();
        BlockCoord coal = view.target();
        MineTunnel.Plan direct = MineTunnel.plan(coal, feet, view, MineTunnel.MAX_STEPS);
        assertTrue(direct.found(), "Живой уголь обязан иметь прямой проход: иначе тест не проверяет обход");
        BlockCoord blocked = direct.steps().get(0).stand();

        MineTunnel.Plan rerouted = MineTunnel.plan(coal, feet, view, MineTunnel.MAX_STEPS, blocked::equals);

        if (rerouted.found()) {
            for (MineTunnel.Step step : rerouted.steps()) {
                assertFalse(
                        blocked.equals(step.stand()) || step.dig().contains(blocked),
                        "Проход к руде обязан обходить защищённую клетку " + blocked + ", а не ломать её");
            }
        } else {
            assertTrue(
                    rerouted.refusal().contains("постройки"),
                    "Если обойти защищённую клетку нельзя, отказ обязан называть постройку: " + rerouted.refusal());
        }
    }
}
