package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

class PillarPlanTests {
    @Test
    void plan_ThreeBlocks_StepsOneAboveAnother() {
        List<BlockCoord> plan = PillarPlan.plan(3);

        assertEquals(3, plan.size(), "блоков в плане = высота: иначе столб не дотянется до верхних брёвен");
        for (int i = 0; i < plan.size(); i++) {
            assertEquals(i, plan.get(i).y(), "каждая ступень выше предыдущей ровно на блок");
        }
    }

    @Test
    void dismantle_ThreeBlocks_ReturnsTopDown() {
        List<BlockCoord> order = PillarPlan.dismantle(3);

        assertEquals(List.of(2, 1, 0), order.stream().map(BlockCoord::y).toList(),
                "разбор обязан идти сверху вниз: снятый снизу блок оставляет верхние висеть без опоры");
    }

    @Test
    void dismantle_AfterRemovingOwnBlocks_NoOwnBlocksLeft() {
        Set<BlockCoord> own = new HashSet<>(PillarPlan.plan(4));
        own.removeAll(PillarPlan.dismantle(4));

        assertTrue(own.isEmpty(), "после разбора в мире не остаётся ни одного своего блока столба: было " + own);
    }
}

class PillarPlanProperties {
    @Property
    void plan_AnyHeight_CountEqualsHeightAndEveryStepOneAbove(@ForAll int rawHeight) {
        int height = Math.floorMod(rawHeight, 32) + 1;
        List<BlockCoord> plan = PillarPlan.plan(height);

        assertEquals(height, plan.size(), "блоков в плане = высота " + height);
        for (int i = 0; i < plan.size(); i++) {
            assertEquals(i, plan.get(i).y(), "ступень " + i + " обязана быть на " + i + " блоков выше основания");
        }
    }

    @Property
    void dismantle_AnyHeight_RemovesEveryPlannedCell(@ForAll int rawHeight) {
        int height = Math.floorMod(rawHeight, 32) + 1;
        Set<BlockCoord> own = new HashSet<>(PillarPlan.plan(height));

        own.removeAll(PillarPlan.dismantle(height));

        assertTrue(own.isEmpty(), "после разбора высоты " + height + " своих блоков не осталось: " + own);
    }
}
