package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class TreeShapeStumpTests {
    @Test
    void detect_ShortStumpUnderNeighbourLeaves_ReturnsNotATrunk() {
        TreeGrid grid = new TreeGrid()
                .log(0, 1, 0)
                .log(0, 2, 0)
                .soil(0, 0, 0)
                .leaves(1, 2, 0)
                .leaves(1, 3, 0)
                .leaves(0, 3, 0);

        NotATree rejection = assertInstanceOf(
                NotATree.class,
                TreeShape.detect(new BlockCoord(0, 1, 0), grid),
                "Пень из двух брёвен под чужой кроной обязан быть отвергнут: листья соседа делают его похожим на "
                        + "ствол, и без проверки высоты дух снова выберет недоступный остаток (observe14 A4), иначе "
                        + "рубка встаёт. При смене TreeShape.detect проверь причину ниже в тесте");
        assertEquals(
                NotATree.Reason.NOT_A_TRUNK,
                rejection.reason(),
                "Короткий остаток — не ствол: настоящее дерево выше, и именно высота отличает пень от дерева, иначе "
                        + "пень берётся за дерево. При смене MIN_HEIGHT проверь строку выше в тесте");
    }

    @Test
    void detect_OrchardStumpWithTops_ReturnsTree() {
        TreeGrid grid = new TreeGrid()
                .soil(0, 0, 0)
                .log(0, 1, 0)
                .log(0, 6, 0)
                .leaves(0, 7, 0)
                .leaves(1, 6, 0);

        Tree tree = assertInstanceOf(
                Tree.class,
                TreeShape.detect(new BlockCoord(0, 1, 0), grid, false, true),
                "Пень сада с оторванной верхушкой обязан опознаваться деревом: иначе билет orchard снова падает "
                        + "«не ствол», остатки висят над грядкой, и саженец не сажается. При смене правила пня в "
                        + "TreeShape.detect проверь строку верхушки ниже в тесте");
        assertTrue(
                tree.logs().contains(new BlockCoord(0, 6, 0)),
                "Верхушка в той же колонке входит в список брёвен: иначе дух снимает только пень и оставляет висеть "
                        + "обрубок над будущим саженцем. При смене сканирования колонок проверь строку основания ниже "
                        + "в тесте");
        assertEquals(
                new BlockCoord(0, 1, 0),
                tree.base(),
                "Основание дерева остаётся на клетке пня: иначе пересадка посадит саженец мимо плановой клетки и "
                        + "грядка сада зарастёт. При смене выбора base проверь строку листвы выше в тесте");
    }

    @Test
    void detect_OrchardStumpInHole_ReturnsTree() {
        TreeGrid grid = new TreeGrid().log(0, 1, 0).air(0, 0, 0).soil(0, -1, 0);

        Tree tree = assertInstanceOf(
                Tree.class,
                TreeShape.detect(new BlockCoord(0, 1, 0), grid, false, true),
                "Пень на дне мелкой ямы всё равно убирается: иначе билет висит в провале, пень не снят, и пересадка "
                        + "на его клетку никогда не запускается. При смене проверки почвы для пня проверь строку "
                        + "основания ниже в тесте");
        assertEquals(
                new BlockCoord(0, 1, 0),
                tree.base(),
                "Основание пня в яме остаётся его собственной клеткой: иначе пересадка промахнётся мимо ямы и "
                        + "саженец повиснет в воздухе. При смене отбора base проверь строку выше в тесте");
    }
}
