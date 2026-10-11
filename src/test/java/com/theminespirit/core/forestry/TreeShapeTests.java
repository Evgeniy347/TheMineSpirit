package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TreeShapeTests {
    private static final BlockCoord AIR_START = new BlockCoord(0, 0, 0);

    @Test
    void detect_TrunkWithCrown_ReturnsLogsBottomUp() {
        TreeGrid grid = TreeGrid.ofLayers(
                "#",
                "L",
                "L",
                "L",
                "L",
                "L@",
                "@@");
        BlockCoord start = new BlockCoord(0, 1, 0);

        Tree tree = assertInstanceOf(Tree.class, TreeShape.detect(start, grid),
                "Ствол с тремя естественными листьями рядом обязан распознаться деревом: иначе дух "
                        + "пройдёт мимо настоящего дерева");

        assertEquals(new Tree(new BlockCoord(0, 1, 0),
                        List.of(new BlockCoord(0, 1, 0), new BlockCoord(0, 2, 0), new BlockCoord(0, 3, 0),
                                new BlockCoord(0, 4, 0), new BlockCoord(0, 5, 0))),
                tree,
                "Дерево обязано содержать все брёвна ствола снизу вверх: порядок рубки начинается с основания, "
                        + "поэтому список брёвен отсортирован по высоте");
    }

    @Test
    void detect_TrunkWithDiagonalBranch_ReturnsAllLogs() {
        TreeGrid grid = TreeGrid.ofLayers(
                "#........",
                "L........",
                "LL.......",
                "L@L@.....",
                "..@......");
        BlockCoord start = new BlockCoord(0, 1, 0);

        Tree tree = assertInstanceOf(Tree.class, TreeShape.detect(start, grid),
                "Акация с диагональной веткой обязана распознаться деревом: 26 соседей связывают ветку со стволом");

        assertEquals(List.of(new BlockCoord(0, 1, 0), new BlockCoord(0, 2, 0), new BlockCoord(1, 2, 0),
                        new BlockCoord(0, 3, 0), new BlockCoord(2, 3, 0)),
                tree.logs(),
                "Диагональная ветка входит в дерево целиком: обход по 26 соседям должен захватить ветку "
                        + "и упорядочить брёвна снизу вверх, а на одном уровне — ближе к основанию");
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

        Tree fromTrunk = assertInstanceOf(Tree.class, TreeShape.detect(trunk, grid),
                "Акация со ствола обязана распознаться деревом: габарит группы 9 блоков в пределах "
                        + "2·MAX_SPREAD, дух обязан рубить такое дерево");
        Tree fromTip = assertInstanceOf(Tree.class, TreeShape.detect(branchTip, grid),
                "Акация с кончика ветви обязана распознаться деревом: стартовое бревно — ближайшее к духу, "
                        + "это может быть конец ветви, и разбор не должен превращать то же дерево в TOO_BIG");
        assertEquals(fromTrunk, fromTip,
                "Ответ разбора обязан не зависеть от выбранного бревна: TreeChooser начинает с ближайшего "
                        + "к духу бревна, и одинаковое дерево обязано давать одинаковый план рубки");
    }

    @Test
    void detect_TwoByTwoTrunk_ReturnsAllEightLogs() {
        TreeGrid grid = TreeGrid.ofLayers(
                "##\n##",
                "LL\nLL",
                "LL\nLL",
                "@@\n@@");
        BlockCoord start = new BlockCoord(0, 1, 0);

        Tree tree = assertInstanceOf(Tree.class, TreeShape.detect(start, grid),
                "Ствол 2x2 тёмного дуба обязан распознаться деревом: дух должен рубить толстый ствол целиком");

        assertEquals(8, tree.logs().size(),
                "Ствол 2x2 из двух слоёв — 8 брёвен: каждое бревно учитывается ровно один раз");
        assertEquals(Set.copyOf(List.of(new BlockCoord(0, 1, 0), new BlockCoord(1, 1, 0),
                        new BlockCoord(0, 1, 1), new BlockCoord(1, 1, 1), new BlockCoord(0, 2, 0),
                        new BlockCoord(1, 2, 0), new BlockCoord(0, 2, 1), new BlockCoord(1, 2, 1))),
                Set.copyOf(tree.logs()),
                "Дерево 2x2 обязано собрать все брёвна обеих слоёв, без пропусков и чужих клеток");
    }

    @Test
    void detect_HutWithoutLeaves_ReturnsNoLeaves() {
        TreeGrid grid = TreeGrid.ofLayers(
                "###",
                "LLL",
                "LLL",
                "LLL",
                "LLL");

        NotATree rejection = assertInstanceOf(NotATree.class, TreeShape.detect(new BlockCoord(0, 1, 0), grid),
                "Хижина из брёвен без листвы обязана быть отвергнута как не-дерево: бревенчатая постройка "
                        + "игрока не должна ломаться под видом леса");
        assertEquals(NotATree.Reason.NO_LEAVES, rejection.reason(),
                "Хижина без листвы отвергается из-за отсутствия кроны: листьев вокруг брёвен меньше "
                        + "MIN_NATURAL_LEAVES");
    }

    @Test
    void reject_HutNotATree_CarriesAllItsLogs() {
        TreeGrid grid = TreeGrid.ofLayers(
                "###",
                "LLL",
                "LLL",
                "LLL",
                "LLL");

        NotATree rejection = assertInstanceOf(NotATree.class, TreeShape.detect(new BlockCoord(0, 1, 0), grid),
                "Хижина обязана быть отвергнута как не-дерево: это постройка игрока, а не лес");
        assertEquals(grid.logs(), rejection.logs(),
                "Отвергнутая группа обязана нести все свои брёвна: по одному разбору на хижину адаптер заносит "
                        + "в known всю группу, иначе на каждое бревно постройки будет отдельный разбор и счёт "
                        + "«отвергнутых» переполнит MAX_REJECTED_TREES до того, как дух дойдёт до настоящих деревьев");
    }

    @Test
    void detect_TrunkInAir_ReturnsNoSoil() {
        TreeGrid grid = TreeGrid.ofLayers(
                ".",
                "L",
                "L",
                "L",
                "@@@");

        NotATree rejection = assertInstanceOf(NotATree.class, TreeShape.detect(new BlockCoord(0, 1, 0), grid),
                "Ствол без почвы под основанием обязан быть отвергнут как не-дерево: летающее бревно — арт-объект "
                        + "или постройка, а не лес");
        assertEquals(NotATree.Reason.NO_SOIL, rejection.reason(),
                "Ствол в воздухе отвергается по почве под нижним бревном, а не по кроне: над листьями "
                        + "проверяется, что дерево стоит на земле");
    }

    @Test
    void detect_TwoHundredLogs_ReturnsTooBig() {
        TreeGrid grid = new TreeGrid().soil(0, 0, 0);
        for (int y = 1; y <= 200; y++) {
            grid.log(0, y, 0);
        }

        NotATree rejection = assertInstanceOf(NotATree.class, TreeShape.detect(new BlockCoord(0, 1, 0), grid),
                "Связная группа из 200 брёвен обязана быть отвергнута по размеру: разбор гигантского массива "
                        + "стоит больше лимита MAX_LOGS и завешивает тик");
        assertEquals(NotATree.Reason.TOO_BIG, rejection.reason(),
                "Группа больше лимита отвергается как TOO_BIG: разбор обязан останавливаться по числу брёвен, "
                        + "а не обходить весь массив");
        assertEquals(ChopTuning.MAX_LOGS + 1, rejection.logs().size(),
                "Отвергнутая группа несёт пройденные до остановки брёвна: адаптер заносит в known именно их, "
                        + "чтобы не разбирать гигантский массив по одному бревну");
    }

    @Test
    void detect_TwoLeavesOnly_ReturnsNoLeaves() {
        TreeGrid grid = TreeGrid.ofLayers(
                "#",
                "L",
                "L",
                "L",
                "@@");

        NotATree rejection = assertInstanceOf(NotATree.class, TreeShape.detect(new BlockCoord(0, 1, 0), grid),
                "Два естественных листа вокруг брёвен — ещё не дерево: граница в три листа отличает лес "
                        + "от декоративного столба и забора");
        assertEquals(NotATree.Reason.NO_LEAVES, rejection.reason(),
                "Две листвы рядом со стволом не дают крону: меньше MIN_NATURAL_LEAVES — группа отвергается");
    }

    @Test
    void detect_StartNotALog_Throws() {
        TreeGrid grid = TreeGrid.ofLayers("#", "L", "@@@");

        assertThrows(IllegalArgumentException.class,
                () -> TreeShape.detect(AIR_START, grid),
                "Разбор с не-бревна обязан отказывать: искать дерево с пустой клетки бессмысленно");
    }
}