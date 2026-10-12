package com.theminespirit.core.tactics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ConditionTests {
    private static Condition condition(String arrayJson) {
        return ConditionParser.parse(ConfigNode.root("rules.json", arrayJson));
    }

    private static Condition atom(String fact, String op, String value) {
        return condition("[{\"fact\": \"" + fact + "\", \"op\": \"" + op + "\", \"value\": " + value + "}]");
    }

    private static Arguments row(String name, UnaryOperator<SituationBuilder> world, String fact, String op,
            String value, boolean expected) {
        return Arguments.of(name, world, fact, op, value, expected);
    }

    static Stream<Arguments> atoms() {
        UnaryOperator<SituationBuilder> quarterHp = builder -> builder.hp(5);
        UnaryOperator<SituationBuilder> zombieAt3 = builder -> builder.enemy("zombie", 3);
        UnaryOperator<SituationBuilder> skeleton = builder -> builder.enemy("skeleton", 10);
        UnaryOperator<SituationBuilder> plain = builder -> builder;
        return Stream.of(
                row("граница: меньше при равенстве", quarterHp, "self.hpFraction", "<", "0.25", false),
                row("граница: меньше или равно при равенстве", quarterHp, "self.hpFraction", "<=", "0.25", true),
                row("граница: больше при равенстве", quarterHp, "self.hpFraction", ">", "0.25", false),
                row("граница: больше или равно при равенстве", quarterHp, "self.hpFraction", ">=", "0.25", true),
                row("равно для числа", quarterHp, "self.hpFraction", "==", "0.25", true),
                row("не равно для числа", quarterHp, "self.hpFraction", "!=", "0.25", false),
                row("нет врагов: счёт равен нулю", plain, "enemy.count", "==", "0", true),
                row("враг ровно на границе <=", zombieAt3, "enemy.nearest", "<=", "3", true),
                row("враг ровно на границе <", zombieAt3, "enemy.nearest", "<", "3", false),
                row("булев факт равен true", builder -> builder.ammo(5), "self.hasRanged", "==", "true", true),
                row("булев факт при false", plain, "self.hasRanged", "==", "true", false),
                row("булев не равно", plain, "env.night", "!=", "true", true),
                row("перечисление равно", plain, "order.mode", "==", "\"aggressive\"", true),
                row("перечисление не равно", plain, "order.mode", "==", "\"passive\"", false),
                row("перечисление: не равно истинно", plain, "order.mode", "!=", "\"passive\"", true),
                row("in: значение в списке", builder -> builder.mode(CombatMode.DEFENSIVE), "order.mode", "in",
                        "[\"guard_area\", \"defensive\"]", true),
                row("in: значения нет в списке", plain, "order.mode", "in", "[\"guard_area\", \"defensive\"]", false),
                row("состояние автомата", plain, "state.tactical", "==", "\"calm\"", true),
                row("лава ровно на границе", builder -> builder.lava(2), "env.lavaNear", "<=", "2", true),
                row("стрелок среди врагов", skeleton, "enemy.anyRanged", "==", "true", true),
                row("дом недостижим по умолчанию", plain, "env.homeReachable", "==", "false", true),
                row("давно не били", plain, "time.sinceHit", ">=", "100", true),
                row("только что ударили", builder -> builder.sinceStrike(5), "time.sinceStrike", "<=", "30", true),
                row("давно не били врага", plain, "time.sinceStrike", ">=", "100", true),
                row("голод", builder -> builder.food(0.4), "self.food", "<", "0.5", true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("atoms")
    void matches_SingleAtom_FollowsOperatorSemantics(String name, UnaryOperator<SituationBuilder> world, String fact,
            String op, String value, boolean expected) {
        Situation situation = world.apply(SituationBuilder.create()).build();

        boolean actual = atom(fact, op, value).matches(Assessments.facts(situation));

        assertEquals(expected, actual,
                "Условие «" + name + "» (" + fact + " " + op + " " + value + "): граница оператора определяет, когда "
                        + "срабатывает жёсткое правило; перепроверьте < против <= в rules.json, если ожидание меняют");
    }

    @Test
    void matches_AllAtomsTrue_ConjunctionHolds() {
        Condition both = condition("[{\"fact\": \"enemy.count\", \"op\": \">=\", \"value\": 1},"
                + " {\"fact\": \"enemy.nearest\", \"op\": \"<=\", \"value\": 5}]");
        FactMap near = Assessments.facts(SituationBuilder.create().enemy("zombie", 4).build());
        FactMap far = Assessments.facts(SituationBuilder.create().enemy("zombie", 6).build());

        assertEquals(List.of(true, false), List.of(both.matches(near), both.matches(far)),
                "Условие — конъюнкция: если не выполнен хотя бы один атом, правило не срабатывает");
    }

    @Test
    void matches_EmptyCondition_AlwaysHolds() {
        assertTrue(condition("[]").matches(Assessments.facts(SituationBuilder.create().build())),
                "Пустое условие истинно всегда: замыкающие правила таблицы действий держатся на нём");
    }

    static Stream<Arguments> brokenAtoms() {
        return Stream.of(
                Arguments.of("неизвестный факт", "[{\"fact\": \"self.hp\", \"op\": \"<\", \"value\": 1}]", "[0].fact"),
                Arguments.of("неизвестный оператор", "[{\"fact\": \"self.armor\", \"op\": \"=~\", \"value\": 1}]",
                        "[0].op"),
                Arguments.of("сравнение булева факта", "[{\"fact\": \"env.night\", \"op\": \"<\", \"value\": true}]",
                        "[0].op"),
                Arguments.of("in для числа", "[{\"fact\": \"self.armor\", \"op\": \"in\", \"value\": [1]}]", "[0].op"),
                Arguments.of("число строкой", "[{\"fact\": \"self.armor\", \"op\": \"<\", \"value\": \"5\"}]",
                        "[0].value"),
                Arguments.of("булев факт числом", "[{\"fact\": \"env.night\", \"op\": \"==\", \"value\": 1}]",
                        "[0].value"),
                Arguments.of("неизвестный режим", "[{\"fact\": \"order.mode\", \"op\": \"==\", \"value\": \"guard\"}]",
                        "[0].value"),
                Arguments.of("in без списка", "[{\"fact\": \"order.mode\", \"op\": \"in\", \"value\": \"passive\"}]",
                        "[0].value"),
                Arguments.of("пустой список in", "[{\"fact\": \"order.mode\", \"op\": \"in\", \"value\": []}]",
                        "[0].value"),
                Arguments.of("лишнее поле атома",
                        "[{\"fact\": \"self.armor\", \"op\": \"<\", \"value\": 1, \"unit\": \"x\"}]", "[0].unit"),
                Arguments.of("нет оператора", "[{\"fact\": \"self.armor\", \"value\": 1}]", "[0].op"),
                Arguments.of("не массив", "{\"fact\": \"self.armor\"}", "массив"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("brokenAtoms")
    void parse_BrokenAtom_FailsWithPath(String name, String json, String path) {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> condition(json),
                "Ошибка в условии («" + name + "») должна ловиться при загрузке, а не при первом срабатывании правила");

        assertTrue(error.getMessage().contains("rules.json") && error.getMessage().contains(path),
                "Сообщение называет файл и путь " + path + ": получили " + error.getMessage());
    }
}
