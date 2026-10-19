package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChopPlanTests {
    @Test
    void of_StraightTrunk_OrdersFarthestFirst() {
        ChopPlan plan = ChopPlan.of(trunk(64, 6), new BlockCoord(0, 64, 0));

        assertEquals(
                List.of(
                        new BlockCoord(0, 69, 0),
                        new BlockCoord(0, 68, 0),
                        new BlockCoord(0, 67, 0),
                        new BlockCoord(0, 66, 0),
                        new BlockCoord(0, 65, 0),
                        new BlockCoord(0, 64, 0)),
                plan.order(),
                "Дух рубит с одной стоянки и дотягивается до любого бревна: порядок обязан идти от самого "
                        + "дальнего (высокого) бревна к ближнему, а не снизу вверх");
    }

    @Test
    void of_FarBranch_ChoppedBeforeNearHighTrunk() {
        Tree tree = new Tree(
                new BlockCoord(0, 64, 0),
                List.of(new BlockCoord(0, 64, 0), new BlockCoord(0, 65, 0), new BlockCoord(5, 64, 0)));
        ChopPlan plan = ChopPlan.of(tree, new BlockCoord(0, 64, 0));

        assertEquals(
                List.of(new BlockCoord(5, 64, 0), new BlockCoord(0, 65, 0), new BlockCoord(0, 64, 0)),
                plan.order(),
                "Порядок считается по расстоянию от стоянки, а не по одной высоте: далёкая ветка впереди "
                        + "низкого верхнего бревна ствола, иначе «самое дальнее» правило нарушено");
    }

    @Test
    void of_SingleLog_ReturnsIt() {
        ChopPlan plan = ChopPlan.of(trunk(64, 1), new BlockCoord(0, 64, 0));

        assertEquals(
                List.of(new BlockCoord(0, 64, 0)),
                plan.order(),
                "Одинокое бревно — вся цель рубки: план обязан нести его единственным пунктом");
    }

    @Test
    void of_NullTree_Throws() {
        assertThrows(
                NullPointerException.class,
                () -> ChopPlan.of(null, new BlockCoord(0, 64, 0)),
                "План без дерева построить нельзя: отсутствие проверки отдало бы пустой план и дух бросил бы рубку молча");
    }

    @Test
    void of_NullFrom_Throws() {
        assertThrows(
                NullPointerException.class,
                () -> ChopPlan.of(trunk(64, 3), null),
                "План без стоянки построить нельзя: порядок рубки считается от стоянки духа");
    }

    private static Tree trunk(int baseY, int count) {
        List<BlockCoord> logs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            logs.add(new BlockCoord(0, baseY + i, 0));
        }
        return new Tree(new BlockCoord(0, baseY, 0), logs);
    }
}
