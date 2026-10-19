package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class TacticsSensitivityTests {
    private static final TacticsConfig CONFIG = TacticsConfig.loadDefault();
    private static final double SPEC_SHIFT = 0.10;

    private static TacticalOutcome outcome(TacticsConfig config, ScenarioFile.Scenario scenario) {
        return new TacticsEngine(config).step(scenario.situation(), scenario.memory(), scenario.tick());
    }

    static TacticsConfig shifted(Factor factor, TacticalOption option, double fraction) {
        ScoreWeights base = CONFIG.weights();
        Map<Factor, Map<TacticalOption, Double>> matrix = new EnumMap<>(Factor.class);
        for (Factor row : Factor.values()) {
            Map<TacticalOption, Double> cells = new EnumMap<>(TacticalOption.class);
            for (TacticalOption column : TacticalOption.values()) {
                cells.put(column, base.weight(row, column));
            }
            matrix.put(row, cells);
        }
        matrix.get(factor).put(option, base.weight(factor, option) * (1 + fraction));
        ScoreWeights weights = new ScoreWeights(
                matrix, base.tieBreak(), base.stickiness(), base.holdTicks(), base.hpDropRelease(), base.scales());
        return new TacticsConfig(CONFIG.enemies(), weights, CONFIG.rules(), CONFIG.modes());
    }

    static List<String> flips(ScenarioFile.Scenario scenario, double shift) {
        TacticalOutcome base = outcome(CONFIG, scenario);
        if (!Decision.BY_SCORE.equals(base.trace().rule())) {
            return List.of();
        }
        List<String> flipped = new ArrayList<>();
        for (Factor factor : Factor.values()) {
            for (TacticalOption option : TacticalOption.values()) {
                for (double fraction : new double[] {-shift, shift}) {
                    TacticalOption got =
                            outcome(shifted(factor, option, fraction), scenario).decision();
                    if (got != base.decision()) {
                        flipped.add(factor + "." + option + " " + Math.round(fraction * 100) + " % -> " + got);
                    }
                }
            }
        }
        return flipped;
    }

    static Map<String, List<String>> fragileUnmarked(List<ScenarioFile.Scenario> scenarios, double shift) {
        Map<String, List<String>> fragile = new TreeMap<>();
        for (ScenarioFile.Scenario scenario : scenarios) {
            List<String> found = scenario.boundary() ? List.of() : flips(scenario, shift);
            if (!found.isEmpty()) {
                fragile.put(scenario.name(), found);
            }
        }
        return fragile;
    }

    static List<String> staleBoundary(List<ScenarioFile.Scenario> scenarios, double shift) {
        return scenarios.stream()
                .filter(scenario ->
                        scenario.boundary() && flips(scenario, shift).isEmpty())
                .map(ScenarioFile.Scenario::name)
                .toList();
    }

    static List<String> pairProblems(List<ScenarioFile.Scenario> scenarios) {
        Map<String, ScenarioFile.Scenario> byName = new TreeMap<>();
        scenarios.forEach(scenario -> byName.put(scenario.name(), scenario));
        List<String> problems = new ArrayList<>();
        for (ScenarioFile.Scenario scenario : scenarios) {
            if (scenario.boundary()) {
                problems.addAll(pairProblem(scenario, byName));
            }
        }
        return problems;
    }

    private static List<String> pairProblem(ScenarioFile.Scenario scenario, Map<String, ScenarioFile.Scenario> byName) {
        Optional<String> pairName = scenario.pair();
        if (pairName.isEmpty()) {
            return List.of(scenario.name() + ": нет поля pair");
        }
        ScenarioFile.Scenario pair = byName.get(pairName.get());
        if (pair == null) {
            return List.of(scenario.name() + ": пара «" + pairName.get() + "» не найдена");
        }
        if (pair.expect().decision().equals(scenario.expect().decision())) {
            return List.of(scenario.name() + ": пара «" + pair.name() + "» ожидает то же решение "
                    + scenario.expect().decision().orElseThrow() + ", а не противоположное");
        }
        return List.of();
    }

    private static List<ScenarioFile.Scenario> synthetic(String boundaryFields) {
        String json = "{\"scenarios\": ["
                + "{\"name\": \"один\", \"enemies\": [{\"type\": \"zombie\", \"distance\": 5}]," + boundaryFields
                + " \"expect\": {\"decision\": \"FIGHT\"}},"
                + "{\"name\": \"два\", \"enemies\": [{\"type\": \"zombie\", \"distance\": 5}],"
                + " \"expect\": {\"decision\": \"FIGHT\"}}]}";
        return ScenarioFile.parse(json);
    }

    @Test
    void scenarios_DecidedByScoreWithoutBoundaryMark_SurviveAnySingleWeightShiftOfTenPercent() {
        Map<String, List<String>> fragile = fragileUnmarked(ScenarioFile.load(), SPEC_SHIFT);

        assertEquals(
                Map.of(),
                fragile,
                "Решение по счёту держится на одном весе: сдвиг любого веса на ±10 % его меняет, значит сценарий "
                        + "проверяет подогнанный вес, а не устройство оценки. Либо добавьте сценарий с запасом, либо "
                        + "пометьте его \"boundary\": true с причиной в \"why\" и парой \"pair\" с другой стороны границы. "
                        + "Хрупкие сценарии");
    }

    @Test
    void scenarios_MarkedBoundary_AreReallySensitiveToSomeWeight() {
        List<String> stale = staleBoundary(ScenarioFile.load(), SPEC_SHIFT);

        assertEquals(
                List.of(),
                stale,
                "Сценарий помечен граничным, но решение не зависит от сдвига ни одного веса на ±10 % (или решено "
                        + "не счётом): пометка устарела и скрывает настоящие границы. Снимите \"boundary\" у сценариев");
    }

    @Test
    void scenarios_MarkedBoundary_HaveOppositeSidePair() {
        List<String> problems = pairProblems(ScenarioFile.load());

        assertEquals(
                List.of(),
                problems,
                "Граничный сценарий проверяет вес с одной стороны границы, поэтому обязан иметь пару по другую "
                        + "(\"pair\": имя сценария с иным решением). Проблемы");
    }

    @Test
    void fragileUnmarked_HalfWeightShift_FindsScenariosDecidedByScore() {
        Map<String, List<String>> fragile = fragileUnmarked(ScenarioFile.load(), 0.5);

        assertFalse(
                fragile.isEmpty(),
                "Анализ ослеп: при сдвиге весов на 50 % ни один сценарий по счёту не переключился, "
                        + "а известные границы весов (например ALLY_SUPPORT) в файле есть. Проверьте shifted() и flips()");
    }

    @Test
    void staleBoundary_RobustScenarioMarkedBoundary_IsReported() {
        List<ScenarioFile.Scenario> scenarios = synthetic("\"boundary\": true, \"pair\": \"два\",");

        assertEquals(
                List.of("один"),
                staleBoundary(scenarios, SPEC_SHIFT),
                "Сторож пометок ослеп: устойчивый сценарий с пометкой boundary обязан попадать в устаревшие");
    }

    @Test
    void pairProblems_MissingOrSameDecisionPair_AreReported() {
        assertEquals(
                List.of("один: нет поля pair"),
                pairProblems(synthetic("\"boundary\": true,")),
                "Граничный сценарий без pair обязан попадать в проблемы");
        assertEquals(
                List.of("один: пара «нет такого» не найдена"),
                pairProblems(synthetic("\"boundary\": true, \"pair\": \"нет такого\",")),
                "Пара с несуществующим именем обязана попадать в проблемы");
        assertEquals(
                List.of("один: пара «два» ожидает то же решение FIGHT, а не противоположное"),
                pairProblems(synthetic("\"boundary\": true, \"pair\": \"два\",")),
                "Пара с тем же решением — не другая сторона границы, обязана попадать в проблемы");
    }

    @Test
    void shifted_OneWeight_ChangesOnlyThatCellByTenPercent() {
        TacticsConfig config = shifted(Factor.ALLY_SUPPORT, TacticalOption.FIGHT, SPEC_SHIFT);

        double base = CONFIG.weights().weight(Factor.ALLY_SUPPORT, TacticalOption.FIGHT);
        assertTrue(
                Math.abs(config.weights().weight(Factor.ALLY_SUPPORT, TacticalOption.FIGHT) - base * 1.1) < 1e-12
                        && config.weights().weight(Factor.ALLY_SUPPORT, TacticalOption.RETREAT)
                                == CONFIG.weights().weight(Factor.ALLY_SUPPORT, TacticalOption.RETREAT)
                        && config.weights().weight(Factor.BIAS, TacticalOption.FIGHT)
                                == CONFIG.weights().weight(Factor.BIAS, TacticalOption.FIGHT),
                "Сдвиг трогает ровно одну ячейку матрицы, иначе анализ чувствительности смешивает веса");
    }
}
