package com.theminespirit.guards;

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
import java.util.stream.Stream;

/** lib: модель модулей-механик для сторожа — модуль, его пакеты и объявленные тестовые зависимости. */
final class MechanicsModuleSources {
    record Module(String id, List<String> packageNames, Path mainRoot, Path testRoot) {
        List<Path> mainPackages() {
            return packageNames.stream().map(name -> packageDir(mainRoot, name)).toList();
        }

        List<Path> testPackages() {
            return packageNames.stream().map(name -> packageDir(testRoot, name)).toList();
        }

        private static Path packageDir(Path root, String packageName) {
            return root.resolve("com/theminespirit/" + packageName.replace('.', '/'));
        }
    }

    private static final String MAIN_SUFFIX = "/src/main/java";
    private static final Pattern INCLUDE = Pattern.compile("^\\s*include\\s+'(:[^']+)'", Pattern.MULTILINE);
    private static final Pattern MECHANICS_LIST = Pattern.compile("(?s)def\\s+mechanicsModules\\s*=\\s*\\[(.*?)]");
    private static final Pattern MODULE_ID = Pattern.compile("'(:[^']+)'");
    private static final Pattern TEST_DEPENDENCY = Pattern.compile("testImplementation\\s+project\\('(:[^']+)'\\)");
    private static final Pattern FIXTURES = Pattern.compile("testFixtures\\(project\\(':test-fixtures'\\)\\)");
    private static final Pattern MODULE_FIXTURES = Pattern.compile("testFixtures\\(project\\('(:[^']+)'\\)\\)");

    private MechanicsModuleSources() {}

    static List<Module> modules() {
        List<Module> modules = new ArrayList<>();
        Set<String> mechanics = mechanicsModules();
        for (Path mainRoot : SourceFiles.moduleMainRoots()) {
            String path = mainRoot.toString().replace('\\', '/');
            if (!path.endsWith(MAIN_SUFFIX)) {
                continue;
            }
            String id = ":"
                    + path.substring(0, path.length() - MAIN_SUFFIX.length()).replace('/', ':');
            if (!mechanics.contains(id) || !Files.isDirectory(mainRoot)) {
                continue;
            }
            Path testRoot = mainRoot.getParent().getParent().resolve("test/java");
            modules.add(new Module(id, packages(mainRoot), mainRoot, testRoot));
        }
        return List.copyOf(modules);
    }

    static Set<String> mechanicsModules() {
        String settings = read("settings.gradle");
        Set<String> mechanics = new LinkedHashSet<>();
        Set<String> included = new LinkedHashSet<>();
        Matcher include = INCLUDE.matcher(settings);
        while (include.find()) {
            String id = include.group(1);
            included.add(id);
            if (id.startsWith(":mech:")) {
                mechanics.add(id);
            }
        }
        Matcher list = MECHANICS_LIST.matcher(settings);
        assertTrue(
                list.find(),
                "В settings.gradle нет списка def mechanicsModules = [...]: тип модуля не вывести. Как надо: "
                        + "механики под mech/ находятся по каталогу, а верхнеуровневую (:policy) перечисли в списке.");
        Matcher id = MODULE_ID.matcher(list.group(1));
        while (id.find()) {
            mechanics.add(id.group(1));
        }
        assertTrue(
                !mechanics.isEmpty(),
                "Модулей-механик не нашлось: сторож молча проверяет ноль механик. Как надо: модуль механики лежит "
                        + "в mech/ или перечислен в mechanicsModules.");
        assertTrue(
                included.containsAll(mechanics),
                "Список mechanicsModules называет модуль, которого нет в include settings.gradle: тип и сборка "
                        + "разъедутся. Лишние: "
                        + mechanics.stream()
                                .filter(module -> !included.contains(module))
                                .toList());
        return Set.copyOf(mechanics);
    }

    static List<String> packages(Path mainRoot) {
        Path packageRoot = mainRoot.resolve("com/theminespirit");
        if (!Files.isDirectory(packageRoot)) {
            return List.of();
        }
        List<String> packages = new ArrayList<>();
        try (Stream<Path> files = Files.walk(packageRoot)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String relative =
                        packageRoot.relativize(file.getParent()).toString().replace('\\', '/');
                String name = relative.replace('/', '.');
                if (!packages.contains(name)) {
                    packages.add(name);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог прочитать пакеты модуля в " + packageRoot, e);
        }
        return packages;
    }

    static List<String> declaredTestDependencies(Module module) {
        Path build = module.mainRoot().getParent().getParent().getParent().resolve("build.gradle");
        if (!Files.isRegularFile(build)) {
            return List.of();
        }
        String text = read(build.toString());
        List<String> packages = new ArrayList<>();
        Matcher dependency = TEST_DEPENDENCY.matcher(text);
        while (dependency.find()) {
            Path root = moduleMainRoot(dependency.group(1));
            if (root != null) {
                packages.addAll(packages(root));
            }
        }
        if (FIXTURES.matcher(text).find()) {
            packages.addAll(testFixturePackages());
        }
        Matcher fixtures = MODULE_FIXTURES.matcher(text);
        while (fixtures.find()) {
            packages.addAll(fixturePackages(fixtures.group(1)));
        }
        return packages;
    }

    static Set<String> testFixturePackages() {
        Path root = Path.of("test-fixtures/src/testFixtures/java");
        return Files.isDirectory(root.resolve("com/theminespirit")) ? new LinkedHashSet<>(packages(root)) : Set.of();
    }

    private static Set<String> fixturePackages(String id) {
        Path root = Path.of(id.substring(1).replace(':', '/'), "src/testFixtures/java");
        return Files.isDirectory(root.resolve("com/theminespirit")) ? new LinkedHashSet<>(packages(root)) : Set.of();
    }

    private static Path moduleMainRoot(String id) {
        Path root = Path.of(id.substring(1).replace(':', '/'), "src/main/java");
        return Files.isDirectory(root.resolve("com/theminespirit")) ? root : null;
    }

    private static String read(String file) {
        try {
            return Files.readString(Path.of(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог прочитать " + file, e);
        }
    }
}
