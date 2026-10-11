package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChopPlanTests {
    @Test
    void of_EightLogsAboveSixtyFour_ReachableSixLeftTwo() {
        ChopPlan plan = ChopPlan.of(trunk(64, 8), ChopTuning.REACH_ABOVE_BASE);

        assertEquals(List.of(new BlockCoord(0, 64, 0), new BlockCoord(0, 65, 0), new BlockCoord(0, 66, 0),
                        new BlockCoord(0, 67, 0), new BlockCoord(0, 68, 0), new BlockCoord(0, 69, 0)),
                plan.reachable(),
                "При досягаемости 5 блоков над основанием из 8 брёвен рубится нижние 6: дух стоит на земле "
                        + "и не дотягивается до верхних брёвен без подъёма");
        assertEquals(2, plan.leftStanding(),
                "Два верхних бревна выше досягаемости обязан посчитать план как оставленные стоять: "
                        + "их число уходит в итоговое сообщение игроку");
    }

    @Test
    void of_SingleLog_ReachableOneLeftZero() {
        ChopPlan plan = ChopPlan.of(trunk(64, 1), ChopTuning.REACH_ABOVE_BASE);

        assertEquals(List.of(new BlockCoord(0, 64, 0)), plan.reachable(),
                "Одинокий столб из одного бревна полностью достижим: план должен предложить его срубить");
        assertEquals(0, plan.leftStanding(),
                "Одиночный столб не оставляет брёвен выше досягаемости");
    }

    @Test
    void of_LogAtExactReachBoundary_IsReachable() {
        ChopPlan plan = ChopPlan.of(trunk(64, 6), ChopTuning.REACH_ABOVE_BASE);

        assertEquals(6, plan.reachable().size(),
                "Бревно ровно на границе base.y + reachAboveBase обязано входить в достижимые: граница "
                        + "инклюзивна, иначе дух оставит видное бревно без причины");
        assertEquals(0, plan.leftStanding(),
                "Дерево до границы включительно рубятся целиком, оставленных брёвен нет");
    }

    @Test
    void of_NegativeReach_Throws() {
        assertThrows(IllegalArgumentException.class, () -> ChopPlan.of(trunk(64, 3), -1),
                "Отрицательная досягаемость недопустима: план с ней заявляет, что рубить нечего");
    }

    private static Tree trunk(int baseY, int count) {
        List<BlockCoord> logs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            logs.add(new BlockCoord(0, baseY + i, 0));
        }
        return new Tree(new BlockCoord(0, baseY, 0), logs);
    }
}