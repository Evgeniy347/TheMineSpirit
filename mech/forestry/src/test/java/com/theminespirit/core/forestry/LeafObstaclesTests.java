package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import org.junit.jupiter.api.Test;

class LeafObstaclesTests {
    @Test
    void blockingStand_LeavesOnStandCells_ReturnsThemFeetFirst() {
        TreeGrid grid =
                new TreeGrid().log(0, 64, 0).soil(1, 63, 0).leaves(1, 64, 0).leaves(1, 65, 0);

        assertEquals(
                List.of(new BlockCoord(1, 64, 0), new BlockCoord(1, 65, 0)),
                LeafObstacles.blockingStand(trunk(), grid, new BlockCoord(0, 64, 0)),
                "Стоянка у ствола — два блока воздуха по высоте; листва в обеих клетках закрывает подход, и дух "
                        + "рубит издалека. План обязан сломать именно эти две клетки, начиная с ближней, иначе ветка "
                        + "висит. При смене LeafObstacles.blockingStand проверь строку свободной стоянки ниже в тесте");
    }

    @Test
    void blockingStand_ClearStand_IsEmpty() {
        TreeGrid grid = new TreeGrid().log(0, 64, 0).soil(1, 63, 0);

        assertEquals(
                List.of(),
                LeafObstacles.blockingStand(trunk(), grid, new BlockCoord(0, 64, 0)),
                "Свободная стоянка не требует ломки: пустой план, иначе дух зря тратит тики на листву, которой на "
                        + "пути нет. При смене blockingStand проверь строку чужой листвы ниже в тесте");
    }

    @Test
    void blockingStand_ForeignLeaves_AreCleared() {
        TreeGrid grid = new TreeGrid()
                .log(0, 64, 0)
                .log(2, 64, 0)
                .soil(1, 63, 0)
                .leaves(1, 64, 0)
                .leaves(1, 65, 0)
                .leaves(-1, 64, 0)
                .leaves(-1, 65, 0);

        assertEquals(
                List.of(
                        new BlockCoord(-1, 64, 0),
                        new BlockCoord(1, 64, 0),
                        new BlockCoord(-1, 65, 0),
                        new BlockCoord(1, 65, 0)),
                LeafObstacles.blockingStand(trunk(), grid, new BlockCoord(0, 64, 0)),
                "Листва соседнего дерева — тоже листва: она мешает встать у ствола так же, как своя, и её тоже "
                        + "надо ломать, иначе стоянка остаётся завалена. При смене проверки листвы проверь строку "
                        + "досок ниже в тесте");
    }

    @Test
    void blockingStand_PlanksOnStandCells_AreNotTouched() {
        TreeGrid grid =
                new TreeGrid().log(0, 64, 0).soil(1, 63, 0).planks(1, 64, 0).planks(1, 65, 0);

        assertEquals(
                List.of(),
                LeafObstacles.blockingStand(trunk(), grid, new BlockCoord(0, 64, 0)),
                "Руками ломается только листва: доски, заборы и прочие постройки игрока не трогаются, иначе рубка "
                        + "сносит чужую стройку. При смене проверки искусственных блоков проверь строку выше головы "
                        + "ниже в тесте");
    }

    @Test
    void blockingStand_LeavesAboveHead_AreIgnored() {
        TreeGrid grid = new TreeGrid().log(0, 64, 0).soil(1, 63, 0).leaves(1, 66, 0);

        assertEquals(
                List.of(),
                LeafObstacles.blockingStand(trunk(), grid, new BlockCoord(0, 64, 0)),
                "Стоянка занимает ровно две клетки по высоте: листва выше головы не мешает встать, и ломать её "
                        + "незачем, иначе дух бьёт декор без нужды. При смене высоты стоянки проверь строку дальней "
                        + "стоянки ниже в тесте");
    }

    @Test
    void blockingStand_LeavesOnFarStand_AreCleared() {
        TreeGrid grid =
                new TreeGrid().log(0, 64, 0).soil(3, 63, 0).leaves(3, 64, 0).leaves(3, 65, 0);

        assertTrue(
                LeafObstacles.blockingStand(trunk(), grid, new BlockCoord(0, 64, 0))
                        .containsAll(List.of(new BlockCoord(3, 64, 0), new BlockCoord(3, 65, 0))),
                "Стоянка в трёх блоках от ствола — та же стоянка: если у ствола всё занято, листва на ней тоже "
                        + "мешает встать, и без её ломки высокое дерево (2×2 jungle) остаётся недостижимым, "
                        + "иначе рубка встанет. При "
                        + "смене перебора стоянок проверь строку null ниже в тесте");
    }

    @Test
    void blockingStand_NullView_Throws() {
        assertThrows(
                NullPointerException.class,
                () -> LeafObstacles.blockingStand(trunk(), null, new BlockCoord(0, 64, 0)),
                "Без TreeView листва неотличима от прочих блоков: план молча снёс бы стену или доски, иначе "
                        + "политика «не трогать постройки» обходится. При смене проверки view проверь строку выше в "
                        + "тесте");
    }

    private static Tree trunk() {
        return new Tree(new BlockCoord(0, 64, 0), List.of(new BlockCoord(0, 64, 0), new BlockCoord(0, 65, 0)));
    }
}
