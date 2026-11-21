package com.theminespirit.core.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PillarPlanTests {
    @Test
    void plan_ThreeBlocks_StepsOneAboveAnother() {
        List<BlockCoord> plan = PillarPlan.plan(3);

        assertEquals(
                3,
                plan.size(),
                "блоков в плане = высота: иначе столб не дотянется до верхних брёвен"
                        + " При смене plan_ThreeBlocks_StepsOneAboveAnother проверь это утверждение.");
        for (int i = 0; i < plan.size(); i++) {
            assertEquals(
                    i,
                    plan.get(i).y(),
                    "каждая ступень выше предыдущей ровно на блок"
                            + " Иначе тест plan_ThreeBlocks_StepsOneAboveAnother перестанет ловить эту поломку. При смене plan_ThreeBlocks_StepsOneAboveAnother проверь это утверждение.");
        }
    }

    @Test
    void dismantle_ThreeBlocks_ReturnsTopDown() {
        List<BlockCoord> order = PillarPlan.dismantle(3);

        assertEquals(
                List.of(2, 1, 0),
                order.stream().map(BlockCoord::y).toList(),
                "разбор обязан идти сверху вниз: снятый снизу блок оставляет верхние висеть без опоры"
                        + " Иначе тест dismantle_ThreeBlocks_ReturnsTopDown перестанет ловить эту поломку. При смене dismantle_ThreeBlocks_ReturnsTopDown проверь это утверждение.");
    }

    @Test
    void dismantle_AfterRemovingOwnBlocks_NoOwnBlocksLeft() {
        Set<BlockCoord> own = new HashSet<>(PillarPlan.plan(4));
        own.removeAll(PillarPlan.dismantle(4));

        assertTrue(
                own.isEmpty(),
                "после разбора в мире не остаётся ни одного своего блока столба: было " + own
                        + " Иначе тест dismantle_AfterRemovingOwnBlocks_NoOwnBlocksLeft перестанет ловить эту поломку. При смене dismantle_AfterRemovingOwnBlocks_NoOwnBlocksLeft проверь это утверждение.");
    }
}
