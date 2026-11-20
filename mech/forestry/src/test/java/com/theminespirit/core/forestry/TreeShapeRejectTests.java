package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.forestry.api.ChopTuning;
import com.theminespirit.core.observation.BlockCoord;
import org.junit.jupiter.api.Test;

class TreeShapeRejectTests {
    private static final BlockCoord AIR_START = new BlockCoord(0, 0, 0);

    @Test
    void detect_HutWithoutLeaves_ReturnsNotATrunk() {
        TreeGrid grid = TreeGrid.ofLayers("###", "LLL", "LLL", "LLL", "LLL");

        NotATree rejection = assertInstanceOf(
                NotATree.class,
                TreeShape.detect(new BlockCoord(0, 1, 0), grid),
                "Хижина из брёвен без листвы обязана быть отвергнута как не-дерево: бревенчатая постройка игрока не "
                        + "должна ломаться под видом леса, иначе дух сносит дом. При смене TreeShape.detect проверь "
                        + "причину ниже в тесте");
        assertEquals(
                NotATree.Reason.NOT_A_TRUNK,
                rejection.reason(),
                "Хижина отвергается по форме нижнего слоя: основание 3x3 — больше четырёх брёвен, которые допускает "
                        + "квадрат 2x2 у настоящего ствола, иначе хижина проходит как дерево. При смене формы ствола "
                        + "проверь строку хижины у дуба ниже в тесте");
    }

    @Test
    void detect_HutNextToOakWithCrown_ReturnsNotATrunk() {
        TreeGrid grid = TreeGrid.ofLayers(
                "####\n####\n####", "LLLL\n.LLL\n.LLL", "LLLL\n.LLL\n.LLL", "LLLL\n.LLL\n.LLL", ".@..\n@@..\n....");

        NotATree rejection = assertInstanceOf(
                NotATree.class,
                TreeShape.detect(new BlockCoord(1, 1, 1), grid),
                "Бревенчатая хижина вплотную к дубу обязана быть отвергнута как не-дерево: крона дуба нависает и "
                        + "даёт три листа, но сруб постройки игрока запрещён политикой «не трогать постройки», иначе "
                        + "дух рубит стену. При смене политики проверь причину ниже в тесте");
        assertEquals(
                NotATree.Reason.NOT_A_TRUNK,
                rejection.reason(),
                "Хижина с примкнувшим стволом отвергается по форме нижнего слоя: основание 3x3 шире, чем четыре "
                        + "бревна в квадрате 2x2 у настоящего дерева, иначе порог формы пропускает сруб. При смене "
                        + "MAX_TRUNK проверь строку столба ниже в тесте");
    }

    @Test
    void detect_PillarUnderNeighborCrown_ReturnsNotATrunk() {
        TreeGrid grid = TreeGrid.ofLayers("####", "LLL.", "LLL.", ".@@@");

        NotATree rejection = assertInstanceOf(
                NotATree.class,
                TreeShape.detect(new BlockCoord(0, 1, 0), grid),
                "Столб из брёвен под кроной соседнего дерева обязан быть отвергнут как не-дерево: листья чужой "
                        + "кроны касаются постройки, но рубить её дух не должен, иначе он сносит забор под чужим "
                        + "деревом. При смене проверки кроны проверь причину ниже в тесте");
        assertEquals(
                NotATree.Reason.NOT_A_TRUNK,
                rejection.reason(),
                "Постройка отвергается по форме нижнего слоя: три бревна в линию не влезают в квадрат 2x2, листья "
                        + "же чужого дерева могут висеть над любой постройкой, иначе столб принимается за ствол. При "
                        + "смене формы проверь строку хижины выше в тесте");
    }

