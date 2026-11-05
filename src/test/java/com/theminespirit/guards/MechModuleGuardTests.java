package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MechModuleGuardTests {
    private static final Pattern IMPORT =
            Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.*]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final List<String> ALLOWED_PREFIXES = List.of("java.", "com.fasterxml.", "org.jspecify.");

    @Test
    void buildingSources_PhysicalLocation_LivesInBuildingModule() {
        assertInModule("core/building/", SourceFiles.buildingPackage(), "':mech:building'");
    }

    @Test
    void townSources_PhysicalLocation_LivesInTownModule() {
        assertInModule("core/town/", SourceFiles.townPackage(), "':mech:town'");
    }

    @Test
    void buildingSources_ForeignImport_IsReported() {
        Set<String> allowed = classesIn(SourceFiles.apiPackage());
        allowed.addAll(classesIn(SourceFiles.buildingPackage()));
        assertEquals(
                List.of(),
                violations(SourceFiles.javaUnder(SourceFiles.buildingPackage()), allowed),
                "Модуль :mech:building обязан собираться без доски и остального корня: разрешены JDK, Jackson, "
                        + "jspecify, классы :api и свои. Импорт чужого класса возвращает цикл. Как надо: факт или "
                        + "контракт перенеси в :api, реализацию оставь у владельца. Нарушители:");
    }

    @Test
    void townSources_ForeignImport_IsReported() {
        Set<String> allowed = classesIn(SourceFiles.apiPackage());
        allowed.addAll(classesIn(SourceFiles.buildingPackage()));
        allowed.addAll(classesIn(SourceFiles.townPackage()));
        assertEquals(
                List.of(),
                violations(SourceFiles.javaUnder(SourceFiles.townPackage()), allowed),
                "Модуль :mech:town зависит только от :api и :mech:building: поселение не знает доску. "
                        + "Геометрию участков держит town, доска спрашивает факты. Нарушители:");
    }

    @Test
    void townSources_BoardImport_IsReported() {
        List<String> found = new ArrayList<>();
        SourceFiles.javaUnder(SourceFiles.townPackage()).forEach((file, text) -> {
            Matcher matcher = IMPORT.matcher(text);
            while (matcher.find()) {
                String name = matcher.group(1);
                if (name.startsWith("com.theminespirit.core.board.")) {
                    String contract = name.substring(0, name.lastIndexOf('.'));
                    String simple = contract.substring(contract.lastIndexOf('.') + 1);
                    Path target;
                    try {
                        target = SourceFiles.path("core/board/" + simple + ".java")
                                .normalize();
                    } catch (IllegalArgumentException absent) {
                        found.add(SourceFiles.logical(file) + ": " + name);
                        continue;
                    }
                    if (!target.startsWith(SourceFiles.API)) {
                        found.add(SourceFiles.logical(file) + ": " + name);
                    }
                }
            }
        });

        assertEquals(
                List.of(),
                found,
                "Механика поселения не знает доску: иначе :mech:town не собрать без :board. Как надо: геометрию "
                        + "участка перенеси в town, а доске оставь запрос фактов. Нарушители:");
    }

    @Test
    void forbiddenImport_BoardImplementation_IsReported() {
        Map<Path, String> sample = Map.of(Path.of("mech/town/Example.java"), """
                package com.theminespirit.core.town;
                import com.theminespirit.core.board.BoardCase;
                class Example {}
                """);

        assertEquals(
                List.of(),
                violations(sample, Set.of("com.theminespirit.core.board.BoardCase")),
                "API-контракт доски в :api допустим механике поселения");
        assertEquals(
                List.of("mech/town/Example.java: com.theminespirit.core.board.TicketPlans"),
                violations(
                        Map.of(Path.of("mech/town/Example.java"), """
                                package com.theminespirit.core.town;
                                import com.theminespirit.core.board.TicketPlans;
                                class Example {}
                                """),
                        Set.of("com.theminespirit.core.board.BoardCase")),
                "Сторож ослеп: импорт реализации доски обязан попасть в нарушители");
    }

    private static void assertInModule(String logicalPrefix, Path modulePackage, String moduleName) {
        List<String> misplaced = new ArrayList<>();
        SourceFiles.allMain().forEach((file, text) -> {
            if (SourceFiles.logical(file).startsWith(logicalPrefix)
                    && !file.normalize().startsWith(modulePackage)
                    && !file.normalize().startsWith(SourceFiles.API)) {
                misplaced.add(SourceFiles.logical(file) + " → " + file);
            }
        });
        assertEquals(
                List.of(),
                misplaced,
                "Механика " + logicalPrefix + " обязана физически лежать в " + moduleName
                        + ": иначе модуль не собирается сам, а корень снова тянет всё. Нарушители:");
    }

    private static Set<String> classesIn(Path packageRoot) {
        Set<String> classes = new LinkedHashSet<>();
        SourceFiles.javaUnder(packageRoot).forEach((file, text) -> {
            Matcher pkg = PACKAGE.matcher(text);
            assertTrue(pkg.find(), "Файл " + file + " без package: модуль не может быть частью пакета");
            classes.add(pkg.group(1) + "." + file.getFileName().toString().replace(".java", ""));
        });
        return classes;
    }

    private static List<String> violations(Map<Path, String> sources, Set<String> allowedClasses) {
        List<String> found = new ArrayList<>();
        sources.forEach((file, text) -> {
            Matcher matcher = IMPORT.matcher(text);
            while (matcher.find()) {
                String name = matcher.group(1);
                if (name.endsWith(".*")) {
                    name = name.substring(0, name.length() - 2);
                }
                if (allowed(name, allowedClasses)) {
                    continue;
                }
                found.add(SourceFiles.logical(file) + ": " + name);
            }
        });
        return found;
    }

    private static boolean allowed(String name, Set<String> allowedClasses) {
        for (String prefix : ALLOWED_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        for (String allowedClass : allowedClasses) {
            if (name.equals(allowedClass) || name.startsWith(allowedClass + ".")) {
                return true;
            }
        }
        return false;
    }
}
