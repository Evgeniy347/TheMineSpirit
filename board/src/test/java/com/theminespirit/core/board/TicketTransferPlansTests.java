package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketTransferPlansTests {
    @Test
    void plan_TransferItemFromSpirit_DeliversToRecipient() {
        UUID giver = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        UUID taker = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
        TaskNode plan = TicketPlans.plan(BoardCase.TRANSFER_ITEM, target().withTransfer(giver, taker, "stick", 2));
        ScriptCall deliver = findCall(plan, "deliver");

        assertEquals(
                "stick",
                deliver.params().get("item"),
                "Передача везёт предмет, которого не хватает крафтеру: иначе доставка закрывает не ту нехватку. При смене plan_TransferItemFromSpirit_DeliversToRecipient проверь TicketPlans.transferItem.");
        assertEquals(
                2,
                deliver.params().get("count"),
                "Передача везёт ровно нехватку: иначе лишний предмет уходит из общего запаса. При смене plan_TransferItemFromSpirit_DeliversToRecipient проверь TransferPlanner.");
        assertEquals(
                "spirit:" + taker,
                deliver.params().get("to"),
                "Адресат deliver — дух-крафтер по UUID: иначе одноимённый дух получает чужой предмет. При смене plan_TransferItemFromSpirit_DeliversToRecipient проверь TicketTarget.transferTo.");
    }

    @Test
    void plan_TransferItemFromWarehouse_WithdrawsToRecipient() {
        UUID taker = UUID.fromString("00000000-0000-0000-0000-0000000000c2");
        TaskNode plan = TicketPlans.plan(BoardCase.TRANSFER_ITEM, target().withTransfer(null, taker, "cobblestone", 3));
        ScriptCall withdraw = findCall(plan, "withdraw");

        assertEquals(
                "cobblestone",
                withdraw.params().get("item"),
                "Со склада берут предмет, которого не хватает крафтеру: иначе передача не закрывает заказ. При смене plan_TransferItemFromWarehouse_WithdrawsToRecipient проверь TicketPlans.transferItem.");
        assertEquals(
                3,
                withdraw.params().get("count"),
                "Со склада берут ровно нехватку: иначе общий запас тратится лишним. При смене plan_TransferItemFromWarehouse_WithdrawsToRecipient проверь TransferPlanner.");
        assertEquals(
                "settlement",
                withdraw.params().get("container"),
                "Pickup ищет предмет по всем разрешённым сундукам поселения: иначе верстак снова падает без досок при дальнем складе. При смене plan_TransferItemFromWarehouse_WithdrawsToRecipient проверь ContainerScan и ChestWithdrawal.");
    }

    @Test
    void plan_TransferItemFromWarehouseToSelf_IsPlainGotoThenWithdraw() {
        UUID self = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
        TaskNode plan = TicketPlans.plan(BoardCase.TRANSFER_ITEM, target().withTransfer(null, self, "stick", 2));

        assertEquals(
                List.of("goto", "withdraw", "wait"),
                collectScripts(plan),
                "Передача себе — goto домой и дистанционный withdraw по поселению без deliver: иначе дух носит предмет сам себе или ищет только ближайший сундук. При смене plan_TransferItemFromWarehouseToSelf_IsPlainGotoThenWithdraw проверь TicketPlans.transferItem.");
    }

    private static TicketTarget target() {
        return TicketTarget.at(new BlockCoord(10, 60, -5));
    }

    private static ScriptCall findCall(TaskNode root, String script) {
        for (TaskNode node : walk(root)) {
            if (node instanceof ScriptCall call && call.script().equals(script)) {
                return call;
            }
        }
        throw new AssertionError("В плане нет вызова скрипта " + script + ": без него задание не работает");
    }

    private static List<String> collectScripts(TaskNode root) {
        return walk(root).stream()
                .filter(ScriptCall.class::isInstance)
                .map(ScriptCall.class::cast)
                .map(ScriptCall::script)
                .toList();
    }

    private static List<TaskNode> walk(TaskNode root) {
        List<TaskNode> nodes = new ArrayList<>();
        Deque<TaskNode> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            TaskNode node = queue.poll();
            nodes.add(node);
            switch (node) {
                case SequenceNode sequence -> queue.addAll(sequence.steps());
                case RepeatNode repeat -> queue.add(repeat.step());
                case FallbackNode fallback -> queue.addAll(fallback.steps());
                case ParallelNode parallel -> queue.addAll(parallel.steps());
                case ConditionNode condition -> {
                    queue.add(condition.thenStep());
                    if (condition.elseStep() != null) {
                        queue.add(condition.elseStep());
                    }
                }
                default -> {}
            }
        }
        return nodes;
    }
}
