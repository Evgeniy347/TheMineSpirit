package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.GraphCaseFixtures.busySettlement;
import static com.theminespirit.core.board.graph.GraphCaseFixtures.home;
import static com.theminespirit.core.board.graph.GraphCaseFixtures.ids;
import static com.theminespirit.core.board.graph.GraphCaseFixtures.maxWork;
import static com.theminespirit.core.board.graph.GraphCaseFixtures.tickets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.BoardCase;
import com.theminespirit.core.board.MoreActivityPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import org.junit.jupiter.api.Test;

class GraphLifeCasesTests {
    private static final String EAT = "event:hungry:eat";
    private static final String DEFEND = "event:life_threat:defend";
    private static final String RETURN = "event:more7_evening_return:more7_return_home";
    private static final String IDLE = "event:more6_spirit_idle:more6_assign_idle";

    @Test
    void tickets_Threat_DefendFirstAboveAnyWork() {
        GraphWorld world = busySettlement().event("life_threat");
        GraphPlan plan = world.plan();

        assertTrue(
                plan.priority(DEFEND) > GraphWorld.GRAPH.maxPriority(),
                "Защита жизни выше потолка работ: " + plan.priority(DEFEND));
        assertEquals(
                DEFEND,
                tickets(world.state()).post().get(0).task().id(),
                "Угроза — первое задание доски, раньше любой стройки или рубки: "
                        + tickets(world.state()).post().stream()
                                .map(GraphTickets.Ticket::id)
                                .toList());
    }

    @Test
    void tickets_Hungry_EatFirstAboveAnyWork() {
        GraphWorld world = busySettlement().event("hungry");
        GraphPlan plan = world.plan();

        assertTrue(
                plan.priority(EAT) > maxWork(plan),
                "Голодный дух ест раньше работы: иначе он падает на рубке с полным сундуком еды. Еда "
                        + plan.priority(EAT) + ", самая важная работа " + maxWork(plan) + ", задания " + ids(plan));
        assertEquals(
                EAT,
                tickets(world.state()).post().get(0).task().id(),
                "Первое задание доски — еда, а не стройка или рубка");
    }

    @Test
    void tickets_EveningOutside_ReturnHomeAboveAnyWork() {
        GraphWorld world = busySettlement().event("more7_evening_return");
        GraphPlan plan = world.plan();

        assertTrue(
                plan.priority(RETURN) > maxWork(plan),
                "Перед ночью дух возвращается домой раньше новой работы: иначе он ночует в лесу. Возврат "
                        + plan.priority(RETURN) + ", работа " + maxWork(plan) + ", задания " + ids(plan));
        assertEquals(
                RETURN,
                tickets(world.state()).post().get(0).task().id(),
                "Первое задание доски вечером — возврат домой");
    }

    @Test
    void plan_IdleSpirit_GetsStandAndScanNotRunAround() {
        GraphWorld world = GraphWorld.empty().event("more6_spirit_idle");
        GraphPlan plan = world.plan();

        GraphPlan.Task idle = plan.task(IDLE)
                .orElseThrow(
                        () -> new AssertionError("Дух простаивает — граф обязан дать занятие ожидания: " + ids(plan)));
        assertEquals(BoardCase.ACTIVITY_SENSE, idle.boardCase(), "Простой — осмотр места, а не бег по кругу");
        assertTrue(idle.feasible(), "Осмотр выполним без материалов: " + idle);

        TaskNode action = MoreActivityPlans.plan(BoardCase.ACTIVITY_SENSE, TicketTarget.at(home()));
        ScriptCall scan = assertInstanceOf(ScriptCall.class, action, "Осмотр — одно действие без переходов: " + action);
        assertEquals(
                "scan_blocks",
                scan.script(),
                "Дух без работы стоит и осматривается, а не ходит кругами: " + scan.params());
    }
}