    @Test
    void reject_HutNotATree_CarriesAllItsLogs() {
        TreeGrid grid = TreeGrid.ofLayers("###", "LLL", "LLL", "LLL", "LLL");

        NotATree rejection = assertInstanceOf(
                NotATree.class,
                TreeShape.detect(new BlockCoord(0, 1, 0), grid),
                "Хижина обязана быть отвергнута как не-дерево: это постройка игрока, а не лес, иначе дух рубит дом. "
                        + "При смене TreeShape.detect проверь строку брёвен ниже в тесте");
        assertEquals(
                grid.logs(),
                rejection.logs(),
                "Отвергнутая группа обязана нести все свои брёвна: по одному разбору на хижину адаптер заносит в "
                        + "known всю группу, иначе на каждое бревно постройки будет отдельный разбор и дух потратит "
                        + "попытки на стены вместо настоящих деревьев. При смене NotATree.logs проверь строку "
                        + "TOO_BIG ниже в тесте");
    }

    @Test
    void detect_TrunkInAir_ReturnsNoSoil() {
        TreeGrid grid = TreeGrid.ofLayers(".", "L", "L", "L", "@@@");

        NotATree rejection = assertInstanceOf(
                NotATree.class,
                TreeShape.detect(new BlockCoord(0, 1, 0), grid),
                "Ствол без почвы под основанием обязан быть отвергнут как не-дерево: летающее бревно — арт-объект "
                        + "или постройка, а не лес, иначе дух рубит декор. При смене проверки почвы проверь причину "
                        + "ниже в тесте");
        assertEquals(
                NotATree.Reason.NO_SOIL,
                rejection.reason(),
                "Ствол в воздухе отвергается по почве под нижним бревном, а не по кроне: над листьями проверяется, "
                        + "что дерево стоит на земле, иначе висячий ствол признаётся лесом. При смене порядка "
                        + "проверок проверь строку 200 брёвен ниже в тесте");
    }

    @Test
    void detect_TwoHundredLogs_ReturnsTooBig() {
        TreeGrid grid = new TreeGrid().soil(0, 0, 0);
        for (int y = 1; y <= 200; y++) {
            grid.log(0, y, 0);
        }

        NotATree rejection = assertInstanceOf(
                NotATree.class,
                TreeShape.detect(new BlockCoord(0, 1, 0), grid),
                "Связная группа из 200 брёвен обязана быть отвергнута по размеру: разбор гигантского массива стоит "
                        + "больше лимита MAX_LOGS и завешивает тик, иначе сервер встаёт. При смене "
                        + "ChopTuning.MAX_LOGS проверь причину ниже в тесте");
        assertEquals(
                NotATree.Reason.TOO_BIG,
                rejection.reason(),
                "Группа больше лимита отвергается как TOO_BIG: разбор обязан останавливаться по числу брёвен, а не "
                        + "обходить весь массив, иначе лаг на густом лесу. При смене предела проверь размер ниже в "
                        + "тесте");
        assertEquals(
                ChopTuning.MAX_LOGS + 1,
                rejection.logs().size(),
                "Отвергнутая группа несёт пройденные до остановки брёвна: адаптер заносит в known именно их, чтобы "
                        + "не разбирать гигантский массив по одному бревну, иначе повторный разбор. При смене "
                        + "остановки в TreeShape.detect проверь строку причины выше в тесте");
    }

    @Test
    void detect_TwoLeavesOnly_ReturnsNoLeaves() {
        TreeGrid grid = TreeGrid.ofLayers("#", "L", "L", "L", "@@");

        NotATree rejection = assertInstanceOf(
                NotATree.class,
                TreeShape.detect(new BlockCoord(0, 1, 0), grid),
                "Два естественных листа вокруг брёвен — ещё не дерево: граница в три листа отличает лес от "
                        + "декоративного столба и забора, иначе дух рубит декор. При смене MIN_NATURAL_LEAVES проверь "
                        + "причину ниже в тесте");
        assertEquals(
                NotATree.Reason.NO_LEAVES,
                rejection.reason(),
                "Две листвы рядом со стволом не дают крону: меньше MIN_NATURAL_LEAVES — группа отвергается, иначе "
                        + "один куст считается деревом. При смене порога проверь строку хижины на склоне ниже в "
                        + "тесте");
    }

