package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LightPlanTests {
    private static final int Y = 64;

    @Test
    void grid_OpenSquare_PlacesCandidateOnEveryGridLine() {
        List<LightCell> cells = openArea(6, 0);

        List<BlockCoord> grid = LightPlan.grid(0, 0, 6, cells);

        assertEquals(
                9,
                grid.size(),
                "Площадь 13×13 при шаге сетки " + LightPlan.GRID_STEP + " даёт 3×3 узла: сетка не нашла часть узлов"
                        + " Иначе тест grid_OpenSquare_PlacesCandidateOnEveryGridLine перестанет ловить эту поломку. При смене grid_OpenSquare_PlacesCandidateOnEveryGridLine проверь это утверждение.");
    }

    @Test
    void plan_OpenDarkSquare_OneTorchAtCenterLightsAll() {
        List<LightCell> cells = openArea(4, 0);

        LightPlan.Plan plan = LightPlan.plan(0, 0, 4, cells);

        assertEquals(
                List.of(new BlockCoord(0, Y, 0)),
                plan.torches(),
                "Квадрат 9×9 целиком лежит в досягаемости одного факела в центре: лишние факелы — перерасход"
                        + " Иначе тест plan_OpenDarkSquare_OneTorchAtCenterLightsAll перестанет ловить эту поломку. При смене plan_OpenDarkSquare_OneTorchAtCenterLightsAll проверь это утверждение.");
        assertEquals(
                0,
                plan.darkRemaining(),
                "После плана тёмных клеток быть не должно: мобы спавнятся при блочном свете 0"
                        + " Иначе тест plan_OpenDarkSquare_OneTorchAtCenterLightsAll перестанет ловить эту поломку. При смене plan_OpenDarkSquare_OneTorchAtCenterLightsAll проверь это утверждение.");
        assertEquals(
                81,
                plan.darkTotal(),
                "Тёмными были все 81 клетка площадки: счёт тёмных врёт — план не защищает территорию"
                        + " Иначе тест plan_OpenDarkSquare_OneTorchAtCenterLightsAll перестанет ловить эту поломку. При смене plan_OpenDarkSquare_OneTorchAtCenterLightsAll проверь это утверждение.");
    }

    @Test
    void plan_AlreadyLitSquare_NoTorchesNeeded() {
        List<LightCell> cells = openArea(4, 5);

        LightPlan.Plan plan = LightPlan.plan(0, 0, 4, cells);

        assertTrue(
                plan.torches().isEmpty(),
                "Светлая площадка не требует факелов: факел на освещённой клетке — трата материала"
                        + " Иначе тест plan_AlreadyLitSquare_NoTorchesNeeded перестанет ловить эту поломку. При смене plan_AlreadyLitSquare_NoTorchesNeeded проверь это утверждение.");
        assertEquals(
                0,
                plan.darkTotal(),
                "Ни одна клетка не темнее порога спавна: счёт тёмных обязан быть нулём"
                        + " Иначе тест plan_AlreadyLitSquare_NoTorchesNeeded перестанет ловить эту поломку. При смене plan_AlreadyLitSquare_NoTorchesNeeded проверь это утверждение.");
    }

    @Test
    void plan_NonPlaceableGridCenter_SubstitutesNearestNeighbor() {
        List<LightCell> cells = openArea(6, 0);
        cells = withPlaceable(cells, new BlockCoord(0, Y, 0), false);

        List<BlockCoord> grid = LightPlan.grid(0, 0, 6, cells);

        assertFalse(
                grid.contains(new BlockCoord(0, Y, 0)),
                "Запрещённая клетка не может стать факелом: иначе дух ставит блок на чужое или в воду"
                        + " При смене plan_NonPlaceableGridCenter_SubstitutesNearestNeighbor проверь это утверждение.");
        assertEquals(
                9,
                grid.size(),
                "Ближайшая свободная клетка заменяет запрещённый узел: сетка не теряет узел"
                        + " Иначе тест plan_NonPlaceableGridCenter_SubstitutesNearestNeighbor перестанет ловить эту поломку. При смене plan_NonPlaceableGridCenter_SubstitutesNearestNeighbor проверь это утверждение.");
    }

    @Test
    void plan_BlockedSurroundings_NeverPlacesOnBlockedCell() {
        List<LightCell> cells = infeasibleArea();
        Set<BlockCoord> placeable = new HashSet<>();
        for (LightCell cell : cells) {
            if (cell.placeable()) {
                placeable.add(cell.coord());
            }
        }

        LightPlan.Plan plan = LightPlan.plan(0, 0, 14, cells);

        for (BlockCoord torch : plan.torches()) {
            assertTrue(
                    placeable.contains(torch),
                    "Факел " + torch + " стоит на запрещённой клетке: чужую постройку, воду и листья не трогаем"
                            + " Иначе тест plan_BlockedSurroundings_NeverPlacesOnBlockedCell перестанет ловить эту поломку. При смене plan_BlockedSurroundings_NeverPlacesOnBlockedCell проверь это утверждение.");
        }
        assertEquals(
                List.of(new BlockCoord(0, Y, 0)),
                plan.torches(),
                "Единственная разрешённая клетка — центр: план ставит факел только там"
                        + " Иначе тест plan_BlockedSurroundings_NeverPlacesOnBlockedCell перестанет ловить эту поломку. При смене plan_BlockedSurroundings_NeverPlacesOnBlockedCell проверь это утверждение.");
        assertTrue(
                plan.darkRemaining() > 0,
                "Дальние тёмные клетки вне досягаемости центрального факела остаются тёмными: "
                        + "план не выдумывает факелы там, где ставить негде"
                        + " Иначе тест plan_BlockedSurroundings_NeverPlacesOnBlockedCell перестанет ловить эту поломку. При смене plan_BlockedSurroundings_NeverPlacesOnBlockedCell проверь это утверждение.");
    }

    @Test
    void plan_SameInput_ReturnsSamePlan() {
        List<LightCell> cells = openArea(6, 0);

        assertEquals(
                LightPlan.plan(0, 0, 6, cells),
                LightPlan.plan(0, 0, 6, cells),
                "Тот же вход даёт тот же план: иначе исполнитель и бронь расходятся"
                        + " При смене plan_SameInput_ReturnsSamePlan проверь это утверждение.");
    }

    @Test
    void plan_ZeroRadius_Throws() {
        List<LightCell> cells = List.of();

        assertThrows(
                IllegalArgumentException.class,
                () -> LightPlan.plan(0, 0, 0, cells),
                "Нулевой радиус обязан падать: освещать нечего"
                        + " Иначе тест plan_ZeroRadius_Throws перестанет ловить эту поломку. При смене plan_ZeroRadius_Throws проверь это утверждение.");
    }

    private static List<LightCell> openArea(int radius, int light) {
        List<LightCell> cells = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                cells.add(new LightCell(new BlockCoord(x, Y, z), light, true));
            }
        }
        return cells;
    }

    private static List<LightCell> infeasibleArea() {
        List<LightCell> cells = new ArrayList<>();
        for (int x = -14; x <= 14; x++) {
            for (int z = -14; z <= 14; z++) {
                boolean placeable = x == 0 && z == 0;
                cells.add(new LightCell(new BlockCoord(x, Y, z), 0, placeable));
            }
        }
        return cells;
    }

    private static List<LightCell> withPlaceable(List<LightCell> cells, BlockCoord target, boolean placeable) {
        List<LightCell> result = new ArrayList<>(cells.size());
        for (LightCell cell : cells) {
            result.add(cell.coord().equals(target) ? new LightCell(cell.coord(), cell.light(), placeable) : cell);
        }
        return result;
    }
}
