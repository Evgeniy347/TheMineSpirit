package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TacticsScenarioTests {
    private static final List<String> MANDATORY = List.of(
            "один зомби, меч, полное HP",
            "пять зомби, без оружия",
            "крипер в 3 блоках",
            "скелеты на дистанции 12",
            "passive, зомби в 2, отступать некуда",
            "guard_area, враг за leash",
            "protect, игрока бьют, HP полное",
            "protect, но HP 20 %",
            "ночь, орда, дом в 30",
            "лук есть, враг в 10",
            "утопленник",
            "врагов нет",
            "aggressive, вдвое сильнее",
            "aggressive, HP ровно 25 %",
            "ravager против железной брони и меча",
            "воин слабее, но союзников 3",
            "срочно: HP 15 %",
            "не срочно при том же перевесе",
            "срочно по счёту",
            "r=1.25");

    static Stream<Arguments> scenarios() {
        return ScenarioFile.load().stream().map(scenario -> Arguments.of(scenario.name(), scenario));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void step_Scenario_MatchesExpectation(String name, ScenarioFile.Scenario scenario) {
        TacticalOutcome outcome = new TacticsEngine(TacticsConfig.loadDefault())
                .step(scenario.situation(), scenario.memory(), scenario.tick());

        List<String> mismatches = mismatches(scenario.expect(), outcome);

        assertEquals(
                List.of(),
                mismatches,
                "Сценарий «" + name + "» расходится с ожиданием: тактическое решение изменилось. Если изменение "
                        + "осознанное (веса, правила), поправьте scenarios.json и docs/tactics/README.md; иначе откатите "
                        + "правку. Трасса: " + outcome.trace().explain() + ". Расхождения");
    }

    private static List<String> mismatches(ScenarioFile.Expectation want, TacticalOutcome got) {
        List<String> found = new ArrayList<>();
        check(found, "decision", want.decision(), got.decision());
        check(found, "action", want.action(), got.action());
        check(found, "retreat", want.retreat(), got.retreatMethod());
        check(found, "rule", want.rule(), got.trace().rule());
        check(found, "mode", want.mode(), got.mode());
        check(found, "state", want.state(), got.state());
        check(found, "callHelp", want.callHelp(), got.callHelp());
        check(found, "returnTo", want.returnTo(), got.returnTo().isPresent());
        return found;
    }

    private static <T> void check(List<String> found, String field, Optional<T> want, T got) {
        want.filter(expected -> !expected.equals(got))
                .ifPresent(expected -> found.add(field + ": ожидали " + expected + ", получили " + got));
    }

    @Test
    void scenarios_File_HasAtLeastFortyUniqueNamedRows() {
        List<String> names =
                ScenarioFile.load().stream().map(ScenarioFile.Scenario::name).toList();

        assertTrue(
                names.size() >= 40,
                "В scenarios.json меньше 40 строк (" + names.size()
                        + "): сценарии — истина, под которую подгоняются веса");
        assertEquals(
                names.size(),
                new HashSet<>(names).size(),
                "Имена сценариев уникальны: по имени находят упавшую строку");
    }

    @Test
    void scenarios_File_ContainsEveryMandatoryCase() {
        List<String> names =
                ScenarioFile.load().stream().map(ScenarioFile.Scenario::name).toList();
        Set<String> missing = new HashSet<>();
        for (String required : MANDATORY) {
            if (names.stream().noneMatch(name -> name.contains(required))) {
                missing.add(required);
            }
        }

        assertEquals(
                Set.of(),
                missing,
                "Обязательные сценарии из задания пропали из scenarios.json: каждый из них защищает отдельное решение");
    }

    @Test
    void scenarios_OutsideMandatoryList_EachExplainsWhy() {
        List<String> unexplained = ScenarioFile.load().stream()
                .filter(scenario -> MANDATORY.stream().noneMatch(scenario.name()::startsWith))
                .filter(scenario -> scenario.why().map(String::isBlank).orElse(true))
                .map(ScenarioFile.Scenario::name)
                .toList();

        assertEquals(
                List.of(),
                unexplained,
                "Сценарий сверх обязательного списка закрепляет решение без объяснения: поле \"why\" называет, почему "
                        + "именно так, и по нему видно, спорное ли это решение геймдизайна. Допишите \"why\" или удалите "
                        + "сценарий. Без объяснения");
    }

    @Test
    void scenarios_Memory_InitialMemoryHasNoPreviousDecision() {
        ScenarioFile.Scenario first = ScenarioFile.load().get(0);

        assertEquals(
                Optional.empty(),
                first.memory().previous(),
                "Сценарий без поля memory начинается с чистой памяти: удержание не влияет на первое решение");
    }
}
