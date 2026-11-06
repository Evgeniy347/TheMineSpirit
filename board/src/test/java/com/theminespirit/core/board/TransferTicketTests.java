package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferTicketTests {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final BlockCoord HOME = new BlockCoord(-170, 68, -487);

    @Test
    void restore_WarehouseTransferToSelfAfterRestart_PlansGotoAndWithdraw() {
        BoardTicket ticket = new BoardTicket(
                "transfer:stock:craft_stone_axe:spirit-1:cobblestone:warehouse",
                BoardCase.TRANSFER_ITEM,
                Optional.of(SELF),
                BoardRules.priorityOf(BoardCase.TRANSFER_ITEM),
                0L,
                1,
                "stock:craft_stone_axe:spirit-1",
                List.of(),
                Map.of(),
                TransferTicket.modifiers(SELF, "cobblestone", 3, null));

        TaskNode plan =
                TicketPlans.plan(BoardCase.TRANSFER_ITEM, TransferTicket.restore(ticket, TicketTarget.at(HOME)));

        List<ScriptCall> calls = calls(plan);
        assertEquals(
                List.of("goto", "withdraw", "wait"),
                calls.stream().map(ScriptCall::script).toList(),
                "INC-86: после перезапуска цель передачи — только точка, получатель и груз — в метках задания; без "
                        + "восстановления план не собирался (×190), крафт каменного топора стоял");
        assertEquals(
                List.of("cobblestone", 3),
                List.of(calls.get(1).params().get("item"), calls.get(1).params().get("count")),
                "Со склада самому себе — withdraw ровно нехватки в свой инвентарь");
    }

    private static List<ScriptCall> calls(TaskNode root) {
        List<ScriptCall> calls = new ArrayList<>();
        Deque<TaskNode> queue = new ArrayDeque<>(List.of(root));
        while (!queue.isEmpty()) {
            TaskNode node = queue.poll();
            if (node instanceof ScriptCall call) {
                calls.add(call);
            } else if (node instanceof SequenceNode sequence) {
                queue.addAll(sequence.steps());
            } else if (node instanceof FallbackNode fallback) {
                queue.addAll(fallback.steps());
            } else {
                throw new IllegalStateException(
                        "Узел " + node + " в плане передачи не ожидался: план — goto и withdraw");
            }
        }
        return calls;
    }
}
