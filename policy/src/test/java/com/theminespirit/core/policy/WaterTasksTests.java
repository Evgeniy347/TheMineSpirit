package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.theminespirit.core.plan.FallbackNode;
import com.theminespirit.core.plan.ScriptCall;
import com.theminespirit.core.plan.SequenceNode;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WaterTasksTests {
    static Stream<Arguments> scripts() {
        return Stream.of(
                Arguments.of("swim", true),
                Arguments.of("cross_water", true),
                Arguments.of("fish", true),
                Arguments.of("dig_out", true),
                Arguments.of("walk_careful", true),
                Arguments.of("goto", false),
                Arguments.of("craft", false),
                Arguments.of("chop_tree", false),
                Arguments.of("", false),
                Arguments.of(null, false));
    }

    @ParameterizedTest(name = "скрипт {0}")
    @MethodSource("scripts")
    void allowsSwimming_Script_OnlyWaterWorkKeepsExemption(String script, boolean allowed) {
        assertTrue(
                WaterTasks.allowsSwimming(script) == allowed,
                "Скрипт «" + script + "»: плавать разрешено только работам, которые сами ведут духа водой "
                        + "(swim/cross_water/fish/dig_out). Остальные работы в воде обязаны выходить на сушу, "
                        + "иначе дух кружит в озере (observe17 A2/N5); ожидалось " + allowed);
    }

    @Test
    void allowsSwimming_PlannedDigOutBeforeStart_KeepsExemption() {
        assertTrue(
                WaterTasks.allowsSwimming(
                        null,
                        new SequenceNode(List.of(
                                new ScriptCall("dig_out", Map.of("x", 1, "y", 2, "z", 3)),
                                new ScriptCall("goto", Map.of("x", 1, "y", 2, "z", 3))))),
                "План спасения ещё не начат (скрипта нет), но первым шагом копает выход: водный рефлекс обязан "
                        + "уступить, иначе он держит духа в воде и задача спасения не стартует никогда");
    }

    @Test
    void allowsSwimming_PlannedDigOutInsideFallback_KeepsExemption() {
        assertTrue(
                WaterTasks.allowsSwimming(
                        "",
                        new FallbackNode(List.of(
                                new SequenceNode(List.of(new ScriptCall("dig_out", Map.of()))),
                                new ScriptCall("dig_out", Map.of())))),
                "Запасной путь спасения тоже копает выход: пустой скрипт не должен прятать первый шаг плана");
    }

    @Test
    void allowsSwimming_RunningNonWaterScript_PlanDoesNotHideIt() {
        assertFalse(
                WaterTasks.allowsSwimming("goto", new SequenceNode(List.of(new ScriptCall("dig_out", Map.of())))),
                "Уже идёт обычный шаг: план с dig_out впереди не отменяет выход из воды — иначе дух плавает, "
                        + "пока не утонет");
    }

    @Test
    void allowsSwimming_PlanWithoutWaterWork_IsFalse() {
        assertFalse(
                WaterTasks.allowsSwimming(null, new ScriptCall("close_door", Map.of())),
                "Обычная работа в воде не даёт права плавать: дух обязан выйти на сушу");
    }

    @ParameterizedTest(name = "скрипт «{0}»")
    @MethodSource("nearScripts")
    void allowsSwimming_NearMiss_IsFalse(String script) {
        assertFalse(
                WaterTasks.allowsSwimming(script),
                "«" + script + "» не водная работа: разрешение на плавание даётся только точному имени "
                        + "swim/cross_water/fish, иначе любая задача снова уводит духа в воду");
    }

    static Stream<String> nearScripts() {
        return Stream.of("swimming", "swim ", "SWIM", "cross-water", "fishing", "goto_water");
    }
}
