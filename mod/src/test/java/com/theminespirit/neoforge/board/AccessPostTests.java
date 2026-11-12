package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccessPostTests {
    @Test
    void steps_WalkwayCells_DemolishInGivenOrderThenCollect() {
        BlockCoord head = new BlockCoord(1, 70, 2);
        BlockCoord feet = new BlockCoord(1, 69, 2);

        SequenceNode steps = (SequenceNode) AccessPost.steps(List.of(head, feet));
        ScriptCall top =
                (ScriptCall) ((FallbackNode) steps.steps().get(0)).steps().get(0);

        assertEquals(70, top.params().get("y1"), "Клетки срезаются в порядке плана: сверху вниз");
        assertTrue(
                Boolean.TRUE.equals(top.params().get("demolish")),
                "Срезка над тропой — снос по плану: иначе защита участка не даёт ломать поставленную духами землю "
                        + "(сервер 12.11: «у действия BREAK нет законного основания»)");
    }
}
