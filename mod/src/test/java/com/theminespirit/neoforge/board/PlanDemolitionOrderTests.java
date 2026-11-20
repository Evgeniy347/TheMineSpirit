package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlanDemolitionOrderTests {
    @Test
    void order_FenceAmongTallerStrays_FenceGoesFirst() {
        BlockCoord highDirt = new BlockCoord(0, 70, 0);
        BlockCoord fence = new BlockCoord(5, 68, 5);

        assertEquals(
                List.of(fence, highDirt),
                PlanDemolition.order(List.of(highDirt, fence), Set.of(fence)),
                "Столб-заглушка обязан идти в уборку первым: иначе задача на снос забора ждёт тысячи чужих клеток");
    }

    @Test
    void order_WithoutFences_TallestGoesFirst() {
        BlockCoord low = new BlockCoord(0, 68, 0);
        BlockCoord high = new BlockCoord(1, 70, 1);

        assertEquals(
                List.of(high, low),
                PlanDemolition.order(List.of(low, high), Set.of()),
                "Без заглушек очередь прежняя: выше — раньше");
    }
}
