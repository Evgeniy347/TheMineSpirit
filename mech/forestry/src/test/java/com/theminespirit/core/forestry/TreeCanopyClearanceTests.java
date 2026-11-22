package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.forestry.api.ChopTuning;
import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TreeCanopyClearanceTests {
    @Test
    void clearance_LeavesInColumns_SkipsPlanAndFarCells() {
        TreeGrid grid = new TreeGrid();
        List<BlockCoord> logs = new ArrayList<>();
        for (int y = 1; y <= 3; y++) {
            grid.log(0, y, 0);
            logs.add(new BlockCoord(0, y, 0));
        }
        grid.leaves(2, 3, 0).leaves(-2, 3, 0).leaves(6, 3, 0).leaves(0, 34, 0);
        Tree tree = new Tree(new BlockCoord(0, 1, 0), logs);

        List<BlockCoord> plan = TreeCanopy.clearance(tree, grid, cell -> cell.equals(new BlockCoord(2, 3, 0)));

        assertEquals(
                List.of(new BlockCoord(0, 34, 0), new BlockCoord(-2, 3, 0)),
                plan,
                "Очистка зоны берёт всю листву в колонках группы в пределах радиуса и высотного окна: дальняя "
                        + "клетка x=6 и плановая x=2 остаются, верхняя y=34 попадает, иначе очистка снимет листву "
                        + "плана или пропустит верхушку. При смене TreeCanopy.clearance проверь строку списка "
                        + "выше в тесте");
    }

    @Test
    void clearance_CellAboveWindow_Ignored() {
        TreeGrid grid = new TreeGrid();
        List<BlockCoord> logs = new ArrayList<>();
        for (int y = 1; y <= 3; y++) {
            grid.log(0, y, 0);
            logs.add(new BlockCoord(0, y, 0));
        }
        int above = 3 + ChopTuning.CLEARANCE_LEAF_UP + 1;
        grid.leaves(0, above, 0);
        Tree tree = new Tree(new BlockCoord(0, 1, 0), logs);

        assertEquals(
                List.of(),
                TreeCanopy.clearance(tree, grid, cell -> false),
                "Листва выше высотного окна не входит в план очистки: окно ограничивает объём сканирования, а не "
                        + "досягаемость рубки, иначе скан идёт по всей колонне и дух теряет тик. При смене "
                        + "CLEARANCE_LEAF_UP проверь строку выше в тесте");
    }
}
