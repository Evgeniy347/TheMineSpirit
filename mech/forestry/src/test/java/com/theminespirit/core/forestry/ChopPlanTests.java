package com.theminespirit.core.forestry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.theminespirit.core.observation.BlockCoord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChopPlanTests {
    @Test
    void of_StraightTrunk_OrdersTopDown() {
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
                "Дух рубит с одной стоянки у ствола: порядок — с самого высокого бревна вниз (правило владельца, "
                        + "INC-49), а не снизу вверх");
    }

    @Test
    void of_FarLowBranch_ChoppedAfterHighTrunk() {
        Tree tree = new Tree(
                new BlockCoord(0, 64, 0),
                List.of(new BlockCoord(0, 64, 0), new BlockCoord(0, 65, 0), new BlockCoord(5, 64, 0)));
        ChopPlan plan = ChopPlan.of(tree, new BlockCoord(0, 64, 0));

        assertEquals(
                List.of(new BlockCoord(0, 65, 0), new BlockCoord(5, 64, 0), new BlockCoord(0, 64, 0)),
                plan.order(),
                "Порядок — по высоте сверху вниз, на одной высоте — дальнее первым: дальняя нижняя ветка джунглей "
                        + "больше не рубится раньше верха ствола (INC-49)");
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
    void of_ProtectedLogs_SkippedAndRestOfTreeChopped() {
        ChopPlan plan = ChopPlan.of(trunk(64, 6), new BlockCoord(0, 64, 0), cell -> cell.y() == 65 || cell.y() == 68);

        assertEquals(
                List.of(
                        new BlockCoord(0, 69, 0),
                        new BlockCoord(0, 67, 0),
                        new BlockCoord(0, 66, 0),
                        new BlockCoord(0, 64, 0)),
                plan.order(),
                "Брёвна постройки в створе не рубятся, остальное дерево снимается целиком: иначе одно защищённое "
                        + "бревно оставляет духа с «незавершённой» рубкой навсегда");
    }

    @Test
    void rest_ProtectedLogLeft_IsNotCountedAsIncomplete() {
        Tree tree = trunk(64, 3);
        ChopPlan plan = ChopPlan.of(tree, new BlockCoord(0, 64, 0));
        ChopPlan rest = plan.rest(
                new MemoryTree(List.of(new BlockCoord(0, 65, 0))), new BlockCoord(0, 64, 0), cell -> cell.y() == 65);

        assertEquals(
                List.of(),
                rest.order(),
                "Оставшееся защищённое бревно не считается остатком работы: рубка считается законченной, а не "
                        + "крутится вокруг постройки до таймаута");
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

    private record MemoryTree(List<BlockCoord> logs) implements TreeView {
        @Override
        public boolean isLog(BlockCoord coord) {
            return logs.contains(coord);
        }

        @Override
        public boolean isNaturalLeaves(BlockCoord coord) {
            return false;
        }

        @Override
        public boolean isSoil(BlockCoord coord) {
            return false;
        }

        @Override
        public boolean isArtificial(BlockCoord coord) {
            return false;
        }

        @Override
        public boolean isEmpty(BlockCoord coord) {
            return !logs.contains(coord);
        }
    }

    private static Tree trunk(int baseY, int count) {
        List<BlockCoord> logs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            logs.add(new BlockCoord(0, baseY + i, 0));
        }
        return new Tree(new BlockCoord(0, baseY, 0), logs);
    }
}
