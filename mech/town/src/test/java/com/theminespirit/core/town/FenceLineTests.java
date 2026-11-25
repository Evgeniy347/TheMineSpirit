package com.theminespirit.core.town;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FenceLineTests {
    @Test
    void hasPost_EveryPostFound_InnerCellRejected() {
        ColumnMask area = ColumnMask.rect(0, 0, 40, 30);
        List<ColumnMask.Cell> posts = FenceLine.loop(area);
        FenceLine line = new FenceLine(posts, List.of(0));
        for (ColumnMask.Cell post : posts) {
            assertTrue(
                    line.hasPost(post.x(), post.z()),
                    "Столб " + post + " линии ограды обязан находиться по индексу: иначе защита считает столб "
                            + "обломком и разрешает снести ограду, а проверка идёт на каждой клетке расчистки. "
                            + "При смене FenceLine.hasPost проверь StageSiteTests и защиту ограды в живом мире");
        }
        assertFalse(
                line.hasPost(2, 2),
                "Клетка внутри контура не столб ограды: без этого индекса защита путает любую клетку контура "
                        + "со столбом и держит лишние блоки при расчистке. При смене FenceLine.hasPost проверь "
                        + "StageSiteProtectionTests и снос ограды по заданию");
    }
}
