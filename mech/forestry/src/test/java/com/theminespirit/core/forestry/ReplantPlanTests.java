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

    private static final List<BlockCoord> SQUARE =
            List.of(new BlockCoord(0, 1, 0), new BlockCoord(1, 1, 0), new BlockCoord(0, 1, 1), new BlockCoord(1, 1, 1));

    @Test
    void keepHarvested_ForeignPlotSpecies_False() {
        assertFalse(
                ReplantPlan.keepHarvested("oak", "jungle"),
                "Порода участка другая: старое дерево не пересаживаем, место засадит сад породы участка, иначе в "
                        + "саду вырастет чужое. При смене ReplantPlan.keepHarvested проверь строку совпадения ниже в "
                        + "тесте");
    }

    @Test
    void keepHarvested_SameSpecies_True() {
        assertTrue(
                ReplantPlan.keepHarvested("jungle", "jungle"),
                "Порода участка совпала: сажаем сразу, сад не пустеет, иначе участок ждёт саженец вместо дерева. При "
                        + "смене keepHarvested проверь строку null ниже в тесте");
    }

    @Test
    void keepHarvested_UnknownPlotWood_True() {
        assertTrue(
                ReplantPlan.keepHarvested(null, "jungle"),
                "Вне сада породы участка нет: пересадка по срубленной породе остаётся прежней, иначе дикий лес не "
                        + "восстанавливается. При смене keepHarvested проверь строку выше в тесте");
    }

    @Test
    void of_SquareWithFourSaplings_PlantsFullSquare() {
        ReplantPlan plan = ReplantPlan.of(SQUARE, 4, DATA);

        assertEquals(
                List.of(
                        new BlockCoord(0, 1, 0),
                        new BlockCoord(0, 1, 1),
                        new BlockCoord(1, 1, 0),
                        new BlockCoord(1, 1, 1)),
                plan.cells(),
                "Ствол 2x2 и четыре саженца: сажаем все четыре клетки квадрата по порядку координат, иначе тёмный "
                        + "дуб не вырастет. При смене ReplantPlan.of проверь строку признака ниже в тесте");
        assertTrue(
                plan.square(),
                "Основание 2x2 обязано отмечаться квадратом: по этому признаку отличают гигантское дерево, иначе "
                        + "обновление участка промахнётся. При смене of проверь строку lacked ниже в тесте");
        assertFalse(
                plan.lacked(),
                "Саженцев хватило на квадрат, значит нужды в них нет, иначе игрок получает ложную нехватку. При "
                        + "смене of проверь строку основания ниже в тесте");
        assertEquals(
                new BlockCoord(0, 1, 0),
                plan.base(),
                "Основание — нижняя угловая клетка ствола: от неё отсчитывается обновление участка, иначе поляна "
                        + "обновляется не там. При смене выбора основания проверь строку трёх саженцев ниже в тесте");
    }

    @Test
    void of_SquareWithThreeSaplings_PlantsOneByOneFallback() {
        ReplantPlan plan = ReplantPlan.of(SQUARE, 3, DATA);

        assertEquals(
                List.of(new BlockCoord(0, 1, 0)),
                plan.cells(),
                "Трёх саженцев на квадрат 2x2 не хватает: по одному сажаем один саженец у основания, а не половину "
                        + "квадрата, которая не прорастёт, иначе саженцы тратятся впустую. При смене fallback в of "
                        + "проверь строку признака ниже в тесте");
        assertTrue(
                plan.square(),
                "Основание всё равно квадрат: признак говорит о форме ствола, а не о запасе саженцев, иначе "
                        + "недостаток меняет форму. При смене of проверь строку lacked ниже в тесте");
        assertTrue(
                plan.lacked(),
                "Нехватка на квадрат обязана быть отмечена: итог скажет, что саженцев не хватило, иначе игрок не "
                        + "узнает о нехватке. При смене of проверь строку без саженцев ниже в тесте");
    }

    @Test
    void of_SquareWithoutSaplings_PlantsNothing() {
        ReplantPlan plan = ReplantPlan.of(SQUARE, 0, DATA);

        assertTrue(
                plan.cells().isEmpty(),
                "Без саженцев сажать нечего: пустой список — законный итог, иначе план падает. При смене of проверь "
                        + "строку lacked ниже в тесте");
        assertTrue(
                plan.lacked(),
                "Ноль саженцев — нехватка: итог обязан сказать «нет саженцев», иначе игрок не узнает причину. При "
                        + "смене of проверь строку одиночного ниже в тесте");
    }

    @Test
    void of_SingleTrunkWithOneSapling_PlantsThatCell() {
        List<BlockCoord> trunk = List.of(new BlockCoord(5, 64, 5));

        ReplantPlan plan = ReplantPlan.of(trunk, 1, DATA);

        assertEquals(
                trunk,
                plan.cells(),
                "Одиночный ствол с одним саженцем: сажаем ровно его клетку, квадрат здесь ни при чём, иначе "
                        + "одиночный ствол считается гигантом. При смене of проверь строку признака ниже в тесте");
        assertFalse(
                plan.square(),
                "Основание из одной клетки не квадрат 2x2: ель и джунгли обычного роста остаются одиночными, иначе "
                        + "лишние саженцы списываются. При смене isFullSquare проверь строку lacked ниже в тесте");
        assertFalse(
                plan.lacked(),
                "Одного саженца на одиночный ствол хватает, нехватки нет, иначе игрок видит ложную нехватку. При "
                        + "смене of проверь строку без саженца ниже в тесте");
    }

    @Test
    void of_SingleTrunkWithoutSapling_PlantsNothing() {
        ReplantPlan plan = ReplantPlan.of(List.of(new BlockCoord(5, 64, 5)), 0, DATA);

        assertTrue(
                plan.cells().isEmpty(),
                "Одиночный ствол без саженца не засадить: список пуст, иначе план падает. При смене of проверь "
                        + "строку lacked ниже в тесте");
        assertTrue(
                plan.lacked(),
                "Ноль саженцев — нехватка независимо от формы ствола, иначе итог не сообщает о нехватке. При смене "
                        + "of проверь строку Г-образной ниже в тесте");
    }

    @Test
    void of_ThreeCellLShape_IsNotSquare() {
        List<BlockCoord> lShape = List.of(new BlockCoord(0, 1, 0), new BlockCoord(1, 1, 0), new BlockCoord(0, 1, 1));

        ReplantPlan plan = ReplantPlan.of(lShape, 4, DATA);

        assertFalse(
                plan.square(),
                "Г-образное основание из трёх клеток — не полный квадрат: квадратом оно не считается, иначе "
                        + "неполное основание тратит четыре саженца. При смене isFullSquare проверь строку клеток "
                        + "ниже в тесте");
        assertEquals(
                List.of(new BlockCoord(0, 1, 0)),
                plan.cells(),
                "Для неполного основания сажаем по одному саженцу у нижней клетки, иначе половина квадрата не "
                        + "прорастёт и запас теряется. При смене of проверь строку пустого ствола ниже в тесте");
    }

    @Test
    void of_EmptyTrunk_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ReplantPlan.of(List.of(), 4, DATA),
                "Без клеток ствола сажать некуда: пустой список основания недопустим, иначе план падает ниже с "
                        + "непонятной ошибкой. При смене of проверь строку квадрата ниже в тесте");
    }

    @Test
    void isFullSquare_FourCellsSpreadTwo_IsTrue() {
        assertTrue(
                ReplantPlan.isFullSquare(SQUARE),
                "Четыре разные клетки в квадрате 2x2 — полный квадрат, иначе гигант не распознаётся. При смене "
                        + "ReplantPlan.isFullSquare проверь строку линии ниже в тесте");
        assertFalse(
                ReplantPlan.isFullSquare(List.of(
                        new BlockCoord(0, 1, 0),
                        new BlockCoord(1, 1, 0),
                        new BlockCoord(2, 1, 0),
                        new BlockCoord(3, 1, 0))),
                "Четыре клетки в линию — не квадрат 2x2, иначе саженец встаёт мимо формы и дерево не растёт. При "
                        + "смене isFullSquare проверь строку высоты ниже в тесте");
        assertFalse(
                ReplantPlan.isFullSquare(List.of(
                        new BlockCoord(0, 1, 0),
                        new BlockCoord(1, 1, 0),
                        new BlockCoord(0, 1, 1),
                        new BlockCoord(0, 2, 1))),
                "Клетки квадрата обязаны лежать на одной высоте: ствол не растёт наискось, иначе горизонтальный "
                        + "квадрат признаётся полным. При смене проверки высоты проверь строку выше в тесте");
    }
}
