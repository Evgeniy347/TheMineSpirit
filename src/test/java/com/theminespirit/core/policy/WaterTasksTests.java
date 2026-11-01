package com.theminespirit.core.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class WaterTasksTests {
    static Stream<Arguments> scripts() {
        return Stream.of(
                Arguments.of("swim", true),
                Arguments.of("cross_water", true),
                Arguments.of("fish", true),
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
                "Скрипт «" + script + "»: плавать разрешено только явным водным работам (swim/cross_water/fish). "
                        + "Остальные работы в воде обязаны выходить на сушу, иначе дух кружит в озере "
                        + "(observe17 A2/N5); ожидалось " + allowed);
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
