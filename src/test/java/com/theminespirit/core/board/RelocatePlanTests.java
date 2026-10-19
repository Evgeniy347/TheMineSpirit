package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.PlanValidator;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class RelocatePlanTests {

    private static final BlockCoord FROM = new BlockCoord(10, 60, -5);
    private static final BlockCoord TO = new BlockCoord(30, 60, 10);

    private static TicketTarget movableTarget() {
        return TicketTarget.at(FROM).withScheme("pen_7x7").withRelocate(FROM, TO);
    }

    @Test
    void buildingCase_FromLiterals_MatchesSet() {
        for (BoardCase boardCase : RelocatePlan.BUILDING_CASES) {
            assertTrue(
                    RelocatePlan.buildingCase(boardCase),
                    "Кейс " + boardCase + " — постройка: его можно переносить (если не неподвижен)");
        }
        assertFalse(
                RelocatePlan.buildingCase(BoardCase.FILL_CRATER),
                "Воронка — не постройка: переносить её нельзя, иначе доска гоняла бы духа " + "переносить яму");
        assertFalse(
                RelocatePlan.buildingCase(BoardCase.EAT_TO_FULL), "Еда — не постройка: перенос еды не имеет смысла");
    }

    @Test
    void immovable_FromClasspath_ContainsMine() {
        assertTrue(
                RelocatePlan.immovable(BoardCase.BUILD_MINE),
                "Вход в шахту неподвижен: его нельзя переносить, иначе доска снесла бы "
                        + "работающую шахту (ответ владельца 16.10)");
        assertFalse(
                RelocatePlan.immovable(BoardCase.BUILD_PEN),
                "Загон переносим: его строят по месту и переставляют по решению стратега");
        assertEquals(
                List.of("BUILD_MINE"),
                List.copyOf(RelocatePlan.immovableNames()),
                "Неподвижный список — данные board.json: пока только вход в шахту, растёт "
                        + "данными без правки кода");
    }

    @Test
    void assertMovable_ImmovableMine_ThrowsNamingCase() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> RelocatePlan.assertMovable(BoardCase.BUILD_MINE),
                "Перенос входа в шахту обязан падать: неподвижную постройку не переносят");

        assertTrue(
                failure.getMessage().contains("BUILD_MINE"),
                "Сообщение обязано называть неподвижный кейс: " + failure.getMessage());
        assertTrue(
                failure.getMessage().contains("неподвижна"),
                "Сообщение обязано называть причину отказа: " + failure.getMessage());
    }

    @Test
    void assertMovable_NonBuilding_ThrowsNamingCase() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> RelocatePlan.assertMovable(BoardCase.FILL_CRATER),
                "Перенос не-постройки обязан падать: переносить можно только строения");

        assertTrue(
                failure.getMessage().contains("FILL_CRATER"),
                "Сообщение обязано называть кейс: " + failure.getMessage());
    }

    @Test
    void assertMovable_MovablePen_Passes() {
        RelocatePlan.assertMovable(BoardCase.BUILD_PEN);
    }

    @Test
    void plan_PenRelocate_BothSites() {
        TaskNode plan = RelocatePlan.plan(BoardCase.BUILD_PEN, movableTarget());
        List<String> scripts = collectScripts(plan);

        assertTrue(
                scripts.contains("clear_area") && scripts.contains("build_schematic"),
                "Перенос загона сносит старое место и строит новое: оба участка в одном плане");
    }

    @Test
    void plan_ImmovableMine_Throws() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RelocatePlan.plan(BoardCase.BUILD_MINE, movableTarget()),
                "План переноса входа в шахту обязан падать: постройка неподвижна");
    }

    @Test
    void plan_FromEqualsTo_Throws() {
        TicketTarget same = TicketTarget.at(FROM).withScheme("pen_7x7").withRelocate(FROM, FROM);

        assertThrows(
                IllegalArgumentException.class,
                () -> RelocatePlan.plan(BoardCase.BUILD_PEN, same),
                "Перенос из точки в ту же точку обязан падать: сносить и строить на одном месте нечего");
    }

    @Test
    void plan_NullArgs_AreRejected() {
        assertThrows(
                NullPointerException.class,
                () -> RelocatePlan.buildingCase(null),
                "Кейс null обязан отвергаться: проверка постройки идёт по имени кейса");
        assertThrows(
                NullPointerException.class,
                () -> RelocatePlan.immovable(null),
                "Кейс null обязан отвергаться: проверка неподвижности идёт по имени кейса");
        assertThrows(
                NullPointerException.class,
                () -> RelocatePlan.assertMovable(null),
                "Кейс null обязан отвергаться: переносить нечего");
    }

    @Property
    void plan_MovableBuilding_ValidByCatalog(@ForAll("movableBuildings") BoardCase boardCase) {
        TaskNode plan = RelocatePlan.plan(boardCase, movableTarget());

        PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
    }

    @Property
    void plan_NonBuilding_AlwaysThrows(@ForAll("nonBuildings") BoardCase boardCase) {
        assertThrows(
                IllegalArgumentException.class,
                () -> RelocatePlan.plan(boardCase, movableTarget()),
                "Непостройку переносить нельзя: " + boardCase);
    }

    @Provide
    Arbitrary<BoardCase> movableBuildings() {
        return Arbitraries.of(
                BoardCase.BUILD_PEN, BoardCase.BUILD_HOUSE, BoardCase.ENCHANT_ROOM, BoardCase.BUILD_FENCE);
    }

    @Provide
    Arbitrary<BoardCase> nonBuildings() {
        return Arbitraries.of(
                BoardCase.FILL_CRATER,
                BoardCase.EAT_TO_FULL,
                BoardCase.REPAIR_FENCE,
                BoardCase.CAPTURE_ANIMAL,
                BoardCase.LIGHTING);
    }

    private static List<String> collectScripts(TaskNode root) {
        List<String> scripts = new ArrayList<>();
        Deque<TaskNode> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            TaskNode node = queue.poll();
            switch (node) {
                case ScriptCall call -> scripts.add(call.script());
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
                default -> throw new IllegalArgumentException("Неизвестный узел плана: " + node);
            }
        }
        return scripts;
    }
}
