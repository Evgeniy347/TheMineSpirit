package com.theminespirit.core.board.graph;

import static com.theminespirit.core.board.graph.GraphCaseFixtures.busySettlement;
import static com.theminespirit.core.board.graph.GraphCaseFixtures.home;
import static com.theminespirit.core.board.graph.GraphCaseFixtures.ids;
import static com.theminespirit.core.board.graph.GraphCaseFixtures.tickets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.board.MoreActivityPlans;
import com.theminespirit.core.board.TicketTarget;
import com.theminespirit.core.board.api.BoardCase;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.TaskNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphLifeCasesTests {
    private static final String EAT = "event:hungry:eat";
    private static final String DEFEND = "event:life_threat:defend";
    private static final String RETURN = "event:more7_evening_return:more7_return_home";
    private static final String IDLE = "event:more6_spirit_idle:more6_assign_idle";

    @Test
    void risk_HurtFreshnessWindow_ComesFromGraphData() {
        GraphFacts facts = GraphFacts.empty()
                .withSpirits(Map.of(
                        "spirit-1",
                        LifeGraphPlannerTests.spirit(
                                "spirit-1", 10, 20, new GraphFacts.Hurt(8, "minecraft:zombie", 150))));
        GraphLife wide = new GraphLife(1.5, 0.4, 0.7, 0.2, 0.8, 0.4, 16.0, 200, 0.35, 6);
        GraphLife narrow = new GraphLife(1.5, 0.4, 0.7, 0.2, 0.8, 0.4, 16.0, 60, 0.35, 6);

        assertTrue(
                LifeThreats.severity(facts, "life_injury", wide) > LifeThreats.severity(facts, "life_injury", narrow),
                "Окно свежего урона берётся из graph.json: в широком окне урон 150 тиков ещё весит, "
                        + "в узком — уже нет"
                        + " Иначе тест risk_HurtFreshnessWindow_ComesFromGraphData перестанет ловить эту поломку. При смене risk_HurtFreshnessWindow_ComesFromGraphData проверь это утверждение.");
    }

    @Test
    void bands_PriorityOrder_WorkBelowNeedBelowLife() {
        int life = GraphWorld.GRAPH.maxPriority();
        assertTrue(
                GraphPriorities.work(GraphWorld.GRAPH) < GraphPriorities.need(GraphWorld.GRAPH)
                        && GraphPriorities.need(GraphWorld.GRAPH) < life,
                "Полосы не слипаются: работа " + GraphPriorities.work(GraphWorld.GRAPH) + ", потребность "
                        + GraphPriorities.need(GraphWorld.GRAPH) + ", жизнь " + life
                        + " Иначе тест bands_PriorityOrder_WorkBelowNeedBelowLife перестанет ловить эту поломку. При смене bands_PriorityOrder_WorkBelowNeedBelowLife проверь это утверждение.");
    }

    @Test
    void tickets_Threat_DefendFirstAtGraphCeiling() {
        GraphWorld world = busySettlement()
                .event("life_threat")
                .facts(LifeGraphPlannerTests.danger("spirit-1", 4, 20, GraphFacts.TargetKind.SPIRIT, 2, 2));
        GraphPlan plan = world.plan();

        assertEquals(
                GraphWorld.GRAPH.maxPriority(),
                plan.priority(DEFEND),
                "Защита жизни получает потолок графа, а не значение выше доски: " + plan.priority(DEFEND)
                        + " Иначе тест tickets_Threat_DefendFirstAtGraphCeiling перестанет ловить эту поломку. При смене tickets_Threat_DefendFirstAtGraphCeiling проверь это утверждение.");
        assertTrue(
                plan.tasks().stream()
                        .filter(task -> !task.id().startsWith("event:life_"))
                        .allMatch(task -> task.priority() <= GraphPriorities.work(GraphWorld.GRAPH)),
                "Обычные работы не поднимаются выше 99: " + plan.tasks()
                        + " Иначе тест tickets_Threat_DefendFirstAtGraphCeiling перестанет ловить эту поломку. При смене tickets_Threat_DefendFirstAtGraphCeiling проверь это утверждение.");
        assertEquals(
                DEFEND,
                tickets(world.state()).post().get(0).task().id(),
                "Угроза — первое задание доски, раньше любой стройки или рубки: "
                        + tickets(world.state()).post().stream()
                                .map(GraphTickets.Ticket::id)
                                .toList()
                        + " Иначе тест tickets_Threat_DefendFirstAtGraphCeiling перестанет ловить эту поломку. При смене tickets_Threat_DefendFirstAtGraphCeiling проверь это утверждение.");
    }

    @Test
    void tickets_Hungry_EatStaysWithinVitalBand() {
        GraphWorld world = busySettlement().event("hungry");
        GraphPlan plan = world.plan();

        GraphPlan.Task eat = plan.task(EAT).orElseThrow();
        assertTrue(
                eat.raised(),
                "Голод поднимает еду: " + ids(plan)
                        + " Иначе тест tickets_Hungry_EatStaysWithinVitalBand перестанет ловить эту поломку. При смене tickets_Hungry_EatStaysWithinVitalBand проверь это утверждение.");
        assertEquals(
                List.of(),
                CrossScene.workNotBehind(plan, eat),
                "Голод — жизненная нужда: еда выбирается раньше любой работы поселения. Задания " + plan.tasks()
                        + " Иначе тест tickets_Hungry_EatStaysWithinVitalBand перестанет ловить эту поломку. При смене tickets_Hungry_EatStaysWithinVitalBand проверь это утверждение.");
        assertTrue(
                tickets(world.state()).post().stream()
                        .anyMatch(ticket -> EAT.equals(ticket.task().id())),
                "Голодный дух получает задание доски «поесть»: "
                        + tickets(world.state()).post()
                        + " Иначе тест tickets_Hungry_EatStaysWithinVitalBand перестанет ловить эту поломку. При смене tickets_Hungry_EatStaysWithinVitalBand проверь это утверждение.");
    }

    @Test
    void tickets_EveningOutside_ReturnHomeWithinOrdinaryCeiling() {
        GraphWorld world = busySettlement().event("more7_evening_return");
        GraphPlan plan = world.plan();

        assertTrue(
                plan.raised(RETURN),
                "Вечер вне дома поднимает возврат: " + ids(plan)
                        + " Иначе тест tickets_EveningOutside_ReturnHomeWithinOrdinaryCeiling перестанет ловить эту поломку. При смене tickets_EveningOutside_ReturnHomeWithinOrdinaryCeiling проверь это утверждение.");
        GraphPlan hungry =
                busySettlement().event("more7_evening_return").event("hungry").plan();
        assertTrue(
                hungry.priority(EAT) > hungry.priority(RETURN),
                "Возврат домой — обычное событие: жизненная нужда (голод) выбирается раньше него. Задания "
                        + hungry.tasks()
                        + " Иначе тест tickets_EveningOutside_ReturnHomeWithinOrdinaryCeiling перестанет ловить эту поломку. При смене tickets_EveningOutside_ReturnHomeWithinOrdinaryCeiling проверь это утверждение.");
        assertTrue(
                tickets(world.state()).post().stream()
                        .anyMatch(ticket -> RETURN.equals(ticket.task().id())),
                "Вечером дух получает задание доски «вернуться домой»: "
                        + tickets(world.state()).post()
                        + " Иначе тест tickets_EveningOutside_ReturnHomeWithinOrdinaryCeiling перестанет ловить эту поломку. При смене tickets_EveningOutside_ReturnHomeWithinOrdinaryCeiling проверь это утверждение.");
    }

    @Test
    void plan_HungryFoodTasks_NameSourceAndOwner() {
        GraphEvent hungry = GraphWorld.GRAPH.events().get("hungry");

        assertTrue(
                hungry != null,
                "Событие голода объявлено в графе"
                        + " Иначе тест plan_HungryFoodTasks_NameSourceAndOwner перестанет ловить эту поломку. При смене plan_HungryFoodTasks_NameSourceAndOwner проверь это утверждение.");
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
        GraphEvent.Task harvest = hungry.tasks().stream()
                .filter(task -> "harvest_crop".equals(task.id()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Нет сбора урожая в голоде: " + hungry.tasks()));

        assertEquals(
                BoardCase.HUNT_WILD_SHEEP,
                hunt.boardCase(),
                "Охота — существующая механика добычи мяса"
                        + " Иначе тест plan_HungryFoodTasks_NameSourceAndOwner перестанет ловить эту поломку. При смене plan_HungryFoodTasks_NameSourceAndOwner проверь это утверждение.");
        assertEquals(
                "animals",
                hunt.world(),
                "Охота требует животных в округе: без них источник не выдумывается"
                        + " Иначе тест plan_HungryFoodTasks_NameSourceAndOwner перестанет ловить эту поломку. При смене plan_HungryFoodTasks_NameSourceAndOwner проверь это утверждение.");
        assertEquals(
                "food",
                hunt.onlyWhenEmpty(),
                "Еду добывают только пока еды нет: иначе она важнее поесть"
                        + " При смене plan_HungryFoodTasks_NameSourceAndOwner проверь это утверждение.");
        assertEquals(
                "hungriest",
                hunt.owner(),
                "Добычу еды граф адресует самому голодному духу"
                        + " Иначе тест plan_HungryFoodTasks_NameSourceAndOwner перестанет ловить эту поломку. При смене plan_HungryFoodTasks_NameSourceAndOwner проверь это утверждение.");
        assertEquals(
                BoardCase.FISH,
                fish.boardCase(),
                "Рыбалка — существующая механика добычи еды"
                        + " Иначе тест plan_HungryFoodTasks_NameSourceAndOwner перестанет ловить эту поломку. При смене plan_HungryFoodTasks_NameSourceAndOwner проверь это утверждение.");
        assertEquals(
                "water",
                fish.world(),
                "Рыбалка требует воды в округе"
                        + " Иначе тест plan_HungryFoodTasks_NameSourceAndOwner перестанет ловить эту поломку. При смене plan_HungryFoodTasks_NameSourceAndOwner проверь это утверждение.");
        assertEquals(
                BoardCase.ACTIVITY_SENSE,
                wait.boardCase(),
                "Без источника дух ждёт на месте, а не копает"
                        + " Иначе тест plan_HungryFoodTasks_NameSourceAndOwner перестанет ловить эту поломку. При смене plan_HungryFoodTasks_NameSourceAndOwner проверь это утверждение.");
        assertEquals(
                "food",
                wait.onlyWhenEmpty(),
                "Ждать еду есть смысл только пока еды нет"
                        + " Иначе тест plan_HungryFoodTasks_NameSourceAndOwner перестанет ловить эту поломку. При смене plan_HungryFoodTasks_NameSourceAndOwner проверь это утверждение.");
        assertEquals(
                "hungriest",
                wait.owner(),
                "Ожидание адресовано голодному духу, а не сытому"
                        + " Иначе тест plan_HungryFoodTasks_NameSourceAndOwner перестанет ловить эту поломку. При смене plan_HungryFoodTasks_NameSourceAndOwner проверь это утверждение.");
        assertEquals(
                BoardCase.GATHER_MATERIALS,
                harvest.boardCase(),
                "Сбор урожая — работа фермы"
                        + " Иначе тест plan_HungryFoodTasks_NameSourceAndOwner перестанет ловить эту поломку. При смене plan_HungryFoodTasks_NameSourceAndOwner проверь это утверждение.");
        assertEquals(
                "wheat_field",
                harvest.building(),
                "Урожай собирают только с построенного поля"
                        + " Иначе тест plan_HungryFoodTasks_NameSourceAndOwner перестанет ловить эту поломку. При смене plan_HungryFoodTasks_NameSourceAndOwner проверь это утверждение.");
        assertEquals(
                "food",
                harvest.onlyWhenEmpty(),
                "Урожай подключается к голоду, пока еды нет"
                        + " Иначе тест plan_HungryFoodTasks_NameSourceAndOwner перестанет ловить эту поломку. При смене plan_HungryFoodTasks_NameSourceAndOwner проверь это утверждение.");
        assertEquals(
                "hungriest",
                harvest.owner(),
                "Урожай собирает самый голодный дух"
                        + " Иначе тест plan_HungryFoodTasks_NameSourceAndOwner перестанет ловить эту поломку. При смене plan_HungryFoodTasks_NameSourceAndOwner проверь это утверждение.");
    }

    @Test
    void plan_IdleSpirit_GetsStandAndScanNotRunAround() {
        GraphWorld world = GraphWorld.empty().event("more6_spirit_idle");
        GraphPlan plan = world.plan();

        GraphPlan.Task idle = plan.task(IDLE)
                .orElseThrow(
                        () -> new AssertionError("Дух простаивает — граф обязан дать занятие ожидания: " + ids(plan)));
        assertEquals(
                BoardCase.ACTIVITY_SENSE,
                idle.boardCase(),
                "Простой — осмотр места, а не бег по кругу"
                        + " Иначе тест plan_IdleSpirit_GetsStandAndScanNotRunAround перестанет ловить эту поломку. При смене plan_IdleSpirit_GetsStandAndScanNotRunAround проверь это утверждение.");
        assertTrue(
                idle.feasible(),
                "Осмотр выполним без материалов: " + idle
                        + " Иначе тест plan_IdleSpirit_GetsStandAndScanNotRunAround перестанет ловить эту поломку. При смене plan_IdleSpirit_GetsStandAndScanNotRunAround проверь это утверждение.");

        TaskNode action = MoreActivityPlans.plan(BoardCase.ACTIVITY_SENSE, TicketTarget.at(home()));
        ScriptCall scan = assertInstanceOf(
                ScriptCall.class,
                action,
                "Осмотр — одно действие без переходов: " + action
                        + " Иначе тест plan_IdleSpirit_GetsStandAndScanNotRunAround перестанет ловить эту поломку. При смене plan_IdleSpirit_GetsStandAndScanNotRunAround проверь это утверждение.");
        assertEquals(
                "scan_blocks",
                scan.script(),
                "Дух без работы стоит и осматривается, а не ходит кругами: " + scan.params()
                        + " Иначе тест plan_IdleSpirit_GetsStandAndScanNotRunAround перестанет ловить эту поломку. При смене plan_IdleSpirit_GetsStandAndScanNotRunAround проверь это утверждение.");
    }
}
