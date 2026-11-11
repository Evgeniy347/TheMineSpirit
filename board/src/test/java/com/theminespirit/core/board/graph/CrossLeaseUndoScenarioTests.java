package com.theminespirit.core.board.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.GraphBlock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrossLeaseUndoScenarioTests {
    private static final String SPIRIT = "spirit-1";

    @Test
    void abandonedScaffold_InsideSettlement_RollsBackBeforeFarBridge() {
        GraphSiteFacts.Lease pillar = new GraphSiteFacts.Lease(
                "pillar-1", "scaffold", SPIRIT, new GraphFacts.Position(0, 78, 0), 5, true, true, 5, 4);
        GraphSiteFacts.Lease bridge = new GraphSiteFacts.Lease(
                "bridge-1", "bridge", null, new GraphFacts.Position(300, 64, 0), 0, false, false, 2, 300);

        GraphPlan plan = PlanCaseFacts.plan(GraphWorld.settled(), facts(List.of(pillar, bridge)));

        GraphPlan.Task inside = PlanCaseFacts.task(plan, taskId(pillar));
        GraphPlan.Task trip = PlanCaseFacts.task(plan, taskId(bridge));
        assertTrue(inside.feasible(), "Брошенный столб в поселении обязан получить откат: " + plan.tasks());
        assertTrue(
                inside.priority() > trip.priority(),
                "Столб внутри поселения и на улице убирается раньше моста далеко в походе: " + inside.priority()
                        + " против " + trip.priority());
        assertTrue(
                inside.priority() >= 25 && inside.priority() < 85,
                "Откат брошенной правки в поселении — важная работа, но не выше работ этапа: 11.11 при весе 90-94 "
                        + "пять духов 4 часа висели в откате без подхода, поселение стояло; " + inside.priority());
        assertEquals(
                "undo:pillar-1#0,78,0:5",
                inside.makes(),
                "Узел отката несёт id аренды и ключ её состояния: исполнитель берёт id, граф — ключ повтора");
    }

    @Test
    void undoTaskId_CarriesLeaseState_SoUnchangedFailureIsNotRetried() {
        GraphSiteFacts.Lease before = new GraphSiteFacts.Lease(
                "pillar-1", "scaffold", SPIRIT, new GraphFacts.Position(0, 78, 0), 5, true, true, 5, 4);
        GraphSiteFacts.Lease same = new GraphSiteFacts.Lease(
                "pillar-1", "scaffold", SPIRIT, new GraphFacts.Position(0, 78, 0), 5, true, true, 5, 4);
        GraphSiteFacts.Lease after = new GraphSiteFacts.Lease(
                "pillar-1", "scaffold", SPIRIT, new GraphFacts.Position(0, 77, 0), 4, true, true, 4, 4);

        assertEquals(GraphEvent.undoTaskId(before), GraphEvent.undoTaskId(same), "Та же аренда — то же задание");
        assertFalse(
                GraphEvent.undoTaskId(before).equals(GraphEvent.undoTaskId(after)),
                "Откатанная клетка меняет ключ задания: только тогда граф вправе повторить откат");
        assertEquals("pillar-1", GraphEvent.leaseId(GraphEvent.undoTaskId(after)), "Из ключа задания берут id аренды");
    }

    @Test
    void undoTaskId_UsesLeaseKey_SoOnlyRollbackChangeRetries() {
        GraphSiteFacts.Lease one = new GraphSiteFacts.Lease(
                "pillar-1", "scaffold", SPIRIT, new GraphFacts.Position(0, 78, 0), 5, true, true, 5, 4, 0, "aaa");
        GraphSiteFacts.Lease two = new GraphSiteFacts.Lease(
                "pillar-1", "scaffold", SPIRIT, new GraphFacts.Position(0, 78, 0), 5, true, true, 5, 4, 0, "bbb");

        assertFalse(
                GraphEvent.undoTaskId(one).equals(GraphEvent.undoTaskId(two)),
                "Ключ отката — отдельное поле аренды: тот же верх и число клеток при другом порядке обязаны "
                        + "различаться");
        assertEquals(
                "undo:pillar-1#aaa",
                GraphEvent.undoTaskId(one),
                "Билет несёт короткий id аренды и ключ, а не координаты с числом клеток");
        assertEquals("pillar-1", GraphEvent.leaseId(GraphEvent.undoTaskId(one)), "id аренды читается из билета");
    }

    @Test
    void undoBlockedWithPath_AfterPathAppears_TaskIsIssuedAgain() {
        GraphSiteFacts.Lease bridge = new GraphSiteFacts.Lease(
                "bridge-1", "bridge", null, new GraphFacts.Position(300, 64, 0), 0, false, false, 2, 300);
        String node = taskId(bridge);
        GraphWorld blocked = GraphWorld.settled();
        blocked.blocks.add(new GraphBlock(node, "300,64,0", new GraphBlock.Path("300,64,0")));

        GraphPlan plan = PlanCaseFacts.plan(blocked, facts(List.of(bridge)));

        GraphPlan.Task task = PlanCaseFacts.task(plan, node);
        assertFalse(task.feasible(), "Без пути откат не выдаётся: дух не дойдёт до брошенного моста");
        assertTrue(
                task.blocked().contains("путь"),
                "Блокировка отката обязана называть нужду (путь): иначе граф не знает, что снимать; получено '"
                        + task.blocked() + "'");

        GraphPlan again = PlanCaseFacts.plan(GraphWorld.settled(), facts(List.of(bridge)));
        assertTrue(
                PlanCaseFacts.posted(again, node),
                "После появления пути задание отката выдаётся снова: " + again.tasks());
    }

    @Test
    void settlementUndo_ComparedToDemolitionWork_GoesFirst() {
        GraphSiteFacts.Lease pillar = new GraphSiteFacts.Lease(
                "pillar-1", "scaffold", SPIRIT, new GraphFacts.Position(0, 78, 0), 5, true, true, 5, 4);
        GraphFacts facts = facts(
                List.of(pillar),
                List.of(new GraphSiteFacts.Stray(
                        "stray:oak_fence@1,69,1", "oak_fence", new GraphFacts.Position(1, 69, 1), "spirit-1")));

        GraphPlan plan = PlanCaseFacts.plan(GraphWorld.settled(), facts);

        assertTrue(
                PlanCaseFacts.task(plan, taskId(pillar)).priority()
                        > PlanCaseFacts.task(plan, "event:demolish_stray:demolish_stray")
                                .priority(),
                "Откат брошенной правки в поселении важнее новой уборки чужого мусора: " + plan.tasks());
    }

    private static String taskId(GraphSiteFacts.Lease lease) {
        return "event:" + GraphWorldEvents.UNDO_LEASE + ":" + GraphEvent.undoTaskId(lease);
    }

    private static GraphFacts facts(List<GraphSiteFacts.Lease> leases) {
        return facts(leases, List.of());
    }

    private static GraphFacts facts(List<GraphSiteFacts.Lease> leases, List<GraphSiteFacts.Stray> strays) {
        return new GraphFacts(
                Map.of(
                        SPIRIT,
                        new GraphFacts.Spirit(
                                SPIRIT,
                                new GraphFacts.Position(0, 70, 0),
                                20,
                                20,
                                null,
                                20,
                                20,
                                false,
                                false,
                                false,
                                null,
                                Map.of(),
                                List.of(),
                                List.of(),
                                null)),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                strays,
                GraphSiteFacts.Terrain.empty(),
                new GraphSiteFacts.StageReqs(Map.of(), Map.of()),
                GraphFeedback.none(),
                List.of(),
                List.of(),
                leases);
    }
}
