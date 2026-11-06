package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TicketStoragePlansTests {
    private static final BlockCoord SITE = new BlockCoord(-179, 68, -515);
    private static final BlockCoord CHEST = new BlockCoord(-181, 68, -524);

    @Test
    void supplySite_WithDepositPoint_GoesToThatContainer() {
        TicketTarget target = TicketTarget.at(SITE).withSupply("oak_planks", 48).withDeposit(CHEST);

        SequenceNode plan = assertInstanceOf(
                SequenceNode.class,
                TicketPlans.plan(BoardCase.SUPPLY_SITE, target),
                "план сдачи обязан быть последовательностью: иначе виден только первый шаг");
        List<ScriptCall> calls =
                plan.steps().stream().map(ScriptCall.class::cast).toList();

        assertEquals(
                List.of("goto", "acquire", "goto", "deposit"),
                calls.stream().map(ScriptCall::script).toList(),
                "после добычи дух идёт к выбранному сундуку и только там сдаёт: иначе падает «рядом нет "
                        + "допустимого контейнера» (аномалия 3)");
        assertEquals(
                Map.of("x", CHEST.x(), "y", CHEST.y(), "z", CHEST.z()),
                calls.get(2).params(),
                "второй переход ведёт в точку с допустимым контейнером, а не в пустое место сдачи");
        assertEquals(
                "warehouse",
                calls.get(3).params().get("container"),
                "deposit выбирает свободный сундук склада в момент сдачи: закреплённый при постановке сундук "
                        + "к этому времени уже полон, и перенос идёт «0 из N»");
    }

    @Test
    void supplySite_NoDepositPoint_KeepsWarehouseAtSite() {
        TaskNode plan =
                TicketPlans.plan(BoardCase.SUPPLY_SITE, TicketTarget.at(SITE).withSupply("dirt", 4));

        ScriptCall deposit = find(plan, "deposit");

        assertEquals(
                "warehouse",
                deposit.params().get("container"),
                "без выбранного контейнера сдача всё равно идёт на склад поселения — туда, где есть место");
        assertEquals(
                Map.of("x", SITE.x(), "y", SITE.y(), "z", SITE.z()),
                find(plan, "goto").params(),
                "первый переход ведёт к месту задания");
    }

    @Test
    void restockFood_WithDepositPoint_WalksToContainerFirst() {
        TicketTarget target = TicketTarget.at(SITE).withSupply("bread", 8).withDeposit(CHEST);

        SequenceNode plan = assertInstanceOf(
                SequenceNode.class,
                TicketPlans.plan(BoardCase.RESTOCK_FOOD, target),
                "план еды обязан быть последовательностью «дойти — взять»: иначе дух берёт из пустоты");
        List<ScriptCall> calls =
                plan.steps().stream().map(ScriptCall.class::cast).toList();

        assertEquals(
                List.of("goto", "withdraw"),
                calls.stream().map(ScriptCall::script).toList(),
                "еда берётся у сундука, до которого дух сначала доходит (аномалия 5: withdraw «nearest» "
                        + "падал при голоде 0.85)");
        assertEquals(
                CHEST.x() + "," + CHEST.y() + "," + CHEST.z(),
                calls.get(1).params().get("container"),
                "withdraw берёт еду из выбранного контейнера, а не из первого попавшегося рядом");
    }

    @Test
    void restockFood_NoDepositPoint_WithdrawsNearest() {
        ScriptCall withdraw = find(
                TicketPlans.plan(BoardCase.RESTOCK_FOOD, TicketTarget.at(SITE).withSupply("bread", 4)), "withdraw");

        assertEquals("nearest", withdraw.params().get("container"), "без выбранной точки еды поиск идёт рядом");
        assertTrue(withdraw.params().get("radius") instanceof Integer, "radius остаётся в запросе withdraw");
    }

    private static ScriptCall find(TaskNode node, String script) {
        List<ScriptCall> calls = new ArrayList<>();
        collect(node, calls);
        return calls.stream()
                .filter(call -> call.script().equals(script))
                .findFirst()
                .orElseThrow(() -> new AssertionError("в плане нет шага '" + script + "': " + calls));
    }

    private static void collect(TaskNode node, List<ScriptCall> calls) {
        if (node instanceof ScriptCall call) {
            calls.add(call);
            return;
        }
        if (node instanceof SequenceNode sequence) {
            sequence.steps().forEach(step -> collect(step, calls));
            return;
        }
        if (node instanceof FallbackNode fallback) {
            fallback.steps().forEach(step -> collect(step, calls));
        }
    }
}
