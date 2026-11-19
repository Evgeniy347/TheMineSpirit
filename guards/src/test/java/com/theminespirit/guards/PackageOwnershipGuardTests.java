package com.theminespirit.guards;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PackageOwnershipGuardTests {
    private static final Path BASELINE = Path.of("config", "package-split-baseline.txt");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+([\\w.]+)\\s*;");
    private static final String RULE = "Один пакет объявлен двумя модулями: владелец пакета не определён, "
            + "JPMS и правило «пакет = модуль» невозможны, а покрытие по модулям искажается — классы api, "
            + "которые гоняют тесты mech, выпадают из отчёта api. Как надо: контракты и данные — в одном "
            + "модуле, алгоритмы — в другом, пакеты не пересекаются. Новый дубль пакета запрещён; "
            + "перенёс классы — сократи config/package-split-baseline.txt.";

    @Test
    void package_DeclaredInTwoModules_IsReported() {
        assertEquals(
                List.of("com.x: один пакет объявлен в модулях api + mech/x"),
                problems(Map.of("com.x", List.of("api", "mech/x")), Set.of()),
                "Сторож ослеп: пакет в двух модулях обязан попасть в нарушители");
    }

    @Test
    void package_OnlyInBaseline_IsAccepted() {
        assertEquals(
                List.of(),
                problems(Map.of("com.x", List.of("api", "mech/x")), Set.of("com.x")),
                "Сторож ложно срабатывает: известный дубль из списка исключений допустим до переноса");
    }

    @Test
    void baseline_PackageMovedToOneModule_IsReported() {
        assertEquals(
                List.of("com.x (api): пакет уже в одном модуле — убери запись из списка"),
                problems(Map.of("com.x", List.of("api")), Set.of("com.x")),
                "Сторож ослеп: исправленный пакет обязан уйти из списка исключений");
    }

    @Test
    void packages_RealOwners_AreSingleOrBaselined() {
        assertEquals(
                List.of(),
                problems(owners(), baseline()),
                RULE + System.lineSeparator() + "Нарушители (пакет: модули):");
    }

    private static Map<String, List<String>> owners() {
        List<Path> roots = new ArrayList<>(SourceFiles.moduleMainRoots());
        roots.add(SourceFiles.API);
        Map<String, List<String>> owners = new TreeMap<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            String module = moduleLabel(root);
            SourceFiles.javaUnder(root).values().forEach(text -> {
                Matcher matcher = PACKAGE.matcher(text);
                if (matcher.find()) {
                    owners.computeIfAbsent(matcher.group(1), pkg -> new ArrayList<>())
                            .add(module);
                }
            });
        }
        return owners;
    }

    private static String moduleLabel(Path root) {
        String path = root.toString().replace('\\', '/');
        int cut = path.indexOf("/src/main/java");
        return cut < 0 ? path : path.substring(0, cut);
    }

    private static Set<String> baseline() {
        try {
            Set<String> entries = new TreeSet<>();
            Files.readAllLines(BASELINE, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .forEach(entries::add);
            return entries;
        } catch (IOException e) {
            throw new UncheckedIOException("Сторож не смог прочитать " + BASELINE, e);
        }
    }

    private static List<String> problems(Map<String, List<String>> owners, Set<String> baseline) {
        List<String> found = new ArrayList<>();
        new TreeMap<>(owners).forEach((pkg, modules) -> {
            Set<String> unique = new TreeSet<>(modules);
            if (unique.size() > 1 && !baseline.contains(pkg)) {
                found.add(pkg + ": один пакет объявлен в модулях " + String.join(" + ", unique));
            }
        });
        new TreeSet<>(baseline).forEach(pkg -> {
            List<String> modules = owners.get(pkg);
            Set<String> unique = modules == null ? Set.of() : new TreeSet<>(modules);
            if (unique.isEmpty()) {
                found.add(pkg + ": пакета нет — убери запись из списка");
            } else if (unique.size() == 1) {
                found.add(
                        pkg + " (" + unique.iterator().next() + "): пакет уже в одном модуле — убери запись из списка");
            }
        });
        return found;
    }
}
