package com.theminespirit.core.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.theminespirit.core.observation.BlockCoord;
import com.theminespirit.core.plan.ConditionNode;
import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ParallelNode;
import com.theminespirit.core.plan.RepeatNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import com.theminespirit.core.plan.TaskNode;
import com.theminespirit.core.reload.DataTables;
import com.theminespirit.core.town.Placement;
import com.theminespirit.core.town.TownFixtures;
import com.theminespirit.core.town.TownPlan;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StageCatalogTests {
    private static final int STAGE = 2;

    @Test
    void stagePlan_EveryId_HasCaseSchemeAndPlanInItsSlot() {
        StageRules.Stage stage = StageRules.load().stages().get(STAGE - 1);
        TownPlan town = TownFixtures.flatPlan();
        List<String> problems = new ArrayList<>();

        for (String id : stage.plan()) {
            Optional<StageCatalog.Entry> entry = StageCatalog.entry(id);
            if (entry.isEmpty()) {
                problems.add(id + ": нет кейса и схемы");
                continue;
            }
            StageCatalog.Entry resolved = entry.orElseThrow();
            if (DataTables.INSTANCE.presets().current().byId(resolved.scheme()).isEmpty()) {
                problems.add(id + ": схема '" + resolved.scheme() + "' не читается из ресурсов");
            }
            Optional<Placement> slot = town.placement(id);
            if (slot.isEmpty()) {
                problems.add(id + ": нет своего участка в плане");
                continue;
            }
            Placement place = slot.orElseThrow();
            BlockCoord origin = new BlockCoord(place.x(), town.anchor().y(), place.z());
            TicketTarget target =
                    TicketTarget.at(origin).withScheme(resolved.scheme()).withBuilding(id);
            try {
                TaskNode task = TicketPlans.plan(resolved.boardCase(), 1, target);
                if (task == null) {
                    problems.add(id + ": пустой план задания");
                } else if (!builds(task, resolved.scheme(), origin)) {
                    problems.add(id + ": план не ставит схему '" + resolved.scheme() + "' на " + origin);
                }
            } catch (RuntimeException failure) {
                problems.add(id + ": план не собирается — " + failure.getMessage());
            }
        }

        assertEquals(
                List.of(),
                problems,
                "Каждый id плана этапа 2 обязан разрешаться в кейс доски, схему и непустой план задания на своём "
                        + "участке; иначе доска молча отбрасывает постройку и этап не собрать: " + problems
                        + "; не поместилось: " + town.unplaced());
    }

    @Test
    void stageRequirements_StagesThreeAndFour_HaveCaseSchemeAndPlanEntry() {
        StageRules rules = StageRules.load();
        List<String> problems = new ArrayList<>();

        for (int number = 3; number <= 4; number++) {
            StageRules.Stage stage = rules.stages().get(number - 1);
            List<String> ids = new ArrayList<>(stage.requires().keySet());
            ids.addAll(stage.optional().keySet());
            for (String id : ids) {
                if (!stage.plan().contains(id)) {
                    problems.add("этап " + number + ", " + id + ": нет в plan этапа");
                }
                Optional<StageCatalog.Entry> entry = StageCatalog.entry(id);
                if (entry.isEmpty()) {
                    problems.add("этап " + number + ", " + id + ": нет кейса и схемы");
                    continue;
                }
                StageCatalog.Entry resolved = entry.orElseThrow();
                if (DataTables.INSTANCE
                        .presets()
                        .current()
                        .byId(resolved.scheme())
                        .isEmpty()) {
                    problems.add("этап " + number + ", " + id + ": схема '" + resolved.scheme() + "' не читается");
                }
                if (DataTables.INSTANCE.town().current().entry(id).isEmpty()) {
                    problems.add("этап " + number + ", " + id + ": нет в каталоге плана town.json");
                }
                if (!StageCatalog.placed(id)) {
                    problems.add("этап " + number + ", " + id + ": кейс " + resolved.boardCase()
                            + " не ставит схему постройки плана");
                }
            }
        }

        assertEquals(
                List.of(),
                problems,
                "Обязательные и желательные постройки этапов 3–4 обязаны иметь кейс постройки плана, схему и "
                        + "запись в town.json; иначе этап не закрыть, а вкладка покажет пустую строку: " + problems);
    }

    @Test
    void watcherOwned_Pen_IsBuiltByGraph() {
        assertFalse(
                StageCatalog.watcherOwned(BoardCase.BUILD_PEN),
                "Курятник и загоны строит граф узлами building:chicken_coop/pen_*, а сторож больше не ставит "
                        + "им задание: иначе этап 2 проходит мимо графа");
    }

    @Test
    void origin_PlacementOfPlanId_IsTopLeftOfItsSlot() {
        TownPlan town = TownFixtures.flatPlan();
        Placement place = town.placement("family_house_a").orElseThrow();

        assertEquals(
                Optional.of(new BlockCoord(place.x(), town.anchor().y(), place.z())),
                StageCatalog.origin("family_house_a", town, town.anchor().y()),
                "Постройка этапа 2 ставится в свой слот плана, а не на двор этапа 1");
    }

    private static boolean builds(TaskNode task, String scheme, BlockCoord origin) {
        for (ScriptCall call : calls(task)) {
            if (!"build_schematic".equals(call.script())) {
                continue;
            }
            return scheme.equals(call.params().get("file"))
                    && Integer.valueOf(origin.x()).equals(call.params().get("x"))
                    && Integer.valueOf(origin.y()).equals(call.params().get("y"))
                    && Integer.valueOf(origin.z()).equals(call.params().get("z"));
        }
        return false;
    }

    private static List<ScriptCall> calls(TaskNode node) {
        List<ScriptCall> found = new ArrayList<>();
        switch (node) {
            case ScriptCall call -> found.add(call);
            case SequenceNode sequence -> sequence.steps().forEach(step -> found.addAll(calls(step)));
            case FallbackNode fallback -> fallback.steps().forEach(step -> found.addAll(calls(step)));
            case ParallelNode parallel -> parallel.steps().forEach(step -> found.addAll(calls(step)));
            case RepeatNode repeat -> found.addAll(calls(repeat.step()));
            case ConditionNode condition -> {
                found.addAll(calls(condition.thenStep()));
                if (condition.elseStep() != null) {
                    found.addAll(calls(condition.elseStep()));
                }
            }
        }
        return found;
    }
}
