package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TacticsConfigTests {
    private static final String HOME_REQUIRES_FIRST = "{\"fact\": \"env.homeReachable\", \"op\": \"==\", \"value\": true}";

    private static TacticsConfig load(String file, String text) {
        String enemies = file.equals("enemies.json") ? text : TacticsTexts.read("enemies.json");
        String weights = file.equals("weights.json") ? text : TacticsTexts.read("weights.json");
        String rules = file.equals("rules.json") ? text : TacticsTexts.read("rules.json");
        String modes = file.equals("modes.json") ? text : TacticsTexts.read("modes.json");
        return TacticsConfig.fromJson(enemies, weights, rules, modes);
    }

    @Test
    void loadDefault_ShippedFiles_ParseWithAllSections() {
        TacticsConfig config = TacticsConfig.loadDefault();

        assertEquals(List.of(13, 7, 6, 9, 8, 16, 5),
                List.of(config.rules().hard().size(), config.rules().actions().size(),
                        config.rules().modeTransitions().size(), config.rules().states().size(),
                        config.rules().retreat().entries().size(), config.weights().matrix().size(),
                        config.modes().params().size()),
                "Стартовый набор: 13 жёстких правил, 7 действий, 6 смен режима, 9 переходов состояния, 8 способов отхода, "
                        + "16 факторов и 5 режимов; изменили файлы осознанно — обновите это число и docs/TACTICS.md");
    }

    @Test
    void loadDefault_CalledTwice_ReturnsSameInstanceAndReadsFilesOnce() {
        TacticsConfig first = TacticsConfig.loadDefault();
        TacticsConfig second = TacticsConfig.loadDefault();

        assertSame(first, second, "Конфигурация по умолчанию читается один раз и неизменяема: она общая для всех духов");
        assertEquals(1, TacticsConfig.defaultLoads(),
                "Файлы тактики читаются с диска ровно один раз за процесс: чтение в тике было бы дефектом производительности");
    }

    @Test
    void loadDefault_RuleIds_AreUniqueAcrossSections() {
        Rules rules = TacticsConfig.loadDefault().rules();
        List<String> ids = new ArrayList<>();
        rules.hard().forEach(rule -> ids.add(rule.id()));
        rules.actions().forEach(rule -> ids.add(rule.id()));
        rules.modeTransitions().forEach(rule -> ids.add(rule.id()));
        rules.states().forEach(rule -> ids.add(rule.id()));
        rules.retreat().entries().forEach(rule -> ids.add(rule.id()));

        assertEquals(ids.size(), ids.stream().distinct().count(),
                "id правил уникальны во всём rules.json: по id сценарии и trace находят правило");
    }

    @Test
    void loadDefault_HardRules_AreSortedByDescendingPriority() {
        List<Integer> priorities = TacticsConfig.loadDefault().rules().hard().stream().map(HardRule::priority).toList();

        assertEquals(priorities.stream().sorted(Comparator.reverseOrder()).toList(), priorities,
                "Жёсткие правила проверяются по убыванию приоритета: первое подходящее решает");
    }

    @Test
    void loadDefault_Scales_MatchTestScales() {
        assertEquals(Scales.defaults(), TacticsConfig.loadDefault().weights().scales(),
                "Тесты факторов считают по тем же масштабам, что лежат в weights.json: расхождение скроет ошибку формул");
    }

    static Stream<Arguments> brokenFiles() {
        String weights = TacticsTexts.read("weights.json");
        String rules = TacticsTexts.read("rules.json");
        String modes = TacticsTexts.read("modes.json");
        return Stream.of(
                broken("удалён вес", "weights.json", TacticsTexts.mutate(weights,
                        "\"BIAS\": {\"FIGHT\": 0, \"RETREAT\": 0.2, \"IGNORE\": 0.5}",
                        "\"BIAS\": {\"FIGHT\": 0, \"RETREAT\": 0.2}"), "weights.BIAS"),
                broken("удалена строка фактора", "weights.json", TacticsTexts.mutate(weights,
                        "    \"LOW_HP\": {\"FIGHT\": -2.0, \"RETREAT\": 2.5, \"IGNORE\": 0},\n", ""), "weights"),
                broken("неизвестный фактор", "weights.json", TacticsTexts.mutate(weights, "\"HUNGER\"", "\"HUNGRY\""),
                        "weights.HUNGRY"),
                broken("неизвестный вариант", "weights.json", TacticsTexts.mutate(weights, "\"IGNORE\": 0.5", "\"IGNOR\": 0.5"),
                        "weights.BIAS.IGNOR"),
                broken("вес вне диапазона", "weights.json", TacticsTexts.mutate(weights, "\"FIGHT\": 2.0", "\"FIGHT\": 20.0"),
                        "weights.WIN_MARGIN.FIGHT"),
                broken("лишнее поле", "weights.json", TacticsTexts.mutate(weights, "\"holdTicks\": 40,",
                        "\"holdTicks\": 40, \"extra\": 1,"), "extra"),
                broken("дубль в tieBreak", "weights.json", TacticsTexts.mutate(weights,
                        "[\"RETREAT\", \"FIGHT\", \"IGNORE\"]", "[\"RETREAT\", \"RETREAT\", \"IGNORE\"]"), "tieBreak[1]"),
                broken("неполный tieBreak", "weights.json", TacticsTexts.mutate(weights,
                        "[\"RETREAT\", \"FIGHT\", \"IGNORE\"]", "[\"RETREAT\", \"FIGHT\"]"), "tieBreak"),
                broken("предел дальности за границей", "weights.json", TacticsTexts.mutate(weights, "\"dropRange\": 9",
                        "\"dropRange\": 500"), "scales.dropRange"),
                broken("предел скорости ниже границы", "weights.json", TacticsTexts.mutate(weights,
                        "\"speedRatioCap\": 10", "\"speedRatioCap\": 0.5"), "scales.speedRatioCap"),
                broken("липкость за границей", "weights.json", TacticsTexts.mutate(weights, "\"stickiness\": 0.25",
                        "\"stickiness\": 5"), "stickiness"),
                broken("окно удержания за границей", "weights.json", TacticsTexts.mutate(weights, "\"holdTicks\": 40",
                        "\"holdTicks\": 5000"), "holdTicks"),
                broken("порог 1.5", "weights.json", TacticsTexts.mutate(weights, "\"hpDropRelease\": 0.3",
                        "\"hpDropRelease\": 1.5"), "hpDropRelease"),
                broken("holdTicks дробью", "weights.json", TacticsTexts.mutate(weights, "\"holdTicks\": 40",
                        "\"holdTicks\": 40.5"), "holdTicks"),
                broken("нет масштаба", "weights.json", TacticsTexts.mutate(weights, "    \"minDps\": 0.1,\n", ""),
                        "scales.minDps"),
                broken("опечатка в факте условия", "rules.json", TacticsTexts.mutate(rules,
                        "\"enemy.nearestExplosive\", \"op\": \"<=\", \"value\": 5",
                        "\"enemy.nearestExplosiv\", \"op\": \"<=\", \"value\": 5"), "hard[4].when[0].fact"),
                broken("дубль id правила", "rules.json", TacticsTexts.mutate(rules, "\"id\": \"cornered_fight\"",
                        "\"id\": \"no_enemies_ignore\""), "hard[1].id"),
                broken("приоритет дробью", "rules.json", TacticsTexts.mutate(rules, "\"priority\": 95",
                        "\"priority\": 95.5"), "hard[1].priority"),
                broken("приоритет строкой", "rules.json", TacticsTexts.mutate(rules, "\"priority\": 95",
                        "\"priority\": \"95\""), "hard[1].priority"),
                broken("опечатка в режиме", "rules.json", TacticsTexts.mutate(rules, "\"value\": \"passive\"",
                        "\"value\": \"pasive\""), "hard[6].when[0].value"),
                broken("неизвестное действие", "rules.json", TacticsTexts.mutate(rules, "\"SHOOT\"", "\"SHOT\""),
                        "actions[0].then.action"),
                broken("нет замыкающего правила", "rules.json", TacticsTexts.mutate(rules,
                        ",\n    {\"id\": \"continue_default\", \"priority\": 10, \"when\": [], \"then\": {\"action\": \"CONTINUE\"}}",
                        ""), "actions"),
                broken("пустая таблица состояний", "rules.json", TacticsTexts.mutateRegex(rules,
                        "\"states\": \\[.*?\\n  \\],", "\"states\": [],"), "states"),
                broken("неизвестный способ отхода", "rules.json", TacticsTexts.mutate(rules, "\"HOME\"", "\"HOMME\""),
                        "retreat.methods[0].method"),
                broken("отход зависит от escapeAvailability", "rules.json", TacticsTexts.mutate(rules,
                        HOME_REQUIRES_FIRST, "{\"fact\": \"env.escapeAvailability\", \"op\": \">\", \"value\": 0}"),
                        "retreat.methods[0].requires"),
                broken("полезность без perUnit", "rules.json", TacticsTexts.mutate(rules, ", \"perUnit\": -0.00625", ""),
                        "retreat.methods[0].utility"),
                broken("состояние из неизвестного", "rules.json", TacticsTexts.mutateFirst(rules, "\"from\": [\"calm\"]",
                        "\"from\": [\"calmm\"]"), "states[0].from[0]"),
                broken("смена режима из неизвестного", "rules.json", TacticsTexts.mutate(rules,
                        "\"from\": [\"aggressive\"]", "\"from\": [\"agressive\"]"), "modeTransitions[0].from[0]"),
                broken("и decision, и exclude", "rules.json", TacticsTexts.mutate(rules,
                        "\"then\": {\"exclude\": [\"FIGHT\"]}", "\"then\": {\"decision\": \"IGNORE\", \"exclude\": [\"FIGHT\"]}"),
                        "hard[7].then"),
                broken("подмена RETREAT на RETREAT", "rules.json", TacticsTexts.mutate(rules,
                        "\"then\": {\"noRoute\": \"FIGHT\"}}", "\"then\": {\"noRoute\": \"RETREAT\"}}"),
                        "hard[11].then.noRoute"),
                broken("нет замыкающего правила подмены", "rules.json", TacticsTexts.mutate(rules,
                        ",\n    {\"id\": \"no_route_ignore\", \"priority\": 10, \"when\": [], \"then\": {\"noRoute\": \"IGNORE\"}}", ""),
                        "hard"),
                broken("неизвестная секция", "rules.json", TacticsTexts.mutateFirst(rules, "\"hard\":", "\"hrad\":"),
                        "hrad"),
                broken("неизвестный режим в modes", "modes.json", TacticsTexts.mutate(modes, "\"guard_area\": {",
                        "\"guard_zone\": {"), "modes.guard_zone"),
                broken("отрицательный радиус", "modes.json", TacticsTexts.mutate(modes, "\"engageRadius\": 6",
                        "\"engageRadius\": -6"), "modes.defensive.engageRadius"),
                broken("радиус за границей", "modes.json", TacticsTexts.mutate(modes, "\"engageRadius\": 6",
                        "\"engageRadius\": 999"), "modes.defensive.engageRadius"),
                broken("допуск возврата за границей", "modes.json", TacticsTexts.mutateRegex(modes,
                        "\"returnTolerance\": [0-9.]+", "\"returnTolerance\": 100"), "returnTolerance"),
                broken("опечатка в якоре", "modes.json", TacticsTexts.mutate(modes, "\"anchor\": \"protected\"",
                        "\"anchor\": \"protecte\""), "modes.protect.anchor"),
                broken("булево строкой", "modes.json", TacticsTexts.mutateFirst(modes, "\"initiates\": false",
                        "\"initiates\": \"no\""), "modes.passive.initiates"));
    }

    @Test
    void fromJson_ArmorCapAboveConstructorLimit_FailsWithFileAndScalePath() {
        String weights = TacticsTexts.mutate(TacticsTexts.read("weights.json"), "\"armorCap\": 0.8", "\"armorCap\": 0.99");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> load("weights.json", weights),
                "Предел брони 0.99 выше границы конструктора ArmorModel (0.95): загрузка обязана отклонить файл");

        assertTrue(error.getMessage().contains("weights.json → scales.armorCap"),
                "Границы чисел держит конструктор, а ошибку с путём в файле строит ConfigNode.build: сообщение называет "
                        + "weights.json → scales.armorCap; получили: " + error.getMessage());
    }

    @Test
    void scoreWeights_WeightAboveLimit_RejectedByConstructorWithCellName() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> TacticsSensitivityTests.shifted(Factor.WIN_MARGIN, TacticalOption.FIGHT, 10),
                "Вес 22 выше границы матрицы: границы весов держит конструктор ScoreWeights, а не парсер");

        assertTrue(error.getMessage().contains("ScoreWeights.weights.WIN_MARGIN.FIGHT"),
                "Сообщение называет ячейку матрицы, чтобы правка шла по подсказке; получили: " + error.getMessage());
    }

    private static Arguments broken(String name, String file, String text, String path) {
        return Arguments.of(name, file, text, path);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenFiles")
    void fromJson_BrokenFile_FailsWithFileAndPath(String name, String file, String text, String path) {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> load(file, text),
                "Сломанная конфигурация («" + name + "») обязана отклоняться при загрузке: иначе ошибка всплывёт в бою");

        assertTrue(error.getMessage().contains(file) && error.getMessage().contains(path),
                "Сообщение называет файл " + file + " и путь " + path + " и подсказывает, что проверить; получили: "
                        + error.getMessage());
    }
}
