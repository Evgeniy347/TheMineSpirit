package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TreeShapeTests {
    @Test
    void detect_TrunkWithCrown_ReturnsLogsBottomUp() {
        TreeGrid grid = TreeGrid.ofLayers("#", "L", "L", "L", "L", "L@", "@@");
        BlockCoord start = new BlockCoord(0, 1, 0);

        Tree tree = assertInstanceOf(
                Tree.class,
                TreeShape.detect(start, grid),
                "Ствол с тремя естественными листьями рядом обязан распознаться деревом, иначе дух пройдёт мимо "
                        + "настоящего дерева и поселение останется без древесины. При смене TreeShape.detect проверь "
                        + "строку порядка брёвен ниже в тесте");

        assertEquals(
                new Tree(
                        new BlockCoord(0, 1, 0),
                        List.of(
                                new BlockCoord(0, 1, 0),
                                new BlockCoord(0, 2, 0),
                                new BlockCoord(0, 3, 0),
                                new BlockCoord(0, 4, 0),
                                new BlockCoord(0, 5, 0))),
                tree,
                "Дерево обязано содержать все брёвна ствола снизу вверх: порядок рубки начинается с основания, "
                        + "иначе дух бьёт верхние брёвна и ствол висит в воздухе. При смене сортировки в TreeShape "
                        + "проверь строку распознавания выше в тесте");
    }

    @Test
    void detect_TrunkWithDiagonalBranch_ReturnsAllLogs() {
        TreeGrid grid = TreeGrid.ofLayers("#........", "L........", "LL.......", "L@L@.....", "..@......");
        BlockCoord start = new BlockCoord(0, 1, 0);

        Tree tree = assertInstanceOf(
                Tree.class,
                TreeShape.detect(start, grid),
                "Акация с диагональной веткой обязана распознаться деревом: 26 соседей связывают ветку со стволом, "
                        + "иначе ветка остаётся стоять после рубки. При смене обхода в TreeShape.detect проверь "
                        + "строку брёвен ниже в тесте");

        assertEquals(
                List.of(
                        new BlockCoord(0, 1, 0),
                        new BlockCoord(0, 2, 0),
                        new BlockCoord(1, 2, 0),
                        new BlockCoord(0, 3, 0),
                        new BlockCoord(2, 3, 0)),
                tree.logs(),
                "Диагональная ветка входит в дерево целиком: обход по 26 соседям должен захватить ветку и "
                        + "упорядочить брёвна снизу вверх, а на одном уровне — ближе к основанию, иначе ветка не "
                        + "попадает в план. При смене порядка в TreeShape проверь строку выше в тесте");
    }

    @Test
    void detect_AcaciaFromTrunkAndBranchTip_ReturnsSameTree() {
        TreeGrid grid = new TreeGrid().soil(0, 0, 0);
        for (int y = 1; y <= 5; y++) {
            grid.log(0, y, 0);
        }
        for (int x = 1; x <= 9; x++) {
            grid.log(x, 5, 0);
        }
        grid.leaves(7, 6, 0).leaves(8, 6, 0).leaves(9, 6, 0);
        BlockCoord trunk = new BlockCoord(0, 1, 0);
        BlockCoord branchTip = new BlockCoord(9, 5, 0);

        Tree fromTrunk = assertInstanceOf(
                Tree.class,
                TreeShape.detect(trunk, grid),
                "Акация со ствола обязана распознаться деревом: габарит группы 9 блоков в пределах 2·MAX_SPREAD, "
                        + "иначе дух считает дерево слишком большим. При смене предела в TreeShape.detect проверь "
                        + "кончик ветви ниже в тесте");
        Tree fromTip = assertInstanceOf(
                Tree.class,
                TreeShape.detect(branchTip, grid),
                "Акация с кончика ветви обязана распознаться деревом: стартовое бревно — ближайшее к духу, это "
                        + "может быть конец ветви, и разбор не должен превращать то же дерево в TOO_BIG, иначе "
                        + "выбор начального бревна меняет вердикт. При смене TreeShape.detect проверь строку "
                        + "равенства ниже в тесте");
        assertEquals(
                fromTrunk,
                fromTip,
                "Ответ разбора обязан не зависеть от выбранного бревна: TreeChooser начинает с ближайшего к духу "
                        + "бревна, и одинаковое дерево обязано давать одинаковый план рубки, иначе дух рубит с "
                        + "другого конца. При смене порядка обхода проверь строки выше в тесте");
    }

    @Test
    void detect_TwoByTwoTrunk_ReturnsAllEightLogs() {
        TreeGrid grid = TreeGrid.ofLayers("##\n##", "LL\nLL", "LL\nLL", "@@\n@@");
        BlockCoord start = new BlockCoord(0, 1, 0);

        Tree tree = assertInstanceOf(
                Tree.class,
                TreeShape.detect(start, grid),
                "Ствол 2x2 тёмного дуба обязан распознаться деревом: дух должен рубить толстый ствол целиком, иначе "
                        + "часть ствола остаётся стоять. При смене TreeShape.detect проверь строку размера ниже в "
                        + "тесте");

        assertEquals(
                8,
                tree.logs().size(),
                "Ствол 2x2 из двух слоёв — 8 брёвен: каждое бревно учитывается ровно один раз, иначе план рубки "
                        + "дублирует брёвна. При смене обхода проверь строку множества ниже в тесте");
        assertEquals(
                Set.copyOf(List.of(
                        new BlockCoord(0, 1, 0),
                        new BlockCoord(1, 1, 0),
                        new BlockCoord(0, 1, 1),
                        new BlockCoord(1, 1, 1),
                        new BlockCoord(0, 2, 0),
                        new BlockCoord(1, 2, 0),
                        new BlockCoord(0, 2, 1),
                        new BlockCoord(1, 2, 1))),
                Set.copyOf(tree.logs()),
                "Дерево 2x2 обязано собрать все брёвна обеих слоёв, без пропусков и чужих клеток, иначе дух рубит "
                        + "чужое дерево. При смене TreeGrid проверь строку размера выше в тесте");
    }

    @Test
    void detect_OakOnSlopeWithBranchOverGrass_ReturnsTreeWithBranch() {
        TreeGrid grid =
                TreeGrid.ofLayers(".#......", ".L......", ".L......", ".L...###", ".LLLLLLL", "@L@.....", ".@......");

        Tree tree = assertInstanceOf(
                Tree.class,
                TreeShape.detect(new BlockCoord(1, 1, 0), grid),
                "Настоящий дуб на склоне, ветка которого висит над травой выше основания, обязан рубиться: почва "
                        + "под веткой не признак постройки, иначе дух не тронет ни одного дерева на холме. При "
                        + "смене проверки почвы в TreeShape.detect проверь строку основания ниже в тесте");
        assertEquals(
                new BlockCoord(1, 1, 0),
                tree.base(),
                "Основанием остаётся нижнее бревно ствола, а не ветка над склоном, иначе рубка начинается с ветки и "
                        + "ствол висит. При смене выбора основания проверь строку размера ниже в тесте");
        assertEquals(
                11,
                tree.logs().size(),
                "Дерево на склоне содержит ствол и ветку целиком: 11 брёвен, ничего не отрезано почвой под веткой, "
                        + "иначе дух недорубает ветку. При смене TreeShape.detect проверь строку основания выше в "
                        + "тесте");
    }

    @Test
    void detect_NeighbourTreeNextToSelected_ReturnsOnlySelectedLogs() {
        TreeGrid grid = new TreeGrid().soil(1, 0, 0);
        for (int y = 1; y <= 3; y++) {
            grid.log(1, y, 0);
        }
        grid.leaves(1, 4, 0).leaves(0, 4, 0).leaves(2, 4, 0);
        grid.soil(4, 0, 0);
        for (int y = 1; y <= 3; y++) {
            grid.log(4, y, 0);
        }
        grid.leaves(4, 4, 0).leaves(3, 4, 0).leaves(5, 4, 0);

        Tree tree = assertInstanceOf(
                Tree.class,
                TreeShape.detect(new BlockCoord(1, 1, 0), grid),
                "Выбранное дерево рядом с соседним обязано распознаться: кроны не мешают разбору ствола, иначе "
                        + "соседнее дерево сливается с выбранным. При смене обхода проверь строку множества ниже в "
                        + "тесте");

        assertEquals(
                Set.of(new BlockCoord(1, 1, 0), new BlockCoord(1, 2, 0), new BlockCoord(1, 3, 0)),
                Set.copyOf(tree.logs()),
                "Цели рубки — ровно брёвна выбранного дерева: соседнее дерево вплотную не входит, иначе дальняя "
                        + "досягаемость срубила бы и его. При смене проверки соседей в TreeShape.detect проверь "
                        + "строку распознавания выше в тесте");
    }
}