    @Test
    void detect_HutOnSlopeWithPlanksTouchingBranches_ReturnsBuiltNearby() {
        TreeGrid grid =
                TreeGrid.ofLayers(".#......", ".L......", ".L......", ".L...###", ".LLLLLLL", "@L@..PPP", ".@......");

        NotATree rejection = assertInstanceOf(
                NotATree.class,
                TreeShape.detect(new BlockCoord(1, 1, 0), grid),
                "Пол из брёвен на склоне с досками поверх, касающийся ствола, обязан быть отвергнут как постройка: "
                        + "сруб пола под видом ветвей — нарушение политики «не трогать постройки игрока», иначе дух "
                        + "ломает постройку. При смене проверки досок проверь причину ниже в тесте");
        assertEquals(
                NotATree.Reason.BUILT_NEARBY,
                rejection.reason(),
                "Постройку выдают искусственные блоки (доски) вплотную к брёвнам группы, а не почва под ветвью, "
                        + "иначе дерево на склоне с досками принимается за ветку. При смене проверки BUILT_NEARBY "
                        + "проверь строку столба ниже в тесте");
    }

    @Test
    void detect_PostBesidePlanksUnderNeighborCrown_ReturnsBuiltNearby() {
        TreeGrid grid = TreeGrid.ofLayers("#.", "LP", "LP", "L@", "@@");

        NotATree rejection = assertInstanceOf(
                NotATree.class,
                TreeShape.detect(new BlockCoord(0, 1, 0), grid),
                "Угловой столб сруба с досками стены, под кроной соседнего дерева, обязан быть отвергнут: один "
                        + "столб с листьями вокруг неотличим от ствола, кроме досок рядом, иначе дух рубит угол "
                        + "сруба. При смене проверки досок проверь причину ниже в тесте");
        assertEquals(
                NotATree.Reason.BUILT_NEARBY,
                rejection.reason(),
                "Доски вплотную к бревну выдают постройку раньше, чем листья чужой кроны выдают дерево, иначе "
                        + "порядок проверок меняет вердикт. При смене порядка проверок проверь строку на склоне выше "
                        + "в тесте");
    }

    @Test
    void detect_StartNotALog_Throws() {
        TreeGrid grid = TreeGrid.ofLayers("#", "L", "@@@");

        assertThrows(
                IllegalArgumentException.class,
                () -> TreeShape.detect(AIR_START, grid),
                "Разбор с не-бревна обязан отказывать: искать дерево с пустой клетки бессмысленно, иначе разбор "
                        + "молча возвращает NotATree и адаптер вешает попытки. При смене проверки старта в "
                        + "TreeShape.detect проверь строку соседа в TreeShapeTests");
    }

    @Test
    void detect_LogHutNextToSelected_IsRejectedAsNotATree() {
        TreeGrid grid = new TreeGrid().soil(0, 0, 0);
        for (int y = 1; y <= 4; y++) {
            grid.log(0, y, 0);
        }
        grid.leaves(0, 5, 0).leaves(-1, 5, 0).leaves(0, 5, 1);
        for (int y = 1; y <= 2; y++) {
            for (int dx = 1; dx <= 2; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    grid.log(dx, y, dz);
                }
            }
        }

        NotATree rejection = assertInstanceOf(
                NotATree.class,
                TreeShape.detect(new BlockCoord(0, 1, 0), grid),
                "Бревенчатая постройка вплотную к выбранному дереву обязана быть отвергнута: сруб постройки игрока "
                        + "запрещён политикой «не трогать постройки», иначе дух рубит стену рядом с деревом. При "
                        + "смене TreeShape.detect проверь причину ниже в тесте");

        assertEquals(
                NotATree.Reason.NOT_A_TRUNK,
                rejection.reason(),
                "Постройка выдаёт себя формой нижнего слоя: пять брёвен в основании шире квадрата 2x2, поэтому ни "
                        + "одно её бревно не попадает в цели рубки, иначе сруб сливается с деревом. При смене формы "
                        + "проверь строку выше в тесте");
    }
}
