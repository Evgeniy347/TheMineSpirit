package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.husbandry.Species;
import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.PlanValidator;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.planner.Ingredient;
import com.theminespirit.core.planner.ResourceKnowledge;
import com.theminespirit.core.script.ConditionCatalog;
import com.theminespirit.core.script.CoreScripts;
import com.theminespirit.core.script.ScriptSpec;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class TicketPlansTests {

    private static final Map<BoardCase, String> CASE_SCRIPTS = Map.of(
            BoardCase.FILL_CRATER, "fill",
            BoardCase.REPAIR_FENCE, "fill",
            BoardCase.CAPTURE_ANIMAL, "capture",
            BoardCase.BUILD_PEN, "build_schematic",
            BoardCase.LURE_ANIMAL, "withdraw",
            BoardCase.BUILD_MINE, "dig_area",
            BoardCase.SUPPLY_SITE, "deliver",
            BoardCase.CLEAR_GROUNDS, "clear_area",
            BoardCase.BUILD_HOUSE, "build_schematic",
            BoardCase.REPAIR_BUILDING, "build_schematic");

    private static final List<BoardCase> PLANNED_CASES = List.of(CASE_SCRIPTS.keySet().toArray(new BoardCase[0]));

    private static final ResourceKnowledge GATHER_EVERYTHING = new ResourceKnowledge() {
        @Override
        public boolean isGatherable(String item) {
            return true;
        }

        @Override
        public boolean isCraftable(String item) {
            return false;
        }

        @Override
        public int outputCount(String item) {
            return 1;
        }

        @Override
        public List<Ingredient> ingredientsFor(String item) {
            return List.of();
        }

        @Override
        public boolean isSmeltable(String item) {
            return false;
        }

        @Override
        public String smeltInputFor(String item) {
            return item;
        }
    };

    private static TicketTarget target() {
        return TicketTarget.at(new BlockCoord(10, 60, -5)).withSpecies(Species.SHEEP)
                .withKnowledge(GATHER_EVERYTHING);
    }

    @Test
    void plan_EveryCase_ContainsCaseScript() {
        for (BoardCase boardCase : PLANNED_CASES) {
            TaskNode plan = TicketPlans.plan(boardCase, target());

            assertTrue(collectScripts(plan).contains(CASE_SCRIPTS.get(boardCase)),
                    "План кейса " + boardCase + " обязан содержать целевой скрипт "
                            + CASE_SCRIPTS.get(boardCase) + ": без него задание не делает свою работу");
        }
    }

    @Test
    void plan_EveryCase_PassesCatalog() {
        for (BoardCase boardCase : PLANNED_CASES) {
            TaskNode plan = TicketPlans.plan(boardCase, target());

            PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
        }
    }

    @Test
    void plan_UnknownCase_Rejected() {
        List<BoardCase> unplanned = new ArrayList<>();
        for (BoardCase boardCase : BoardCase.values()) {
            if (!CASE_SCRIPTS.containsKey(boardCase)) {
                unplanned.add(boardCase);
            }
        }

        for (BoardCase boardCase : unplanned) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> TicketPlans.plan(boardCase, target()),
                    "Кейс " + boardCase + " не имеет плана на доске: молчаливый пропуск "
                            + "выдал бы духу пустой план");
            assertTrue(error.getMessage().contains("не имеет плана"),
                    "Сообщение обязано называть причину: плана нет. Фактическое: " + error.getMessage());
        }
    }

    @Test
    void plan_Is_Deterministic() {
        TaskNode first = TicketPlans.plan(BoardCase.FILL_CRATER, target().withCraterBlocks(29));
        TaskNode second = TicketPlans.plan(BoardCase.FILL_CRATER, target().withCraterBlocks(29));

        assertEquals(first, second,
                "План обязан быть детерминирован: доска выдаёт план духу, и пересборка "
                        + "не должна менять задание на лету");
    }

    @Test
    void plan_FillCrater_GathersDirt() {
        TaskNode plan = TicketPlans.plan(BoardCase.FILL_CRATER, target().withCraterBlocks(29));

        assertTrue(collectScripts(plan).contains("gather"),
                "Воронка обязана начинаться с добычи земли через ResourceResolver: без "
                        + "сбора дух пришёл бы засыпать яму пустыми руками");
    }

    @Test
    void plan_CaptureAnimal_UsesSpecies() {
        TaskNode plan = TicketPlans.plan(BoardCase.CAPTURE_ANIMAL,
                target().withSpecies(Species.CHICKEN));

        assertTrue(collectScripts(plan).contains("capture"),
                "Отлов обязан идти скриптом capture: сам отлов — существующий механизм "
                        + " husbandry (25), план только его вызывает");
    }

    @Test
    void plan_BuildPen_UsesRingScheme() {
        TaskNode plan = TicketPlans.plan(BoardCase.BUILD_PEN, target());

        assertTrue(collectScripts(plan).contains("build_schematic"),
                "Згон строится схемой-пресетом кольца из блоков (поправка 8): отдельный "
                        + "файл схемы описывает кольцо, новый скрипт не заводим");
    }

    @Test
    void plan_LureAnimal_WithdrawsLure() {
        TaskNode plan = TicketPlans.plan(BoardCase.LURE_ANIMAL, target().withLureItem("wheat"));

        assertTrue(collectScripts(plan).contains("withdraw"),
                "Приманить — сначала взять приманку: гейт требует приманку в запасах, "
                        + "а план — сначала вынуть её со склада");
    }

    @Test
    void plan_BuildMine_DigsDescent() {
        TaskNode plan = TicketPlans.plan(BoardCase.BUILD_MINE, target());

        assertTrue(collectScripts(plan).contains("dig_area"),
                "Вход в шахту — копка спуска dig_area: версия 1, размер 3");
    }

    @Property
    void plan_AnyPlannedCase_ValidByCatalog(@ForAll("plannedCases") BoardCase boardCase) {
        TaskNode plan = TicketPlans.plan(boardCase, target().withCraterBlocks(29));

        PlanValidator.validate(plan, CoreScripts.defaultCatalog(), ConditionCatalog.empty());
    }

    @Property
    void plan_AnyPlannedCase_OnlyCatalogScripts(@ForAll("plannedCases") BoardCase boardCase) {
        TaskNode plan = TicketPlans.plan(boardCase, target().withCraterBlocks(29));
        List<String> catalogScripts = CoreScripts.defaultCatalog().all().stream()
                .map(ScriptSpec::id)
                .toList();

        for (String script : collectScripts(plan)) {
            assertTrue(catalogScripts.contains(script),
                    "План не обязан ссылаться на скрипты вне каталога: " + script
                            + " — иначе дух получит задание с неизвестным шагом");
        }
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

    @Provide
    Arbitrary<BoardCase> plannedCases() {
        return Arbitraries.of(PLANNED_CASES);
    }
}
