package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettlementHomeTests {
    @Test
    void center_NoHomes_IsAbsent() {
        assertTrue(
                SettlementHome.center(List.of()).isEmpty(),
                "Поселение без домов не имеет центра: подставлять (0,64,0) нельзя, иначе dig_out и снабжение "
                        + "идут в пустой угол мира. Как надо: отсутствие видно в Optional");
    }

    @Test
    void center_SpiritOrder_KeepsMedianPoint() {
        BlockCoord first = new BlockCoord(-171, 72, -497);
        BlockCoord second = new BlockCoord(-165, 72, -491);
        BlockCoord third = new BlockCoord(-168, 72, -494);

        assertEquals(
                Optional.of(new BlockCoord(-168, 72, -494)),
                SettlementHome.center(List.of(first, second, third)),
                "Центр домов — середина по каждой оси и не зависит от порядка духов: иначе цель выхода и "
                        + "сцена меняются от порядка сущностей в мире");
        assertEquals(
                SettlementHome.center(List.of(first, second, third)),
                SettlementHome.center(List.of(third, first, second)),
                "Перестановка домов не двигает центр поселения: иначе дом духа и цель dig_out расходятся");
    }
}
