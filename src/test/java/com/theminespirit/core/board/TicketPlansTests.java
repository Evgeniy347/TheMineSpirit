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
import com.theminespirit.core.script.CatalogLimits;
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

    private static final Map<BoardCase, String> CASE_SCRIPTS = Map.ofEntries(
            Map.entry(BoardCase.FILL_CRATER, "fill"),
            Map.entry(BoardCase.REPAIR_FENCE, "fill"),
            Map.entry(BoardCase.CAPTURE_ANIMAL, "capture"),
            Map.entry(BoardCase.BUILD_PEN, "build_schematic"),
            Map.entry(BoardCase.LURE_ANIMAL, "withdraw"),
            Map.entry(BoardCase.BUILD_MINE, "dig_area"),
            Map.entry(BoardCase.SUPPLY_SITE, "acquire"),
            Map.entry(BoardCase.CLEAR_GROUNDS, "clear_area"),
            Map.entry(BoardCase.BUILD_HOUSE, "build_schematic"),
            Map.entry(BoardCase.REPAIR_BUILDING, "build_schematic"),
            Map.entry(BoardCase.STOCK_CHESTS, "craft"),
            Map.entry(BoardCase.CRAFTING_STATION, "craft"),
            Map.entry(BoardCase.CRAFT_TOOLS, "craft"),
            Map.entry(BoardCase.BUILD_FENCE, "build_schematic"),
            Map.entry(BoardCase.LIGHTING, "place_block"),
            Map.entry(BoardCase.ENCHANT_ROOM, "build_schematic"),
            Map.entry(BoardCase.ENCHANT_SHELVES, "craft"),
            Map.entry(BoardCase.DISMANTLE_SHELTER, "clear_area"));

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
    void plan_EveryCase_HasPlan() {
        for (BoardCase boardCase : BoardCase.values()) {
            TaskNode plan = TicketPlans.plan(boardCase, target());

            assertTrue(plan != null && !collectScripts(plan).isEmpty(),
                    "Каждый кейс обязан иметь план: кейс " + boardCase
                            + " без плана не выдавался бы духу, а выдача без плана — задание в пустоту");
        }
    }

    @Test
    void plan_Lighting_PlacesFourTorches() {
        TaskNode plan = TicketPlans.plan(BoardCase.LIGHTING, target());
        long torches = collectScripts(plan).stream().filter("place_block"::equals).count();

        assertEquals(4, torches,
                "Освещение — четыре факела по углам: один факел не освещает территорию, "
                        + "а больше — перерасход ресурсов доски");
    }

    @Test
    void plan_BuildFence_UsesFenceScheme() {
        TaskNode plan = TicketPlans.plan(BoardCase.BUILD_FENCE, target());

        assertTrue(collectScripts(plan).contains("build_schematic"),
                "Забор строится схемой-кольцом fence: новый скрипт не заводим, кольцо "
                        + "описано файлом схемы");
    }

    @Test
    void plan_EnchantShelves_CraftsBookshelves() {
        TaskNode plan = TicketPlans.plan(BoardCase.ENCHANT_SHELVES, target());

        assertTrue(collectScripts(plan).contains("craft"),
                "Полки достраиваются крафтом книжных полок: книги гейтят задание, "
                        + "а план их собирает в полки");
    }

    @Test
    void plan_DismantleShelter_ClearsShelter() {
        TaskNode plan = TicketPlans.plan(BoardCase.DISMANTLE_SHELTER, target());

        assertTrue(collectScripts(plan).contains("clear_area"),
                "Разбор укрытия — существующий clear_area: второй разборочный скрипт "
                        + "не заводим");
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
    void plan_FillCrater_AcquiresDirtFromStock() {
        TaskNode plan = TicketPlans.plan(BoardCase.FILL_CRATER, target().withCraterBlocks(29));

        assertTrue(collectScripts(plan).contains("acquire"),
                "Воронка обязана брать землю через acquire: гейт уже проверил склад, "
                        + "и план берёт из запаса, а не копает заново");
        assertTrue(!collectScripts(plan).contains("gather"),
                "План воронки не копает вслепую: gather игнорирует склад и проваливается "
                        + "там, где земли рядом нет, хотя гейт её уже нашёл");
    }

    @Test
    void plan_SupplySite_ClampsHugeCount() {
        TaskNode plan = TicketPlans.plan(BoardCase.SUPPLY_SITE,
                target().withSupply("dirt", 5000));
        ScriptCall acquire = findCall(plan, "acquire");

        assertEquals(256, acquire.params().get("count"),
                "Снабжение режет партию пределом каталога: AcquireParams принимает не больше "
                        + "COUNT_MAX, без нарезки дух падает на выдаче (" + acquire.params().get("count")
                        + "), а остаток доска дозакажет следующим заданием");
    }

    @Test
    void plan_FillCrater_ClampsHugeEstimate() {
        TaskNode plan = TicketPlans.plan(BoardCase.FILL_CRATER, target().withCraterBlocks(10000));
        ScriptCall acquire = findCall(plan, "acquire");

        assertEquals(256, acquire.params().get("count"),
                "Оценка большой воронки тоже режется пределом каталога: иначе acquire "
                        + "падает на выдаче (" + acquire.params().get("count") + ")");
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

    @Test
    void plan_CraftCases_CarryStationRadius() {
        for (BoardCase craftCase : List.of(BoardCase.STOCK_CHESTS, BoardCase.CRAFTING_STATION,
                BoardCase.CRAFT_TOOLS, BoardCase.ENCHANT_SHELVES)) {
            TaskNode plan = TicketPlans.plan(craftCase, target());
            ScriptCall craft = findCall(plan, "craft");
            Object radius = craft.params().get("radius");

            assertTrue(radius instanceof Integer distance
                            && distance >= CatalogLimits.STATION_RADIUS_MIN
                            && distance <= CatalogLimits.STATION_RADIUS_MAX,
                    "craft обязан нести радиус поиска станции в границах каталога: CraftParams.from "
                            + "читает radius обязательным, без него дух проваливает задание " + craftCase
                            + " на выдаче (радиус " + radius + ")");
        }
    }

    @Test
    void plan_SupplySite_DepositCarriesContainerRadius() {
        TaskNode plan = TicketPlans.plan(BoardCase.SUPPLY_SITE, target());
        ScriptCall deposit = findCall(plan, "deposit");
        Object radius = deposit.params().get("radius");

        assertTrue(radius instanceof Integer distance
                        && distance >= CatalogLimits.CONTAINER_RADIUS_MIN
                        && distance <= CatalogLimits.CONTAINER_RADIUS_MAX,
                "deposit обязан нести радиус поиска контейнера в границах каталога: TransferParams.from "
                        + "читает radius обязательным, без него план падает на выдаче и добыча не кладёт "
                        + "предметы в склад (радиус " + radius + ")");
    }

    private static ScriptCall findCall(TaskNode root, String script) {
        Deque<TaskNode> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            TaskNode node = queue.poll();
            switch (node) {
                case ScriptCall call -> {
                    if (call.script().equals(script)) {
                        return call;
                    }
                }
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
                default -> {
                }
            }
        }
        throw new AssertionError("В плане нет вызова скрипта " + script + ": без него задание не работает");
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
