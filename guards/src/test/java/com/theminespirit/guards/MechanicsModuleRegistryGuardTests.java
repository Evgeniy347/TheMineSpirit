package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * lib: каждый include в settings.gradle обязан иметь тип — механика (mech/* или mechanicsModules) либо
 * инфраструктура (nonMechanicModules). Иначе новый модуль молча останется без сторожа импортов.
 */
class MechanicsModuleRegistryGuardTests {
    private static final Pattern INCLUDE = Pattern.compile("^\\s*include\\s+'(:[^']+)'", Pattern.MULTILINE);
    private static final Pattern MECHANICS = Pattern.compile("(?s)def\\s+mechanicsModules\\s*=\\s*\\[(.*?)]");
    private static final Pattern NON_MECHANICS = Pattern.compile("(?s)def\\s+nonMechanicModules\\s*=\\s*\\[(.*?)]");
    private static final Pattern MODULE_ID = Pattern.compile("'(:[^']+)'");

    @Test
    void includedModules_WithoutType_AreReported() {
        String settings = readSettings();
        Set<String> mechanics = declared(MECHANICS, settings, "mechanicsModules");
        Set<String> infrastructure = declared(NON_MECHANICS, settings, "nonMechanicModules");
        List<String> unclassified = new ArrayList<>();
        for (String id : included(settings)) {
            boolean mechanic = id.startsWith(":mech:") || mechanics.contains(id);
            if (!mechanic && !infrastructure.contains(id)) {
                unclassified.add(id);
            }
        }

        assertEquals(
                List.of(),
                unclassified,
                "Модуль не механика и не объявлен инфраструктурой: его импорты останутся без сторожа, а тесты — "
                        + "без тира. Как надо: механику положи в mech/ или в def mechanicsModules, инфраструктуру — "
                        + "в def nonMechanicModules в settings.gradle. Нарушители:");
    }

    @Test
    void declaredTypes_NotIncluded_AreReported() {
        String settings = readSettings();
        Set<String> included = included(settings);
        List<String> unknown = new ArrayList<>();
        for (String id : declared(MECHANICS, settings, "mechanicsModules")) {
            if (!included.contains(id)) {
                unknown.add("mechanicsModules: " + id);
            }
        }
        for (String id : declared(NON_MECHANICS, settings, "nonMechanicModules")) {
            if (!included.contains(id)) {
                unknown.add("nonMechanicModules: " + id);
            }
        }

        assertEquals(
                List.of(),
                unknown,
                "Список типов называет модуль, которого нет в include: тип и сборка разъедутся. Как надо: убери "
                        + "строку или добавь include. Нарушители:");
    }

    private static String readSettings() {
        try {
            return Files.readString(Path.of("settings.gradle"));
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог прочитать settings.gradle", e);
        }
    }

    private static Set<String> included(String settings) {
        Set<String> included = new LinkedHashSet<>();
        Matcher include = INCLUDE.matcher(settings);
        while (include.find()) {
            included.add(include.group(1));
        }
        assertTrue(
                !included.isEmpty(),
                "В settings.gradle не нашлось ни одного include: сторож проверяет нуль модулей и молча зелёный");
        return included;
    }

    private static Set<String> declared(Pattern pattern, String settings, String name) {
        Matcher list = pattern.matcher(settings);
        assertTrue(
                list.find(),
                "В settings.gradle нет def " + name + " = [...]: тип модуля не вывести. Как надо: перечисли "
                        + "верхнеуровневые модули явно, чтобы сторож знал, что проверять.");
        Set<String> modules = new LinkedHashSet<>();
        Matcher id = MODULE_ID.matcher(list.group(1));
        while (id.find()) {
            modules.add(id.group(1));
        }
        return modules;
    }
}
