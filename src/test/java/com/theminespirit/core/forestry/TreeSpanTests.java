package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TreeSpanTests {
    private static final Set<BlockCoord> OAK = Set.of(
            new BlockCoord(-169, 70, -501),
            new BlockCoord(-169, 71, -501),
            new BlockCoord(-169, 72, -501),
            new BlockCoord(-169, 73, -501),
            new BlockCoord(-169, 74, -501),
            new BlockCoord(-168, 74, -501));
    private static final BlockCoord HOUSE_LOG = new BlockCoord(-160, 71, -501);

    @Test
    void of_TrunkCellInSchemeAir_WholeTreeTopDown() {
        List<BlockCoord> tree =
                TreeSpan.of(new BlockCoord(-169, 72, -501), cell -> OAK.contains(cell) || cell.equals(HOUSE_LOG));

        assertEquals(
                List.of(OAK.size(), 74, 70),
                List.of(tree.size(), tree.get(0).y(), tree.get(tree.size() - 1).y()),
                "INC-89: ствол в клетке схемы снимается целиком — от макушки до пня, а не вырезом y71–72, "
                        + "оставляющим бревно над головой; чужое бревно постройки в стороне не трогается: " + tree);
    }

    @Test
    void of_NotALog_Empty() {
        assertEquals(
                List.of(),
                TreeSpan.of(new BlockCoord(0, 0, 0), cell -> false),
                "Не бревно — дерева нет, снимать нечего");
    }
}
