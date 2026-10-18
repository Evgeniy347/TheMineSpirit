package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReplantPlanTests {
    private static final ReplantData DATA = ReplantData.defaults();

    private static final List<BlockCoord> SQUARE = List.of(
            new BlockCoord(0, 1, 0), new BlockCoord(1, 1, 0), new BlockCoord(0, 1, 1), new BlockCoord(1, 1, 1));

    @Test
    void of_SquareWithFourSaplings_PlantsFullSquare() {
        ReplantPlan plan = ReplantPlan.of(SQUARE, 4, DATA);

        assertEquals(List.of(new BlockCoord(0, 1, 0), new BlockCoord(0, 1, 1), new BlockCoord(1, 1, 0),
                        new BlockCoord(1, 1, 1)), plan.cells(),
                "Ствол 2x2 и четыре саженца: сажаем все четыре клетки квадрата по порядку координат, "
                        + "иначе тёмный дуб не вырастет");
        assertTrue(plan.square(),
                "Основание 2x2 обязано отмечаться квадратом: по этому признаку отличают гигантское дерево");
        assertFalse(plan.lacked(),
                "Саженцев хватило на квадрат, значит нужды в них нет");
        assertEquals(new BlockCoord(0, 1, 0), plan.base(),
                "Основание — нижняя угловая клетка ствола: от неё отсчитывается обновление участка");
    }

    @Test
    void of_SquareWithThreeSaplings_PlantsOneByOneFallback() {
        ReplantPlan plan = ReplantPlan.of(SQUARE, 3, DATA);

        assertEquals(List.of(new BlockCoord(0, 1, 0)), plan.cells(),
                "Трёх саженцев на квадрат 2x2 не хватает: по одному сажаем один саженец у основания, "
                        + "а не половину квадрата, которая не прорастёт");
        assertTrue(plan.square(),
                "Основание всё равно квадрат: признак говорит о форме ствола, а не о запасе саженцев");
        assertTrue(plan.lacked(),
                "Нехватка на квадрат обязана быть отмечена: итог скажет, что саженцев не хватило");
    }

    @Test
    void of_SquareWithoutSaplings_PlantsNothing() {
        ReplantPlan plan = ReplantPlan.of(SQUARE, 0, DATA);

        assertTrue(plan.cells().isEmpty(),
                "Без саженцев сажать нечего: пустой список — законный итог");
        assertTrue(plan.lacked(),
                "Ноль саженцев — нехватка: итог обязан сказать «нет саженцев»");
    }

    @Test
    void of_SingleTrunkWithOneSapling_PlantsThatCell() {
        List<BlockCoord> trunk = List.of(new BlockCoord(5, 64, 5));

        ReplantPlan plan = ReplantPlan.of(trunk, 1, DATA);

        assertEquals(trunk, plan.cells(),
                "Одиночный ствол с одним саженцем: сажаем ровно его клетку, квадрат здесь ни при чём");
        assertFalse(plan.square(),
                "Основание из одной клетки не квадрат 2x2: ель и джунгли обычного роста остаются одиночными");
        assertFalse(plan.lacked(),
                "Одного саженца на одиночный ствол хватает, нехватки нет");
    }

    @Test
    void of_SingleTrunkWithoutSapling_PlantsNothing() {
        ReplantPlan plan = ReplantPlan.of(List.of(new BlockCoord(5, 64, 5)), 0, DATA);

        assertTrue(plan.cells().isEmpty(),
                "Одиночный ствол без саженца не засадить: список пуст");
        assertTrue(plan.lacked(),
                "Ноль саженцев — нехватка независимо от формы ствола");
    }

    @Test
    void of_ThreeCellLShape_IsNotSquare() {
        List<BlockCoord> lShape = List.of(new BlockCoord(0, 1, 0), new BlockCoord(1, 1, 0),
                new BlockCoord(0, 1, 1));

        ReplantPlan plan = ReplantPlan.of(lShape, 4, DATA);

        assertFalse(plan.square(),
                "Г-образное основание из трёх клеток — не полный квадрат: квадратом оно не считается");
        assertEquals(List.of(new BlockCoord(0, 1, 0)), plan.cells(),
                "Для неполного основания сажаем по одному саженцу у нижней клетки");
    }

    @Test
    void of_EmptyTrunk_Throws() {
        assertThrows(IllegalArgumentException.class, () -> ReplantPlan.of(List.of(), 4, DATA),
                "Без клеток ствола сажать некуда: пустой список основания недопустим");
    }

    @Test
    void isFullSquare_FourCellsSpreadTwo_IsTrue() {
        assertTrue(ReplantPlan.isFullSquare(SQUARE),
                "Четыре разные клетки в квадрате 2x2 — полный квадрат");
        assertFalse(ReplantPlan.isFullSquare(List.of(new BlockCoord(0, 1, 0), new BlockCoord(1, 1, 0),
                        new BlockCoord(2, 1, 0), new BlockCoord(3, 1, 0))),
                "Четыре клетки в линию — не квадрат 2x2");
        assertFalse(ReplantPlan.isFullSquare(List.of(new BlockCoord(0, 1, 0), new BlockCoord(1, 1, 0),
                        new BlockCoord(0, 1, 1), new BlockCoord(0, 2, 1))),
                "Клетки квадрата обязаны лежать на одной высоте: ствол не растёт наискось");
    }
}
