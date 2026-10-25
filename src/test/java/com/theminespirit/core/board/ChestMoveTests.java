package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChestMoveTests {

    @Test
    void plan_OldChestOutsideYard_WithdrawThenDepositToYard() {
        BlockCoord old = new BlockCoord(-153, 72, -491);
        BlockCoord yard = new BlockCoord(-177, 70, -484);
        TicketTarget target =
                TicketTarget.at(yard).withSupply("minecraft:jungle_fence", 300).withRelocate(old, yard);
        List<ScriptCall> calls = ((SequenceNode) TicketPlans.plan(BoardCase.SUPPLY_SITE, target))
                .steps().stream().map(ScriptCall.class::cast).toList();
        assertEquals(
                List.of("withdraw:-153,72,-491:256", "deposit:-177,70,-484:256"),
                calls.stream()
                        .map(call -> call.script() + ":" + call.params().get("container") + ":"
                                + call.params().get("count"))
                        .toList(),
                "Старый сундук вне двора разбирается в сундук двора партиями ≤256: двор — единственный склад, "
                        + "материалы из старого сундука доходят до заданий (INC-80)");
    }
}
