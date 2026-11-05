package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MechModuleGuardTests {
    private static final Pattern IMPORT =
            Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.*]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final List<String> ALLOWED_PREFIXES = List.of(
            "java.", "com.fasterxml.", "org.jspecify.", "org.junit.", "org.opentest4j.", "net.jqwik.", "com.tngtech.");

    private record Module(String id, Path main, Path test, List<String> packages) {}

    private static final List<Module> MODULES = List.of(
            new Module(
                    ":mech:mining",
                    SourceFiles.MECH_MINING_PACKAGE,
                    SourceFiles.MECH_MINING_TEST_PACKAGE,
                    List.of("core/mining/")),
            new Module(
                    ":mech:movement",
                    SourceFiles.MECH_MOVEMENT_PACKAGE,
                    SourceFiles.MECH_MOVEMENT_TEST_PACKAGE,
                    List.of("core/movement/", "core/navigation/", "core/route/")),
            new Module(
                    ":mech:inventory",
                    SourceFiles.MECH_INVENTORY_PACKAGE,
                    SourceFiles.MECH_INVENTORY_TEST_PACKAGE,
                    List.of("core/inventory/")));

    @Test
    void mechSources_LiveInTheirModule_NotInRoot() {
        List<String> found = new ArrayList<>();
        for (Module module : MODULES) {
            if (!Files.isDirectory(module.main())) {
                found.add(module.id() + ": нет каталога " + module.main());
                continue;
            }
            Map<Path, String> sources = SourceFiles.javaUnder(module.main());
            if (sources.isEmpty()) {
                found.add(module.id() + ": каталог " + module.main() + " без исходников");
            }
            SourceFiles.allMain().forEach((file, text) -> {
                String logical = SourceFiles.logical(file);
                if (!belongsTo(module, logical)) {
                    return;
                }
                Path normalized = file.normalize();
                if (!normalized.startsWith(module.main()) && !normalized.startsWith(SourceFiles.API)) {
                    found.add(logical + " → " + file);
                }
            });
        }

        assertEquals(
                List.of(),
                found,
                ":mech:* — настоящие sourceSet: исходник механики обязан лежать в mech/<модуль>/src/main/java "
                        + "или, если это словарь/контракт, в :api. Копия в корне держит модуль в общей сборке "
                        + "и прячет зависимость от доски. Нарушители (логический путь → фактический):");
    }

    @Test
    void mechSources_ImportsResolveToApiAndOwn_NoViolations() {
        List<String> found = new ArrayList<>();
        for (Module module : MODULES) {
            Path main = module.main();
            if (!Files.isDirectory(main)) {
                found.add(module.id() + ": нет каталога " + main);
                continue;
            }
            found.addAll(violations(
                    SourceFiles.javaUnder(main), List.of(SourceFiles.API, main), List.of(SourceFiles.API, main)));
        }

        assertEquals(
                List.of(),
                found,
                "Механика зависит только от :api (и разрешённых механик): импорт класса корня делает "
                        + "модуль частью общей сборки, а импорт доски/графа запрещён прямо. Как надо: "
                        + "контракт в api/src/main/java (пакет не меняй), реализацию — у владельца. Нарушители:");
    }

    @Test
    void mechTests_LiveInModule_AndImportOnlyAllowedClasses() {
        List<String> misplaced = new ArrayList<>();
        List<String> imports = new ArrayList<>();
        Map<Path, String> tests = SourceFiles.allTests();
        for (Module module : MODULES) {
            if (Files.isDirectory(module.test())) {
                imports.addAll(violations(
                        SourceFiles.javaUnder(module.test()),
                        List.of(SourceFiles.API, module.main(), module.test()),
                        List.of(SourceFiles.API_TEST, module.test())));
            } else {
                misplaced.add(module.id() + ": нет каталога тестов " + module.test());
            }
            tests.forEach((file, text) -> {
                String logical = SourceFiles.logical(file);
                if (!belongsTo(module, logical)) {
                    return;
                }
                Path normalized = file.normalize();
                if (!normalized.startsWith(module.test()) && !normalized.startsWith(SourceFiles.API_TEST)) {
                    misplaced.add(logical + " → " + file);
                }
            });
        }

        assertEquals(
                List.of(),
                misplaced,
                "Тесты механики живут в mech/<модуль>/src/test/java: иначе модуль нельзя прогнать отдельно, "
                        + "а общий прогон скрывает его собственные зависимости. Нарушители (логический путь → фактический):");
        assertEquals(
                List.of(),
                imports,
                "Тест механики компилируется только с :api и своим модулем: импорт корня/доски в тесте "
                        + "скрывает, что модуль без корня не собрать. Нарушители:");
    }

    @Test
    void mechSources_BoardAndGraphImport_IsReported() {
        Module mining = MODULES.get(0);
        Map<Path, String> sample = Map.of(Path.of("mech/mining/src/main/java/Forged.java"), """
                package com.theminespirit.core.mining;
                import com.theminespirit.core.board.BoardTicket;
                class Forged {}
                """);
        Map<Path, String> contract = Map.of(Path.of("mech/mining/src/main/java/Tool.java"), """
                package com.theminespirit.core.mining;
                import com.theminespirit.core.board.ToolsConfig;
                class Tool {}
                """);
        Map<Path, String> root = Map.of(Path.of("mech/mining/src/main/java/Root.java"), """
                package com.theminespirit.core.mining;
                import com.theminespirit.core.script.CoreScripts;
                class Root {}
                """);

        assertEquals(
                List.of("mech/mining/src/main/java/Forged.java: com.theminespirit.core.board.BoardTicket"),
                violations(sample, List.of(SourceFiles.API, mining.main()), List.of(SourceFiles.API, mining.main())),
                "Сторож ослеп: импорт доски из механики обязан попасть в нарушители");
        assertFalse(
                violations(contract, List.of(SourceFiles.API, mining.main()), List.of(SourceFiles.API, mining.main()))
                        .stream()
                        .anyMatch(line -> line.contains("ToolsConfig")),
                "Словарь :api (ToolsConfig) не считается доской: иначе шаг 7 нельзя выполнить без переименования пакетов");
        assertEquals(
                List.of("mech/mining/src/main/java/Root.java: com.theminespirit.core.script.CoreScripts"),
                violations(root, List.of(SourceFiles.API, mining.main()), List.of(SourceFiles.API, mining.main())),
                "Сторож ослеп: импорт класса корня из механики обязан попасть в нарушители");
    }

    private static boolean belongsTo(Module module, String logical) {
        for (String prefix : module.packages()) {
            if (logical.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> violations(Map<Path, String> sources, List<Path> allowedMain, List<Path> allowedTest) {
        List<String> found = new ArrayList<>();
        sources.forEach((file, text) -> {
            Matcher imports = IMPORT.matcher(text);
            while (imports.find()) {
                String name = imports.group(1);
                if (name.endsWith(".*")) {
                    name = name.substring(0, name.length() - 2);
                }
                if (allowedPrefix(name)) {
                    continue;
                }
                Path target;
                try {
                    target = resolve(name);
                } catch (IllegalArgumentException absent) {
                    found.add(SourceFiles.logical(file) + ": " + name);
                    continue;
                }
                if (under(target, allowedMain) || under(target, allowedTest)) {
                    continue;
                }
                found.add(SourceFiles.logical(file) + ": " + name);
            }
        });
        return found;
    }

    private static Path resolve(String imported) {
        String relative = imported.startsWith("com.theminespirit.")
                ? imported.substring("com.theminespirit.".length())
                : imported;
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < relative.length(); i++) {
            if (relative.charAt(i) == '.') {
                parts.add(relative.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(relative.substring(start));
        IllegalArgumentException last = null;
        for (int length = parts.size(); length > 0; length--) {
            StringBuilder logical = new StringBuilder();
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    logical.append('/');
                }
                logical.append(parts.get(i));
            }
            logical.append(".java");
            try {
                return SourceFiles.path(logical.toString()).normalize();
            } catch (IllegalArgumentException absent) {
                last = absent;
            }
        }
        throw last == null
                ? new IllegalArgumentException("Импорт без класса: " + imported)
                : new IllegalArgumentException(last.getMessage(), last);
    }

    private static boolean under(Path target, List<Path> roots) {
        for (Path root : roots) {
            if (target.startsWith(root.normalize())) {
                return true;
            }
        }
        return false;
    }

    private static boolean allowedPrefix(String name) {
        for (String prefix : ALLOWED_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void guard_DoesNotTrustPackageName_OnlyPhysicalModule() {
        Module inventory = MODULES.get(2);
        String text = """
                package com.theminespirit.core.inventory;
                import com.theminespirit.core.mining.ToolChoice;
                class Forged {}
                """;

        assertEquals(
                1,
                violations(
                                Map.of(Path.of("mech/inventory/src/main/java/Forged.java"), text),
                                List.of(SourceFiles.API, inventory.main()),
                                List.of(SourceFiles.API, inventory.main()))
                        .size(),
                "Сторож обязан отличать модуль по физическому пути, а не по имени пакета: ToolChoice в :mech:mining "
                        + "недоступен :mech:inventory");
        assertTrue(
                PACKAGE.matcher(text).find(),
                "Тестовый образец обязан содержать package: иначе проверка пути ничего не значит");
    }
}
