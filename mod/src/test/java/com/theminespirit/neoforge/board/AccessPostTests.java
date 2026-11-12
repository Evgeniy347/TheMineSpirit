package com.theminespirit.neoforge.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.town.AccessPath;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccessPostTests {
    @Test
    void steps_Doorway_DemolishTopDownThenCollect() {
        BlockCoord feet = new BlockCoord(1, 69, 2);
        BlockCoord head = new BlockCoord(1, 70, 2);
        AccessPath.Plan plan = new AccessPath.Plan(List.of(feet, head), List.of(), List.of());

        SequenceNode steps = (SequenceNode) AccessPost.steps(plan, feet);
        ScriptCall top =
                (ScriptCall) ((FallbackNode) steps.steps().get(0)).steps().get(0);
        ScriptCall low =
                (ScriptCall) ((FallbackNode) steps.steps().get(1)).steps().get(0);

        assertEquals(70, top.params().get("y1"), "Проём срезается сверху вниз: сначала голова, потом ноги");
        assertEquals(69, low.params().get("y1"), "Вторая клетка — уровень ног");
        assertTrue(
                Boolean.TRUE.equals(top.params().get("demolish")),
                "Срезка прохода — снос по плану: иначе защита участка не даёт ломать поставленную духами землю "
                        + "(сервер 12.11: «у действия BREAK нет законного основания»)");
    }
}
