package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MechanicsModuleGuardTests {
    private static final Pattern IMPORT =
            Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.*]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final List<String> ALLOWED_PREFIXES = List.of("java.", "com.fasterxml.", "org.jspecify.");

    private record Module(String id, List<String> packageNames, Path mainRoot, Path testRoot) {
        List<Path> mainPackages() {
            return packageNames.stream()
                    .map(packageName -> mainRoot.resolve("com/theminespirit/" + packageName.replace('.', '/')))
                    .toList();
        }

        List<Path> testPackages() {
            return packageNames.stream()
                    .map(packageName -> testRoot.resolve("com/theminespirit/" + packageName.replace('.', '/')))
                    .toList();
        }
    }

    private static final List<Module> DECLARED_MODULES = List.of(
            new Module(
                    ":policy",
                    List.of("core.policy"),
                    Path.of("policy/src/main/java"),
                    Path.of("policy/src/test/java")),
            new Module(
                    ":mech:farming",
                    List.of("core.farming"),
                    Path.of("mech/farming/src/main/java"),
                    Path.of("mech/farming/src/test/java")),
            new Module(
                    ":mech:forestry",
                    List.of("core.forestry"),
                    Path.of("mech/forestry/src/main/java"),
                    Path.of("mech/forestry/src/test/java")),
            new Module(
                    ":mech:crafting",
                    List.of("core.crafting"),
                    Path.of("mech/crafting/src/main/java"),
                    Path.of("mech/crafting/src/test/java")),
            new Module(
                    ":mech:husbandry",
                    List.of("core.husbandry"),
                    Path.of("mech/husbandry/src/main/java"),
                    Path.of("mech/husbandry/src/test/java")),
            new Module(
                    ":mech:small",
                    List.of(
                            "core.alchemy",
                            "core.armor",
                            "core.doors",
                            "core.experience",
                            "core.hunger",
                            "core.night",
                            "core.repair",
                            "core.sleep"),
                    Path.of("mech/small/src/main/java"),
                    Path.of("mech/small/src/test/java")));

    private static List<Module> modules() {
        List<Path> declared = SourceFiles.moduleMainRoots();
        return DECLARED_MODULES.stream()
                .filter(module -> declared.contains(module.mainRoot()))
                .toList();
    }

    @Test
    void moduleSources_LiveInTheirOwnSourceSet_NotInRoot() {
        List<String> missing = new ArrayList<>();
        for (Module module : modules()) {
            for (Path packageDir : module.mainPackages()) {
                if (!Files.isDirectory(packageDir)) {
                    missing.add(module.id() + ": нет каталога " + packageDir);
                    continue;
                }
                for (Path file : SourceFiles.javaUnder(packageDir).keySet()) {
                    if (!file.normalize().startsWith(module.mainRoot())) {
                        missing.add(module.id() + ": " + SourceFiles.logical(file));
                    }
                }
            }
        }

        assertEquals(
                List.of(),
                missing,
                "Механика обязана физически лежать в своём модуле (шаг 7): классы с прежним пакетом в "
                        + "module/src/main/java, тесты — в module/src/test/java. Иначе модуль не собирается сам "
                        + "и правка механики гоняет все тесты корня. Нарушители:");
    }

    @Test
    void moduleTests_DeclaredModules_HaveTheirOwnTests() {
        List<String> missing = new ArrayList<>();
        for (Module module : modules()) {
            for (Path packageDir : module.testPackages()) {
                if (!Files.isDirectory(packageDir)
                        || SourceFiles.javaUnder(packageDir).isEmpty()) {
                    missing.add(module.id() + ": нет тестов в " + packageDir);
                }
            }
        }

        assertEquals(
                List.of(),
                missing,
                "У модуля обязаны быть свои тесты: иначе ./gradlew :module:test нечего проверять, а перенос "
                        + "молча отключил покрытие. Нарушители:");
    }

    @Test
    void mechanicsModule_Imports_OnlyJdkJacksonApiAndOwnPackage() {
        Set<String> apiClasses = apiClasses(SourceFiles.javaUnder(SourceFiles.apiPackage()));
        List<String> violations = new ArrayList<>();
        for (Module module : modules()) {
            for (Path packageDir : module.mainPackages()) {
                if (!Files.isDirectory(packageDir)) {
                    continue;
                }
                violations.addAll(violations(SourceFiles.javaUnder(packageDir), apiClasses, module.packageNames()));
            }
        }

        assertEquals(
                List.of(),
                violations,
                "Механика зависит только от :api: импорт корня (board/graph/reload/…), другой механики или "
                        + "Minecraft возвращает циклическую зависимость и запрещает отдельную сборку. Как надо: "
                        + "нужный контракт — в :api, данные и граф передаёт владелец (доска/граф знает механику, "
                        + "а не наоборот). Нарушители:");
    }

    @Test
    void forbiddenImport_IsReported_AndApiImportAllowed() {
        Map<Path, String> sample =
                Map.of(Path.of("policy/src/main/java/com/theminespirit/core/policy/Example.java"), """
                package com.theminespirit.core.policy;
                import com.theminespirit.core.reload.DataTables;
                class Example {}
                """);
        Map<Path, String> neighbor = Map.of(Path.of("policy/src/main/java/com/theminespirit/core/policy/Ok.java"), """
                package com.theminespirit.core.policy;
                import com.theminespirit.core.observation.BlockCoord;
                class Ok {}
                """);
        Set<String> apiClasses = Set.of("com.theminespirit.core.observation.BlockCoord");

        assertEquals(
                List.of("core/policy/Example.java: com.theminespirit.core.reload.DataTables"),
                violations(sample, apiClasses, List.of("core.policy")),
                "Сторож ослеп: импорт класса корня обязан попадать в нарушители");
        assertEquals(
                List.of(),
                violations(neighbor, apiClasses, List.of("core.policy")),
                "Сторож ложно срабатывает: импорт класса :api допустим");
        Map<Path, String> sibling = Map.of(Path.of("mech/small/src/main/java/core/night/Night.java"), """
                package com.theminespirit.core.night;
                import com.theminespirit.core.doors.DoorTuning;
                class Night {}
                """);
        assertEquals(
                List.of(),
                violations(sibling, apiClasses, List.of("core.doors", "core.night")),
                "Сторож ложно срабатывает: пакеты одного модуля (:mech:small) вправе знать друг друга");
    }

    private static Set<String> apiClasses(Map<Path, String> sources) {
        Set<String> classes = new LinkedHashSet<>();
        sources.forEach((file, text) -> {
            Matcher pkg = PACKAGE.matcher(text);
            assertTrue(pkg.find(), "Файл " + file + " без package: :api не может быть частью пакета");
            classes.add(pkg.group(1) + "." + file.getFileName().toString().replace(".java", ""));
        });
        return classes;
    }

    private static List<String> violations(
            Map<Path, String> sources, Set<String> apiClasses, List<String> ownPackages) {
        List<String> found = new ArrayList<>();
        sources.forEach((file, text) -> {
            Matcher matcher = IMPORT.matcher(text);
            while (matcher.find()) {
                String name = matcher.group(1);
                if (name.endsWith(".*")) {
                    name = name.substring(0, name.length() - 2);
                }
                if (allowed(name, apiClasses, ownPackages)) {
                    continue;
                }
                found.add(SourceFiles.logical(file) + ": " + name);
            }
        });
        return found;
    }

    private static boolean allowed(String name, Set<String> apiClasses, List<String> ownPackages) {
        for (String prefix : ALLOWED_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        for (String ownPackage : ownPackages) {
            String own = "com.theminespirit." + ownPackage;
            if (name.equals(own) || name.startsWith(own + ".")) {
                return true;
            }
        }
        for (String apiClass : apiClasses) {
            if (name.equals(apiClass) || name.startsWith(apiClass + ".")) {
                return true;
            }
        }
        return false;
    }
}
