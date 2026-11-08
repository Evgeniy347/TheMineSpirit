package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.GraphCaseFixtures.busySettlement;
import static com.theminespirit.core.board.graph.GraphCaseFixtures.home;
import static com.theminespirit.core.board.graph.GraphCaseFixtures.ids;
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
    void tickets_Threat_DefendFirstAtGraphCeiling() {
        GraphWorld world = busySettlement()
                .event("life_threat")
                .facts(LifeGraphPlannerTests.danger("spirit-1", 4, 20, GraphFacts.TargetKind.SPIRIT, 2, 2));
        GraphPlan plan = world.plan();

        assertEquals(
                GraphWorld.GRAPH.maxPriority(),
                plan.priority(DEFEND),
                "Защита жизни получает потолок графа, а не значение выше доски: " + plan.priority(DEFEND));
        assertTrue(
                plan.tasks().stream()
                        .filter(task -> !task.id().startsWith("event:life_"))
                        .allMatch(task -> task.priority() <= GraphPriorities.work(GraphWorld.GRAPH)),
                "Обычные работы не поднимаются выше 99: " + plan.tasks());
        assertEquals(
                DEFEND,
                tickets(world.state()).post().get(0).task().id(),
                "Угроза — первое задание доски, раньше любой стройки или рубки: "
                        + tickets(world.state()).post().stream()
                                .map(GraphTickets.Ticket::id)
                                .toList());
    }

    @Test
    void tickets_Hungry_EatStaysWithinOrdinaryCeiling() {
        GraphWorld world = busySettlement().event("hungry");
        GraphPlan plan = world.plan();

        assertTrue(
                plan.priority(EAT) > 0 && plan.priority(EAT) <= GraphPriorities.work(GraphWorld.GRAPH),
                "Еда — обычное событие: она важна, но не выше потолка 99. Еда " + plan.priority(EAT) + ", задания "
                        + ids(plan));
        assertTrue(
                tickets(world.state()).post().stream()
                        .anyMatch(ticket -> EAT.equals(ticket.task().id())),
                "Голодный дух получает задание доски «поесть»: "
                        + tickets(world.state()).post());
    }

    @Test
    void tickets_EveningOutside_ReturnHomeWithinOrdinaryCeiling() {
        GraphWorld world = busySettlement().event("more7_evening_return");
        GraphPlan plan = world.plan();

        assertTrue(
                plan.priority(RETURN) > 0 && plan.priority(RETURN) <= GraphPriorities.work(GraphWorld.GRAPH),
                "Возврат домой — обычное событие, не выше потолка 99. Возврат " + plan.priority(RETURN) + ", задания "
                        + ids(plan));
        assertTrue(
                tickets(world.state()).post().stream()
                        .anyMatch(ticket -> RETURN.equals(ticket.task().id())),
                "Вечером дух получает задание доски «вернуться домой»: "
                        + tickets(world.state()).post());
    }

    @Test
    void plan_HungryFoodTasks_NameSourceAndOwner() {
        GraphEvent hungry = GraphWorld.GRAPH.events().get("hungry");

        assertTrue(hungry != null, "Событие голода объявлено в графе");
        GraphEvent.Task hunt = hungry.tasks().stream()
                .filter(task -> "hunt".equals(task.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Нет задания охоты в голоде: " + hungry.tasks()));
        GraphEvent.Task fish = hungry.tasks().stream()
                .filter(task -> "fish".equals(task.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Нет задания рыбалки в голоде: " + hungry.tasks()));
        GraphEvent.Task wait = hungry.tasks().stream()
                .filter(task -> "wait_food".equals(task.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Нет ожидания еды в голоде: " + hungry.tasks()));

        assertEquals(BoardCase.HUNT_WILD_SHEEP, hunt.boardCase(), "Охота — существующая механика добычи мяса");
        assertEquals("animals", hunt.world(), "Охота требует животных в округе: без них источник не выдумывается");
        assertEquals("food", hunt.onlyWhenEmpty(), "Еду добывают только пока еды нет: иначе она важнее поесть");
        assertEquals("hungriest", hunt.owner(), "Добычу еды граф адресует самому голодному духу");
        assertEquals(BoardCase.FISH, fish.boardCase(), "Рыбалка — существующая механика добычи еды");
        assertEquals("water", fish.world(), "Рыбалка требует воды в округе");
        assertEquals(BoardCase.ACTIVITY_SENSE, wait.boardCase(), "Без источника дух ждёт на месте, а не копает");
        assertEquals("food", wait.onlyWhenEmpty(), "Ждать еду есть смысл только пока еды нет");
        assertEquals("hungriest", wait.owner(), "Ожидание адресовано голодному духу, а не сытому");
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
